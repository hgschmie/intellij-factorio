package de.softwareforge.factorio;

import com.google.gson.JsonParser;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.debug.json.DebugMessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugResponseMessage;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletionException;
import static org.junit.jupiter.api.Assertions.*;

class FactorioDapErrorsTest {
    @Test void parsesFactorioWireErrorWithoutChangingRawMessage() {
        String wire="""
                {"type":"response","seq":42,"request_seq":3,"command":"evaluate","success":false,
                 "message":"error","body":{"error":{"id":1,"format":"attempt to index nil"}}}
                """;
        var json=new DebugMessageJsonHandler(Map.of());
        var response=(DebugResponseMessage)json.parseMessage(wire);
        var original=new ResponseErrorException(response.getError());
        var detailed=(ResponseErrorException)FactorioDapErrors.detailed(new CompletionException(original));
        assertEquals("attempt to index nil",detailed.getResponseError().getMessage());
        assertEquals("error",response.getError().getMessage());
        assertEquals(JsonParser.parseString(wire),JsonParser.parseString(json.serialize(response)));
    }

    @Test void formatsVariablesAndFallsBackForMissingOrMalformedDetails() {
        var error=new ResponseError(1,"fallback",Map.of("error",Map.of("format","Bad {name}: {value} ({unknown})",
                "variables",Map.of("name","$field","value","\\nil"))));
        assertEquals("Bad $field: \\nil ({unknown})",FactorioDapErrors.message(error));
        for (Object data:List.of(Map.of(),Map.of("error",List.of()),Map.of("error",Map.of("format",false)),"not an object")) {
            assertEquals("fallback",FactorioDapErrors.message(new ResponseError(1,"fallback",data)));
        }
        assertEquals("Debug adapter request failed",FactorioDapErrors.message(new ResponseError(1,null,null)));
    }

    @Test void evaluationErrorsStayOutOfConsoleInEveryTraceModeButOtherFailuresRemainVisible() {
        for (ServerTrace trace:ServerTrace.values()) {
            var reported=new ArrayList<String>();var delivered=new ArrayList<Object>();
            var route=FactorioDapLog.route(null,trace,text -> {},reported::add).apply(delivered::add);
            var response=new DebugResponseMessage();response.setId(1);response.setResponseId(3);response.setMethod("evaluate");
            response.setError(new ResponseError(1,"error",Map.of("error",Map.of("format","Missing variable"))));
            route.consume(response);
            assertTrue(reported.isEmpty());assertEquals(List.of(response),delivered);
            response.setMethod("launch");route.consume(response);
            assertEquals(List.of("Missing variable"),reported);
        }
    }
}
