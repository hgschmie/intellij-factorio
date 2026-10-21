package de.softwareforge.factorio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.xdebugger.XDebuggerManager;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Exercises the real runner, client, process handler and final protocol/log draining. */
public class FactorioStopTest extends HeavyPlatformTestCase {
    private String backgroundTasks;

    @Override protected void setUp() throws Exception {
        super.setUp();
        backgroundTasks = System.getProperty("intellij.progress.task.ignoreHeadless");
        System.setProperty("intellij.progress.task.ignoreHeadless", "true");
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(),
                Path.of(System.getProperty("factorio.test.workspace"), "intellij-factorio/.intellijPlatform/sandbox").toString());
    }

    @Override protected void tearDown() throws Exception {
        try { super.tearDown(); }
        finally {
            if (backgroundTasks == null) System.clearProperty("intellij.progress.task.ignoreHeadless");
            else System.setProperty("intellij.progress.task.ignoreHeadless", backgroundTasks);
        }
    }

    public void testPausedStopWaitsForTerminatedAndDrainsFinalOutput() throws Exception { runStop("normal"); }
    public void testUnsupportedTerminateUsesDisconnect() throws Exception { runStop("no-terminate"); }
    public void testRejectedTerminateFallsBackToDisconnect() throws Exception { runStop("terminate-error"); }
    public void testUnansweredTerminateFallsBackToDisconnect() throws Exception { runStop("ignore-terminate"); }
    public void testUnansweredDisconnectFallsBackToSigterm() throws Exception { runStop("ignore-disconnect"); }
    public void testUnresponsiveGameIsEventuallyKilled() throws Exception { runStop("ignore-signal"); }
    public void testExitWithoutTerminatedEventStillCleansUp() throws Exception { runStop("exit-on-terminate"); }
    public void testExplicitProfileStopSavesBeforeShutdown() throws Exception { runStop("profile"); }
    public void testProfilingWithoutProtocolTracing() throws Exception { runStop("profile-no-trace"); }
    public void testLuaInitiatedRecordingDoesNotDelayShutdown() throws Exception { runStop("profile-lua"); }
    public void testActiveRecordingDoesNotSaveOnIdeStop() throws Exception { runStop("profile-active"); }
    public void testPendingProfileStartDoesNotDelayShutdown() throws Exception { runStop("profile-ignore-start"); }
    public void testStopBeforeLaunchAcknowledgementDoesNotStartProfiling() throws Exception { runStop("profile-stop-startup"); }
    public void testUnresponsiveProfilerDoesNotBlockShutdown() throws Exception { runStop("profile-ignore-stop"); }
    public void testProfileExecutorStartsAfterLaunchAndSavesOnExplicitProfileStop() throws Exception { runStop("profile-auto"); }
    public void testProfileExecutorIgnoresObsoleteHookSetting() throws Exception {
        var persisted = new org.jdom.Element("state").addContent(new org.jdom.Element("option")
                .setAttribute("name", "profileShowHookEvents").setAttribute("value", "true"));
        getProject().getService(FactorioSettings.class).loadState(
                com.intellij.util.xmlb.XmlSerializer.deserialize(persisted, FactorioSettings.Data.class));
        runStop("profile-auto");
    }
    public void testRunExecutorDoesNotStartRecording() throws Exception { runStop("run"); }
    public void testExplicitSlowProfileSaveCanFinishBeforeIdeShutdown() throws Exception { runStop("profile-slow-stop"); }
    public void testGameQuitWithoutProfilingUsesNormalShutdown() throws Exception { runStop("game-quit"); }
    public void testGameQuitDoesNotRequestAProfileAfterTermination() throws Exception { runStop("profile-game-quit"); }

    private void runStop(String mode) throws Exception {
        FactorioSettings.get(getProject()).serviceMode = "ENABLED";
        Path dir = Files.createTempDirectory(Files.createDirectories(Path.of(System.getProperty("factorio.test.work"))), "stop-");
        Path fixture = Path.of(System.getProperty("factorio.test.workspace"), "intellij-factorio/src/ideTest/fixtures/stopping-dap.py");
        Path executable = dir.resolve("adapter");
        Files.writeString(executable, "#!/usr/bin/python3\nimport runpy\nrunpy.run_path(" + PathsAndMods.JSON.toJson(fixture.toString()) +
                ",init_globals={'ROOT':" + PathsAndMods.JSON.toJson(dir.toString()) + "})\n");
        assertTrue(executable.toFile().setExecutable(true));
        Files.writeString(dir.resolve("mode"), mode);
        Files.writeString(dir.resolve("control.lua"), "local a = 1\n");
        if (mode.startsWith("profile")) Files.copy(Path.of(System.getProperty("factorio.test.workspace"),
                "intellij-factorio/src/test/resources/profiling/factorio-2.1.23.cpuprofile"), dir.resolve("profile.cpuprofile"));
        var settings = RunManager.getInstance(getProject()).createConfiguration("Stop test", new FactorioDebug.Type().getConfigurationFactories()[0]);
        var config = (FactorioDebug.Configuration)settings.getConfiguration();
        config.setCommand(executable.toString());
        config.setWorkingDirectory(dir.toString());
        config.setServerTrace(mode.equals("profile-no-trace") ? ServerTrace.off : ServerTrace.verbose);
        config.getOptions().setDapLogToFile(true);
        config.getOptions().setDapLogDirectory(dir.resolve("logs").toString());
        com.intellij.execution.Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();
        if (mode.equals("profile-auto") || mode.equals("profile-stop-startup")) {
            executor = com.intellij.profiler.DefaultProfilerExecutorGroup.Companion.getInstance().childExecutors().stream()
                    .filter(e -> e.getId().equals(FactorioProfiling.EXECUTOR_ID)).findFirst().orElseThrow();
        } else if (mode.equals("run")) executor = com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance();
        String launchConfiguration = config.getLaunchConfiguration();
        var env = ExecutionEnvironmentBuilder.create(executor, settings).build();
        FactorioDebugProcessHandler handler = null;
        boolean manualSave = mode.equals("profile") || mode.equals("profile-auto")
                || mode.equals("profile-no-trace") || mode.equals("profile-slow-stop");
        boolean gameQuit = mode.endsWith("game-quit");
        boolean stopDuringStartup = mode.equals("profile-stop-startup");
        try {
            ProgramRunnerUtil.executeConfiguration(env, false, false);
            if (stopDuringStartup) waitUntil(() -> log(dir).contains("\"command\":\"configurationDone\""));
            else waitUntil(() -> java.util.Arrays.stream(XDebuggerManager.getInstance(getProject()).getDebugSessions()).anyMatch(s -> s.isSuspended()));
            var session = XDebuggerManager.getInstance(getProject()).getDebugSessions()[0];
            handler = (FactorioDebugProcessHandler)session.getDebugProcess().getProcessHandler();
            if (mode.startsWith("profile") && !stopDuringStartup) {
                var profile = handler.getUserData(FactorioProfiling.SESSION);
                assertNotNull(profile);
                if (!mode.equals("profile-lua") && !mode.equals("profile-auto")) profile.start();
                if (mode.equals("profile-ignore-start")) assertEquals(FactorioProfileSession.State.STARTING, profile.state());
                else waitUntil(() -> profile.state() == FactorioProfileSession.State.RECORDING);
                if (mode.equals("profile")) {
                    profile.stop();
                    waitUntil(profile::canStart);
                    // Every interval excludes hook timing, including repeated captures.
                    profile.start();
                    waitUntil(profile::canStop);
                }
                if (manualSave) {
                    profile.stop();
                    waitUntil(profile::canStart);
                } else if (mode.equals("profile-ignore-stop")) {
                    profile.stop();
                    assertEquals(FactorioProfileSession.State.STOPPING, profile.state());
                }
            }
            // Exercise the Stop action's process-first path as well as LSP4IJ's stop callback.
            if (gameQuit) Files.createFile(dir.resolve("user-quit"));
            else {
                handler.destroyProcess();
                if (!stopDuringStartup) session.stop();
            }
            var runningHandler = handler;
            waitUntil(runningHandler::isProcessTerminated);
            waitUntil(() -> XDebuggerManager.getInstance(getProject()).getDebugSessions().length == 0);
            if (mode.startsWith("profile")) {
                var profile = handler.getUserData(FactorioProfiling.SESSION);
                waitUntil(() -> profile.state() == FactorioProfileSession.State.CLOSED);
            }
            assertFalse("Factorio must never observe premature stdin EOF", Files.exists(dir.resolve("premature-eof")));
            if (mode.equals("ignore-signal")) {
                assertTrue(Files.exists(dir.resolve("sigterm")));
                assertFalse(Files.exists(dir.resolve("clean-exit")));
                assertTrue(handler.getExitCode() != 0);
            } else {
                assertTrue("Expected adapter cleanup to finish", Files.exists(dir.resolve("clean-exit")));
                assertEquals(Integer.valueOf(0), handler.getExitCode());
                if (!mode.equals("profile-no-trace")) waitUntil(() -> log(dir).contains("final shutdown output"));
            }
            List<JsonObject> requests = Files.readAllLines(dir.resolve("requests.jsonl")).stream()
                    .map(line -> JsonParser.parseString(line).getAsJsonObject()).toList();
            var commands = requests.stream().map(r -> r.get("command").getAsString()).toList();
            var starts = requests.stream().filter(r -> r.get("command").getAsString().equals("startProfile")).toList();
            for (var start : starts)
                assertFalse(start.getAsJsonObject("arguments").get("show_hook_events").getAsBoolean());
            for (var request : requests) {
                if (request.get("command").getAsString().equals("stopProfile"))
                    assertTrue(request.getAsJsonObject("arguments").entrySet().isEmpty());
            }
            assertEquals(launchConfiguration, config.getLaunchConfiguration());
            if (mode.equals("profile-auto")) {
                assertEquals(1L, commands.stream().filter("startProfile"::equals).count());
                assertTrue(commands.indexOf("configurationDone") < commands.indexOf("startProfile"));
            } else if (stopDuringStartup || !mode.startsWith("profile"))
                assertFalse("Cancelled launch must not start profiling: " + commands, commands.contains("startProfile"));
            if (manualSave || mode.equals("profile-ignore-stop")) {
                assertTrue(commands.indexOf("stopProfile") < commands.indexOf("terminate"));
                assertEquals(mode.equals("profile") ? 2L : 1L, commands.stream().filter("stopProfile"::equals).count());
                if (manualSave) {
                    waitUntil(() -> com.intellij.openapi.wm.ToolWindowManager.getInstance(getProject()).getToolWindow("Profiler") != null);
                }
            } else assertFalse("Shutdown must not request a profile save", commands.contains("stopProfile"));
            assertEquals(mode.equals("no-terminate") || gameQuit ? 0L : 1L, commands.stream().filter("terminate"::equals).count());
            assertEquals(mode.equals("exit-on-terminate") ? 0L : 1L, commands.stream().filter("disconnect"::equals).count());
            for (var request : requests) {
                if (request.get("command").getAsString().equals("disconnect")) {
                    assertFalse(request.getAsJsonObject("arguments").get("restart").getAsBoolean());
                    assertFalse(request.getAsJsonObject("arguments").has("terminateDebuggee"));
                }
            }
            if (mode.equals("normal") || mode.equals("ignore-disconnect")) {
                String trace = log(dir);
                assertTrue(trace.indexOf("\"event\":\"terminated\"") < trace.indexOf("\"command\":\"disconnect\""));
                assertTrue(trace.contains("output after terminate response"));
            }
        } finally {
            if (handler != null && handler.getProcess().isAlive()) handler.getProcess().destroyForcibly();
            for (var session : XDebuggerManager.getInstance(getProject()).getDebugSessions()) session.stop();
            waitUntil(() -> XDebuggerManager.getInstance(getProject()).getDebugSessions().length == 0);
        }
    }

    private static String log(Path dir) {
        try (var files = Files.list(dir.resolve("logs"))) {
            return Files.readString(files.findFirst().orElseThrow());
        } catch (Exception pending) { return ""; }
    }

    private static void waitUntil(BooleanSupplier condition) throws Exception {
        waitUntil(condition, 30);
    }
    private static void waitUntil(BooleanSupplier condition, int seconds) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        assertTrue("Timed out waiting for debugger shutdown", condition.getAsBoolean());
    }
}
