package de.softwareforge.factorio;

import com.google.gson.JsonParser;
import com.redhat.devtools.lsp4ij.console.explorer.TracingMessageConsumer;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in acceptance against the disposable protocol fixture; launches the real game. */
@EnabledIfEnvironmentVariable(named="FACTORIO_DAP_FIXTURE",matches=".+")
class FactorioDapProtocolTest {
    @Test void logsLaunchBreakpointInspectionSteppingAndRelaunch() throws Exception {
        Path fixture=Path.of(System.getenv("FACTORIO_DAP_FIXTURE")).toRealPath();
        Path output=Files.createTempDirectory(Files.createDirectories(Path.of("build/test-work/dap-protocol")),"session-").toAbsolutePath();
        var paths=new HashSet<Path>();
        for(int cycle=0;cycle<2;cycle++) {
            var logPath=new CompletableFuture<Path>();var errors=new CopyOnWriteArrayList<Exception>();
            var log=new FactorioDapLog(() -> output,"Factorio probe",logPath::complete,errors::add);
            String executable=Objects.requireNonNullElse(System.getenv("FACTORIO_EXECUTABLE"),"/Applications/factorio.app/Contents/MacOS/factorio");
            var process=new ProcessBuilder(executable,"--dap").directory(fixture.toFile())
                .redirectError(output.resolve("stderr-"+cycle+".txt").toFile()).start();
            var executor=Executors.newCachedThreadPool();
            Future<?> listening=null;
            var initialized=new CompletableFuture<Void>();
            var stopped=new LinkedBlockingQueue<StoppedEventArguments>();
            var console=Collections.synchronizedList(new ArrayList<String>());
            var rawOutput=Collections.synchronizedList(new ArrayList<String>());
            var shutdown=new java.util.concurrent.atomic.AtomicReference<FactorioDapShutdown>();
            try {
                var client=new IDebugProtocolClient() {
                    @Override public void initialized() {initialized.complete(null);}
                    @Override public void stopped(StoppedEventArguments event) {stopped.add(event);}
                    @Override public void output(OutputEventArguments event) {rawOutput.add(event.getOutput());}
                    @Override public void terminated(TerminatedEventArguments event) {shutdown.get().terminated(false);}
                };
                var consoleTrace=new TracingMessageConsumer();
                var launcher=DSPLauncher.createClientLauncher(client,process.getInputStream(),process.getOutputStream(),executor,
                    FactorioDapLog.route(log,consumer -> message -> {
                        console.add(consoleTrace.log(message,consumer,ServerTrace.verbose));
                        consumer.consume(message);
                    },error -> fail("Unexpected protocol error: "+error)));
                listening=launcher.startListening();var server=launcher.getRemoteProxy();
                var initialize=new InitializeRequestArguments();initialize.setAdapterID("factorio");initialize.setClientID("factorio-plugin-test");
                initialize.setLinesStartAt1(true);initialize.setColumnsStartAt1(true);initialize.setPathFormat("path");
                var capabilities=await(server.initialize(initialize));
                shutdown.set(new FactorioDapShutdown(() -> server,
                        () -> Boolean.TRUE.equals(capabilities.getSupportsTerminateRequest()),
                        () -> process.toHandle().destroy()));
                var launch=server.launch(Map.of("factorioArgs",List.of("--config",fixture.resolve("config.ini").toString(),
                    "--mod-directory",fixture.resolve("mods").toString(),"--load-game",fixture.resolve("probe.zip").toString(),"--disable-audio"),
                    "followSymlinks",true,"hookDebugConsole",true));
                await(initialized);
                Path control=fixture.resolve("mods/fmtk-probe/control.lua");
                var lines=Files.readAllLines(control);
                int line=java.util.stream.IntStream.range(0,lines.size()).filter(i -> lines.get(i).contains("-- BREAKPOINT")).findFirst().orElseThrow()+1;
                var source=new Source();source.setPath(control.toString());
                var breakpoint=new SourceBreakpoint();breakpoint.setLine(line);
                var set=new SetBreakpointsArguments();set.setSource(source);set.setBreakpoints(new SourceBreakpoint[]{breakpoint});
                await(server.setBreakpoints(set));
                var exceptions=new SetExceptionBreakpointsArguments();exceptions.setFilters(new String[0]);await(server.setExceptionBreakpoints(exceptions));
                await(server.configurationDone(new ConfigurationDoneArguments()));await(launch);
                var stop=stopped.poll(90,TimeUnit.SECONDS);assertNotNull(stop,"Breakpoint was not hit");int thread=stop.getThreadId();
                var stackArgs=new StackTraceArguments();stackArgs.setThreadId(thread);
                var stack=await(server.stackTrace(stackArgs));int frame=stack.getStackFrames()[0].getId();
                var scopes=new ScopesArguments();scopes.setFrameId(frame);
                for(var scope:await(server.scopes(scopes)).getScopes()) {
                    if(scope.getName().toLowerCase(Locale.ROOT).startsWith("local")) {
                        var variables=new VariablesArguments();variables.setVariablesReference(scope.getVariablesReference());
                        assertTrue(await(server.variables(variables)).getVariables().length>0);
                    }
                }
                var evaluate=new EvaluateArguments();evaluate.setExpression("count + 1");evaluate.setFrameId(frame);evaluate.setContext("watch");
                assertEquals("42",await(server.evaluate(evaluate)).getResult());
                var into=new StepInArguments();into.setThreadId(thread);await(server.stepIn(into));assertNotNull(stopped.poll(30,TimeUnit.SECONDS));
                var out=new StepOutArguments();out.setThreadId(thread);await(server.stepOut(out));assertNotNull(stopped.poll(30,TimeUnit.SECONDS));
                var next=new NextArguments();next.setThreadId(thread);await(server.next(next));assertNotNull(stopped.poll(30,TimeUnit.SECONDS));
                shutdown.get().stop();
                assertTrue(process.waitFor(15,TimeUnit.SECONDS));assertEquals(0,process.exitValue());
                shutdown.get().exited();
                listening.get(10,TimeUnit.SECONDS);
                log.close();await(log.completion());
                Path file=await(logPath);assertTrue(paths.add(file));
                var logLines=Files.readAllLines(file);
                var messages=logLines.stream().map(lineText -> JsonParser.parseString(lineText).getAsJsonObject()).toList();
                for(int i=0;i<messages.size();i++) {
                    assertEquals(messages.get(i).toString(),logLines.get(i),"Expected one compact DAP envelope per line");
                    assertTrue(messages.get(i).get("seq").getAsJsonPrimitive().isNumber());
                    assertFalse(messages.get(i).has("jsonrpc"));
                }
                for(String method:List.of("initialize","launch","setBreakpoints","configurationDone","stackTrace","scopes","variables","evaluate","stepIn","stepOut","next","terminate")) {
                    var request=messages.stream().filter(m -> "request".equals(m.get("type").getAsString()) && method.equals(m.get("command").getAsString())).findFirst().orElseThrow();
                    var response=messages.stream().filter(m -> "response".equals(m.get("type").getAsString()) && method.equals(m.get("command").getAsString())).findFirst().orElseThrow();
                    assertEquals(request.get("seq"),response.get("request_seq"),method+" request/response IDs must match");
                    assertTrue(response.get("success").getAsBoolean());
                }
                assertTrue(console.isEmpty(),"File mode must not emit protocol traces to the console");
                assertTrue(messages.stream().anyMatch(m -> m.has("event") && "stopped".equals(m.get("event").getAsString())));
                assertTrue(messages.stream().anyMatch(m -> m.has("event") && "terminated".equals(m.get("event").getAsString())));
                assertTrue(messages.stream().anyMatch(m -> m.has("command") && "disconnect".equals(m.get("command").getAsString())));
                assertTrue(rawOutput.stream().anyMatch(text -> text.contains("Goodbye")), "Factorio should finish normal cleanup");
                var evaluation=messages.stream().filter(m -> m.has("arguments") && m.getAsJsonObject("arguments").has("expression")).findFirst().orElseThrow();
                assertEquals("count + 1",evaluation.getAsJsonObject("arguments").get("expression").getAsString());
                assertTrue(messages.stream().anyMatch(m -> m.has("event") && "output".equals(m.get("event").getAsString()) && m.getAsJsonObject("body").has("output")));
                assertFalse(rawOutput.isEmpty(),"Protocol output events must still reach the client");
                assertTrue(errors.isEmpty(),errors.toString());
            } finally {
                if(shutdown.get()!=null) shutdown.get().exited();
                process.destroyForcibly();process.waitFor(10,TimeUnit.SECONDS);
                if(listening!=null)listening.cancel(true);executor.shutdownNow();
                log.close();await(log.completion());
            }
        }
    }
    private static <T> T await(CompletableFuture<T> future) throws Exception {return future.get(90,TimeUnit.SECONDS);}
}
