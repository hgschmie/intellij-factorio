package de.softwareforge.factorio;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.tang.intellij.lua.lang.LuaLanguageLevel;
import com.tang.intellij.lua.lang.LuaLanguageLevelProvider;
import java.nio.file.Files;
import java.nio.file.Path;

public class FactorioLuaLanguageLevelTest extends HeavyPlatformTestCase {
    public void testModUses52BeforeServerStartupAndUnrelatedFilesStayStrict() throws Exception {
        FactorioSettings.get(getProject()).serviceMode = "DISABLED";
        Path temporary = Files.createTempDirectory(Files.createDirectories(Path.of(System.getProperty("factorio.test.work"))), "language-level-");
        Path mod = Files.createDirectories(temporary.resolve("mod"));
        file(mod.resolve("info.json"), "{}");
        var lua = file(mod.resolve("settings.lua"), "return settings.global[name]");
        Path ordinary = Files.createDirectories(temporary.resolve("ordinary"));
        file(ordinary.resolve("info.json"), "{}");
        var unrelated = file(ordinary.resolve("ordinary.lua"), "return settings.global[name]");
        WriteAction.run(() -> {
            var module = ModuleManager.getInstance(getProject()).newModule(temporary.resolve("mod.iml"), "SOFTWAREFORGE_FACTORIO_MOD");
            ModuleRootModificationUtil.addContentRoot(module, mod.toString());
            var plain = ModuleManager.getInstance(getProject()).newModule(temporary.resolve("plain.iml"), "EMPTY_MODULE");
            ModuleRootModificationUtil.addContentRoot(plain, ordinary.toString());
        });
        var provider = LuaLanguageLevelProvider.EP_NAME.getExtensionList().stream()
            .filter(FactorioLuaLanguageLevel.class::isInstance).findFirst().orElseThrow();
        assertEquals(LuaLanguageLevel.LUA52, provider.getLanguageLevel(getProject(), lua));
        assertNull(provider.getLanguageLevel(getProject(), unrelated));
        var psi = PsiManager.getInstance(getProject());
        assertTrue(PsiTreeUtil.findChildrenOfType(psi.findFile(lua), PsiErrorElement.class).isEmpty());
        assertFalse(PsiTreeUtil.findChildrenOfType(psi.findFile(unrelated), PsiErrorElement.class).isEmpty());
    }

    private VirtualFile file(Path path, String content) throws Exception {
        Files.writeString(path, content);
        var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        assertNotNull(file);
        return file;
    }
}
