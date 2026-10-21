package de.softwareforge.factorio.profiler;

import com.intellij.profiler.ui.flamechart.*;
import com.intellij.ui.JBColor;
import de.softwareforge.factorio.FactorioProfile;
import de.softwareforge.factorio.ModDiscovery;
import java.awt.Color;
import java.util.*;

/** Stable presentation, prepared during import; painting does no source or project lookup. */
final class FactorioProfileColors {
    record Style(String description, FlameChartColorScheme colors) {}
    private static final int[] HUES = {0, 20, 40, 60, 80, 100, 120, 140, 160, 180, 260, 280, 300, 320, 340};
    private static final int FILE_SHADES = 7;
    private static final Style ROOT = fixed("Time outside recorded Lua stacks", 0xE1E4E8, 0x3B3E45);
    private static final Style PAUSE = fixed("Recording paused", 0xE1E4E8, 0x3B3E45);
    private static final Style HOOK = fixed("Profiler hook", 0xD8DCE8, 0x434858);
    private static final Style RUNTIME = fixed("Event / runtime", 0xDFD1EF, 0x514062);
    private static final Style NATIVE = fixed("Native", 0xB8D8F4, 0x305778);

    static Map<FactorioProfile.Frame, Style> create(FactorioProfile profile, List<ModDiscovery.Mod> mods,
                                                  Runnable checkCancelled) {
        return create(profile, new FactorioProfileModules(profile, mods, checkCancelled), checkCancelled);
    }

    static Map<FactorioProfile.Frame, Style> create(FactorioProfile profile, FactorioProfileModules modules,
                                                  Runnable checkCancelled) {
        var sources = new HashMap<FactorioProfileModules.Source, Style>();
        var styles = new HashMap<FactorioProfile.Frame, Style>();
        for (var node : profile.nodes()) {
            checkCancelled.run();
            var frame = node.frame();
            if (styles.containsKey(frame)) continue;
            Style style = builtin(frame);
            if (style == null) {
                var source = modules.source(frame);
                style = sources.computeIfAbsent(source, FactorioProfileColors::lua);
            }
            styles.put(frame, style);
        }
        return Map.copyOf(styles);
    }

    /** For standalone frames without import metadata; never consults source files. */
    static Style fallback(FactorioProfile.Frame frame) {
        var style = builtin(frame);
        return style != null ? style : lua(new FactorioProfileModules.Source("", frame.source(), false));
    }

    private static Style builtin(FactorioProfile.Frame frame) {
        if (frame.nativeCall()) return NATIVE;
        if (!frame.source().isBlank()) return null;
        return frame.name().equals("(root)") ? ROOT : frame.name().equals("(pause)") ? PAUSE
                : frame.name().startsWith("(hook:") ? HOOK : RUNTIME;
    }

    private static Style lua(FactorioProfileModules.Source source) {
        // Hash the stable mod identity, never its position in the recording.
        // Leave the blue range primarily to native calls. Files vary within
        // their mod's hue family, independent of function name or line number.
        int hueIndex = Math.min(HUES.length - 1, (int)(fraction(source.mod().isEmpty() ? source.file() : source.mod()) * HUES.length));
        int shadeIndex = Math.min(FILE_SHADES - 1, (int)(fraction(source.file()) * FILE_SHADES));
        float hue = HUES[hueIndex];
        float variation = (float)shadeIndex / (FILE_SHADES - 1) - .5f;
        hue = ((hue + variation * 24 + 360) % 360) / 360;
        var background = new JBColor(Color.getHSBColor(hue, .30f + variation * .16f, .94f + variation * .06f),
                Color.getHSBColor(hue, .58f + variation * .14f, .35f + variation * .06f));
        String description = source.mod().isEmpty() ? "Lua / game library"
                : (source.project() ? "Project Lua — " : "Lua mod — ") + source.mod();
        return new Style(description, new Scheme(background, "lua." + hueIndex + "." + shadeIndex));
    }

    private static float fraction(String value) {
        // Mix Java's stable string hash so similar mod/file names spread out.
        int hash = value.hashCode();
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        hash *= 0x846ca68b;
        hash ^= hash >>> 16;
        return (hash & 0x7fffffff) / 2147483648f;
    }

    private static Style fixed(String description, int light, int dark) {
        return new Style(description, new Scheme(new JBColor(light, dark), "fixed." + light + "." + dark));
    }

    private static Color blend(Color from, Color to, float amount) {
        return new Color(Math.round(from.getRed() * (1 - amount) + to.getRed() * amount),
                Math.round(from.getGreen() * (1 - amount) + to.getGreen() * amount),
                Math.round(from.getBlue() * (1 - amount) + to.getBlue() * amount));
    }

    private static final class Scheme extends FlameChartColorScheme {
        private final JBColor background;
        private final JBColor hover;
        private final String prefix;
        Scheme(JBColor background, String key) {
            this.background = background;
            this.hover = JBColor.lazy(() -> blend(background, JBColor.isBright() ? Color.BLACK : Color.WHITE, .08f));
            // FlameChartColorScheme caches produced colors in UIManager by prefix.
            // Distinct palette entries need distinct keys, but the palette must be
            // bounded so opening more recordings cannot grow that global cache forever.
            this.prefix = "FactorioLua." + key;
        }
        @Override public String getPrefix() { return prefix; }
        private JBColor foreground(ColorType backgroundType) {
            return JBColor.lazy(() -> {
                Color color = color(backgroundType);
                double luminance = .2126 * linear(color.getRed()) + .7152 * linear(color.getGreen()) + .0722 * linear(color.getBlue());
                // Choose the higher-contrast foreground, including after a theme switch.
                return luminance > .179 ? Color.BLACK : Color.WHITE;
            });
        }
        private static double linear(int channel) {
            double value = channel / 255.0;
            return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
        }
        @Override protected JBColor produce(ColorType type) {
            var fallback = FlameChartColors.DEFAULT.INSTANCE;
            return switch (type) {
                case BACKGROUND, SEARCH_OK -> background;
                case BACKGROUND_HOVER, BACKGROUND_HOVER_GROUP, SEARCH_OK_HOVER -> hover;
                case FOREGROUND -> foreground(ColorType.BACKGROUND);
                case FOREGROUND_HOVER -> foreground(ColorType.BACKGROUND_HOVER);
                case FOREGROUND_INACTIVE -> foreground(ColorType.BACKGROUND_INACTIVE);
                case FOREGROUND_HOVER_INACTIVE -> foreground(ColorType.BACKGROUND_HOVER_INACTIVE);
                case BACKGROUND_INACTIVE, BACKGROUND_HOVER_INACTIVE, BACKGROUND_HOVER_INACTIVE_GROUP ->
                        JBColor.lazy(() -> blend(type == ColorType.BACKGROUND_INACTIVE ? background : hover,
                                fallback.color(type), .55f));
                case SEARCH_OK_INACTIVE -> color(ColorType.BACKGROUND_INACTIVE);
                case SEARCH_OK_HOVER_INACTIVE -> color(ColorType.BACKGROUND_HOVER_INACTIVE);
                // Matched frames keep their palette; nonmatches dim and focused
                // results retain IntelliJ's border and search-failure treatment.
                default -> fallback.color(type);
            };
        }
    }
}
