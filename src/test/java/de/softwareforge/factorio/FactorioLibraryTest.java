package de.softwareforge.factorio;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FactorioLibraryTest {
    @Test void exclusionsStayOnDataLibraryAndPreserveApiLibrary() {
        var config=JsonParser.parseString("{\"workspace\":{\"library\":[\"/generated/api\"],\"ignoreGlobs\":[\"custom/**\"]}}").getAsJsonObject();
        FactorioLibrary.add(config,Path.of("/factorio/data"),FactorioLibrary.IGNORE_DIR,FactorioLibrary.IGNORE_GLOBS);
        var workspace=config.getAsJsonObject("workspace");
        assertEquals("custom/**",workspace.getAsJsonArray("ignoreGlobs").get(0).getAsString());
        var libraries=workspace.getAsJsonArray("library");
        assertEquals("/generated/api",libraries.get(0).getAsString());
        var data=libraries.get(1).getAsJsonObject();
        assertTrue(data.getAsJsonArray("ignoreDir").contains(new JsonPrimitive("core/lualib/story.lua")));
        assertTrue(data.getAsJsonArray("ignoreDir").contains(new JsonPrimitive("core/lualib/util.lua")));
        assertTrue(data.getAsJsonArray("ignoreGlobs").contains(new JsonPrimitive("*/scenarios/**")));
    }
    @Test void customListsReplaceDefaultsAndAllowEmptyLists() {
        var config=JsonParser.parseString("{\"workspace\":{\"library\":[]}}").getAsJsonObject();
        FactorioLibrary.add(config,Path.of("/data")," custom/path \n\ncustom/path\n","");
        var data=config.getAsJsonObject("workspace").getAsJsonArray("library").get(0).getAsJsonObject();
        assertEquals("[\"custom/path\"]",data.getAsJsonArray("ignoreDir").toString());
        assertTrue(data.getAsJsonArray("ignoreGlobs").isEmpty());
    }
}
