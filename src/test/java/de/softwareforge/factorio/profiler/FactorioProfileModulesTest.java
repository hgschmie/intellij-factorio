package de.softwareforge.factorio.profiler;

import de.softwareforge.factorio.FactorioProfile;
import de.softwareforge.factorio.ModDiscovery;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FactorioProfileModulesTest {
    @TempDir Path dir;
    @Test void resolvesProjectAliasesNestedRootsAndExternalManifestsWithoutGuessing() throws Exception {
        Path a = Files.createDirectories(dir.resolve("a"));
        Path nested = Files.createDirectories(a.resolve("nested"));
        Path external = Files.createDirectories(dir.resolve("external"));
        Files.writeString(external.resolve("info.json"), "{\"name\":\"external-mod\"}");
        Path file = Files.writeString(a.resolve("control.lua"), "return true");
        Path alias = dir.resolve("alias");
        Files.createSymbolicLink(alias, a);
        var frames = List.of(frame("@__A__/control.lua"), frame(file.toUri().toString()),
                frame(alias.resolve("control.lua").toString()), frame(nested.resolve("missing.lua").toString()),
                frame(external.resolve("scripts/missing.lua").toString()), frame(dir.resolve("a-lookalike/control.lua").toString()),
                frame(a.resolve("../external/control.lua").toString()));
        var profile = flat(frames);
        var modules = new FactorioProfileModules(profile, List.of(
                new ModDiscovery.Mod("Module A", a.toRealPath(), "A", "1.0.0", "2.1"),
                new ModDiscovery.Mod("Nested", nested.toRealPath(), "nested", "1.0.0", "2.1")), () -> {});
        assertEquals(List.of("A", "A", "A", "nested", "external-mod", "", "external-mod"), frames.stream().map(f -> modules.source(f).mod()).toList());
        assertEquals(modules.source(frames.get(0)), modules.source(frames.get(1)));
        assertTrue(modules.source(frames.get(2)).project());
        assertFalse(modules.source(frames.get(4)).project());
        assertEquals("scripts/missing.lua", modules.source(frames.get(4)).file());
        assertTrue(modules.choices().stream().anyMatch(c -> c.label().equals("A — Module A")));
        assertTrue(modules.choices().stream().anyMatch(c -> c.id().equals(FactorioProfileModules.UNIDENTIFIED)));
    }

    @Test void selectedModulesIncludeCalleesButExcludeUnrelatedSiblingWorkAndAncestorSelfTime() {
        var profile = calls();
        var modules = new FactorioProfileModules(profile, List.of(), () -> {});
        assertSame(profile, modules.select(profile, null, () -> {}));
        var a = modules.select(profile, Set.of("A"), () -> {});
        assertEquals(195, a.durationNanos());
        assertEquals(0, node(a, 0).selfNanos());
        assertEquals(0, node(a, 1).selfNanos());
        assertEquals(100, node(a, 2).selfNanos());
        assertEquals(60, node(a, 4).totalNanos()); // Shared library + native call.
        assertEquals(20, node(a, 5).selfNanos()); // A called B: retain this work.
        assertEquals(10, node(a, 6).selfNanos()); // Pause/hook policy stays unchanged.
        assertEquals(5, node(a, 7).selfNanos());
        assertFalse(a.nodes().stream().anyMatch(n -> n.id() == 3)); // Unrelated B invocation.
        var b = modules.select(profile, Set.of("B"), () -> {});
        assertEquals(270, b.durationNanos());
        assertEquals(0, node(b, 2).selfNanos()); // A is context only for its B call.
        assertEquals(20, node(b, 2).totalNanos());
        var both = modules.select(profile, Set.of("A", "B"), () -> {});
        assertEquals(445, both.durationNanos()); // Nested B work is not counted twice.
        assertEquals(35, modules.select(profile, Set.of(FactorioProfileModules.UNIDENTIFIED), () -> {}).durationNanos());
        var none = modules.select(profile, Set.of(), () -> {});
        assertEquals(0, none.durationNanos());
        assertEquals(1, none.nodes().size());
        assertTrue(none.nodes().getFirst().children().isEmpty());
        assertEquals(1_500, profile.durationNanos());
        assertEquals(1_000, profile.nodes().getFirst().selfNanos());
        for (var filtered : List.of(a, b, both, none)) for (var n : filtered.nodes())
            assertEquals(n.totalNanos(), n.selfNanos() + n.children().stream().mapToLong(id -> node(filtered, id).totalNanos()).sum());
    }

    @Test void deepStacksAndCancellationDoNotDependOnJavaRecursion() {
        var nodes = new ArrayList<FactorioProfile.Node>();
        int length = 20_000;
        nodes.add(n(0, "", 1, length + 1, 1));
        for (int i = 1; i <= length; i++) nodes.add(n(i, "@__A__/control.lua", 1, length - i + 1,
                i == length ? new Integer[0] : new Integer[]{i + 1}));
        var profile = new FactorioProfile(List.copyOf(nodes), length + 1);
        var modules = new FactorioProfileModules(profile, List.of(), () -> {});
        assertEquals(length, modules.select(profile, Set.of("A"), () -> {}).durationNanos());
        assertThrows(java.util.concurrent.CancellationException.class, () -> modules.select(profile, Set.of("A"),
                () -> { throw new java.util.concurrent.CancellationException(); }));
    }

    private static FactorioProfile calls() {
        return new FactorioProfile(List.of(n(0, "", 1_000, 1_500, 1, 10), n(1, "", 20, 465, 2, 3),
                n(10, "unavailable/control.lua", 35, 35), n(2, "@__A__/control.lua", 100, 195, 4, 5, 6, 7),
                n(3, "@__B__/control.lua", 200, 250, 9), n(4, "@__core__/lib.lua", 30, 60, 8),
                n(5, "@__B__/remote.lua", 20, 20), n(6, "", 10, 10), n(7, "", 5, 5),
                n(9, "=[C]", 50, 50), n(8, "=[C]", 30, 30)), 1_500);
    }
    private static FactorioProfile.Node n(int id, String source, long self, long total, Integer... children) {
        return new FactorioProfile.Node(id, new FactorioProfile.Frame(id == 0 ? "(root)" : "call" + id, source, 0), List.of(children), self, total);
    }
    private static FactorioProfile.Frame frame(String source) { return new FactorioProfile.Frame("call", source, 0); }
    private static FactorioProfile flat(List<FactorioProfile.Frame> frames) {
        var nodes = new ArrayList<FactorioProfile.Node>();
        nodes.add(n(0, "", 0, frames.size(), java.util.stream.IntStream.rangeClosed(1, frames.size()).boxed().toArray(Integer[]::new)));
        for (int i = 0; i < frames.size(); i++) nodes.add(new FactorioProfile.Node(i + 1, frames.get(i), List.of(), 1, 1));
        return new FactorioProfile(List.copyOf(nodes), frames.size());
    }
    private static FactorioProfile.Node node(FactorioProfile p, int id) { return p.nodes().stream().filter(n -> n.id() == id).findFirst().orElseThrow(); }
}
