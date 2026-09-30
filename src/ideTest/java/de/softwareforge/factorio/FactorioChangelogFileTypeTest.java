package de.softwareforge.factorio;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.LightVirtualFile;
import com.redhat.devtools.lsp4ij.LSPIJEditorUtils;

public class FactorioChangelogFileTypeTest extends HeavyPlatformTestCase {
    public void testChangelogIsFirstClassWithoutClaimingOtherTextFiles() {
        FactorioSettings.get(getProject()).serviceMode = "DISABLED";
        var types = FileTypeManager.getInstance();
        assertSame(FactorioChangelogFileType.INSTANCE, types.getFileTypeByFileName("changelog.txt"));
        assertSame(PlainTextFileType.INSTANCE, types.getFileTypeByFileName("notes.txt"));
        var file = new LightVirtualFile("changelog.txt", "Version: 0.1.0\n  Features:\n    - Initial release.\n");
        var psi = PsiManager.getInstance(getProject()).findFile(file);
        assertNotNull(psi);
        assertEquals(file.getContent().toString(), psi.getText());
        // Test the actual suggestion eligibility checks: the notification provider
        // itself unconditionally returns null in IDE unit-test mode.
        assertFalse(LSPIJEditorUtils.isPlainTextFile(psi));
        assertFalse(LSPIJEditorUtils.isAbstractFileTypeFile(psi));
        assertFalse(LSPIJEditorUtils.isTextMateFile(psi));
    }
}
