package de.softwareforge.factorio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

class FactorioProfileParserTest {
    @TempDir Path dir;
    private FactorioProfile parse(String text) throws Exception {
        var path = dir.resolve("test.cpuprofile");
        Files.writeString(path, text);
        return FactorioProfile.read(path, () -> {});
    }
    private static final String TREE = """
        [{"id":0,"callFrame":{"functionName":"root"},"children":[1]},
         {"id":1,"callFrame":{"functionName":"recursive","sourceId":"@__mod__/control.lua","lineNumber":5},"children":[2]},
         {"id":2,"callFrame":{"functionName":"recursive","sourceId":"@__mod__/control.lua","lineNumber":5}}]
        """;
    @Test void aggregatesFractionalMicrosecondsAndRecursionWithoutUsingHitCount() throws Exception {
        var p = parse("{\"timeDeltas\":[0.125,2.5,7],\"samples\":[0,1,2],\"nodes\":" + TREE + "}");
        assertEquals(9625, p.durationNanos());
        assertEquals(9625, p.nodes().get(0).totalNanos());
        assertEquals(2625, p.nodes().get(0).selfNanos());
        assertEquals(7000, p.nodes().get(1).totalNanos());
        assertEquals(7000, p.nodes().get(1).selfNanos());
        assertEquals(p.nodes().get(1).frame(), p.nodes().get(2).frame());
    }
    @Test void readsNativeRuntimeRecording() throws Exception {
        var p = FactorioProfile.read(Path.of("src/test/resources/profiling/factorio-2.1.23.cpuprofile"), () -> {});
        assertEquals(16, p.nodes().size());
        assertEquals(198132049L, p.durationNanos());
        assertEquals(p.durationNanos(), p.nodes().getFirst().totalNanos());
        assertTrue(p.nodes().stream().anyMatch(n -> n.frame().nativeCall()));
        assertTrue(p.nodes().stream().anyMatch(n -> n.frame().source().endsWith("/script/metrics.lua")));
        // This recording waits for several ticks: waiting must belong to the root,
        // not the on_tick event that follows each wait.
        assertTrue(p.nodes().getFirst().selfNanos() > 190_000_000);
        assertTrue(p.nodes().stream().filter(n -> n.frame().name().startsWith("on_tick")).allMatch(n -> n.totalNanos() < 1_000_000));
    }
    @Test void assignsTheFinalIntervalToTheLastStack() throws Exception {
        var p = parse("{\"startTime\":100,\"endTime\":120,\"timeDeltas\":[1,2,3],\"samples\":[0,1,2],\"nodes\":" + TREE + "}");
        assertEquals(20_000, p.durationNanos());
        assertEquals(14_000, p.nodes().get(2).selfNanos());
        assertEquals(17_000, p.nodes().get(1).totalNanos());
    }
    @Test void acceptsEmptyRecording() throws Exception {
        assertEquals(0, parse("{\"nodes\":" + TREE + ",\"samples\":[],\"timeDeltas\":[]}").durationNanos());
    }
    @Test void rejectsMissingAndInvalidTimingData() {
        for (String fields : new String[]{"\"samples\":[0]", "\"samples\":[0],\"timeDeltas\":[]",
                "\"samples\":[42],\"timeDeltas\":[1]", "\"samples\":[0],\"timeDeltas\":[-1]"})
            assertThrows(IOException.class, () -> parse("{\"nodes\":" + TREE + "," + fields + "}"));
    }
    @Test void rejectsCyclesAndMissingFrames() {
        for (String tree : new String[]{TREE.replace("\"children\":[1]", "\"children\":[42]"),
                TREE.replace("\"children\":[2]", "\"children\":[0]")})
            assertThrows(IOException.class, () -> parse("{\"nodes\":" + tree + ",\"samples\":[],\"timeDeltas\":[]}"));
    }
    @Test void cancellationIsNotReportedAsMalformedData() throws Exception {
        var path = dir.resolve("test.cpuprofile");
        Files.writeString(path, "{\"nodes\":" + TREE + "}");
        assertThrows(CancellationException.class, () -> FactorioProfile.read(path, () -> { throw new CancellationException(); }));
    }
    @Test void acceptsMoreThanTwentyMillionEntriesWithoutKeepingSampleArrays() throws Exception {
        var path = dir.resolve("long.cpuprofile");
        int entries = 20_000_001;
        try (var writer = Files.newBufferedWriter(path)) {
            writer.write("{\"nodes\":" + TREE);
            for (String name : new String[]{"samples", "timeDeltas"}) {
                writer.write(",\"" + name + "\":[");
                for (int left = entries - 1; left > 0; left -= Math.min(left, 4096))
                    writer.write("1,".repeat(Math.min(left, 4096)));
                writer.write("1]");
            }
            writer.write("}");
        }
        var profile = FactorioProfile.read(path, () -> {});
        assertEquals(entries * 1000L, profile.durationNanos());
        assertEquals(1000L, profile.nodes().getFirst().selfNanos());
        assertEquals((entries - 1) * 1000L, profile.nodes().get(1).selfNanos());
    }
    @Test void rejectsDuplicateArraysAndBothKindsOfCountMismatch() {
        for (String fields : new String[]{"\"samples\":[],\"samples\":[],\"timeDeltas\":[]",
                "\"samples\":[],\"timeDeltas\":[],\"timeDeltas\":[]",
                "\"samples\":[],\"timeDeltas\":[1]", "\"samples\":[1,2],\"timeDeltas\":[1]"})
            assertThrows(IOException.class, () -> parse("{\"nodes\":" + TREE + "," + fields + "}"));
    }
}
