package de.softwareforge.factorio;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;

public final class PathsAndMods {
    private PathsAndMods() {}
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
    public static void write(Path path, JsonObject object) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), ".fmtk-", ".tmp");
        try { Files.writeString(temp, JSON.toJson(object) + "\n"); Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        finally { Files.deleteIfExists(temp); }
    }
    public static List<Path> mods(Path root) throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        try (var files = Files.walk(root, 4)) {
            return files.filter(p -> p.getFileName().toString().equals("info.json"))
                .filter(p -> !p.toString().contains("/.fmtk/") && !p.toString().contains("/node_modules/"))
                .filter(p -> { try { return read(p).has("factorio_version"); } catch (Exception e) { return false; } })
                .map(Path::getParent).sorted().toList();
        }
    }
    public static List<Path> lines(String text) {
        return text.lines().map(String::trim).filter(s -> !s.isEmpty()).map(Path::of).toList();
    }
    public static void extract(Path zip, Path target) throws IOException {
        Files.createDirectories(target);
        Path root = target.toAbsolutePath().normalize();
        long total = 0;
        try (var input = new ZipInputStream(Files.newInputStream(zip))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                Path output = root.resolve(entry.getName()).normalize();
                if (!output.startsWith(root)) throw new IOException("Unsafe archive path: " + entry.getName());
                if (entry.isDirectory()) { Files.createDirectories(output); continue; }
                Files.createDirectories(output.getParent());
                try (var out = Files.newOutputStream(output)) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > 1_000_000_000L) throw new IOException("Dependency archive exceeds 1 GB expanded size");
                        out.write(buffer, 0, count);
                    }
                }
            }
        }
    }
}
