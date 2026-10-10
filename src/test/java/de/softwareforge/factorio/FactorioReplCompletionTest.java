package de.softwareforge.factorio;

import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class FactorioReplCompletionTest {
    @Test void parsesOnlySafeRuntimePaths() {
        assertEquals(List.of(),query("loc").path());
        assertEquals("loc",query("loc").prefix());
        assertEquals(List.of(key("nested"),key("inner")),query("return nested.inner.va").path());
        assertEquals(List.of(key("storage"),key("creative-mode"),new FactorioReplExpression.Key("1",true)),query("storage['creative-mode'][1].").path());
        assertEquals(List.of(key("a"),new FactorioReplExpression.Key("-1",true)),query("a[-1].").path());
        assertEquals("get_",query("surface:get_").prefix());
        assertEquals("creative-",query("storage[\"creative-").prefix());
        for (String unsafe:List.of("get_train().", "obj[computed].", "-- comment", "'a string", "[[long string", "obj.method().x.", "['literal']."))
            assertNull(FactorioReplExpression.parse(unsafe,unsafe.length()),unsafe);
    }

    @Test void escapesKeysAndExcludesInvalidDotMembers() {
        var bracket=query("t[");
        assertEquals("\"creative-mode\"",bracket.insertion("creative-mode",false));
        assertEquals("]",bracket.closing());
        assertTrue(bracket.accepts("12",true));
        var quoted=query("t['");
        assertEquals("can\\'t",quoted.insertion("can't",false));
        assertEquals("']",quoted.closing());
        assertFalse(query("t.").accepts("creative-mode",false));
        assertFalse(query("t.").accepts("end",false));
        assertFalse(query("t.").accepts("12",true));
        assertFalse(quoted.accepts("12",true));
        assertEquals("a\nb\"c",FactorioReplExpression.unquote("\"a\\nb\\\"c\""));
    }

    @Test void localShadowingMemberTraversalStorageIsolationAndCache() throws Exception {
        var requests=new ArrayList<Integer>();
        var scopeRequests=new AtomicInteger();
        var server=new IDebugProtocolServer() {
            @Override public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
                scopeRequests.incrementAndGet();assertEquals(7,args.getFrameId());
                var response=new ScopesResponse();response.setScopes(new Scope[]{scope("Globals",4),scope("mod storage",3),scope("Upvals",2),scope("Locals",1)});
                return CompletableFuture.completedFuture(response);
            }
            @Override public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
                requests.add(args.getVariablesReference());
                return values(switch(args.getVariablesReference()) {
                    case 1 -> new Variable[]{variable("[1] shadow",10),variable("[2] shadow",11),variable("[3] nested",12),virtual("[temporaries]",99)};
                    case 2 -> new Variable[]{variable("shadow",20),variable("upvalue",0)};
                    case 4 -> new Variable[]{variable("\"shadow\"",30),variable("\"storage\"",13),variable("\"game\"",0)};
                    case 11 -> new Variable[]{variable("\"localWins\"",0)};
                    case 12 -> new Variable[]{variable("\"inner\"",14),variable("\"creative-mode\"",15),variable("[1] nauvis",16),lazy("unsafe",99),virtual("[address]",0)};
                    case 14 -> new Variable[]{variable("\"value\"",0)};
                    case 15 -> new Variable[]{variable("\"quoted\"",0)};
                    case 16 -> new Variable[]{variable("\"indexed\"",0)};
                    default -> throw new AssertionError("Must not enumerate storage scope, shadowed variables or virtual nodes: "+args.getVariablesReference());
                });
            }
        };
        var engine=new FactorioReplCompletion(server,7,() -> true);
        assertEquals(Set.of("shadow","nested","upvalue","storage","game"),keys(engine,""));
        assertEquals(Set.of("localWins"),keys(engine,"shadow."));
        assertEquals(Set.of("inner"),keys(engine,"nested."));
        assertEquals(Set.of("creative-mode"),keys(engine,"nested['cre"));
        assertEquals(Set.of("value"),keys(engine,"nested.inner."));
        assertEquals(Set.of("quoted"),keys(engine,"nested['creative-mode']."));
        assertEquals(Set.of("indexed"),keys(engine,"nested[1]."));
        assertTrue(keys(engine,"missing.").isEmpty());
        assertTrue(keys(engine,"nested.inner.value.").isEmpty());
        assertEquals(1,scopeRequests.get());
        assertEquals(requests.size(),new HashSet<>(requests).size(),"One read per reference and paused epoch");
    }

    @Test void ignoresLateRepliesAndDoesNotRequestChildrenAfterResume() {
        var alive=new AtomicBoolean(true);
        var pending=new CompletableFuture<ScopesResponse>();
        var server=new IDebugProtocolServer() {
            @Override public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) { return pending; }
            @Override public CompletableFuture<VariablesResponse> variables(VariablesArguments args) { fail("Stale frame");return null; }
        };
        var engine=new FactorioReplCompletion(server,7,alive::get);
        var completion=engine.complete(query("loc"));
        alive.set(false);
        var response=new ScopesResponse();response.setScopes(new Scope[]{scope("Locals",1)});pending.complete(response);
        assertTrue(completion.join().isEmpty());
        assertTrue(engine.complete(query("nested.")).join().isEmpty());
    }

    @Test void boundsLargeTablesAndRetriesFailedRequests() throws Exception {
        var attempts=new AtomicInteger();
        var server=new IDebugProtocolServer() {
            @Override public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
                if (attempts.incrementAndGet()==1) return CompletableFuture.failedFuture(new IllegalStateException("expired"));
                var response=new ScopesResponse();response.setScopes(new Scope[]{scope("Locals",1)});return CompletableFuture.completedFuture(response);
            }
            @Override public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
                return values(java.util.stream.IntStream.range(0,1500).mapToObj(i -> variable("name"+i,0)).toArray(Variable[]::new));
            }
        };
        var engine=new FactorioReplCompletion(server,7,() -> true);
        assertThrows(CompletionException.class,() -> engine.complete(query("name")).join());
        engine.retry();
        assertEquals(FactorioReplCompletion.LIMIT,keys(engine,"name").size());
    }

    private static FactorioReplExpression.Query query(String text) { return FactorioReplExpression.parse(text,text.length()); }
    private static FactorioReplExpression.Key key(String name) { return new FactorioReplExpression.Key(name,false); }
    private static Set<String> keys(FactorioReplCompletion engine,String text) throws Exception {
        return engine.complete(query(text)).get(5,TimeUnit.SECONDS).stream().map(FactorioReplCompletion.Item::key).collect(java.util.stream.Collectors.toSet());
    }
    private static Scope scope(String name,int reference) { var s=new Scope();s.setName(name);s.setVariablesReference(reference);return s; }
    private static Variable variable(String name,int reference) { var v=new Variable();v.setName(name);v.setValue("test");v.setVariablesReference(reference);return v; }
    private static Variable lazy(String name,int reference) { var v=variable(name,reference);var hint=new VariablePresentationHint();hint.setLazy(true);v.setPresentationHint(hint);return v; }
    private static Variable virtual(String name,int reference) { var v=variable(name,reference);var hint=new VariablePresentationHint();hint.setKind("virtual");v.setPresentationHint(hint);return v; }
    private static CompletableFuture<VariablesResponse> values(Variable[] variables) { var response=new VariablesResponse();response.setVariables(variables);return CompletableFuture.completedFuture(response); }
}
