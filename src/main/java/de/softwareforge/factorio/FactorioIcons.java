package de.softwareforge.factorio;

import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;

/** Native-size Factorio icon; IconLoader selects the @2x asset on HiDPI displays. */
public final class FactorioIcons {
    private FactorioIcons() {}
    public static final Icon FACTORIO = IconLoader.getIcon("/icons/factorio.png", FactorioIcons.class);
}
