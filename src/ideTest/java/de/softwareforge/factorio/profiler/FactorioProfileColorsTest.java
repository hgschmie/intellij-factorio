package de.softwareforge.factorio.profiler;

import com.intellij.profiler.ui.flamechart.*;
import com.intellij.testFramework.HeavyPlatformTestCase;
import de.softwareforge.factorio.FactorioProfile;
import de.softwareforge.factorio.ModDiscovery;
import java.awt.Color;
import java.nio.file.*;
import java.util.*;

public class FactorioProfileColorsTest extends HeavyPlatformTestCase {
    private static FactorioProfile.Frame frame(String name, String source) {
        return new FactorioProfile.Frame(name, source, source.isEmpty() || source.equals("=[C]") ? -1 : 10);
    }
    private static FactorioProfile profile(List<FactorioProfile.Frame> frames) {
        var nodes = new ArrayList<FactorioProfile.Node>();
        for (int i = 0; i < frames.size(); i++) nodes.add(new FactorioProfile.Node(i, frames.get(i), List.of(), 1, 1));
        return new FactorioProfile(nodes, frames.size());
    }
    public void testColorsFollowStableModAndFileIdentities() {
        var root = Path.of("/test/mod folder");
        var mod = new ModDiscovery.Mod("LTN", root, "LogisticTrainNetwork", "1.0.0", "2.1");
        var relative = frame("first", "@__LogisticTrainNetwork__/script/dispatcher.lua");
        var absolute = frame("second", root.resolve("script/dispatcher.lua").toString());
        var uri = frame("third", root.resolve("script/dispatcher.lua").toUri().toString());
        var windows = frame("fourth", "__LogisticTrainNetwork__\\script\\dispatcher.lua");
        var sameFile = new FactorioProfile.Frame("renamed", relative.source(), 400);
        var anotherFile = frame("first", "__LogisticTrainNetwork__/script/tools.lua");
        var anotherMod = frame("first", "__fiber-optics__/script/dispatcher.lua");
        var external = frame("first", "/test/mod folder-other/script/dispatcher.lua");
        var frames = List.of(relative, absolute, uri, windows, sameFile, anotherFile, anotherMod, external);
        var styles = FactorioProfileColors.create(profile(frames), List.of(mod), () -> {});
        var scheme = styles.get(relative).colors();
        for (var alias : List.of(absolute, uri, windows, sameFile)) assertSame(scheme, styles.get(alias).colors());
        assertTrue(styles.get(relative).description().contains("Project Lua — LogisticTrainNetwork"));
        assertFalse(styles.get(external).description().contains("Project"));
        assertFalse(scheme.color(ColorType.BACKGROUND).equals(styles.get(anotherFile).colors().color(ColorType.BACKGROUND)));
        assertFalse(scheme.color(ColorType.BACKGROUND).equals(styles.get(anotherMod).colors().color(ColorType.BACKGROUND)));

        var reordered = new ArrayList<>(frames);
        Collections.reverse(reordered);
        reordered.add(frame("unrelated", "__new-mod__/control.lua"));
        var moved = new ModDiscovery.Mod("Other IDE module name", Path.of("/different/root"), mod.name(), "2.0.0", "2.1");
        var later = FactorioProfileColors.create(profile(reordered), List.of(moved), () -> {});
        assertEquals(scheme.color(ColorType.BACKGROUND), later.get(relative).colors().color(ColorType.BACKGROUND));
    }

    public void testLightAndDarkContrastAndSearchColors() throws Exception {
        var frames = List.of(frame("(root)", ""), frame("(hook: line)", ""), frame("on_tick (ID 0)", ""),
                frame("pairs", "=[C]"), frame("dispatch", "__LogisticTrainNetwork__/script/dispatcher.lua"),
                frame("tools", "__LogisticTrainNetwork__/script/tools.lua"), frame("cache", "__LogisticTrainNetwork__/script/cache.lua"),
                frame("control", "__fiber-optics__/control.lua"), frame("control", "__inventory-sensor-improved__/control.lua"),
                frame("control", "__electric-locomotives__/control.lua"), frame("control", "__base__/control.lua"));
        var styles = FactorioProfileColors.create(profile(frames), List.of(), () -> {});
        var original = javax.swing.UIManager.getLookAndFeel();
        boolean wasDark = !com.intellij.ui.JBColor.isBright();
        try {
            for (boolean dark : new boolean[]{false, true}) {
                javax.swing.LookAndFeel theme = dark ? new com.intellij.ide.ui.laf.darcula.DarculaLaf() : new com.intellij.ide.ui.laf.IntelliJLaf();
                javax.swing.UIManager.setLookAndFeel(theme);
                com.intellij.ui.JBColor.setDark(dark);
                var picture = new java.awt.image.BufferedImage(1040, 70 + frames.size() * 42, java.awt.image.BufferedImage.TYPE_INT_RGB);
                var graphics = picture.createGraphics();
                graphics.setColor(com.intellij.util.ui.UIUtil.getPanelBackground());
                graphics.fillRect(0, 0, picture.getWidth(), picture.getHeight());
                graphics.setColor(com.intellij.util.ui.UIUtil.getLabelForeground());
                graphics.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 14));
                graphics.drawString("Factorio profile colors — " + theme.getName() + " (normal / hover)", 20, 30);
                var categoryColors = new HashSet<Integer>();
                for (int i = 0; i < frames.size(); i++) {
                    var style = styles.get(frames.get(i));
                    var colors = style.colors();
                    if (i < 4) categoryColors.add(colors.color(ColorType.BACKGROUND).getRGB());
                    for (boolean hover : new boolean[]{false, true}) {
                        Color bg = colors.color(hover ? ColorType.BACKGROUND_HOVER : ColorType.BACKGROUND);
                        Color fg = colors.color(hover ? ColorType.FOREGROUND_HOVER : ColorType.FOREGROUND);
                        assertTrue(theme.getName() + " " + frames.get(i) + " contrast=" + contrast(bg, fg), contrast(bg, fg) >= 4.5);
                        int x = hover ? 525 : 20, y = 50 + i * 42;
                        graphics.setColor(bg); graphics.fillRect(x, y, 495, 34);
                        graphics.setColor(fg); graphics.drawString(frames.get(i).name() + " — " + style.description(), x + 8, y + 22);
                    }
                    assertFalse(colors.color(ColorType.BACKGROUND).equals(colors.color(ColorType.BACKGROUND_HOVER)));
                    assertEquals(colors.color(ColorType.BACKGROUND), colors.color(ColorType.SEARCH_OK));
                    assertEquals(colors.color(ColorType.BACKGROUND_HOVER), colors.color(ColorType.SEARCH_OK_HOVER));
                    for (var type : List.of(ColorType.SEARCH_FAIL, ColorType.SEARCH_FOCUSED_BORDER,
                            ColorType.FOREGROUND_SEARCH_FAIL))
                        assertEquals(FlameChartColors.DEFAULT.INSTANCE.color(type), colors.color(type));
                }
                assertEquals("Runtime, hooks, native and unattributed time must be distinct", 4, categoryColors.size());
                graphics.dispose();
                var output = Path.of(System.getProperty("factorio.test.workspace"), "intellij-factorio/build/reports/ui/profiling-colors-"
                        + (dark ? "dark" : "light") + ".png");
                Files.createDirectories(output.getParent());
                javax.imageio.ImageIO.write(picture, "png", output.toFile());
            }
        } finally { javax.swing.UIManager.setLookAndFeel(original); com.intellij.ui.JBColor.setDark(wasDark); }
    }
    private static double contrast(Color a, Color b) {
        double first = luminance(a), second = luminance(b);
        return (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
    }
    private static double luminance(Color c) {
        return .2126 * linear(c.getRed()) + .7152 * linear(c.getGreen()) + .0722 * linear(c.getBlue());
    }
    private static double linear(int component) {
        double value = component / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }
}
