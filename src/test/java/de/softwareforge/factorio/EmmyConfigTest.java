package de.softwareforge.factorio;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EmmyConfigTest {
    @Test void preservesUserConfigAndReplacesManagedEntries() {
        var original=JsonParser.parseString("""
          {"diagnostics":{"disable":["custom"]},"workspace":{"library":["user","old"],"moduleMap":[{"pattern":"user","replace":"user"}]},"runtime":{"version":"Lua 5.4"}}
          """).getAsJsonObject();
        var previous=JsonParser.parseString("{\"library\":[\"old\"]}").getAsJsonObject();
        var result=EmmyConfig.merge(original,previous,List.of("new"),List.of("mod"),Map.of("dependency","Dependency"));
        assertEquals("[\"user\",\"new\"]",result.getAsJsonObject("workspace").getAsJsonArray("library").toString());
        assertEquals(2,result.getAsJsonObject("workspace").getAsJsonArray("moduleMap").size());
        assertEquals("Lua 5.4",result.getAsJsonObject("runtime").get("version").getAsString());
        assertEquals(original.get("diagnostics"),result.get("diagnostics"));
        assertTrue(original.toString().contains("old"));
    }
    @Test void regenerationIsIdempotentAndDoesNotClaimUserEntries() {
        var original=JsonParser.parseString("{\"workspace\":{\"library\":[\"user\"]}}").getAsJsonObject();
        var first=EmmyConfig.merge(original,new JsonObject(),List.of("user","managed"),List.of("mod"),Map.of());
        var owned=EmmyConfig.ownership(original,first);
        assertEquals("[\"managed\"]",owned.get("library").toString());
        assertEquals(first,EmmyConfig.merge(first,owned,List.of("user","managed"),List.of("mod"),Map.of()));
        var removed=EmmyConfig.merge(first,owned,List.of(),List.of(),Map.of());
        assertEquals("[\"user\"]",removed.getAsJsonObject("workspace").get("library").toString());
    }
}
