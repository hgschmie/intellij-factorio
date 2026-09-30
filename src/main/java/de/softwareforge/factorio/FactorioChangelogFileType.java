package de.softwareforge.factorio;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/** Registers changelog files with the IDE; parsing and diagnostics are provided by FMTK. */
public final class FactorioChangelogFileType extends LanguageFileType {
    public static final Language LANGUAGE = new Language("FactorioChangelog") {};
    public static final FactorioChangelogFileType INSTANCE = new FactorioChangelogFileType();

    private FactorioChangelogFileType() { super(LANGUAGE); }

    @Override public @NotNull String getName() { return "Factorio Changelog"; }
    @Override public @NotNull String getDescription() { return "Factorio changelog file"; }
    @Override public @NotNull String getDefaultExtension() { return "txt"; }
    @Override public Icon getIcon() { return FactorioIcons.FACTORIO; }
}
