package de.softwareforge.factorio;

import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class FactorioDebugVariablesTest {
    @Test void shortensNativeReferencesIncludingNestedPreviewsAndKeepsSourceLocations() {
        assertEquals("{ [30360377] = [table], }",preview("table","{ [30360377] = [table *0x00006000e1792220], }"));
        assertEquals("[C function]",preview("function","[C function *0x0000000102c55bc8]"));
        assertEquals("[C closure]",preview("function","[C closure *0x0000600010bc8640]"));
        assertEquals("[Lua helper.lua:5]",preview("function","[Lua helper.lua:5 *0x00006000024ed0e0]"));
        assertEquals("[Lua]",preview("function","[Lua *0x0000600003043400]"));
        assertEquals("{ [\"set\"] = [Lua metrics.lua:50], [\"other\"] = [table], }",
                preview("table","{ [\"set\"] = [Lua metrics.lua:50 *0x12ab], [\"other\"] = [table *0xCAFE], }"));
        assertEquals("[userdata]",preview("userdata","[userdata *0xabcdef]"));
        assertEquals("[thread]",preview("thread","[thread *0xabcdef]"));
    }

    @Test void preservesStringsEscapedQuotesOrdinaryHexValuesAndUnrecognizedText() {
        String literal="[table *0x1234]";
        assertEquals(literal,preview("string",literal));
        assertEquals("0x1234",preview("number","0x1234"));
        assertEquals("*0x1234",preview(null,"*0x1234"));
        String table="{ [\"[table *0x1234]\"] = \"escaped \\\"[C function *0xabcd]\\\"\", [\"t\"] = [table *0x5678] }";
        assertEquals(table.replace("[table *0x5678]","[table]"),preview("table",table));
        String single="{ 'escaped \\'[table *0x1234]' = [Lua source's-file.lua:5 *0x5678] }";
        assertEquals(single.replace(" *0x5678",""),preview("table",single));
        // Truncated strings and unfamiliar descriptors are left intact.
        String truncated="{ [\"text\"] = \"[table *0x1234] ...";
        assertEquals(truncated,preview("table",truncated));
        assertEquals("[custom *0x1234]",preview("table","[custom *0x1234]"));
        assertEquals("[table *0xNOTHEX]",preview("table","[table *0xNOTHEX]"));
    }

    @Test void removesOnlyNativeVirtualAddressRowsWithoutChangingTheResponse() {
        var hint=new VariablePresentationHint();hint.setKind("virtual");
        var address=variable("address","*0x0000600018e93720");address.setName("[address]");address.setPresentationHint(hint);
        var legacyAddress=variable(null,"*0x1234");legacyAddress.setName("[address]");legacyAddress.setPresentationHint(hint);
        var field=variable("string","*0x1234");field.setName("[address]");
        var virtualString=variable("string","*0x1234");virtualString.setName("[address]");virtualString.setPresentationHint(hint);
        var metatable=variable("table","[table *0x1234]");metatable.setName("[metatable]");metatable.setPresentationHint(hint);
        var child=variable("table","{value = 42}");child.setVariablesReference(7);
        var response=new VariablesResponse();
        var original=new Variable[]{address,legacyAddress,field,virtualString,metatable,child};response.setVariables(original);
        var request=new VariablesArguments();request.setVariablesReference(42);request.setStart(10);request.setCount(20);
        IDebugProtocolServer delegate=new IDebugProtocolServer() {
            @Override public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
                assertSame(request,args);return CompletableFuture.completedFuture(response);
            }
        };
        var hidden=new AtomicBoolean();var server=new FactorioDebugVariables(hidden::get).filterServer(delegate);
        assertSame(response,server.variables(request).join());
        hidden.set(true);
        var visible=server.variables(request).join();
        assertNotSame(response,visible);
        assertArrayEquals(new Variable[]{field,virtualString,metatable,child},visible.getVariables());
        assertSame(child,visible.getVariables()[3]);assertEquals(7,child.getVariablesReference());
        assertSame(original,response.getVariables());assertEquals(6,original.length);
        assertEquals("*0x0000600018e93720",address.getValue());
        hidden.set(false);assertSame(response,server.variables(request).join());
        // An empty table may have only an address, leaving zero visible rows.
        response.setVariables(new Variable[]{address});hidden.set(true);
        assertEquals(0,server.variables(request).join().getVariables().length);
    }

    @Test void delegatesOtherRequestsAndPreservesFailures() {
        var evaluate=new EvaluateArguments();evaluate.setExpression("test");
        var future=new CompletableFuture<EvaluateResponse>();
        var failure=new IllegalStateException("adapter error");
        IDebugProtocolServer delegate=new IDebugProtocolServer() {
            @Override public CompletableFuture<EvaluateResponse> evaluate(EvaluateArguments args) {
                assertSame(evaluate,args);return future;
            }
            @Override public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
                return CompletableFuture.failedFuture(failure);
            }
            @Override public CompletableFuture<Void> terminate(TerminateArguments args) { throw failure; }
        };
        var server=new FactorioDebugVariables(() -> true).filterServer(delegate);
        assertSame(future,server.evaluate(evaluate));
        assertSame(failure,assertThrows(CompletionException.class,() -> server.variables(new VariablesArguments()).join()).getCause());
        assertSame(failure,assertThrows(IllegalStateException.class,() -> server.terminate(new TerminateArguments())));
        assertEquals(server,server);assertNotEquals(delegate,server);
    }

    @Test void settingIsReversibleWithoutMutatingValuesOrExpansionReferences() {
        var hidden=new AtomicBoolean();var support=new FactorioDebugVariables(hidden::get);
        var variable=variable("function","[Lua helper.lua:5 *0x1234]");
        variable.setVariablesReference(42);variable.setEvaluateName("helper");
        assertEquals(variable.getValue(),support.formatValue(variable));
        hidden.set(true);assertEquals("[Lua helper.lua:5]",support.formatValue(variable));
        assertEquals("[Lua helper.lua:5 *0x1234]",variable.getValue());
        assertEquals(42,variable.getVariablesReference());assertEquals("helper",variable.getEvaluateName());
        hidden.set(false);assertEquals(variable.getValue(),support.formatValue(variable));
        hidden.set(true);
        assertEquals("[table *0x1234]",support.formatValue(variable("string","\"[table *0x1234]\"")));
    }

    private static String preview(String type,String value) { return FactorioDebugVariables.preview(variable(type,value),true); }
    private static Variable variable(String type,String value) {
        var variable=new Variable();variable.setName("test");variable.setType(type);variable.setValue(value);return variable;
    }
}
