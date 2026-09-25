package de.softwareforge.factorio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class PathsAndModsTest {
    @TempDir Path root;
    @Test void releasePreviewRedactsRemoteCredentials() {
        assertEquals("https://[redacted]@example.invalid/repo?token=[redacted]",ReleaseSummary.redactUrl("https://user:secret@example.invalid/repo?token=private"));
    }
    @Test void rejectsZipTraversal() throws Exception {
        Path zip=root.resolve("bad.zip");
        try(var out=new ZipOutputStream(Files.newOutputStream(zip))) { out.putNextEntry(new ZipEntry("../escaped"));out.write(1);out.closeEntry(); }
        assertThrows(java.io.IOException.class,()->PathsAndMods.extract(zip,root.resolve("extracted")));
        assertFalse(Files.exists(root.resolve("escaped")));
    }
    @Test void discoversModsAndExtractsVersionedDependency() throws Exception {
        Path zip=root.resolve("dep.zip");
        try(var out=new ZipOutputStream(Files.newOutputStream(zip))) { out.putNextEntry(new ZipEntry("dep_1.0.0/info.json"));out.write("{\"name\":\"dep\",\"factorio_version\":\"2.0\"}".getBytes());out.closeEntry(); }
        PathsAndMods.extract(zip,root.resolve("deps"));
        assertEquals(1,PathsAndMods.mods(root.resolve("deps")).size());
    }
}
