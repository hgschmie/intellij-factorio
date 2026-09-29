package de.softwareforge.factorio;

import com.google.gson.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Data-library exclusions from FMTK's VersionSelector, plus the stateful story helper. */
public final class FactorioLibrary {
    private FactorioLibrary() {}
    private static final List<String> BUNDLED_MODS = List.of("base", "core", "elevated-rails", "quality", "recycler", "space-age");
    public static final String IGNORE_DIR = String.join("\n",
        "core/lualib/event_handler.lua", "core/lualib/crash-site.lua", "core/lualib/math2d.lua",
        "core/lualib/meld.lua", "core/lualib/mod-gui.lua", "core/lualib/sound-util.lua",
        "core/lualib/util.lua", "core/lualib/silo-script.lua", "core/lualib/space-finish-script.lua",
        "core/lualib/prototype-info.lua", "core/lualib/circuit-connector-sprites.lua",
        "core/lualib/resource-autoplace.lua", "core/lualib/story.lua",
        "base/script/freeplay/", "base/script/pvp/", "base/script/sandbox/", "base/script/wave-defense/",
        "base/script/rocket-rush/", "base/script/supply/", "base/script/team-production/");
    public static final String IGNORE_GLOBS = String.join("\n", "*/migrations/**", "*/scenarios/**",
        "*/campaigns/**", "*/tutorials/**", "*/menu-simulations/**");
    /** Remove only the erroneous defaults saved by 0.2.2; retain other user entries. */
    public static String removeObsoletePaths(String text) {
        return text.lines().filter(line -> !line.trim().matches(
            "base/scripts/(freeplay|pvp|sandbox|wave-defense|rocket-rush|supply|team-production)/?"))
            .collect(java.util.stream.Collectors.joining("\n"));
    }
    public static void add(JsonObject config, Path data, String directories, String globs) {
        var library = new JsonObject();
        library.addProperty("path",data.toString());
        library.add("ignoreDir",lines(directories));
        library.add("ignoreGlobs",lines(globs));
        var workspace = config.getAsJsonObject("workspace");
        workspace.getAsJsonArray("library").add(library);
        var maps = workspace.getAsJsonArray("moduleMap");
        if (maps == null) { maps = new JsonArray(); workspace.add("moduleMap", maps); }
        // The data library indexes files as base.prototypes..., core.lualib..., etc.
        // Match Factorio's __mod-name__ imports without adding unfiltered library roots.
        for (String name : BUNDLED_MODS) {
            if (!Files.isRegularFile(data.resolve(name).resolve("info.json"))) continue;
            var map = new JsonObject();
            map.addProperty("pattern", "^" + name + "[.](.*)$");
            map.addProperty("replace", "__" + name + "__.$1");
            if (!maps.contains(map)) maps.add(map);
        }
    }
    private static JsonArray lines(String text) {
        var result = new JsonArray();
        text.lines().map(String::trim).filter(s -> !s.isEmpty()).distinct().forEach(result::add);
        return result;
    }
}
