package de.softwareforge.factorio;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import java.nio.file.*;
import java.util.*;

public final class Toolkit {
    private Toolkit() {}
    public static Path root(Project p) { return Path.of(Objects.requireNonNull(p.getBasePath(), "Open a project directory first")); }
    public static Path cli(Project p) {
        String override = FactorioSettings.get(p).cli;
        return override.isBlank() ? PluginManagerCore.getPlugin(PluginId.getId("de.softwareforge.factorio")).getPluginPath().resolve("fmtk/fmtk-cli.js") : Path.of(override);
    }
    public static List<String> command(Project p, String... args) {
        var result = new ArrayList<String>(); result.add(FactorioSettings.get(p).node); result.add(cli(p).toString()); result.addAll(List.of(args)); return result;
    }
    public static Path activeMod(Project p) {
        String mod = FactorioSettings.get(p).activeMod;
        if (mod.isBlank()) throw new IllegalStateException("Select an active mod in Settings → Factorio Modding Tool Kit");
        Path result = Path.of(mod);
        if (!Files.isRegularFile(result.resolve("info.json"))) throw new IllegalStateException("The active mod has no info.json: " + result);
        return result;
    }
}
