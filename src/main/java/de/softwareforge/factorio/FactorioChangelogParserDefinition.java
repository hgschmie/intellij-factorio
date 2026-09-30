package de.softwareforge.factorio;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/** Supplies native PSI identity; FMTK remains responsible for changelog validation. */
public final class FactorioChangelogParserDefinition implements ParserDefinition {
    private static final IFileElementType FILE = new IFileElementType(FactorioChangelogFileType.LANGUAGE);
    private static final IElementType TEXT = new IElementType("CHANGELOG_TEXT", FactorioChangelogFileType.LANGUAGE);

    @Override public @NotNull Lexer createLexer(Project project) {
        return new LexerBase() {
            private CharSequence buffer = "";
            private int start, end;
            @Override public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
                this.buffer = buffer; start = startOffset; end = endOffset;
            }
            @Override public int getState() { return 0; }
            @Override public IElementType getTokenType() { return start < end ? TEXT : null; }
            @Override public int getTokenStart() { return start; }
            @Override public int getTokenEnd() { return end; }
            @Override public void advance() { start = end; }
            @Override public @NotNull CharSequence getBufferSequence() { return buffer; }
            @Override public int getBufferEnd() { return end; }
        };
    }
    @Override public @NotNull PsiParser createParser(Project project) {
        return (root, builder) -> {
            var marker = builder.mark();
            while (!builder.eof()) builder.advanceLexer();
            marker.done(root);
            return builder.getTreeBuilt();
        };
    }
    @Override public @NotNull IFileElementType getFileNodeType() { return FILE; }
    @Override public @NotNull TokenSet getWhitespaceTokens() { return TokenSet.EMPTY; }
    @Override public @NotNull TokenSet getCommentTokens() { return TokenSet.EMPTY; }
    @Override public @NotNull TokenSet getStringLiteralElements() { return TokenSet.EMPTY; }
    @Override public @NotNull PsiElement createElement(ASTNode node) { return new ASTWrapperPsiElement(node); }
    @Override public @NotNull PsiFile createFile(@NotNull FileViewProvider provider) {
        return new PsiFileBase(provider, FactorioChangelogFileType.LANGUAGE) {
            @Override public @NotNull FileType getFileType() { return FactorioChangelogFileType.INSTANCE; }
        };
    }
}
