package de.softwareforge.factorio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Runs the production DAP client, UI, history, evaluation and variable expansion. */
public class FactorioReplTest extends HeavyPlatformTestCase {
    private String backgroundTasks;
    @Override protected void setUp() throws Exception {
        super.setUp();
        backgroundTasks=System.getProperty("intellij.progress.task.ignoreHeadless");
        System.setProperty("intellij.progress.task.ignoreHeadless","true");
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(),
                Path.of(System.getProperty("factorio.test.workspace"),"intellij-factorio/.intellijPlatform/sandbox").toString());
    }
    @Override protected void tearDown() throws Exception {
        try { super.tearDown(); }
        finally {
            if (backgroundTasks==null) System.clearProperty("intellij.progress.task.ignoreHeadless");
            else System.setProperty("intellij.progress.task.ignoreHeadless",backgroundTasks);
        }
    }
    public void testReplWithFileTracing() throws Exception { runRepl(ServerTrace.verbose,true); }
    public void testReplWithConsoleTracing() throws Exception { runRepl(ServerTrace.verbose,false); }
    public void testReplWithoutTracing() throws Exception { runRepl(ServerTrace.off,false); }

    public void testRuntimeCompletion() throws Exception { runRepl(ServerTrace.off,false,true); }
    public void testHiddenAddressesWithRawFileTracing() throws Exception { runRepl(ServerTrace.verbose,true,true,true); }

    private void runRepl(ServerTrace trace,boolean file) throws Exception { runRepl(trace,file,false); }
    private void runRepl(ServerTrace trace,boolean file,boolean completion) throws Exception {
        runRepl(trace,file,completion,false);
    }
    private void runRepl(ServerTrace trace,boolean file,boolean completion,boolean hideAddresses) throws Exception {
        FactorioSettings.get(getProject()).serviceMode="ENABLED";
        FactorioSettings.get(getProject()).hideDebugAddresses=hideAddresses;
        Path dir=Files.createTempDirectory(Files.createDirectories(Path.of(System.getProperty("factorio.test.work"))),"repl-");
        Path fixture=Path.of(System.getProperty("factorio.test.workspace"),"intellij-factorio/src/ideTest/fixtures/evaluation-dap.py");
        Path executable=dir.resolve("adapter");
        Files.writeString(executable,"#!/usr/bin/python3\nimport runpy\nrunpy.run_path("+PathsAndMods.JSON.toJson(fixture.toString())+
                ",init_globals={'ROOT':"+PathsAndMods.JSON.toJson(dir.toString())+"})\n");
        assertTrue(executable.toFile().setExecutable(true));
        Files.writeString(dir.resolve("control.lua"),"local a = 1\n");
        var settings=RunManager.getInstance(getProject()).createConfiguration("REPL test",new FactorioDebug.Type().getConfigurationFactories()[0]);
        var config=(FactorioDebug.Configuration)settings.getConfiguration();
        config.setCommand(executable.toString());config.setWorkingDirectory(dir.toString());config.setServerTrace(trace);
        // An existing pre-0.6 configuration must receive the fix, too.
        config.setLaunchConfiguration("{\"request\":\"launch\",\"factorioArgs\":[],\"hookDebugConsole\":true}");
        config.getOptions().setDapLogToFile(file);config.getOptions().setDapLogDirectory(dir.resolve("logs").toString());
        var env=ExecutionEnvironmentBuilder.create(DefaultDebugExecutor.getDebugExecutorInstance(),settings).build();
        FactorioDebugProcessHandler handler=null;
        try {
            ProgramRunnerUtil.executeConfiguration(env,false,false);
            waitUntil(() -> java.util.Arrays.stream(XDebuggerManager.getInstance(getProject()).getDebugSessions()).anyMatch(s -> s.isSuspended() && s.getCurrentStackFrame()!=null && s.getUI().findContent(FactorioRepl.CONTENT_ID)!=null));
            var session=XDebuggerManager.getInstance(getProject()).getDebugSessions()[0];
            handler=(FactorioDebugProcessHandler)session.getDebugProcess().getProcessHandler();
            var repl=(FactorioRepl)session.getUI().findContent(FactorioRepl.CONTENT_ID).getComponent();
            var tree=UIUtil.findComponentOfType(repl,XDebuggerTree.class);
            assertNotNull(tree);
            var input=ReflectionUtil.getField(FactorioRepl.class,repl,XDebuggerExpressionComboBox.class,"input");
            assertNotNull(input);
            if (completion) {
                complete(input,"nes<caret>","nested","nested");
                complete(input,"nested.in<caret>","inner","nested.inner");
                complete(input,"nested.inner.va<caret>lue","value","nested.inner.value");
                complete(input,"nested['cre<caret>","creative-mode","nested['creative-mode']");
                complete(input,"nested[\"cre<caret>ative-mode\"].value","creative-mode","nested[\"creative-mode\"].value");
                complete(input,"nested['creative-mode'].va<caret>","value","nested['creative-mode'].value");
                assertFalse(Files.readString(dir.resolve("requests.jsonl")).contains("\"command\": \"evaluate\""));
            }
            input.setExpression(XExpressionImpl.fromText("nested"));repl.submit();
            waitUntil(() -> tree.getRoot().getChildCount()==1 && ((XValueNodeImpl)tree.getRoot().getChildAt(0)).getRawValue().contains("inner"));
            var table=(XValueNodeImpl)tree.getRoot().getChildAt(0);
            assertEquals(!hideAddresses,table.getRawValue().contains("*0x1234"));
            table.startComputingChildren();
            waitUntil(() -> !table.getLoadedChildren().isEmpty());
            assertEquals(hideAddresses?2:3,table.getLoadedChildren().size());
            assertEquals(!hideAddresses,table.getLoadedChildren().stream().map(XValueNodeImpl.class::cast).anyMatch(n -> n.getName().equals("[address]")));
            var inner=table.getLoadedChildren().stream().map(XValueNodeImpl.class::cast).filter(n -> n.getName().equals("inner")).findFirst().orElseThrow();
            assertEquals("inner",inner.getName());inner.startComputingChildren();
            waitUntil(() -> !inner.getLoadedChildren().isEmpty());
            assertEquals(hideAddresses?1:2,inner.getLoadedChildren().size());
            assertEquals(!hideAddresses,inner.getLoadedChildren().stream().map(XValueNodeImpl.class::cast).anyMatch(n -> n.getName().equals("[address]")));
            assertEquals("42",inner.getLoadedChildren().stream().map(XValueNodeImpl.class::cast).filter(n -> n.getName().equals("value")).findFirst().orElseThrow().getRawValue());
            input.setExpression(XExpressionImpl.fromText("bad()"));repl.submit();
            waitUntil(() -> tree.getRoot().getChildCount()==2 && ((XValueNodeImpl)tree.getRoot().getChildAt(1)).getRawValue().contains("invalid argument 3"));
            assertTrue(input.getRecentExpressions().stream().anyMatch(e -> e.getExpression().equals("nested")));
            assertTrue(input.getRecentExpressions().stream().anyMatch(e -> e.getExpression().equals("bad()")));
            var watchError=new CompletableFuture<String>();
            session.getCurrentStackFrame().getEvaluator().evaluate("bad()",new XDebuggerEvaluator.XEvaluationCallback() {
                @Override public void evaluated(XValue value) { watchError.complete("unexpected success"); }
                @Override public void errorOccurred(String error) { watchError.complete(error); }
            },null);
            waitUntil(watchError::isDone);assertEquals("LuaTrain.get_rail_end: invalid argument 3",watchError.join());
            // No duplicate submissions while evaluation is pending; late replies cannot revive expired values.
            input.setExpression(XExpressionImpl.fromText("slow()"));repl.submit();repl.submit();
            session.resume();
            waitUntil(() -> !session.isSuspended() && tree.getRoot().getChildCount()==0);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while (System.nanoTime()<deadline) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue();Thread.sleep(20); }
            assertEquals(0,tree.getRoot().getChildCount());
            input.setExpression(XExpressionImpl.fromText("must_not_run"));repl.submit();
            var console=(FactorioDebugConsole)session.getConsoleView();
            console.flushDeferredText();
            String consoleText=console.getEditor().getDocument().getText();
            assertTrue(consoleText.contains("real stderr survives"));
            assertFalse(consoleText.lines().anyMatch("error"::equals));
            if (file || trace==ServerTrace.off) assertFalse(consoleText.contains("invalid argument 3"));
            handler.destroyProcess();session.stop();
            var stoppedHandler=handler;
            waitUntil(stoppedHandler::isProcessTerminated);
            waitUntil(() -> XDebuggerManager.getInstance(getProject()).getDebugSessions().length==0);
            if (hideAddresses) {
                try (var logs=Files.list(dir.resolve("logs"))) {
                    String raw=Files.readString(logs.findFirst().orElseThrow());
                    assertTrue(raw.contains("*0x1234"));assertTrue(raw.contains("[address]"));
                }
            }
            var requests=Files.readAllLines(dir.resolve("requests.jsonl")).stream().map(line -> JsonParser.parseString(line).getAsJsonObject()).toList();
            var launch=requests.stream().filter(r -> r.get("command").getAsString().equals("launch")).findFirst().orElseThrow();
            assertFalse(launch.getAsJsonObject("arguments").get("hookDebugConsole").getAsBoolean());
            var evaluations=requests.stream().filter(r -> r.get("command").getAsString().equals("evaluate")).map(r -> r.getAsJsonObject("arguments")).toList();
            assertEquals(4,evaluations.size());
            assertEquals("repl",evaluations.get(0).get("context").getAsString());
            assertEquals(7,evaluations.get(0).get("frameId").getAsInt());
            assertEquals("watch",evaluations.get(2).get("context").getAsString());
        } finally {
            if (handler!=null && handler.getProcess().isAlive()) handler.getProcess().destroyForcibly();
            for (var session:XDebuggerManager.getInstance(getProject()).getDebugSessions()) session.stop();
            waitUntil(() -> XDebuggerManager.getInstance(getProject()).getDebugSessions().length==0);
        }
    }
    private void complete(XDebuggerExpressionComboBox input,String source,String suggestion,String expected) throws Exception {
        int offset=source.indexOf("<caret>");
        input.setExpression(XExpressionImpl.fromText(""));
        waitUntil(() -> input.getEditor()!=null && com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getPsiFile(input.getEditor().getDocument())!=null);
        var editor=input.getEditor();assertNotNull("REPL editor",editor);
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(),() -> editor.getDocument().setText(source.replace("<caret>","")));
        editor.getCaretModel().moveToOffset(offset);
        com.intellij.psi.PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        var handler=new com.intellij.codeInsight.completion.CodeCompletionHandlerBase(com.intellij.codeInsight.completion.CompletionType.BASIC,true,false,false);
        handler.invokeCompletion(getProject(),editor);
        waitUntil(() -> {
            var lookup=com.intellij.codeInsight.lookup.LookupManager.getActiveLookup(editor);
            return lookup!=null && lookup.getItems().stream().anyMatch(i -> i.getLookupString().equals(suggestion));
        });
        var lookup=(com.intellij.codeInsight.lookup.impl.LookupImpl)com.intellij.codeInsight.lookup.LookupManager.getActiveLookup(editor);
        lookup.setCurrentItem(lookup.getItems().stream().filter(i -> i.getLookupString().equals(suggestion)).findFirst().orElseThrow());
        if (suggestion.equals("nested")) {
            var presentation=new com.intellij.codeInsight.lookup.LookupElementPresentation();
            lookup.getCurrentItem().renderElement(presentation);
            assertEquals(!FactorioSettings.get(getProject()).hideDebugAddresses,presentation.getTailText().contains("*0x1234"));
        }
        lookup.finishLookup('\n');
        assertEquals(expected,input.getExpression().getExpression());
        assertNull(com.intellij.codeInsight.lookup.LookupManager.getActiveLookup(editor));
    }
    private static void waitUntil(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        while (!condition.getAsBoolean() && System.nanoTime()<deadline) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue();Thread.sleep(20); }
        assertTrue("Timed out waiting for REPL",condition.getAsBoolean());
    }
}
