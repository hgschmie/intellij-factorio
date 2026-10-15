package de.softwareforge.factorio;

import java.nio.file.Path;
import java.util.*;

/** Immutable presentation snapshots; no IDE objects, analysis, or filesystem reads. */
final class FactorioProblemsModel {
    record Module(Path root, String name) {
        @Override public String toString() { return name; }
    }
    record Problem(Module module, Path file, int line, int column, int severity,
                   String message, String source, String code) {}
    record Snapshot(List<Module> modules, List<Problem> problems) {}
    private record Owner(Module module, Map<Path,List<Problem>> files) {}
    private List<Module> modules = List.of();
    private final Map<Object,Owner> owners = new IdentityHashMap<>();

    synchronized boolean configure(List<Module> next) {
        var sorted=next.stream().sorted(Comparator.comparing(Module::name).thenComparing(m->m.root().toString())).toList();
        if (modules.equals(sorted)) return false;
        modules=sorted;
        owners.values().removeIf(owner->!modules.contains(owner.module()));
        for(var owner:owners.values()) owner.files().keySet().removeIf(file->!owns(owner.module(),file));
        return true;
    }
    synchronized void register(Object key, Module module) {
        if (modules.contains(module)) owners.put(key,new Owner(module,new HashMap<>()));
    }
    synchronized boolean remove(Object key) { return owners.remove(key)!=null; }
    synchronized boolean update(Object key, Path file, List<Problem> problems) {
        var owner=owners.get(key);
        if (owner==null || !owns(owner.module(),file)) return false;
        var accepted=problems.stream().filter(p->p.module().equals(owner.module()) && p.file().equals(file)).distinct().toList();
        var previous=owner.files().getOrDefault(file,List.of());
        if (previous.equals(accepted)) return false;
        if (accepted.isEmpty()) owner.files().remove(file); else owner.files().put(file,accepted);
        return true;
    }
    private boolean owns(Module module,Path file) {
        // A newly added nested module also takes ownership of previously reported files.
        return file.startsWith(module.root()) && modules.stream().noneMatch(m->!m.equals(module)
            && file.startsWith(m.root()) && m.root().startsWith(module.root()));
    }
    synchronized boolean removeUnder(Path path) {
        boolean changed=false;
        for (var owner:owners.values()) changed |= owner.files().keySet().removeIf(f->f.startsWith(path));
        return changed;
    }
    synchronized Snapshot snapshot() {
        var problems=owners.values().stream().flatMap(o->o.files().values().stream()).flatMap(List::stream)
            .distinct().sorted(Comparator.comparing((Problem p)->p.module().name())
                .thenComparing(p->p.file().toString()).thenComparingInt(Problem::line)
                .thenComparingInt(Problem::column).thenComparingInt(Problem::severity)
                .thenComparing(Problem::message).thenComparing(Problem::source).thenComparing(Problem::code)).toList();
        return new Snapshot(modules,problems);
    }
}
