package de.softwareforge.factorio;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/** Registers locale files with the IDE; parsing and diagnostics are provided by FMTK. */
public final class FactorioLocaleFileType extends LanguageFileType {
    public static final Language LANGUAGE = new Language("FactorioLocale") {};
    public static final FactorioLocaleFileType INSTANCE = new FactorioLocaleFileType();

    private FactorioLocaleFileType() { super(LANGUAGE); }

    @Override public @NotNull String getName() { return "Factorio Locale"; }
    @Override public @NotNull String getDescription() { return "Factorio locale file"; }
    @Override public @NotNull String getDefaultExtension() { return "cfg"; }
    @Override public Icon getIcon() { return FactorioIcons.FACTORIO; }
}
