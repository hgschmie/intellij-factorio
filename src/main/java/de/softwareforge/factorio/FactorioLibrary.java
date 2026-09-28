package de.softwareforge.factorio;

import com.google.gson.*;
import java.nio.file.Path;

/** Data-library exclusions from FMTK's VersionSelector, plus the stateful story helper. */
public final class FactorioLibrary {
    private FactorioLibrary() {}
    public static final String IGNORE_DIR = String.join("\n",
        "core/lualib/event_handler.lua", "core/lualib/crash-site.lua", "core/lualib/math2d.lua",
        "core/lualib/meld.lua", "core/lualib/mod-gui.lua", "core/lualib/sound-util.lua",
        "core/lualib/util.lua", "core/lualib/silo-script.lua", "core/lualib/space-finish-script.lua",
        "core/lualib/prototype-info.lua", "core/lualib/circuit-connector-sprites.lua",
        "core/lualib/resource-autoplace.lua", "base/scripts/freeplay/", "base/scripts/pvp/",
        "base/scripts/sandbox/", "base/scripts/wave-defense/", "core/lualib/story.lua",
        "base/script/freeplay/", "base/script/pvp/", "base/script/sandbox/", "base/script/wave-defense/",
        "base/script/rocket-rush/", "base/script/supply/", "base/script/team-production/",
        "base/scripts/rocket-rush/", "base/scripts/supply/", "base/scripts/team-production/");
    public static final String IGNORE_GLOBS = String.join("\n", "*/migrations/**", "*/scenarios/**",
        "*/campaigns/**", "*/tutorials/**", "*/menu-simulations/**");
    public static void add(JsonObject config, Path data, String directories, String globs) {
        var library = new JsonObject();
        library.addProperty("path",data.toString());
        library.add("ignoreDir",lines(directories));
        library.add("ignoreGlobs",lines(globs));
        config.getAsJsonObject("workspace").getAsJsonArray("library").add(library);
    }
    private static JsonArray lines(String text) {
        var result = new JsonArray();
        text.lines().map(String::trim).filter(s -> !s.isEmpty()).distinct().forEach(result::add);
        return result;
    }
}
