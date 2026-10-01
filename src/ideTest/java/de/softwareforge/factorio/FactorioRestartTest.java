package de.softwareforge.factorio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.execution.RunManager;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.xdebugger.XDebuggerManager;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPBreakpointProperties;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Runs the actual IntelliJ runner and LSP4IJ client against a controllable adapter. */
public class FactorioRestartTest extends HeavyPlatformTestCase {
    private String backgroundTasks;
    @Override protected void setUp() throws Exception {
        super.setUp();
        // The real runner starts a background DAP handshake before startNotify.
        // Synchronous headless tasks would deadlock that normal startup sequence.
        backgroundTasks=System.getProperty("intellij.progress.task.ignoreHeadless");
        System.setProperty("intellij.progress.task.ignoreHeadless","true");
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(),
                Path.of(System.getProperty("factorio.test.workspace"),"intellij-factorio/.intellijPlatform/sandbox").toString());
    }
    @Override protected void tearDown() throws Exception {
        try { super.tearDown(); }
        finally {
            if(backgroundTasks==null) System.clearProperty("intellij.progress.task.ignoreHeadless");
            else System.setProperty("intellij.progress.task.ignoreHeadless",backgroundTasks);
        }
    }
    public void testRestartReinitializesBreakpointsAndStopCancelsPendingRestart() throws Exception {
        FactorioSettings.get(getProject()).serviceMode="ENABLED";
        Path dir=Files.createTempDirectory(Files.createDirectories(Path.of(System.getProperty("factorio.test.work"))),"restart-");
        Path fixture=Path.of(System.getProperty("factorio.test.workspace"),"intellij-factorio/src/ideTest/fixtures/restarting-dap.py");
        Path executable=dir.resolve("adapter");
        Files.writeString(executable,"#!/usr/bin/python3\nimport runpy\nrunpy.run_path("+PathsAndMods.JSON.toJson(fixture.toString())+
                ",init_globals={'ROOT':"+PathsAndMods.JSON.toJson(dir.toString())+"})\n");
        assertTrue(executable.toFile().setExecutable(true));
        Path lua=dir.resolve("control.lua");Files.writeString(lua,"local a = 1\n");
        var file=LocalFileSystem.getInstance().refreshAndFindFileByNioFile(lua);assertNotNull(file);
        WriteAction.run(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                .addLineBreakpoint(com.intellij.xdebugger.breakpoints.XBreakpointType.EXTENSION_POINT_NAME
                        .findExtension(FactorioDebug.Breakpoint.class),file.getUrl(),0,new DAPBreakpointProperties()));
        var settings=RunManager.getInstance(getProject()).createConfiguration("Restart test",new FactorioDebug.Type().getConfigurationFactories()[0]);
        var config=(FactorioDebug.Configuration)settings.getConfiguration();
        config.setCommand(executable.toString());config.setWorkingDirectory(dir.toString());
        String saved=config.getLaunchConfiguration();
        var env=ExecutionEnvironmentBuilder.create(DefaultDebugExecutor.getDebugExecutorInstance(),settings).build();
        try {
            ProgramRunnerUtil.executeConfiguration(env,false,false);
            waitUntil(() -> hasRequest(dir,1,"configurationDone"));
            String payload="{\"relaunchArgs\":[\"--config\",\"a path/config.ini\"],\"nested\":{\"value\":42}}";
            trigger(dir,1,"{\"restart\":"+payload+"}");
            waitUntil(() -> hasRequest(dir,2,"configurationDone"));
            assertEquals(JsonParser.parseString(payload),request(dir,2,"launch").getAsJsonObject("arguments").get("__restart"));
            assertTrue(hasRequest(dir,2,"initialize"));
            assertEquals(1,request(dir,2,"setBreakpoints").getAsJsonObject("arguments").getAsJsonArray("breakpoints").size());
            assertTrue(request(dir,1,"disconnect").getAsJsonObject("arguments").get("restart").getAsBoolean());
            assertEquals(saved,config.getLaunchConfiguration());
            // Factorio can exit immediately after the event, without a disconnect response.
            Files.writeString(dir.resolve("exit-immediately-2"),"");
            trigger(dir,2,"{\"restart\":true}");
            waitUntil(() -> hasRequest(dir,3,"configurationDone"));
            assertTrue(request(dir,3,"launch").getAsJsonObject("arguments").get("__restart").getAsBoolean());
            assertNotNull(request(dir,3,"setBreakpoints"));
            var restartedEnvironment=XDebuggerManager.getInstance(getProject()).getDebugSessions()[0].getExecutionEnvironment();

            // An ordinary terminated event ends the replacement without another launch.
            trigger(dir,3,"{\"restart\":false}");
            waitUntil(() -> XDebuggerManager.getInstance(getProject()).getDebugSessions().length==0);
            assertEquals("3",Files.readString(dir.resolve("starts")));

            // A manual rerun starts clean, without the previous __restart payload.
            ProgramRunnerUtil.executeConfiguration(new ExecutionEnvironmentBuilder(restartedEnvironment).build(),false,false);
            waitUntil(() -> hasRequest(dir,4,"configurationDone"));
            assertFalse(request(dir,4,"launch").getAsJsonObject("arguments").has("__restart"));

            // Hold the old process alive after disconnect so Stop can cancel the pending restart.
            Files.writeString(dir.resolve("hold-exit"),"");
            trigger(dir,4,"{\"restart\":true}");
            waitUntil(() -> hasRequest(dir,4,"disconnect"));
            var session=XDebuggerManager.getInstance(getProject()).getDebugSessions()[0];
            session.stop();
            Files.writeString(dir.resolve("release-exit"),"");
            waitUntil(() -> XDebuggerManager.getInstance(getProject()).getDebugSessions().length==0);
            // Drain callbacks queued by process termination and restart dispatch.
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            assertEquals("4",Files.readString(dir.resolve("starts")));
        } finally {
            Files.writeString(dir.resolve("release-exit"),"");
            for(var session:XDebuggerManager.getInstance(getProject()).getDebugSessions()) session.stop();
            waitUntil(() -> XDebuggerManager.getInstance(getProject()).getDebugSessions().length==0);
        }
    }
    private static void trigger(Path dir,int cycle,String body) throws Exception {
        Path temp=dir.resolve("trigger.tmp");Files.writeString(temp,body);
        Files.move(temp,dir.resolve("terminate-"+cycle+".json"),StandardCopyOption.ATOMIC_MOVE);
    }
    private static boolean hasRequest(Path dir,int cycle,String command) {
        return request(dir,cycle,command)!=null;
    }
    private static JsonObject request(Path dir,int cycle,String command) {
        try {
            return Files.readAllLines(dir.resolve("requests-"+cycle+".jsonl")).stream()
                    .map(line -> JsonParser.parseString(line).getAsJsonObject())
                    .filter(message -> command.equals(message.get("command").getAsString())).findFirst().orElse(null);
        } catch(Exception notReady) { return null; }
    }
    private static void waitUntil(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while(!condition.getAsBoolean() && System.nanoTime()<deadline) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        assertTrue("Timed out waiting for debugger lifecycle",condition.getAsBoolean());
    }
}
