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
    public void testLocaleHighlightingAndIncrementalRestart() {
        var highlighter = com.intellij.openapi.fileTypes.SyntaxHighlighterFactory.getSyntaxHighlighter(
            FactorioLocaleFileType.INSTANCE, getProject(), null);
        assertInstanceOf(highlighter, FactorioLocaleHighlighter.class);
        String text = "  # comment\r\n[entity-name]\r\nname=Hello __1__ __CONTROL__open-gui__ [color=green]world[/color]\\n😀\n"
            + "; another comment\n[unfinished\nnext=value=with;literal#characters\nempty=\n";
        var lexer = highlighter.getHighlightingLexer();
        lexer.start(text);
        var types = new java.util.HashSet<com.intellij.psi.tree.IElementType>();
        int end = 0;
        while (lexer.getTokenType() != null) {
            assertEquals(end, lexer.getTokenStart());
            assertTrue(lexer.getTokenEnd() > end);
            types.add(lexer.getTokenType());
            // Editor highlighting restarts from a saved token boundary after an edit.
            var restarted = highlighter.getHighlightingLexer();
            restarted.start(text, lexer.getTokenStart(), text.length(), lexer.getState());
            assertSame(lexer.getTokenType(), restarted.getTokenType());
            assertEquals(lexer.getTokenEnd(), restarted.getTokenEnd());
            if (lexer.getTokenType() != com.intellij.psi.TokenType.WHITE_SPACE)
                assertEquals(1, highlighter.getTokenHighlights(lexer.getTokenType()).length);
            end = lexer.getTokenEnd();
            lexer.advance();
        }
        assertEquals(text.length(), end);
        assertTrue(types.containsAll(java.util.List.of(FactorioLocaleHighlighter.SECTION,
            FactorioLocaleHighlighter.PROPERTY, FactorioLocaleHighlighter.COMMENT,
            FactorioLocaleHighlighter.SEPARATOR, FactorioLocaleHighlighter.PARAMETER,
            FactorioLocaleHighlighter.MARKUP, FactorioLocaleHighlighter.ESCAPE)));
        lexer.start("name=__1__\nnext=value");
        assertSame(FactorioLocaleHighlighter.PROPERTY, lexer.getTokenType());
        lexer.advance(); assertSame(FactorioLocaleHighlighter.SEPARATOR, lexer.getTokenType());
        lexer.advance(); assertSame(FactorioLocaleHighlighter.PARAMETER, lexer.getTokenType());
        lexer.advance(); lexer.advance(); assertSame(FactorioLocaleHighlighter.PROPERTY, lexer.getTokenType());
        for (String placeholder : java.util.List.of("__1__", "__CONTROL_KEY_SHIFT__", "__CONTROL__open-gui__", "__plural_for_parameter__1__")) {
            lexer.start("key=" + placeholder);
            lexer.advance(); lexer.advance();
            assertSame(FactorioLocaleHighlighter.PARAMETER, lexer.getTokenType());
            assertEquals(4 + placeholder.length(), lexer.getTokenEnd());
        }
        assertEquals(8, new FactorioLocaleHighlighter.Colors().getAttributeDescriptors().length);
    }
}
