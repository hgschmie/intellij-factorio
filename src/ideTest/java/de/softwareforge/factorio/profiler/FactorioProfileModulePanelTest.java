package de.softwareforge.factorio.profiler;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.Disposer;
import com.intellij.profiler.api.Success;
import com.intellij.profiler.ui.JBRunnerClosableTabs;
import com.intellij.profiler.ui.LabeledComboBox;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ui.UIUtil;
import de.softwareforge.factorio.FactorioProfile;
import de.softwareforge.factorio.FactorioSettings;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class FactorioProfileModulePanelTest extends HeavyPlatformTestCase {
    public void testSelectionUpdatesAllViewsAndRestoresFullRecording() throws Exception {
        FactorioSettings.get(getProject()).serviceMode = "DISABLED";
        var profile = new FactorioProfile(List.of(
                n(0, "", 900, 1_000, 1, 2), n(1, "@__A__/control.lua", 20, 60, 3),
                n(2, "@__B__/control.lua", 40, 40), n(3, "@__core__/lib.lua", 30, 40, 4),
                n(4, "=[C]", 10, 10)), 1_000);
        var data = (FactorioProfileImporter.Data) FactorioProfileImporter.data(getProject(), profile);
        var lifetime = Disposer.newDisposable();
        try {
            open(data, "Filter test");
            var panel = current();
            awaitCurrent(100);
            panel = current();
            var selector = UIUtil.findComponentOfType(panel.nativeView(), LabeledComboBox.class);
            var combo = UIUtil.findComponentOfType(selector, javax.swing.JComboBox.class);
            combo.setSelectedIndex(1);
            awaitCurrent(1_000);
            panel = current();
            var tabs = UIUtil.findComponentOfType(panel.nativeView(), JBRunnerClosableTabs.class);
            tabs.select(tabs.getTabs().stream().filter(t -> t.getText().equals("Method List")).findFirst().orElseThrow(), true);
            panel.selectModules(Set.of("A"));
            awaitCurrent(60);
            panel = current();
            assertEquals(Set.of("A"), panel.selection());
            tabs = UIUtil.findComponentOfType(panel.nativeView(), JBRunnerClosableTabs.class);
            assertEquals("Method List", tabs.getSelectedInfo().getText());
            for (var tab : tabs.getTabs()) {
                tabs.select(tab, true); PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
                assertEquals(60L, total(panel));
            }
            panel.selectModules(Set.of("A", "B"));
            awaitCurrent(100);
            panel = current();
            panel.selectModules(Set.of());
            awaitCurrent(0);
            panel = current();
            panel.selectModules(null);
            awaitCurrent(1_000);
            panel = current(); // Restore the user's full-recording mode.
            panel.selectModules(Set.of("A"));
            panel.selectModules(Set.of("B"));
            awaitCurrent(40);
            panel = current(); // An older background result must not replace the last selection.
            assertEquals(Set.of("B"), panel.selection());
            var dialog = new FactorioProfileModulePanel.ModuleDialog(getProject(), data.modules.choices(), Set.of("A", "B"));
            assertEquals(Set.of("A", "B"), dialog.selection());
            dialog.close(com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE);
        } finally { Disposer.dispose(lifetime); }
    }

    public void testNativeSnapshotModuleFilter() throws Exception {
        FactorioSettings.get(getProject()).serviceMode = "DISABLED";
        var path = Path.of(System.getenv().getOrDefault("FACTORIO_TEST_PROFILE", Path.of(System.getProperty("factorio.test.workspace"),
                "intellij-factorio/src/test/resources/profiling/factorio-2.1.23.cpuprofile").toString()));
        String roots = System.getenv("FACTORIO_TEST_PROFILE_SOURCE_ROOTS");
        if (roots != null && !roots.isBlank()) com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(), roots.split(java.io.File.pathSeparator));
        var result = new FactorioProfileImporter().createParser(getProject()).parse(path.toFile(), new EmptyProgressIndicator());
        assertTrue(result instanceof Success);
        var data = (FactorioProfileImporter.Data) ((Success) result).getData();
        assertTrue(data.modules.choices().toString(), data.modules.choices().stream().anyMatch(c -> c.id().equals("LogisticTrainNetwork")));
        var lifetime = Disposer.newDisposable();
        var window = new javax.swing.JFrame("FDE module filter test");
        window.setGlassPane(new com.intellij.openapi.wm.impl.IdeGlassPaneImpl(window.getRootPane()));
        try {
            open(data, "Native fixture");
            var panel = current();
            window.setContentPane(panel.nativeView()); window.setSize(1100, 700); window.setVisible(true);
            var selected = Set.of("LogisticTrainNetwork");
            long expected = data.modules.select(data.profile, selected, () -> {}).durationNanos();
            assertTrue(expected > 0);
            assertTrue(expected < data.profile.durationNanos());
            panel.selectModules(selected);
            awaitCurrent(expected);
            panel = current();
            window.setContentPane(panel.nativeView());
            var tabs = UIUtil.findComponentOfType(panel.nativeView(), JBRunnerClosableTabs.class);
            for (var tab : tabs.getTabs()) {
                tabs.select(tab, true); PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
                assertEquals(expected, total(panel));
                panel.nativeView().setSize(1100, 700); layout(panel.nativeView());
                var image = new java.awt.image.BufferedImage(1100, 700, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics(); panel.nativeView().printAll(graphics); graphics.dispose();
                var output = Path.of(System.getProperty("factorio.test.workspace"), "intellij-factorio/build/reports/ui/profiling-module-LTN-" + tab.getText().replace(' ', '-') + ".png");
                Files.createDirectories(output.getParent()); javax.imageio.ImageIO.write(image, "png", output.toFile());
            }
            System.out.println("Module-filter snapshot " + path + ": choices=" + data.modules.choices() + "; LTN=" + expected + " ns");
        } finally { window.dispose(); Disposer.dispose(lifetime); }
    }
    public void testSnapshotComparisonBeforeAndAfterModuleFilter() throws Exception {
        FactorioSettings.get(getProject()).serviceMode = "DISABLED";
        var profile = new FactorioProfile(List.of(n(0, "", 0, 100, 1, 2),
                n(1, "__A__/control.lua", 60, 60), n(2, "__B__/control.lua", 40, 40)), 100);
        var data = FactorioProfileImporter.data(getProject(), profile);
        var baseline = open(data, "Baseline");
        var baselineView = (com.intellij.profiler.ui.MainCallTreeDataComponent) baseline.getTopLevelComponent();
        awaitCurrent(100);
        open(data, "Current");
        awaitCurrent(100);
        assertComparisonAvailable(current().nativeView(), "Baseline");
        assertComparisonAvailable(baselineView, "Current");
        var oldView = current().nativeView();
        current().selectModules(Set.of("A"));
        awaitCurrent(60);
        assertNotSame(oldView, current().nativeView());
        assertTrue(Disposer.isDisposed(oldView));
        assertComparisonAvailable(current().nativeView(), "Baseline");
        assertComparisonAvailable(baselineView, "Current");
        // Invoke the actual registered-tab comparison action, not only its menu.
        var action = Arrays.stream(new com.intellij.profiler.ui.diff.CompareSnapshotsActionGroup(getProject(),
                current().nativeView()).getChildren((com.intellij.openapi.actionSystem.AnActionEvent) null))
                .filter(a -> a.getTemplatePresentation().getText().contains("Baseline")).findFirst().orElseThrow();
        var context = com.intellij.openapi.actionSystem.impl.SimpleDataContext.getProjectContext(getProject());
        action.actionPerformed(com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(action, null, "test", context));
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertEquals(3, com.intellij.profiler.ProfilerToolWindowManager.getInstance(getProject()).getAllTabs().length);
    }
    public void testModuleFilteringAfterSnapshotIsMovedAndDeleted() throws Exception {
        FactorioSettings.get(getProject()).serviceMode = "DISABLED";
        var file = Files.createTempFile(Path.of(System.getProperty("factorio.test.work")), "loaded-profile-", ".cpuprofile");
        try (var fixture = getClass().getResourceAsStream("/profiling/factorio-2.1.23.cpuprofile")) {
            assertNotNull(fixture);
            Files.copy(fixture, file, StandardCopyOption.REPLACE_EXISTING);
        }
        var result = new FactorioProfileImporter().createParser(getProject()).parse(file.toFile(), new EmptyProgressIndicator());
        assertTrue(result instanceof Success);
        var data = (FactorioProfileImporter.Data) ((Success) result).getData();
        var baseline = open(data, "Baseline");
        var dump = open(data, "Moved recording", file);
        dump.getContent().setPinned(true);
        long execution = data.profile.durationNanos() - data.profile.nodes().getFirst().selfNanos();
        awaitCurrent(execution);
        var moved = Files.move(file, file.resolveSibling(file.getFileName() + ".moved"));
        try {
            var selected = Set.of("LogisticTrainNetwork");
            long expected = data.modules.select(data.profile, selected, () -> {}).durationNanos();
            assertTrue(expected > 0);
            current().selectModules(selected);
            awaitCurrent(expected);
            assertEquals(selected, current().selection());
            Files.delete(moved);
            current().selectModules(Set.of());
            awaitCurrent(0);
            current().selectModules(null);
            awaitCurrent(execution);
            var content = com.intellij.profiler.ProfilerToolWindowManager.getInstance(getProject()).getCurrentTab();
            var replacement = (com.intellij.profiler.ProfilerDumpPanel) content.getComponent();
            assertEquals(file.toFile(), replacement.getSnapshotFile());
            assertEquals("cpuprofile", replacement.getFileExtension());
            assertEquals("Moved recording", content.getDisplayName());
            assertTrue(content.isPinned());
            assertTrue(Disposer.isDisposed(dump));
            assertComparisonAvailable(current().nativeView(), "Baseline");
            assertComparisonAvailable((com.intellij.profiler.ui.MainCallTreeDataComponent) baseline.getTopLevelComponent(), "Moved recording");
        } finally { Files.deleteIfExists(moved); Files.deleteIfExists(file); }
    }
    private void assertComparisonAvailable(com.intellij.profiler.ui.MainCallTreeDataComponent view, String name) {
        var actions = new com.intellij.profiler.ui.diff.CompareSnapshotsActionGroup(getProject(), view)
                .getChildren((com.intellij.openapi.actionSystem.AnActionEvent) null);
        assertTrue(Arrays.toString(actions), Arrays.stream(actions).anyMatch(a -> a.getTemplatePresentation().getText().contains(name)));
    }
    private com.intellij.profiler.ProfilerDumpPanel open(com.intellij.profiler.api.ProfilerData data, String name) throws Exception {
        var file = Files.createTempFile(Path.of(System.getProperty("factorio.test.work")), "profile-compare-", ".cpuprofile");
        return open(data, name, file);
    }
    private com.intellij.profiler.ProfilerDumpPanel open(com.intellij.profiler.api.ProfilerData data, String name, Path file) {
        var descriptor = new com.intellij.profiler.api.ProfilerDumpDescriptor(getProject(), name, new Date(), file.toFile(),
                new FactorioProfileImporter(), new java.util.concurrent.atomic.AtomicInteger());
        var dump = new com.intellij.profiler.ProfilerDumpPanel(getProject(), data, name, null, descriptor, file.toString());
        assertTrue(dump.getTopLevelComponent() instanceof com.intellij.profiler.ui.MainCallTreeDataComponent);
        com.intellij.profiler.ProfilerToolWindowManager.getInstance(getProject()).addProfilerDumpTab(dump, false);
        dump.getContent().getManager().setSelectedContent(dump.getContent());
        return dump;
    }
    private FactorioProfileModulePanel current() {
        var content = com.intellij.profiler.ProfilerToolWindowManager.getInstance(getProject()).getCurrentTab();
        var dump = (com.intellij.profiler.ProfilerDumpPanel) content.getComponent();
        assertTrue(dump.getTopLevelComponent() instanceof com.intellij.profiler.ui.MainCallTreeDataComponent);
        return UIUtil.findComponentOfType(dump.getTopLevelComponent(), FactorioProfileModulePanel.class);
    }
    private void awaitCurrent(long expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        do {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            var panel = current();
            if (!panel.isFiltering() && total(panel) == expected) return;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        assertFalse(current().isFiltering()); assertEquals(expected, total(current()));
    }
    private static FactorioProfile.Node n(int id, String source, long self, long total, Integer... children) {
        return new FactorioProfile.Node(id, new FactorioProfile.Frame(id == 0 ? "(root)" : "call" + id, source, 0), List.of(children), self, total);
    }
    private static long total(FactorioProfileModulePanel panel) {
        var models = panel.nativeView().getRichCallModel();
        // The native UI exposes both the all-threads aggregate and our one Lua
        // context. They are alternative views of the same time, not additive.
        return models.isEmpty() ? 0 : models.values().iterator().next().getCallTree().getChildren().stream()
                .mapToLong(n -> n.getData().getValue()).sum();
    }
    private static void await(FactorioProfileModulePanel panel, long expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        do {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            if (!panel.isFiltering() && total(panel) == expected) return;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        assertFalse(panel.isFiltering()); assertEquals(expected, total(panel));
    }
    private static void layout(java.awt.Container parent) {
        parent.doLayout();
        for (var child : parent.getComponents()) if (child instanceof java.awt.Container c) layout(c);
    }
}
