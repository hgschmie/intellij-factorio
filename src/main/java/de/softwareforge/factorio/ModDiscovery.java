package de.softwareforge.factorio;

import java.nio.file.*;
import java.util.*;

/** Filesystem-only discovery; never recursively mistakes dependency folders for modules. */
public final class ModDiscovery {
    public record Candidate(String module, Path root) {}
    public record Mod(String module, Path root, String name, String version, String factorioVersion) {
        @Override public String toString() { return name + " — " + module + " (" + root + ")"; }
    }
    public record Result(List<Mod> mods, List<String> errors) {}
    public static Mod read(String module, Path root) throws Exception {
        root=root.toRealPath();
        var info=PathsAndMods.read(root.resolve("info.json"));
        String name=info.get("name").getAsString(), version=info.get("version").getAsString(), target=info.get("factorio_version").getAsString();
        if(!name.matches("[A-Za-z0-9_-]+") || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || !target.matches("[0-9]+\\.[0-9]+")) throw new IllegalArgumentException("Invalid mod name or version");
        return new Mod(module,root,name,version,target);
    }
    public static Result discover(List<Candidate> candidates) {
        var found=new ArrayList<Mod>(); var errors=new ArrayList<String>(); var seen=new HashSet<Path>();
        for(var c:candidates) {
            if(!Files.isRegularFile(c.root().resolve("info.json"))) continue;
            try { var m=read(c.module(),c.root()); if(seen.add(m.root()))found.add(m); }
            catch(Exception e) { errors.add(c.root()+": invalid Factorio info.json ("+e.getMessage()+")"); }
        }
        var ambiguous=new HashSet<Mod>();
        for(var a:found) for(var b:found) if(a!=b && (a.name().equals(b.name()) || a.module().equals(b.module()))) { ambiguous.add(a); ambiguous.add(b); }
        if(!ambiguous.isEmpty()) errors.add("Duplicate mod identities or multiple mod roots in one module: "+ambiguous);
        found.removeAll(ambiguous); found.sort(Comparator.comparing(Mod::name));
        return new Result(List.copyOf(found),List.copyOf(errors));
    }
    public static Mod containing(List<Mod> mods, Path file) {
        Path normalized=file.toAbsolutePath().normalize();
        try { normalized=file.toRealPath(); } catch(Exception ignored) {}
        final Path path=normalized;
        return mods.stream().filter(m->path.startsWith(m.root())).max(Comparator.comparingInt(m->m.root().getNameCount())).orElse(null);
    }
}
