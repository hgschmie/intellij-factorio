package de.softwareforge.factorio.profiler;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.profiler.api.CallTreeBuildingData;
import com.intellij.profiler.api.MultipleCallTreesProfilerData;
import com.intellij.profiler.ui.JBRunnerClosableTabs;
import com.intellij.profiler.ui.MainCallTreeDataComponent;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;

/** Per-snapshot filter. Reuses parsed data; filtering never reads Lua files or the snapshot. */
final class FactorioProfileModulePanel extends JPanel implements Disposable {
    private final Project project;
    private final FactorioProfileImporter.Data data;
    private final JButton modules = new JButton("Modules: All");
    private final JBLabel status = new JBLabel();
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final AtomicInteger generation = new AtomicInteger();
    private final MainCallTreeDataComponent view;
    private final com.intellij.profiler.ProfilerDumpPanel owner;
    private Set<String> selection; // Null = all modules, empty = no modules.
    private boolean filtering;
    private boolean lastAllIncludedUnattributed;

    FactorioProfileModulePanel(Project project, FactorioProfileImporter.Data data,
                               List<CallTreeBuildingData> trees, Set<String> selection,
                               boolean lastAllIncludedUnattributed, String selectedTab, Disposable parent) {
        super(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4)));
        this.project = project; this.data = data; this.selection = selection;
        this.lastAllIncludedUnattributed = lastAllIncludedUnattributed;
        owner = parent instanceof com.intellij.profiler.ProfilerDumpPanel panel ? panel : null;
        Disposer.register(parent, this);
        add(modules); add(status);
        modules.setEnabled(!data.modules.choices().isEmpty() && owner != null);
        modules.setToolTipText("Choose one or more modules. Calls they make into libraries and native functions are included.");
        modules.addActionListener(event -> {
            var dialog = new ModuleDialog(project, data.modules.choices(), this.selection);
            if (dialog.showAndGet()) selectModules(dialog.selection());
        });
        view = (MainCallTreeDataComponent) new MultipleCallTreesProfilerData(trees).createTopLevelComponent(project, parent);
        // Comparison actions require this exact native component as the dump's
        // top-level component. The filter is a toolbar inside it, not a wrapper.
        view.add(this, BorderLayout.NORTH);
        var tabs = UIUtil.findComponentOfType(view, JBRunnerClosableTabs.class);
        if (tabs != null && selectedTab != null) for (var tab : tabs.getTabs())
            if (selectedTab.equals(tab.getText())) { tabs.select(tab, true); break; }
        updateLabel();
    }

    void selectModules(Set<String> selected) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        if (disposed.get() || owner == null || owner.getContent().getManager() == null) return;
        Set<String> requested = selected == null ? null : Set.copyOf(selected);
        int request = generation.incrementAndGet();
        filtering = true;
        status.setText("Filtering…");
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            Runnable checkCancelled = () -> {
                if (disposed.get() || project.isDisposed() || generation.get() != request) throw new ProcessCanceledException();
            };
            try {
                checkCancelled.run();
                var filtered = data.modules.select(data.profile, requested, checkCancelled);
                var trees = requested == null ? data.getTrees() : data.trees(filtered, false, checkCancelled);
                checkCancelled.run();
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (disposed.get() || project.isDisposed() || generation.get() != request) return;
                    // Read the current view at delivery time: the user may have
                    // changed tabs or Show while the calculation was running.
                    if (selection == null) lastAllIncludedUnattributed = FactorioProfileImporter.includesUnattributed(view.getMetric());
                    var tabs = UIUtil.findComponentOfType(view, JBRunnerClosableTabs.class);
                    String tab = tabs == null || tabs.getSelectedInfo() == null ? null : tabs.getSelectedInfo().getText();
                    try {
                        replaceSnapshot(trees, requested, tab);
                    } catch (ProcessCanceledException cancelled) {
                        filtering = false;
                        updateLabel();
                        throw cancelled;
                    } catch (Exception failure) {
                        // Kotlin platform APIs can propagate checked exceptions
                        // without declaring them in their Java signatures.
                        failed(failure);
                    }
                }, ModalityState.any());
            } catch (ProcessCanceledException ignored) {
                // Superseded selection or closed snapshot.
            } catch (RuntimeException failure) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (disposed.get() || project.isDisposed() || generation.get() != request) return;
                    failed(failure);
                }, ModalityState.any());
            }
        });
    }

    private void replaceSnapshot(List<CallTreeBuildingData> trees, Set<String> requested, String selectedTab) {
        var content = owner.getContent();
        var manager = content.getManager();
        if (manager == null) return;
        var ordered = new ArrayList<>(trees);
        if (requested == null && lastAllIncludedUnattributed && ordered.size() > 1)
            Collections.swap(ordered, 0, 1);
        var file = owner.getSnapshotFile();
        // This is an in-memory view of an already imported recording. Passing
        // a descriptor makes the native constructor reread file attributes;
        // the original recording may since have been moved or deleted.
        var next = new com.intellij.profiler.ProfilerDumpPanel(project,
                new FactorioProfileImporter.FilteredData(data, ordered, requested, lastAllIncludedUnattributed, selectedTab),
                owner.getTabName(), content.getHelpId(), null, file == null ? null : file.toString());
        var nextContent = next.getContent();
        nextContent.setDisplayName(content.getDisplayName());
        nextContent.setPinned(content.isPinned());
        boolean selected = manager.isSelected(content);
        // Replace the entire native snapshot panel, so both its identity and
        // comparison data agree with the displayed filter. Disposing the old
        // content cancels outstanding UI work and releases its native models.
        manager.addContent(nextContent, manager.getIndexOfContent(content));
        next.onPanelAdd();
        manager.removeContent(content, true);
        if (selected) manager.setSelectedContent(nextContent);
    }

    private void failed(Exception failure) {
        Logger.getInstance(FactorioProfileModulePanel.class).warn("Cannot filter Factorio profile", failure);
        filtering = false;
        status.setText("Could not apply module filter; previous view retained.");
    }

    private void updateLabel() {
        modules.setText(selection == null ? "Modules: All" : selection.isEmpty() ? "Modules: None"
                : selection.size() == 1 ? "Modules: " + data.modules.choices().stream()
                        .filter(c -> selection.contains(c.id())).map(FactorioProfileModules.Choice::label).findFirst().orElse("1 selected")
                : "Modules: " + selection.size() + " selected");
        status.setText(selection == null ? "Includes calls into libraries and native functions."
                : "Unattributed time is available with All modules.");
    }
    Set<String> selection() { return selection; }
    boolean isFiltering() { return filtering; }
    MainCallTreeDataComponent nativeView() { return view; }
    @Override public void dispose() { disposed.set(true); generation.incrementAndGet(); }

    static final class ModuleDialog extends DialogWrapper {
        private final List<FactorioProfileModules.Choice> choices;
        private final Set<String> selected;
        private final Map<String, JBCheckBox> checks = new LinkedHashMap<>();
        ModuleDialog(Project project, List<FactorioProfileModules.Choice> choices, Set<String> selected) {
            super(project);
            this.choices = choices; this.selected = selected;
            setTitle("Filter Factorio Profile by Modules");
            setOKButtonText("Apply");
            init();
        }
        @Override protected JComponent createCenterPanel() {
            var panel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
            var items = new JPanel();
            items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
            for (var choice : choices) {
                var check = new JBCheckBox(choice.label(), selected == null || selected.contains(choice.id()));
                checks.put(choice.id(), check);
                items.add(check);
            }
            var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            var all = new JButton("All"); var none = new JButton("None");
            all.addActionListener(e -> checks.values().forEach(c -> c.setSelected(true)));
            none.addActionListener(e -> checks.values().forEach(c -> c.setSelected(false)));
            buttons.add(all); buttons.add(none);
            panel.add(buttons, BorderLayout.NORTH);
            var scroll = new JBScrollPane(items);
            scroll.setPreferredSize(new Dimension(JBUI.scale(460), JBUI.scale(Math.min(360, Math.max(100, choices.size() * 28)))));
            panel.add(scroll, BorderLayout.CENTER);
            panel.add(new JBLabel("<html>Includes calls made by selected modules.<br>Other frames remain only where needed for context.</html>"), BorderLayout.SOUTH);
            return panel;
        }
        Set<String> selection() {
            var ids = new HashSet<String>();
            checks.forEach((id, check) -> { if (check.isSelected()) ids.add(id); });
            return ids.size() == choices.size() ? null : Set.copyOf(ids);
        }
    }
}
