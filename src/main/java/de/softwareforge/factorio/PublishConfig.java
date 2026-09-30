package de.softwareforge.factorio;

import com.google.gson.JsonObject;
import java.nio.file.*;
import java.io.IOException;

/** Per-publish author override without modifying the user's package configuration. */
record PublishConfig(String path, boolean temporary) implements AutoCloseable {
    static String author(String name, String email) {
        name = name.trim(); email = email.trim();
        if (name.isEmpty() && email.isEmpty()) return "";
        if (name.isEmpty() || email.isEmpty()) throw new IllegalArgumentException("Provide both publish author name and email, or leave both empty.");
        // FMTK embeds --author inside a double-quoted shell argument.
        for (String value : new String[]{name, email}) {
            if (value.chars().anyMatch(c -> c < 32 || "\"`$\\<>".indexOf(c) >= 0))
                throw new IllegalArgumentException("Publish author name and email cannot contain shell quoting characters, angle brackets, or line breaks.");
        }
        return name + " <" + email + ">";
    }
    static PublishConfig prepare(String original, String name, String email, Path directory) throws IOException {
        String author = author(name, email);
        if (author.isEmpty()) return new PublishConfig(original, false);
        Path source = original.isBlank() ? Path.of(System.getProperty("user.home"), ".fmtk/config.json") : Path.of(original);
        JsonObject config = Files.isRegularFile(source) ? PathsAndMods.read(source) : new JsonObject();
        if (!original.isBlank() && !Files.isRegularFile(source)) throw new IOException("Package configuration not found: " + source);
        config.addProperty("package.autoCommitAuthor", author);
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, "publish-", ".json");
        try { Files.writeString(temp, PathsAndMods.JSON.toJson(config)); }
        catch (IOException failure) { Files.deleteIfExists(temp); throw failure; }
        return new PublishConfig(temp.toString(), true);
    }
    @Override public void close() throws IOException { if (temporary) Files.deleteIfExists(Path.of(path)); }
}
