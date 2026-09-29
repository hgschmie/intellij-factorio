package de.softwareforge.factorio;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import javax.swing.Icon;
import java.util.Map;

/** Local, incremental highlighting; diagnostics and navigation remain owned by FMTK. */
public final class FactorioLocaleHighlighter extends SyntaxHighlighterBase {
    public static final IElementType SECTION = token("SECTION"), PROPERTY = token("PROPERTY"),
        SEPARATOR = token("SEPARATOR"), COMMENT = token("COMMENT"), TEXT = token("TEXT"),
        PARAMETER = token("PARAMETER"), MARKUP = token("MARKUP"), ESCAPE = token("ESCAPE");
    private static IElementType token(String name) { return new IElementType(name, FactorioLocaleFileType.LANGUAGE); }
    private static TextAttributesKey color(String name, TextAttributesKey fallback) {
        return TextAttributesKey.createTextAttributesKey("FACTORIO_LOCALE_" + name, fallback);
    }
    private static final Map<IElementType, TextAttributesKey> COLORS = Map.of(
        SECTION, color("SECTION", DefaultLanguageHighlighterColors.METADATA),
        PROPERTY, color("KEY", DefaultLanguageHighlighterColors.INSTANCE_FIELD),
        SEPARATOR, color("SEPARATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN),
        COMMENT, color("COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT),
        TEXT, color("VALUE", DefaultLanguageHighlighterColors.STRING),
        PARAMETER, color("PLACEHOLDER", DefaultLanguageHighlighterColors.PARAMETER),
        MARKUP, color("RICH_TEXT", DefaultLanguageHighlighterColors.MARKUP_TAG),
        ESCAPE, color("ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE));
    @Override public @NotNull Lexer getHighlightingLexer() { return new FlexAdapter(new FactorioLocaleLexer(null)); }
    @Override public TextAttributesKey @NotNull [] getTokenHighlights(IElementType type) { return pack(COLORS.get(type)); }

    public static final class Factory extends SyntaxHighlighterFactory {
        @Override public @NotNull SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile file) {
            return new FactorioLocaleHighlighter();
        }
    }
    public static final class Colors implements ColorSettingsPage {
        @Override public @Nullable Icon getIcon() { return FactorioIcons.FACTORIO; }
        @Override public @NotNull String getDisplayName() { return "Factorio Locale"; }
        @Override public @NotNull SyntaxHighlighter getHighlighter() { return new FactorioLocaleHighlighter(); }
        @Override public @NotNull String getDemoText() {
            return "# Factorio translations\n[entity-name]\nlocomotive=Electric locomotive\n\n[entity-description]\nlocomotive=Power: __1__ MW\\n[color=green]Ready[/color] __CONTROL__open-gui__\n";
        }
        @Override public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() { return null; }
        @Override public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
            return new AttributesDescriptor[] {
                new AttributesDescriptor("Section", COLORS.get(SECTION)), new AttributesDescriptor("Key", COLORS.get(PROPERTY)),
                new AttributesDescriptor("Separator", COLORS.get(SEPARATOR)), new AttributesDescriptor("Comment", COLORS.get(COMMENT)),
                new AttributesDescriptor("Value", COLORS.get(TEXT)), new AttributesDescriptor("Placeholder", COLORS.get(PARAMETER)),
                new AttributesDescriptor("Rich text tag", COLORS.get(MARKUP)), new AttributesDescriptor("Escape", COLORS.get(ESCAPE)) };
        }
        @Override public ColorDescriptor @NotNull [] getColorDescriptors() { return ColorDescriptor.EMPTY_ARRAY; }
    }
}
