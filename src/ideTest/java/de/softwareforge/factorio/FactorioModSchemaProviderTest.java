package de.softwareforge.factorio;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import java.nio.file.Files;
import java.nio.file.Path;

public class FactorioModSchemaProviderTest extends HeavyPlatformTestCase {
    public void testSchemaAssociationAndBundledReference() throws Exception {
        FactorioSettings.get(getProject()).serviceMode = "DISABLED";
        Path temporary = Files.createTempDirectory(Files.createDirectories(Path.of(System.getProperty("factorio.test.work"))), "schema-");
        Path mod = Files.createDirectories(temporary.resolve("external-mod"));
        var info = file(mod.resolve("info.json"), "{}");
        WriteAction.run(() -> {
            var module = ModuleManager.getInstance(getProject()).newModule(temporary.resolve("external.iml"), "SOFTWAREFORGE_FACTORIO_MOD");
            ModuleRootModificationUtil.addContentRoot(module, mod.toString());
        });
        var factory = JsonSchemaProviderFactory.EP_NAME.getExtensionList().stream()
            .filter(FactorioModSchemaProvider.class::isInstance).findFirst().orElseThrow();
        var provider = factory.getProviders(getProject()).getFirst();
        assertTrue(provider.isAvailable(info)); // Missing required fields must not disable the schema.
        for (String nested : new String[]{"locale/en/info.json", "scenarios/test/info.json", "campaigns/test/info.json", "dependencies/other/info.json"})
            assertFalse(provider.isAvailable(file(mod.resolve(nested), "{}")));
        assertFalse(provider.isAvailable(file(temporary.resolve("unattached/info.json"), "{}")));
        assertFalse(provider.isAvailable(file(mod.resolve("settings.json"), "{}")));
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(),
            Path.of(System.getProperty("factorio.test.workspace"), "intellij-factorio", ".intellijPlatform", "sandbox").toString());
        var schemaFile = provider.getSchemaFile();
        assertNotNull(schemaFile);
        var service = getProject().getService(JsonSchemaService.class);
        assertEquals(java.util.Set.of(schemaFile), new java.util.HashSet<>(service.getSchemaFilesForFile(info)));
        assertEquals(JsonSchemaVersion.SCHEMA_7, service.getSchemaVersion(schemaFile));
        var schema = service.getSchemaObject(info);
        assertNotNull(schema);
        assertEquals("Factorio Mod info.json", schema.getTitle());
        var allOf = schema.getAllOf();
        assertNotNull(allOf);
        String reference = allOf.getFirst().getRef();
        assertEquals("urn:de.softwareforge.factorio:schema:datainfo", reference);
        var baseProvider = factory.getProviders(getProject()).get(1);
        assertFalse(baseProvider.isAvailable(info));
        assertFalse(baseProvider.isUserVisible());
        var baseFile = service.findSchemaFileByReference(reference, schemaFile);
        assertNotNull(baseFile);
        assertEquals(baseProvider.getSchemaFile(), baseFile);
        var data = service.getSchemaObjectForSchemaFile(baseFile);
        assertNotNull(data.getPropertyByName("dependencies"));
        assertNotNull(data.getPropertyByName("factorio_version"));
        assertNotNull(data.getPropertyByName("name").getPattern());
        var modSchema = allOf.get(1);
        assertNotNull(modSchema.getPropertyByName("package"));
        assertTrue(modSchema.getRequired().containsAll(java.util.List.of("name", "version", "title", "author", "factorio_version")));
        WriteAction.run(() -> {
            try { info.setBinaryContent("{\"name\":".getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            catch (java.io.IOException e) { throw new RuntimeException(e); }
        });
        assertTrue(provider.isAvailable(info));
        assertEquals(java.util.Set.of(schemaFile), new java.util.HashSet<>(service.getSchemaFilesForFile(info)));
    }
    private VirtualFile file(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
        var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        assertNotNull(file);
        return file;
    }
}
