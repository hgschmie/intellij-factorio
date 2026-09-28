package de.softwareforge.factorio;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.IconUtil;
import javax.swing.Icon;

/** Original Factorio thumbnail, scaled by the platform for standard UI rows. */
public final class FactorioIcons {
    private FactorioIcons() {}
    private static final Icon SOURCE = IconLoader.getIcon("/icons/factorio.png", FactorioIcons.class);
    public static final Icon FACTORIO = IconUtil.scale(SOURCE, null, 16f / SOURCE.getIconWidth());
}
