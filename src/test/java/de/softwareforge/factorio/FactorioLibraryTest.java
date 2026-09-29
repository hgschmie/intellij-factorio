package de.softwareforge.factorio;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FactorioLibraryTest {
    @TempDir Path directory;
    @Test void mapsOnlyInstalledBundledModsAndKeepsExistingMappings() throws Exception {
        var config=JsonParser.parseString("{\"workspace\":{\"library\":[],\"moduleMap\":[{\"pattern\":\"^mine[.](.*)$\",\"replace\":\"__mine__.$1\"}]}}").getAsJsonObject();
        for (String name:List.of("base","core")) Files.writeString(Files.createDirectories(directory.resolve(name)).resolve("info.json"),"{}");
        Files.createDirectories(directory.resolve("quality")); // A directory alone is not an installed mod.
        FactorioLibrary.add(config,directory,FactorioLibrary.IGNORE_DIR,FactorioLibrary.IGNORE_GLOBS);
        var maps=config.getAsJsonObject("workspace").getAsJsonArray("moduleMap");
        assertEquals(3,maps.size());
        assertEquals("__mine__.$1",maps.get(0).getAsJsonObject().get("replace").getAsString());
        for (String name:List.of("elevated-rails","quality","recycler","space-age")) Files.writeString(Files.createDirectories(directory.resolve(name)).resolve("info.json"),"{}");
        FactorioLibrary.add(config,directory,FactorioLibrary.IGNORE_DIR,FactorioLibrary.IGNORE_GLOBS);
        assertEquals(7,maps.size());
        for (String name:List.of("base","core","elevated-rails","quality","recycler","space-age")) {
            assertTrue(maps.asList().stream().anyMatch(m -> m.getAsJsonObject().get("replace").getAsString().equals("__"+name+"__.$1")));
        }
        assertFalse(config.getAsJsonObject("workspace").has("packages"));
    }
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
