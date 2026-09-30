package de.softwareforge.factorio;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.*;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/** One live, disposable console per toolkit operation. */
final class FactorioOutput {
    private final ConsoleView console;
    private volatile ProgressIndicator indicator;
    private volatile boolean finished;
    private volatile boolean closed;

    private FactorioOutput(ConsoleView console) { this.console = console; }
    static FactorioOutput open(Project project, String title) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        var window = ToolWindowManager.getInstance(project).getToolWindow("Factorio");
        var console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        var output = new FactorioOutput(console);
        var panel = new JPanel(new BorderLayout());
        panel.add(console.getComponent(), BorderLayout.CENTER);
        var actions = new DefaultActionGroup();
        actions.add(new DumbAwareAction("Stop", "Cancel this toolkit operation", com.intellij.icons.AllIcons.Actions.Suspend) {
            @Override public void actionPerformed(@NotNull AnActionEvent e) { output.cancel(); }
            @Override public void update(@NotNull AnActionEvent e) { e.getPresentation().setEnabled(!output.finished && output.indicator != null); }
            @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
        });
        actions.addAll(console.createConsoleActions());
        var toolbar = ActionManager.getInstance().createActionToolbar("FactorioOutput", actions, false);
        toolbar.setTargetComponent(console.getComponent());
        panel.add(toolbar.getComponent(), BorderLayout.WEST);
        var content = ContentFactory.getInstance().createContent(panel, title, false);
        content.setDisposer(() -> { output.closed = true; output.cancel(); com.intellij.openapi.util.Disposer.dispose(console); });
        window.getContentManager().addContent(content);
        window.getContentManager().setSelectedContent(content);
        window.show();
        output.print(title + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
        return output;
    }
    void start(ProgressIndicator indicator) {
        this.indicator = indicator;
        if (closed) indicator.cancel();
    }
    private void cancel() { var active = indicator; if (!finished && active != null) active.cancel(); }
    void print(String text, ConsoleViewContentType type) { if (!closed) console.print(text, type); }
    void finish(String text, boolean error) {
        finished = true;
        print(text + "\n", error ? ConsoleViewContentType.ERROR_OUTPUT : ConsoleViewContentType.SYSTEM_OUTPUT);
    }
    public static final class Factory implements ToolWindowFactory, DumbAware {
        @Override public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {}
    }
}
