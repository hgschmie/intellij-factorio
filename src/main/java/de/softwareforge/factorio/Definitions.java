package de.softwareforge.factorio;

import com.google.gson.*;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;

public final class Definitions {
    private Definitions() {}
    public static Path cache(Project project) {
        return PathManager.getSystemDir().resolve("softwareforge-factorio").resolve(Integer.toHexString(Toolkit.root(project).toString().hashCode()));
    }
    public static void generate(Project project, ProgressIndicator indicator, Consumer<String> log) throws Exception {
        var settings = FactorioSettings.get(project);
        Path root = Toolkit.root(project), active = Toolkit.activeMod(project), cache = cache(project);
        Files.createDirectories(cache);
        Path executable = Path.of(settings.factorio).toAbsolutePath();
        Path docs = settings.apiDocs.isBlank() ? executable.getParent().getParent().resolve("doc-html") : Path.of(settings.apiDocs);
        Path runtime = docs.resolve("runtime-api.json"), prototypes = docs.resolve("prototype-api.json");
        if (!Files.isRegularFile(runtime) || !Files.isRegularFile(prototypes)) throw new IllegalArgumentException("Select a directory containing runtime-api.json and prototype-api.json in Factorio settings");
        var digest = MessageDigest.getInstance("SHA-256");
        digest.update(Files.readAllBytes(runtime)); digest.update(Files.readAllBytes(prototypes)); digest.update(Files.readAllBytes(Toolkit.cli(project)));
        Path generated = cache.resolve("api-" + HexFormat.of().formatHex(digest.digest()).substring(0,24));
        String version = PathsAndMods.read(runtime).get("application_version").getAsString();
        if (!Files.isRegularFile(generated.resolve("complete"))) {
            Path staging = Files.createTempDirectory(cache,"api-staging-");
            Processes.get(project).run(Toolkit.command(project, "docs", staging.toString(), "--docs", runtime.toString(), "--protos", prototypes.toString(), "--docbase", "https://lua-api.factorio.com/" + version), root, Map.of(), indicator, log);
            Files.writeString(staging.resolve("complete"), version);
            Files.move(staging, generated, StandardCopyOption.ATOMIC_MOVE);
        }
        List<String> libraries = new ArrayList<>(); libraries.add(generated.resolve("factorio/library").toString());
        Path core = docs.getParent().resolve("data/core/lualib");
        if (Files.isDirectory(core)) libraries.add(core.toString());
        Map<String,String> modules = new LinkedHashMap<>();
        List<Path> dependencies = new ArrayList<>(PathsAndMods.lines(settings.dependencies));
        dependencies.addAll(PathsAndMods.mods(root));
        for (Path dependency : dependencies) {
            indicator.checkCanceled();
            if (dependency.equals(active)) continue;
            if (Files.isRegularFile(dependency) && dependency.toString().endsWith(".zip")) {
                var hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(dependency));
                Path target = cache.resolve("dependencies/" + HexFormat.of().formatHex(hash).substring(0,24));
                if (!Files.exists(target.resolve("complete"))) { PathsAndMods.extract(dependency,target); Files.writeString(target.resolve("complete"), "ok"); }
                for (Path dep : PathsAndMods.mods(target)) addMod(dep,libraries,modules);
            } else if (Files.isRegularFile(dependency.resolve("info.json"))) addMod(dependency,libraries,modules);
            else for (Path dep : PathsAndMods.mods(dependency)) addMod(dep,libraries,modules);
        }
        Path config = root.resolve(".emmyrc.json"), ownership = cache.resolve("emmy-owned.json");
        JsonObject before = Files.exists(config) ? PathsAndMods.read(config) : new JsonObject();
        JsonObject previous = Files.exists(ownership) ? PathsAndMods.read(ownership) : new JsonObject();
        JsonObject next = EmmyConfig.merge(before, previous, libraries, List.of(active.toString()), modules);
        // Carry forward ownership of retained managed entries as well as newly introduced ones.
        JsonObject owned = EmmyConfig.ownership(EmmyConfig.merge(before,previous,List.of(),List.of(),Map.of()), next);
        PathsAndMods.write(config, next); PathsAndMods.write(ownership, owned);
        com.redhat.devtools.lsp4ij.LanguageServerManager.getInstance(project).start("EmmyLua", new com.redhat.devtools.lsp4ij.LanguageServerManager.StartOptions().setForceRestart(true));
        log.accept("Generated Factorio " + version + " API and updated EmmyLua libraries.\n");
    }
    private static void addMod(Path mod, List<String> libraries, Map<String,String> modules) throws Exception {
        String name = PathsAndMods.read(mod.resolve("info.json")).get("name").getAsString();
        String parent = mod.getParent().toString(); if (!libraries.contains(parent)) libraries.add(parent);
        modules.put(mod.getFileName().toString(),name);
    }
}
