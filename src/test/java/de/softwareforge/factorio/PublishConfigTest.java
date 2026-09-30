package de.softwareforge.factorio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class PublishConfigTest {
    @TempDir Path root;
    @Test void mergesAuthorAndCleansUpWithoutChangingSource() throws Exception {
        var source = root.resolve("config.json");
        String original = "{\"package.tagName\":\"release-$VERSION\",\"package.autoCommitAuthor\":\"Old <old@example.com>\",\"other.value\":true}";
        Files.writeString(source, original);
        Path generated;
        try (var config = PublishConfig.prepare(source.toString(), "New Author", "new@example.com", root)) {
            generated = Path.of(config.path());
            var json = PathsAndMods.read(generated);
            assertEquals("New Author <new@example.com>", json.get("package.autoCommitAuthor").getAsString());
            assertEquals("release-$VERSION", json.get("package.tagName").getAsString());
            assertTrue(json.get("other.value").getAsBoolean());
        }
        assertFalse(Files.exists(generated));
        assertEquals(original, Files.readString(source));
        try (var config = PublishConfig.prepare(source.toString(), "", "", root)) {
            assertEquals(source.toString(), config.path());
        }
        assertTrue(Files.exists(source));
    }
    @Test void validatesPairedFieldsAndShellSafeIdentity() {
        assertThrows(IllegalArgumentException.class, () -> PublishConfig.author("Name", ""));
        assertThrows(IllegalArgumentException.class, () -> PublishConfig.author("$(command)", "mail@example.com"));
        assertEquals("O'Name <mail@example.com>", PublishConfig.author("O'Name", "mail@example.com"));
    }
}
