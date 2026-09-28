package de.softwareforge.factorio;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Immutable source boundary used by configuration, editor routing and file watching. */
public record ModLanguageScope(Path root, String name, Path workspace, List<Path> libraries,
                               List<Path> dependencies, JsonObject config) {
    public ModLanguageScope {
        root = canonical(root); workspace = workspace.toAbsolutePath().normalize();
        libraries = libraries.stream().map(ModLanguageScope::canonical).distinct().toList();
        Path ownRoot = root;
        dependencies = dependencies.stream().map(ModLanguageScope::canonical).filter(p -> !p.equals(ownRoot)).distinct().toList();
        config = config.deepCopy();
    }
    public static Path canonical(Path path) {
        try { return path.toRealPath(); } catch (Exception ignored) { return path.toAbsolutePath().normalize(); }
    }
    public static String identity(Path project, Path root) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((canonical(project)+"\n"+canonical(root)).getBytes(StandardCharsets.UTF_8))).substring(0,24);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public List<Path> roots(boolean lua) {
        var roots = new ArrayList<Path>(); roots.add(root); roots.addAll(dependencies);
        if (lua) { roots.addAll(libraries); roots.add(workspace); }
        return roots.stream().distinct().toList();
    }
    public boolean includes(Path file, boolean lua) {
        Path path = file.toAbsolutePath().normalize();
        return roots(lua).stream().anyMatch(path::startsWith);
    }
    public static ModLanguageScope owner(List<ModLanguageScope> scopes, Path file, boolean lua) {
        Path path = file.toAbsolutePath().normalize();
        // An attached module always owns its files, even when another module depends on it.
        var own = scopes.stream().filter(s -> path.startsWith(s.root))
            .max(Comparator.comparingInt(s -> s.root.getNameCount()));
        return own.orElseGet(() -> scopes.stream().filter(s -> s.includes(path,lua))
            .min(Comparator.comparing(s -> s.root.toString())).orElse(null));
    }
    public static JsonObject configuration(JsonObject user, Path root, List<Path> libraries, List<Path> dependencies) throws Exception {
        var result = user.deepCopy();
        var workspace = result.has("workspace") ? result.getAsJsonObject("workspace").deepCopy() : new JsonObject();
        workspace.add("workspaceRoots", strings(List.of(root)));
        workspace.add("library", strings(libraries));
        var packages = new ArrayList<Path>(); packages.add(root); packages.addAll(dependencies);
        workspace.add("packages", strings(packages));
        var maps = workspace.has("moduleMap") ? workspace.getAsJsonArray("moduleMap").deepCopy() : new JsonArray();
        for (Path mod : packages) {
            var map = new JsonObject();
            String directory = mod.getFileName().toString().replaceAll("([\\\\.^$|?*+()\\[\\]{}])", "\\\\$1");
            map.addProperty("pattern", "^"+directory+"[.](.*)$");
            map.addProperty("replace", "__"+PathsAndMods.read(mod.resolve("info.json")).get("name").getAsString()+"__.$1");
            maps.add(map);
        }
        workspace.add("moduleMap", maps);
        result.add("workspace",workspace);
        var runtime = result.has("runtime") ? result.getAsJsonObject("runtime") : new JsonObject();
        if (!runtime.has("version")) runtime.addProperty("version","Lua 5.2");
        if (!runtime.has("requirePattern")) { var patterns=new JsonArray(); patterns.add("?.lua"); runtime.add("requirePattern",patterns); }
        result.add("runtime",runtime);
        return result;
    }
    private static JsonArray strings(List<Path> paths) { var a=new JsonArray(); paths.stream().distinct().forEach(p -> a.add(p.toString())); return a; }
    /** Replace server-wide globs with bounded watches, including external module directories. */
    public JsonObject watchers(boolean lua) {
        var watchers = new JsonArray();
        for (Path path : roots(lua)) for (String pattern : lua ? List.of("**/*.lua", ".emmyrc.json") : List.of("**/locale/*/*.cfg", "**/changelog.txt")) {
            var glob = new JsonObject(); glob.addProperty("baseUri",path.toUri().toString()); glob.addProperty("pattern",pattern);
            var watcher = new JsonObject(); watcher.add("globPattern",glob); watcher.addProperty("kind",7); watchers.add(watcher);
        }
        var options = new JsonObject(); options.add("watchers",watchers); return options;
    }
}
