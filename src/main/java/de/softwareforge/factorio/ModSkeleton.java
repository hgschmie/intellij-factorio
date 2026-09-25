package de.softwareforge.factorio;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Pure creation logic shared by both IntelliJ wizards. */
public final class ModSkeleton {
    public record Metadata(String name, String title, String author, String description, String factorioVersion) {
        public void validate() {
            if (!name.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Mod name must contain only letters, digits, underscores and hyphens");
            if (title.isBlank() || author.isBlank()) throw new IllegalArgumentException("Title and author are required");
            if (!factorioVersion.matches("[0-9]+\\.[0-9]+")) throw new IllegalArgumentException("Factorio version must be major.minor (for example 2.1)");
        }
    }
    public static Map<String,String> files(Metadata m) {
        m.validate();
        var info = new JsonObject();
        info.addProperty("name",m.name()); info.addProperty("version","0.1.0");
        info.addProperty("title",m.title()); info.addProperty("author",m.author());
        info.addProperty("description",m.description()); info.addProperty("factorio_version",m.factorioVersion());
        var deps = new JsonArray(); deps.add("base >= " + m.factorioVersion() + ".0"); info.add("dependencies",deps);
        var result = new LinkedHashMap<String,String>();
        result.put("info.json",PathsAndMods.JSON.toJson(info)+"\n");
        result.put("changelog.txt","---------------------------------------------------------------------------------------------------\nVersion: 0.1.0\n  Features:\n    - Initial release.\n");
        result.put("control.lua","-- Runtime stage: register event handlers here.\n-- Keep persistent state in storage; do not access game at file scope.\n");
        result.put("settings.lua","-- Settings stage: define startup and runtime mod settings here.\n");
        result.put("data.lua","-- Data stage: define prototypes here or require files from prototypes/.\n");
        result.put("data-updates.lua","-- Data updates stage: adjust prototypes after all mods have loaded data.lua.\n");
        result.put("data-final-fixes.lua","-- Final data stage: apply changes that depend on other mods' updates.\n");
        return result;
    }
    public static void checkDestination(Path root) throws IOException {
        if (!Files.exists(root)) return;
        if (!Files.isDirectory(root)) throw new IOException("Destination is not a directory: " + root);
        try (var entries = Files.list(root)) {
            if (entries.findAny().isPresent()) throw new IOException("Choose an empty directory; use Import for an existing mod");
        }
    }
    public static void create(Path root, Metadata metadata) throws IOException {
        create(root, metadata, false);
    }
    static void createForIde(Path root, Metadata metadata) throws IOException { create(root,metadata,true); }
    private static void create(Path root, Metadata metadata, boolean ideMetadata) throws IOException {
        var files = files(metadata);
        if (ideMetadata && Files.isDirectory(root)) {
            // The wizard may already have written its own project/module metadata.
            try(var entries=Files.list(root)) {
                if(entries.anyMatch(p->!p.getFileName().toString().equals(".idea") && !p.getFileName().toString().endsWith(".iml"))) throw new IOException("Choose an empty mod directory");
            }
        } else checkDestination(root);
        Files.createDirectories(root);
        var created = new ArrayList<Path>();
        try {
            for (String directory : List.of("prototypes","scripts","graphics")) {
                Path path=root.resolve(directory); Files.createDirectory(path); created.add(path);
            }
            for (var file : files.entrySet()) {
                Path path=root.resolve(file.getKey()); Files.writeString(path,file.getValue(),StandardOpenOption.CREATE_NEW); created.add(path);
            }
        } catch (IOException failure) {
            Collections.reverse(created);
            for (Path path:created) try { Files.deleteIfExists(path); } catch(IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
}
