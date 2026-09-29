package de.softwareforge.factorio;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModLanguageScopeTest {
    @TempDir Path directory;
    private Path mod(String name) throws Exception {
        Path root=Files.createDirectories(directory.resolve(name));
        Files.writeString(root.resolve("info.json"),"{\"name\":\""+name+"\"}"); return root.toRealPath();
    }
    private ModLanguageScope scope(Path root, List<Path> dependencies) {
        return new ModLanguageScope(root,root.getFileName().toString(),directory.resolve("config/"+root.getFileName()),List.of(directory.resolve("api")),dependencies,new JsonObject());
    }
    @Test void unrelatedModsAreNotIndexed() throws Exception {
        Path a=mod("a"), b=mod("b"); var scope=scope(a,List.of());
        assertTrue(scope.includes(a.resolve("lib/this.lua"),true));
        assertFalse(scope.includes(b.resolve("lib/this.lua"),true));
        var config=ModLanguageScope.configuration(a,scope.libraries(),List.of());
        var workspace=config.getAsJsonObject("workspace");
        assertEquals(1,workspace.getAsJsonArray("workspaceRoots").size());
        assertEquals(1,workspace.getAsJsonArray("packages").size());
        assertFalse(workspace.getAsJsonArray("library").toString().contains(a.getParent().toString()+"\""));
        assertFalse(config.toString().contains(b.toString()));
    }
    @Test void attachedModuleOwnsItsFilesEvenWhenDependencyOfAnother() throws Exception {
        Path a=mod("a"),b=mod("b"); var one=scope(a,List.of(b)); var two=scope(b,List.of());
        assertEquals(two,ModLanguageScope.owner(List.of(one,two),b.resolve("control.lua"),true));
        assertEquals(two,ModLanguageScope.owner(List.of(two,one),b.resolve("control.lua"),false));
        assertEquals(one,ModLanguageScope.owner(List.of(one),b.resolve("control.lua"),true));
    }
    @Test void externalDependencyHasDeterministicOwner() throws Exception {
        Path a=mod("a"),b=mod("b"),dep=mod("dep"); var one=scope(a,List.of(dep)); var two=scope(b,List.of(dep));
        assertEquals(one,ModLanguageScope.owner(List.of(two,one),dep.resolve("helper.lua"),true));
        assertNull(ModLanguageScope.owner(List.of(one,two),directory.resolve("plain.lua"),true));
    }
    @Test void pathBoundariesAndProjectIdsAreDistinct() throws Exception {
        Path a=mod("a"); var scope=scope(a,List.of());
        assertFalse(scope.includes(directory.resolve("another/control.lua"),true));
        assertNotEquals(ModLanguageScope.identity(directory,a),ModLanguageScope.identity(directory.resolve("other-project"),a));
        assertEquals(ModLanguageScope.identity(directory,a),ModLanguageScope.identity(directory.resolve("."),a.resolve(".")));
    }
    @Test void watcherRegistrationCannotReachUnrelatedMods() throws Exception {
        Path a=mod("a"),b=mod("b"),dep=mod("dep"); var scope=scope(a,List.of(dep));
        var watchers=scope.watchers(false).getAsJsonArray("watchers");
        assertEquals(4,watchers.size());
        for(var w:watchers) {
            var glob=w.getAsJsonObject().getAsJsonObject("globPattern");
            assertTrue(Set.of(a.toUri().toString(),dep.toUri().toString()).contains(glob.get("baseUri").getAsString()));
        }
        assertFalse(watchers.toString().contains(b.toUri().toString()));
        assertFalse(watchers.toString().contains(directory.resolve("api").toUri().toString()));
    }
    @Test void configurationLeavesDiagnosticsToModuleAndMapsOnlyExplicitPackages() throws Exception {
        Path a=mod("a"),dep=mod("dep-name");
        var result=ModLanguageScope.configuration(a,List.of(directory.resolve("api")),List.of(dep));
        assertFalse(result.has("diagnostics"));
        var workspace=result.getAsJsonObject("workspace");
        assertEquals(2,workspace.getAsJsonArray("packages").size());
        assertTrue(workspace.getAsJsonArray("moduleMap").toString().contains("__dep-name__.$1"));
        assertEquals("Lua 5.2",result.getAsJsonObject("runtime").get("version").getAsString());
    }
}
