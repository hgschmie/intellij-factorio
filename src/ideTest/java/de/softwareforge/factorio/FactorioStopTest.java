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
        var settings = RunManager.getInstance(getProject()).createConfiguration("Stop test", new FactorioDebug.Type().getConfigurationFactories()[0]);
        var config = (FactorioDebug.Configuration)settings.getConfiguration();
        config.setCommand(executable.toString());
        config.setWorkingDirectory(dir.toString());
        config.setServerTrace(ServerTrace.verbose);
        config.getOptions().setDapLogToFile(true);
        config.getOptions().setDapLogDirectory(dir.resolve("logs").toString());
        var env = ExecutionEnvironmentBuilder.create(DefaultDebugExecutor.getDebugExecutorInstance(), settings).build();
        FactorioDebugProcessHandler handler = null;
        try {
            ProgramRunnerUtil.executeConfiguration(env, false, false);
            waitUntil(() -> java.util.Arrays.stream(XDebuggerManager.getInstance(getProject()).getDebugSessions()).anyMatch(s -> s.isSuspended()));
            var session = XDebuggerManager.getInstance(getProject()).getDebugSessions()[0];
            handler = (FactorioDebugProcessHandler)session.getDebugProcess().getProcessHandler();
            // Exercise the Stop action's process-first path as well as LSP4IJ's stop callback.
            handler.destroyProcess();
            session.stop();
            var runningHandler = handler;
            waitUntil(runningHandler::isProcessTerminated);
            waitUntil(() -> XDebuggerManager.getInstance(getProject()).getDebugSessions().length == 0);
            assertFalse("Factorio must never observe premature stdin EOF", Files.exists(dir.resolve("premature-eof")));
            if (mode.equals("ignore-signal")) {
                assertTrue(Files.exists(dir.resolve("sigterm")));
                assertFalse(Files.exists(dir.resolve("clean-exit")));
                assertTrue(handler.getExitCode() != 0);
            } else {
                assertTrue("Expected adapter cleanup to finish", Files.exists(dir.resolve("clean-exit")));
                assertEquals(Integer.valueOf(0), handler.getExitCode());
                waitUntil(() -> log(dir).contains("final shutdown output"));
            }
            List<JsonObject> requests = Files.readAllLines(dir.resolve("requests.jsonl")).stream()
                    .map(line -> JsonParser.parseString(line).getAsJsonObject()).toList();
            var commands = requests.stream().map(r -> r.get("command").getAsString()).toList();
            assertEquals(mode.equals("no-terminate") ? 0L : 1L, commands.stream().filter("terminate"::equals).count());
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
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        assertTrue("Timed out waiting for debugger shutdown", condition.getAsBoolean());
    }
}
