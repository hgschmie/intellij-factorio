package de.softwareforge.factorio.profiler;

import com.google.gson.JsonParser;
import de.softwareforge.factorio.FactorioProfile;
import de.softwareforge.factorio.ModDiscovery;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Module identities and filtering captured once per import, with no UI-time filesystem lookup. */
final class FactorioProfileModules {
    static final String UNIDENTIFIED = "<unidentified>";
    record Source(String mod, String file, boolean project) {}
    record Choice(String id, String label) {}
    private record Root(String path, String mod) {}
    private static final Pattern MOD = Pattern.compile("^__([A-Za-z0-9_-]+)__/(.+)$");
    private final Map<FactorioProfile.Frame, Source> sources;
    private final List<Choice> choices;

    FactorioProfileModules(FactorioProfile profile, List<ModDiscovery.Mod> mods, Runnable checkCancelled) {
        var roots = mods.stream().map(m -> new Root(normalize(m.root().toString()) + "/", m.name()))
                .sorted(Comparator.comparingInt((Root r) -> r.path().length()).reversed()).toList();
        var labels = new HashMap<String, String>();
        for (var mod : mods) labels.put(mod.name(), mod.module().equals(mod.name()) ? mod.name()
                : mod.name() + " — " + mod.module());
        var directories = new HashMap<Path, Optional<Root>>();
        var locations = new HashMap<String, Source>();
        var resolved = new HashMap<FactorioProfile.Frame, Source>();
        var ids = new TreeSet<String>();
        for (var node : profile.nodes()) {
            checkCancelled.run();
            var frame = node.frame();
            if (frame.source().isBlank() || frame.nativeCall() || resolved.containsKey(frame)) continue;
            var source = locations.computeIfAbsent(frame.source(), path -> resolve(path, roots, labels.keySet(), directories, checkCancelled));
            resolved.put(frame, source);
            ids.add(source.mod().isEmpty() ? UNIDENTIFIED : source.mod());
        }
        sources = Map.copyOf(resolved);
        choices = ids.stream().map(id -> new Choice(id, id.equals(UNIDENTIFIED)
                ? "Unidentified Lua sources" : labels.getOrDefault(id, id))).toList();
    }

    Source source(FactorioProfile.Frame frame) { return sources.get(frame); }
    List<Choice> choices() { return choices; }

    /** Null selection means all. Include a selected frame's whole subtree once,
     * retaining ancestors for context with only their selected descendants' time. */
    FactorioProfile select(FactorioProfile profile, Set<String> selected, Runnable checkCancelled) {
        if (selected == null || profile.nodes().isEmpty()) return profile;
        var active = new HashSet<Integer>();
        var self = new HashMap<Integer, Long>();
        var totals = new HashMap<Integer, Long>();
        var parents = new HashMap<Integer, Integer>();
        for (var node : profile.nodes()) {
            checkCancelled.run();
            var source = sources.get(node.frame());
            boolean keep = active.contains(node.id()) || source != null
                    && selected.contains(source.mod().isEmpty() ? UNIDENTIFIED : source.mod());
            long time = keep ? node.selfNanos() : 0;
            self.put(node.id(), time);
            totals.put(node.id(), time);
            for (int child : node.children()) {
                parents.put(child, node.id());
                if (keep) active.add(child);
            }
        }
        // FactorioProfile.read returns parent-before-child order; reverse traversal
        // recomputes inclusive values without recursion or overlapping selections.
        for (int i = profile.nodes().size() - 1; i > 0; i--) {
            checkCancelled.run();
            var node = profile.nodes().get(i);
            totals.merge(parents.get(node.id()), totals.get(node.id()), Math::addExact);
        }
        int root = profile.nodes().getFirst().id();
        var nodes = new ArrayList<FactorioProfile.Node>();
        for (var node : profile.nodes()) {
            checkCancelled.run();
            if (node.id() == root || totals.get(node.id()) > 0)
                nodes.add(new FactorioProfile.Node(node.id(), node.frame(),
                        node.children().stream().filter(id -> totals.get(id) > 0).toList(),
                        self.get(node.id()), totals.get(node.id())));
        }
        return new FactorioProfile(List.copyOf(nodes), totals.get(root));
    }

    private static Source resolve(String location, List<Root> roots, Set<String> projectMods,
                                  Map<Path, Optional<Root>> directories, Runnable checkCancelled) {
        String path = normalize(location);
        var match = MOD.matcher(path);
        if (match.matches()) return new Source(match.group(1), match.group(2), projectMods.contains(match.group(1)));
        Source known = fromRoots(path, roots);
        if (known != null) return known;
        try {
            Path file = Path.of(path);
            if (file.isAbsolute()) {
                // Existing symlinks can point from Factorio's mods folder to an IDE module.
                try { file = file.toRealPath(); } catch (IOException ignored) { /* Retain the recorded path. */ }
                path = normalize(file.toString());
                known = fromRoots(path, roots);
                if (known != null) return known;
                var visited = new ArrayList<Path>();
                Optional<Root> found = Optional.empty();
                for (Path dir = file.getParent(); dir != null; dir = dir.getParent()) {
                    checkCancelled.run();
                    if (directories.containsKey(dir)) { found = directories.get(dir); break; }
                    visited.add(dir);
                    Path info = dir.resolve("info.json");
                    if (!Files.isRegularFile(info)) continue;
                    try {
                        if (Files.size(info) > 1_048_576) break;
                        try (var reader = Files.newBufferedReader(info)) {
                            var name = JsonParser.parseReader(reader).getAsJsonObject().get("name");
                            if (name != null && name.isJsonPrimitive() && name.getAsJsonPrimitive().isString()
                                    && name.getAsString().matches("[A-Za-z0-9_-]+"))
                                found = Optional.of(new Root(normalize(dir.toString()) + "/", name.getAsString()));
                        }
                    } catch (IOException | IllegalStateException | com.google.gson.JsonParseException ignored) { /* Unidentified source. */ }
                    break; // An invalid nearest manifest is not an enclosing module's source.
                }
                for (var dir : visited) directories.put(dir, found);
                if (found.isPresent()) {
                    var root = found.get();
                    return new Source(root.mod(), path.substring(root.path().length()), projectMods.contains(root.mod()));
                }
            }
        } catch (InvalidPathException | SecurityException ignored) { /* Old or inaccessible source. */ }
        return new Source("", path, false);
    }
    private static Source fromRoots(String path, List<Root> roots) {
        for (var root : roots) if (path.startsWith(root.path()))
            return new Source(root.mod(), path.substring(root.path().length()), true);
        return null;
    }
    private static String normalize(String source) {
        String path = source.startsWith("@") ? source.substring(1) : source;
        if (path.startsWith("file:")) {
            try { path = Path.of(URI.create(path)).normalize().toString(); }
            catch (IllegalArgumentException ignored) { /* Keep unidentified source text. */ }
        }
        path = path.replace('\\', '/');
        try { if (Path.of(path).isAbsolute()) path = Path.of(path).normalize().toString().replace('\\', '/'); }
        catch (InvalidPathException ignored) { /* Keep unidentified source text. */ }
        return path;
    }
}
