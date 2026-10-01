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
    static final String API_TARGET = "emmylua";
    // Include the annotation contract in the cache even with a user-supplied CLI.
    static final String API_FORMAT = "asymmetric-fields-v1";
    public static Path cache(Project project) {
        return PathManager.getSystemDir().resolve("softwareforge-factorio").resolve(Integer.toHexString(Toolkit.root(project).toString().hashCode()));
    }
    public static synchronized void generate(Project project, ProgressIndicator indicator, Consumer<String> log) throws Exception {
        var settings = FactorioSettings.get(project);
        Path root = Toolkit.root(project), cache = cache(project);
        var registry=FactorioModules.get(project);
        var mods=registry.mods();
        Files.createDirectories(cache);
        Path executable = Path.of(settings.factorio).toAbsolutePath();
        Path docs = settings.apiDocs.isBlank() ? executable.getParent().getParent().resolve("doc-html") : Path.of(settings.apiDocs);
        Path runtime = docs.resolve("runtime-api.json"), prototypes = docs.resolve("prototype-api.json");
        if (!Files.isRegularFile(runtime) || !Files.isRegularFile(prototypes)) throw new IllegalArgumentException("Select a directory containing runtime-api.json and prototype-api.json in Factorio settings");
        var digest = MessageDigest.getInstance("SHA-256");
        digest.update((API_TARGET+":"+API_FORMAT).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update(Files.readAllBytes(runtime)); digest.update(Files.readAllBytes(prototypes)); digest.update(Files.readAllBytes(Toolkit.cli(project)));
        Path generated = cache.resolve("api-" + HexFormat.of().formatHex(digest.digest()).substring(0,24));
        String version = PathsAndMods.read(runtime).get("application_version").getAsString();
        if (!Files.isRegularFile(generated.resolve("complete"))) {
            Path staging = Files.createTempDirectory(cache,"api-staging-");
            Processes.get(project).run(Toolkit.command(project, "docs", staging.toString(), "--target", API_TARGET, "--docs", runtime.toString(), "--protos", prototypes.toString(), "--docbase", "https://lua-api.factorio.com/" + version), root, Map.of(), indicator, log);
            Files.writeString(staging.resolve("complete"), version);
            Files.move(staging, generated, StandardCopyOption.ATOMIC_MOVE);
        }
        List<Path> shared = new ArrayList<>(); shared.add(generated.resolve("factorio/library"));
        Path data = docs.getParent().resolve("data");
        var scopes = new ArrayList<ModLanguageScope>();
        for (var mod : mods) {
            var dependencies = new ArrayList<Path>();
            for (Path dependency : PathsAndMods.lines(registry.dependencies(mod))) {
                indicator.checkCanceled();
                dependency = root.resolve(dependency).normalize();
                if (Files.isRegularFile(dependency) && dependency.toString().endsWith(".zip")) {
                    var hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(dependency));
                    Path target = cache.resolve("dependencies/" + HexFormat.of().formatHex(hash).substring(0,24));
                    if (!Files.exists(target.resolve("complete"))) { PathsAndMods.extract(dependency,target); Files.writeString(target.resolve("complete"), "ok"); }
                    dependencies.addAll(PathsAndMods.mods(target));
                } else if (Files.isRegularFile(dependency.resolve("info.json"))) dependencies.add(dependency);
                else throw new IllegalArgumentException("Select individual dependency mod folders or ZIPs, not a parent directory: " + dependency);
            }
            dependencies = new ArrayList<>(dependencies.stream().map(ModLanguageScope::canonical).filter(p -> !p.equals(mod.root())).distinct().toList());
            Path workspace = cache.resolve("language-servers/" + ModLanguageScope.identity(root,mod.root())).resolve("workspace");
            Files.createDirectories(workspace);
            var perMod = ModLanguageScope.configuration(mod.root(),shared,dependencies);
            var libraries = new ArrayList<>(shared);
            if (Files.isDirectory(data)) {
                FactorioLibrary.add(perMod,data,settings.libraryIgnoreDir,settings.libraryIgnoreGlobs);
                libraries.add(data);
            }
            PathsAndMods.write(workspace.resolve("managed-emmy-config.json"),perMod);
            scopes.add(new ModLanguageScope(mod.root(),mod.name(),workspace,libraries,dependencies,perMod));
        }
        FactorioServerManager.get(project).configure(scopes);
        log.accept("Generated Factorio " + version + " API and configured separate language servers for " + scopes.size() + " mods.\n");
    }
}
