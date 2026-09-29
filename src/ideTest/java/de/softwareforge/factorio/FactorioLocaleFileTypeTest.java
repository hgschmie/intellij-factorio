package de.softwareforge.factorio;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.openapi.fileEditor.FileDocumentManager;

public class FactorioLocaleFileTypeTest extends HeavyPlatformTestCase {
    public void testCfgIsRegisteredAndCanBeOpenedAsText() {
        FactorioSettings.get(getProject()).serviceMode="DISABLED";
        var type=FileTypeManager.getInstance().getFileTypeByFileName("messages.cfg");
        assertSame(FactorioLocaleFileType.INSTANCE,type);
        assertNotSame(UnknownFileType.INSTANCE,type);
        assertSame(FactorioIcons.FACTORIO,type.getIcon());
        assertFalse(type.isBinary());
        var file=new LightVirtualFile("messages.cfg","[entity-name]\nlocomotive=Locomotive\n");
        assertSame(type,file.getFileType());
        var document=FileDocumentManager.getInstance().getDocument(file);
        assertNotNull(document);
        assertEquals("[entity-name]\nlocomotive=Locomotive\n",document.getText());
        assertNotSame(type,FileTypeManager.getInstance().getFileTypeByFileName("control.lua"));
    }
}
