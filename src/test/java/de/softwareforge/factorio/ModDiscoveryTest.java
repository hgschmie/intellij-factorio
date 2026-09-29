package de.softwareforge.factorio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModDiscoveryTest {
    @TempDir Path root;
    private Path mod(String dir,String name) throws Exception {Path p=root.resolve(dir);ModSkeleton.create(p,new ModSkeleton.Metadata(name,name,"Test","","2.1"));return p;}
    @Test void findsExternalRootsAndKeepsLocalContextSeparate() throws Exception {
        Path a=mod("outside-a/mod","a"),b=mod("outside-b/mod","b");
        var result=ModDiscovery.discover(List.of(new ModDiscovery.Candidate("a",a),new ModDiscovery.Candidate("b",b)));
        assertEquals(2,result.mods().size());assertTrue(result.errors().isEmpty());
        assertEquals("a",ModDiscovery.containing(result.mods(),a.resolve("scripts/helper.lua")).name());
        assertEquals("b",ModDiscovery.containing(result.mods(),b.resolve("scripts/helper.lua")).name());
        assertNull(ModDiscovery.containing(result.mods(),root.resolve("unrelated.lua")));
    }
    @Test void rejectsDuplicateIdentitiesAndAmbiguousModuleRoots() throws Exception {
        Path a=mod("a","same"),b=mod("b","same");
        var result=ModDiscovery.discover(List.of(new ModDiscovery.Candidate("a",a),new ModDiscovery.Candidate("b",b)));
        assertTrue(result.mods().isEmpty());assertEquals(1,result.errors().size());
        Path c=mod("c","different");
        assertTrue(ModDiscovery.discover(List.of(new ModDiscovery.Candidate("module",a),new ModDiscovery.Candidate("module",c))).mods().isEmpty());
    }
    @Test void ignoresNestedDependenciesAndReportsInvalidMetadata() throws Exception {
        mod("container/nested","nested");
        var result=ModDiscovery.discover(List.of(new ModDiscovery.Candidate("container",root.resolve("container"))));assertTrue(result.mods().isEmpty());
        Files.writeString(root.resolve("container/info.json"),"{}");
        assertEquals(1,ModDiscovery.discover(List.of(new ModDiscovery.Candidate("container",root.resolve("container")))).errors().size());
    }
    @Test void deduplicatesSymlinkAndLegacyRoot() throws Exception {
        Path a=mod("a","a"),link=root.resolve("link");Files.createSymbolicLink(link,a);
        var result=ModDiscovery.discover(List.of(new ModDiscovery.Candidate("module",a),new ModDiscovery.Candidate("Legacy mod",link)));
        assertEquals(1,result.mods().size());assertEquals("module",result.mods().getFirst().module());
    }
    @Test void locatesMissingFilesThroughSymlinkedParents() throws Exception {
        Path a=mod("real/a","a"),b=mod("real/b","b");
        Path alias=root.resolve("alias");
        Files.createSymbolicLink(alias,root.resolve("real").toRealPath());
        var mods=ModDiscovery.discover(List.of(new ModDiscovery.Candidate("a",a),new ModDiscovery.Candidate("b",b))).mods();
        var expected=mods.stream().filter(m->m.name().equals("a")).findFirst().orElseThrow();
        Path missing=alias.resolve("a/scripts/new/nested/helper.lua");
        assertFalse(Files.exists(missing));
        assertEquals(expected,ModDiscovery.containing(mods,missing));
        assertEquals(expected,ModDiscovery.containing(mods,alias.resolve("a/control.lua")));
        Path deleted=Files.writeString(a.resolve("scripts/deleted.lua"),"return {}");
        Files.delete(deleted);
        assertEquals(expected,ModDiscovery.containing(mods,alias.resolve("a/scripts/deleted.lua")));
        assertNull(ModDiscovery.containing(mods,alias.resolve("unrelated/new.lua")));
        Path other=alias.resolve("a/scripts/other");
        Files.createSymbolicLink(other,b.toRealPath());
        assertEquals("b",Objects.requireNonNull(ModDiscovery.containing(mods,other.resolve("new.lua"))).name());
    }
    @Test void refreshDropsRemovedAndInvalidModsAndAcceptsRepairs() throws Exception {
        Path a=mod("a","a"),b=mod("b","b");
        var candidates=List.of(new ModDiscovery.Candidate("a",a),new ModDiscovery.Candidate("b",b));
        String valid=Files.readString(b.resolve("info.json"));
        Files.writeString(b.resolve("info.json"),"invalid JSON");
        var invalid=ModDiscovery.discover(candidates);
        assertEquals(List.of("a"),invalid.mods().stream().map(ModDiscovery.Mod::name).toList());
        assertEquals(1,invalid.errors().size());
        Files.writeString(b.resolve("info.json"),valid);
        assertEquals(2,ModDiscovery.discover(candidates).mods().size());
        Files.delete(b.resolve("info.json"));
        assertEquals(1,ModDiscovery.discover(candidates).mods().size());
        assertTrue(ModDiscovery.discover(List.of()).mods().isEmpty());
    }
}
