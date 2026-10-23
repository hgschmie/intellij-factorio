package de.softwareforge.factorio;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/** Verifies that generator upgrades replace managed API libraries without editing existing caches. */
public class DefinitionsUpgradeTest extends HeavyPlatformTestCase {
    @Override protected void tearDown() throws Exception {
        try { FactorioServerManager.get(getProject()).configure(List.of()); }
        finally { super.tearDown(); }
    }

    public void testGeneratorUpgradeRegeneratesManagedLibraryAndReusesUnchangedInputs() throws Exception {
        var settings = FactorioSettings.get(getProject());
        settings.serviceMode = "DISABLED";
        Path root = Files.createDirectories(Toolkit.root(getProject())).toRealPath();
        Files.writeString(root.resolve("info.json"), "{\"name\":\"cache-upgrade\",\"version\":\"0.1.0\",\"factorio_version\":\"2.1\"}");
        var virtualRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root);
        assertNotNull(virtualRoot);
        WriteAction.run(() -> ModuleRootModificationUtil.addContentRoot(getModule(), virtualRoot));
        FactorioModules.get(getProject()).refresh();
        assertEquals(1, FactorioModules.get(getProject()).mods().size());

        Path tools = Files.createTempDirectory(Files.createDirectories(
            Path.of(System.getProperty("factorio.test.work"))), "generator-upgrade-");
        Path docs = Files.createDirectories(tools.resolve("docs"));
        Files.writeString(docs.resolve("runtime-api.json"), "{\"application_version\":\"2.1.23\"}");
        Files.writeString(docs.resolve("prototype-api.json"), "{}");
        settings.apiDocs = docs.toString();
        Path cli = tools.resolve("generator.mjs");
        Files.writeString(cli, generator("before"));
        settings.cli = cli.toString();
        Path before = generate(root);
        assertEquals("-- before\n", Files.readString(before.resolve("defines.lua")));
        assertEquals("2.1.23", Files.readString(before.getParent().getParent().resolve("complete")));
        assertEquals(before, generate(root));
        assertEquals("before\n", Files.readString(root.resolve("generations.log")));

        Path relocated = Files.copy(cli, tools.resolve("relocated.mjs"));
        settings.cli = relocated.toString();
        assertEquals(before, generate(root));
        assertEquals("before\n", Files.readString(root.resolve("generations.log")));

        var oldTimestamp = Files.getLastModifiedTime(relocated);
        Files.writeString(relocated, generator("after!"));
        Files.setLastModifiedTime(relocated, oldTimestamp);
        Path after = generate(root);
        assertFalse("Changed generator bytes must replace the configured API library", before.equals(after));
        assertEquals("-- after!\n", Files.readString(after.resolve("defines.lua")));
        assertEquals("-- before\n", Files.readString(before.resolve("defines.lua")));
        assertEquals(after, generate(root));
        assertEquals("before\nafter!\n", Files.readString(root.resolve("generations.log")));
    }

    private Path generate(Path root) throws Exception {
        var generation = CompletableFuture.runAsync(() -> {
            try { Definitions.generate(getProject(), new EmptyProgressIndicator(), message -> {}); }
            catch (Exception error) { throw new CompletionException(error); }
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!generation.isDone() && System.nanoTime() < deadline) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        generation.get(1, TimeUnit.SECONDS);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        Path managed = Definitions.cache(getProject()).resolve("language-servers/"
            + ModLanguageScope.identity(root, root) + "/workspace/managed-emmy-config.json");
        var libraries = PathsAndMods.read(managed).getAsJsonObject("workspace").getAsJsonArray("library");
        assertEquals(1, libraries.size());
        return Path.of(libraries.get(0).getAsString());
    }

    private static String generator(String marker) {
        return """
            import {mkdirSync, writeFileSync, appendFileSync} from 'node:fs';
            import {resolve} from 'node:path';
            if (process.argv[2] !== 'docs' || process.argv[5] !== 'emmylua') {
                throw new Error('Expected the EmmyLua docs command');
            }
            const library = resolve(process.argv[3], 'factorio/library');
            mkdirSync(library, {recursive: true});
            writeFileSync(resolve(library, 'defines.lua'), '-- %s\\n');
            appendFileSync(resolve('generations.log'), '%s\\n');
            """.formatted(marker, marker);
    }
}
