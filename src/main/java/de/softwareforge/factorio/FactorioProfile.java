package de.softwareforge.factorio;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Native Factorio Chrome CPU profiles. Values are nanoseconds, never invocation counts. */
public record FactorioProfile(List<Node> nodes, long durationNanos) {
    public record Frame(String name, String source, int line) {
        public boolean nativeCall() { return source.equals("=[C]"); }
    }
    public record Node(int id, Frame frame, List<Integer> children, long selfNanos, long totalNanos) {}
    private record RawNode(int id, Frame frame, List<Integer> children) {}
    // A malformed or unexpectedly large snapshot must not exhaust the IDE heap.
    private static final int MAX_NODES = 500_000;

    public static FactorioProfile read(Path path, Runnable checkCancelled) throws IOException {
        var raw = new LinkedHashMap<Integer, RawNode>();
        boolean haveNodes = false, haveSamples = false, haveDeltas = false;
        Double startMicros = null, endMicros = null;
        try (var reader = new JsonReader(Files.newBufferedReader(path))) {
            reader.beginObject();
            while (reader.hasNext()) {
                checkCancelled.run();
                switch (reader.nextName()) {
                    case "startTime" -> startMicros = reader.nextDouble();
                    case "endTime" -> endMicros = reader.nextDouble();
                    case "nodes" -> {
                        if (haveNodes) throw invalid("Duplicate nodes");
                        haveNodes = true;
                        reader.beginArray();
                        while (reader.hasNext()) {
                            checkCancelled.run();
                            RawNode node = readNode(reader);
                            if (raw.size() >= MAX_NODES) throw invalid("Too many frames");
                            if (raw.putIfAbsent(node.id(), node) != null) throw invalid("Duplicate frame ID");
                        }
                        reader.endArray();
                    }
                    case "samples" -> {
                        if (haveSamples) throw invalid("Duplicate samples");
                        haveSamples = true;
                        skip(reader, checkCancelled);
                    }
                    case "timeDeltas" -> {
                        if (haveDeltas) throw invalid("Duplicate timeDeltas");
                        haveDeltas = true;
                        skip(reader, checkCancelled);
                    }
                    default -> skip(reader, checkCancelled);
                }
            }
            reader.endObject();
        } catch (java.util.concurrent.CancellationException e) { throw e; }
        catch (IllegalStateException | NumberFormatException e) { throw invalid("Malformed CPU profile", e); }
        if (!haveNodes || !haveSamples || raw.isEmpty()) throw invalid("Missing nodes or samples");
        if (!haveDeltas) throw invalid("Missing timeDeltas; counts cannot be interpreted as elapsed time");
        if ((startMicros == null) != (endMicros == null)) throw invalid("Incomplete recording time bounds");
        if (startMicros != null && (!Double.isFinite(startMicros) || !Double.isFinite(endMicros) || endMicros < startMicros
                || endMicros - startMicros > Long.MAX_VALUE / 1000.0)) throw invalid("Invalid recording time bounds");

        // Independent cursors pair the two arrays regardless of JSON property order.
        // Memory depends on the call tree, not the recording length (even billions of
        // timing entries). Mutable accumulators also avoid allocating a Long per sample.
        var timings = new HashMap<Integer, long[]>();
        for (int id : raw.keySet()) timings.put(id, new long[1]);
        long duration = 0;
        long initialNanos = 0;
        long[] previous = null;
        try (var samples = new JsonReader(Files.newBufferedReader(path));
             var deltas = new JsonReader(Files.newBufferedReader(path))) {
            seekArray(samples, "samples", checkCancelled);
            seekArray(deltas, "timeDeltas", checkCancelled);
            long index = 0;
            while (deltas.hasNext()) {
                if ((index++ & 4095) == 0) checkCancelled.run();
                if (!samples.hasNext()) throw invalid("Sample/time delta counts differ");
                double micros = deltas.nextDouble();
                if (!Double.isFinite(micros) || micros < 0 || micros > Long.MAX_VALUE / 1000.0)
                    throw invalid("Invalid time delta");
                long nanos = Math.round(micros * 1000);
                int current = samples.nextInt();
                var timing = timings.get(current);
                if (timing == null) throw invalid("Sample refers to missing frame " + current);
                // Deltas locate samples in time. The interval before sample i belongs
                // to sample i-1, not i (otherwise idle time becomes on_tick self time).
                if (previous == null) initialNanos = nanos;
                else previous[0] = Math.addExact(previous[0], nanos);
                previous = timing;
                duration = Math.addExact(duration, nanos);
            }
            deltas.endArray();
            if (samples.hasNext()) throw invalid("Sample/time delta counts differ");
            samples.endArray();
        } catch (java.util.concurrent.CancellationException e) { throw e; }
        catch (IllegalStateException | IllegalArgumentException | ArithmeticException e) {
            throw invalid("Invalid profile timings", e);
        }
        if (startMicros != null) {
            long tail = Math.round((endMicros - startMicros) * 1000) - duration;
            // Absolute floating point timestamps can differ by one nanosecond after rounding.
            if (tail < -1) throw invalid("Timing entries exceed the recording duration");
            if (tail > 0) {
                if (previous == null) initialNanos = Math.addExact(initialNanos, tail);
                else previous[0] = Math.addExact(previous[0], tail);
                duration = Math.addExact(duration, tail);
            }
        }

        var self = new HashMap<Integer, Long>();
        timings.forEach((id, time) -> self.put(id, time[0]));
        // Validate a tree, then calculate inclusive values iteratively (deep Lua recursion
        // must not overflow the Java stack). Keep duplicate function frames at distinct depths.
        var parents = new HashMap<Integer, Integer>();
        for (var node : raw.values()) for (int child : node.children()) {
            if (!raw.containsKey(child)) throw invalid("Missing child frame " + child);
            if (parents.putIfAbsent(child, node.id()) != null) throw invalid("Frame has multiple parents");
        }
        var roots = raw.keySet().stream().filter(id -> !parents.containsKey(id)).toList();
        if (roots.size() != 1) throw invalid("Expected one profile root");
        int root = roots.getFirst();
        self.put(root, Math.addExact(self.getOrDefault(root, 0L), initialNanos));
        var order = new ArrayList<Integer>();
        var queue = new ArrayDeque<Integer>();
        queue.add(roots.getFirst());
        while (!queue.isEmpty()) {
            checkCancelled.run();
            int id = queue.removeFirst();
            order.add(id);
            queue.addAll(raw.get(id).children());
        }
        if (order.size() != raw.size()) throw invalid("Cyclic or disconnected frames");
        var totals = new HashMap<>(self);
        for (int i = order.size() - 1; i > 0; i--) {
            int id = order.get(i), parent = parents.get(id);
            totals.put(parent, Math.addExact(totals.getOrDefault(parent, 0L), totals.getOrDefault(id, 0L)));
        }
        var nodes = new ArrayList<Node>();
        for (int id : order) {
            var n = raw.get(id);
            nodes.add(new Node(id, n.frame(), n.children(), self.getOrDefault(id, 0L), totals.getOrDefault(id, 0L)));
        }
        return new FactorioProfile(List.copyOf(nodes), duration);
    }

    private static RawNode readNode(JsonReader reader) throws IOException {
        Integer id = null;
        Frame frame = null;
        var children = new ArrayList<Integer>();
        reader.beginObject();
        while (reader.hasNext()) switch (reader.nextName()) {
            case "id" -> id = reader.nextInt();
            case "callFrame" -> {
                String name = "(anonymous)", source = "", url = "";
                int line = -1;
                reader.beginObject();
                while (reader.hasNext()) switch (reader.nextName()) {
                    case "functionName" -> name = reader.nextString();
                    case "sourceId" -> source = reader.nextString();
                    case "url" -> url = reader.nextString();
                    case "lineNumber" -> line = reader.nextInt();
                    default -> reader.skipValue();
                }
                reader.endObject();
                // Factorio supplies @__mod__/path in sourceId, but loaded chunks may
                // have a source hash and their useful location in url instead.
                frame = new Frame(name, source.startsWith("@") || source.equals("=[C]") ? source : url, line);
            }
            case "children" -> {
                reader.beginArray();
                while (reader.hasNext()) {
                    if (children.size() >= MAX_NODES) throw invalid("Too many children");
                    children.add(reader.nextInt());
                }
                reader.endArray();
            }
            default -> reader.skipValue();
        }
        reader.endObject();
        if (id == null || frame == null) throw invalid("Missing frame ID or callFrame");
        return new RawNode(id, frame, List.copyOf(children));
    }
    private static IOException invalid(String reason) { return new IOException(reason); }
    private static void skip(JsonReader reader, Runnable checkCancelled) throws IOException {
        if (reader.peek() != com.google.gson.stream.JsonToken.BEGIN_ARRAY) { reader.skipValue(); return; }
        reader.beginArray();
        int count = 0;
        while (reader.hasNext()) {
            if ((count++ & 4095) == 0) checkCancelled.run();
            reader.skipValue();
        }
        reader.endArray();
    }
    private static IOException invalid(String reason, Exception cause) { return new IOException(reason, cause); }
    private static void seekArray(JsonReader reader, String name, Runnable checkCancelled) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            checkCancelled.run();
            if (reader.nextName().equals(name)) {
                reader.beginArray();
                return;
            }
            skip(reader, checkCancelled);
        }
        throw invalid("Missing " + name);
    }
}
