package de.softwareforge.factorio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModSkeletonTest {
    @TempDir Path root;
    private ModSkeleton.Metadata metadata(){return new ModSkeleton.Metadata("test-mod","Test Mod","Test author","Description","2.1");}
    @Test void createsLoadableMinimalStages() throws Exception {
        ModSkeleton.create(root,metadata());
        var info=PathsAndMods.read(root.resolve("info.json"));assertEquals("0.1.0",info.get("version").getAsString());assertEquals("2.1",info.get("factorio_version").getAsString());
        for(String file:List.of("control.lua","settings.lua","data.lua","data-updates.lua","data-final-fixes.lua"))assertTrue(Files.readString(root.resolve(file)).startsWith("--"));
        for(String dir:List.of("prototypes","scripts","graphics"))assertTrue(Files.isDirectory(root.resolve(dir)));
        assertTrue(Files.readString(root.resolve("changelog.txt")).contains("Version: 0.1.0"));
    }
    @Test void refusesAnyExistingContentWithoutOverwriting() throws Exception {
        Path marker=root.resolve("existing.txt");Files.writeString(marker,"unchanged");
        assertThrows(java.io.IOException.class,()->ModSkeleton.create(root,metadata()));
        assertEquals("unchanged",Files.readString(marker));assertFalse(Files.exists(root.resolve("info.json")));
    }
    @Test void invalidInputAndPreviewDoNotWrite() {
        assertThrows(IllegalArgumentException.class,()->ModSkeleton.create(root,new ModSkeleton.Metadata("../escape","T","A","","2.1")));
        assertEquals(7,ModSkeleton.files(metadata()).size());assertFalse(Files.exists(root.resolve("info.json")));
    }
    @Test void ideMetadataIsPreservedButModFilesStillPreventCreation() throws Exception {
        Files.createDirectory(root.resolve(".idea"));Files.writeString(root.resolve(".idea/marker"),"untouched");
        ModSkeleton.createForIde(root,metadata());assertEquals("untouched",Files.readString(root.resolve(".idea/marker")));
        assertThrows(java.io.IOException.class,()->ModSkeleton.createForIde(root,metadata()));
    }
}
