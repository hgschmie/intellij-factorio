package de.softwareforge.factorio;

import com.google.gson.JsonObject;
import com.intellij.icons.AllIcons;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.xdebugger.*;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage;
import org.jetbrains.annotations.NotNull;
import java.nio.file.*;
import java.util.function.UnaryOperator;

public final class FactorioProfiling {
    public static final String EXECUTOR_ID = "FactorioLuaProfiler";
    static final int SHUTDOWN_SAVE_SECONDS = 120;
    static final Key<FactorioProfileSession> SESSION = Key.create("factorio.profile.session");

    static FactorioProfileSession install(DAPDebugProcess process) {
        var session = new FactorioProfileSession(path -> {
            process.print("Factorio profile saved: " + path + "\n", com.intellij.execution.ui.ConsoleViewContentType.SYSTEM_OUTPUT);
            try { open(process.getProject(), Path.of(path)); }
            catch (InvalidPathException failure) { error(process.getProject(), "Invalid profile path: " + path); }
        }, message -> error(process.getProject(), message), message -> {
            process.print(message + "\n", com.intellij.execution.ui.ConsoleViewContentType.SYSTEM_OUTPUT);
            if (!process.getProject().isDisposed()) NotificationGroupManager.getInstance().getNotificationGroup("Softwareforge Factorio")
                    .createNotification("Factorio profiling", message, NotificationType.INFORMATION).notify(process.getProject());
        });
        process.getProcessHandler().putUserData(SESSION, session);
        return session;
    }

    static UnaryOperator<MessageConsumer> events(FactorioProfileSession session, UnaryOperator<MessageConsumer> logging) {
        return consumer -> logging.apply(message -> {
            if (message instanceof NotificationMessage event) {
                if ("profileRunning".equals(event.getMethod())) { session.running(); return; }
                if ("profileComplete".equals(event.getMethod())) {
                    var value = PathsAndMods.JSON.toJsonTree(event.getParams());
                    String path = null;
                    if (value instanceof JsonObject body && body.has("path") && body.get("path").isJsonPrimitive()
                            && body.getAsJsonPrimitive("path").isString()) path = body.get("path").getAsString();
                    session.complete(path);
                    return;
                }
            }
            consumer.consume(message);
        });
    }

    static void open(Project project, Path file) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (project.isDisposed()) return;
            if (!Files.isRegularFile(file) || !Files.isReadable(file)) { error(project, "Profile file is missing or unreadable: " + file); return; }
            ApplicationManager.getApplication().invokeLater(() -> {
                if (project.isDisposed()) return;
                FactorioSettings.get(project).lastProfileDirectory = file.toAbsolutePath().getParent().toString();
                var viewers = FactorioProfileViewer.EP.getExtensionList();
                if (!viewers.isEmpty()) viewers.getFirst().open(project, file);
                else NotificationGroupManager.getInstance().getNotificationGroup("Softwareforge Factorio")
                        .createNotification("Factorio profile saved", "IntelliJ's common Profiler module is unavailable. Snapshot: " + file, NotificationType.INFORMATION)
                        .addAction(NotificationAction.createSimple("Show snapshot", () -> com.intellij.ide.actions.RevealFileAction.openFile(file)))
                        .notify(project);
            });
        });
    }
    static void error(Project project, String message) {
        if (!project.isDisposed()) NotificationGroupManager.getInstance().getNotificationGroup("Softwareforge Factorio")
                .createNotification("Factorio profiling", message, NotificationType.ERROR).notify(project);
    }
    private static Path defaultProfileDirectory(String remembered) {
        Path home = Path.of(System.getProperty("user.home"));
        Path parent = SystemInfo.isMac ? home.resolve("Library/Application Support")
                : SystemInfo.isWindows ? Path.of(System.getenv().getOrDefault("APPDATA", home.resolve("AppData/Roaming").toString()))
                : home;
        return FactorioProfileLocation.find(remembered, parent, SystemInfo.isLinux ? ".factorio" : "factorio");
    }
    private static FactorioProfileSession session(AnActionEvent event) {
        var debug = event.getData(XDebugSession.DATA_KEY);
        if (debug == null && event.getProject() != null) debug = XDebuggerManager.getInstance(event.getProject()).getCurrentSession();
        return debug == null ? null : debug.getDebugProcess().getProcessHandler().getUserData(SESSION);
    }
    public static final class Start extends DumbAwareAction {
        public Start() { super("Start Lua Profiling", "Record Lua execution timings in Factorio", AllIcons.Actions.StartMemoryProfile); }
        @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
        @Override public void update(@NotNull AnActionEvent event) {
            var session = session(event);
            event.getPresentation().setVisible(session != null);
            event.getPresentation().setEnabled(session != null && session.canStart());
        }
        @Override public void actionPerformed(@NotNull AnActionEvent event) { var session = session(event); if (session != null) session.start(); }
    }
    public static final class Stop extends DumbAwareAction {
        public Stop() { super("Stop Lua Profiling", "Finish recording and open the Factorio profile", AllIcons.Actions.Suspend); }
        @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
        @Override public void update(@NotNull AnActionEvent event) {
            var session = session(event);
            event.getPresentation().setVisible(session != null);
            event.getPresentation().setEnabled(session != null && session.canStop());
        }
        @Override public void actionPerformed(@NotNull AnActionEvent event) { var session = session(event); if (session != null) session.stop(); }
    }
    public static final class Open extends DumbAwareAction {
        @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
        @Override public void update(@NotNull AnActionEvent event) { event.getPresentation().setEnabled(event.getProject() != null); }
        @Override public void actionPerformed(@NotNull AnActionEvent event) {
            var project = event.getProject();
            if (project == null) return;
            var descriptor = new FileChooserDescriptor(true, false, false, false, false, false)
                    .withTitle("Open Factorio Profile").withFileFilter(file -> "cpuprofile".equals(file.getExtension()));
            String remembered = FactorioSettings.get(project).lastProfileDirectory;
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                Path directory = defaultProfileDirectory(remembered);
                var selection = directory == null ? null : LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory);
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!project.isDisposed()) FileChooser.chooseFile(descriptor, project, selection, file -> open(project, file.toNioPath()));
                });
            });
        }
    }
}
