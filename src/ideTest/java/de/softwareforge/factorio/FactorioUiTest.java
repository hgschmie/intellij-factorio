package de.softwareforge.factorio;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.openapi.actionSystem.ActionManager;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;

/** Real platform controls; persistent settings must change only when Apply is called. */
public class FactorioUiTest extends HeavyPlatformTestCase {
    public void testCompactSettingsBrowseControlsAndStagedValues() throws Exception {
        var settings=FactorioSettings.get(getProject());settings.serviceMode="DISABLED";
        String oldNode=settings.node;
        var ui=new FactorioConfigurable(getProject());var component=ui.createComponent();
        assertFalse(ui.isModified());
        var node=field(component,"Node:");
        assertTrue(node.getParent() instanceof TextFieldWithBrowseButton);
        node.setText("/edited/node");assertTrue(ui.isModified());assertEquals(oldNode,settings.node);
        ui.reset();assertFalse(ui.isModified());assertEquals(oldNode,node.getText());
        var config=field(component,"Package config:");config.setText("/project/package.json");
        ui.apply();assertEquals("/project/package.json",settings.packageConfig);assertFalse(ui.isModified());
        var path = field(component,"Command PATH:");
        path.setText("/custom/tools:/usr/bin:/bin");
        assertTrue(ui.isModified());assertEquals("",settings.commandPath);
        ui.reset();assertEquals("",path.getText());
        path.setText("/custom/tools:/usr/bin:/bin");ui.apply();
        assertEquals("/custom/tools:/usr/bin:/bin",settings.commandPath);
        assertFalse(ui.isModified());
        settings.commandPath="";ui.reset();
        field(component,"Author name:").setText("Test Author");
        field(component,"Author email:").setText("author@example.com");
        assertEquals("",settings.publishAuthorName);ui.apply();
        assertEquals("Test Author",settings.publishAuthorName);
        assertEquals("author@example.com",settings.publishAuthorEmail);
        ui.reset();assertEquals("Test Author",field(component,"Author name:").getText());
        assertEquals(5,children(component,TextFieldWithBrowseButton.class).size());
        render(component,"settings.png",760,850);
        for(var browse:children(component,TextFieldWithBrowseButton.class)) {
            assertTrue("Browse button is missing",children(browse,AbstractButton.class).stream().anyMatch(Component::isVisible));
            assertTrue("Single-line field stretched vertically",browse.getHeight()<=browse.getPreferredSize().height+2);
        }
        for(var label:children(component,JLabel.class)) {
            if(label.getLabelFor() instanceof JTextField input) {
                var left=SwingUtilities.convertPoint(label,label.getWidth(),0,component);
                var right=SwingUtilities.convertPoint(input,0,0,component);
                assertTrue("Label/field gap too large for "+label.getText(),right.x-left.x<80);
            }
        }
    }
    public void testRunConfigurationBrowsersAndRoundTrip() throws Exception {
        FactorioSettings.get(getProject()).serviceMode="DISABLED";
        var type=new FactorioDebug.Type();
        var configuration=new FactorioDebug.Configuration(getProject(),new FactorioDebug.Factory(type),"UI test");
        configuration.setLaunchConfiguration("{\"request\":\"launch\",\"factorioArgs\":[\"--load-game\",\"/save with spaces.zip\",\"--mod-directory\",\"/mods\",\"--config\",\"/config.ini\",\"--disable-audio\"]}");
        var editor=new FactorioDebug.Editor(getProject());editor.resetEditorFrom(configuration);
        var component=editor.createEditor();assertEquals(6,children(component,TextFieldWithBrowseButton.class).size());
        assertEquals("/save with spaces.zip",field(component,"Save ZIP:").getText());
        editor.applyEditorTo(configuration);
        assertTrue(configuration.getLaunchConfiguration().contains("/save with spaces.zip"));
        assertTrue(configuration.getLaunchConfiguration().contains("--disable-audio"));
        var trace=children(component,JCheckBox.class).stream().filter(c -> c.getText().equals("Enable DAP logging")).findFirst().orElseThrow();
        var saveLog=children(component,JCheckBox.class).stream().filter(c -> c.getText().equals("Save DAP log to file")).findFirst().orElseThrow();
        var directory=field(component,"Output folder:");
        assertFalse(trace.isSelected());assertFalse(saveLog.isEnabled());assertFalse(directory.isEnabled());
        trace.doClick();assertTrue(saveLog.isEnabled());assertFalse(directory.isEnabled());
        saveLog.doClick();assertTrue(directory.isEnabled());
        directory.setText(Path.of(System.getProperty("factorio.test.work"),"dap-ui").toString());
        assertEquals(com.redhat.devtools.lsp4ij.settings.ServerTrace.off,configuration.getServerTrace());
        editor.applyEditorTo(configuration);
        assertEquals(com.redhat.devtools.lsp4ij.settings.ServerTrace.verbose,configuration.getServerTrace());
        assertTrue(configuration.getOptions().getDapLogToFile());
        var xml=new org.jdom.Element("configuration");configuration.writeExternal(xml);
        var restored=new FactorioDebug.Configuration(getProject(),new FactorioDebug.Factory(type),"Restored");restored.readExternal(xml);
        assertEquals(configuration.getOptions().getDapLogDirectory(),restored.getOptions().getDapLogDirectory());
        assertEquals(configuration.getServerTrace(),restored.getServerTrace());
        assertTrue(restored.getOptions().getDapLogToFile());
        var copy=(FactorioDebug.Configuration)restored.clone();
        assertEquals(restored.getOptions().getDapLogDirectory(),copy.getOptions().getDapLogDirectory());
        assertTrue(copy.getOptions().getDapLogToFile());
        editor.resetEditorFrom(restored);assertTrue(trace.isSelected());assertTrue(saveLog.isSelected());
        render(component,"run-configuration.png",760,620);
        trace.doClick();assertFalse(saveLog.isEnabled());assertFalse(directory.isEnabled());
        editor.applyEditorTo(restored);assertEquals(com.redhat.devtools.lsp4ij.settings.ServerTrace.off,restored.getServerTrace());
        // A pre-feature configuration and default folder need no migration.
        restored.readExternal(new org.jdom.Element("configuration"));
        assertEquals(com.redhat.devtools.lsp4ij.settings.ServerTrace.off,restored.getServerTrace());
        assertFalse(restored.getOptions().getDapLogToFile());
        assertEquals(FactorioDebug.Options.defaultLogDirectory(),restored.getOptions().logDirectory());
    }
    public void testFactorioIconsAndWizard() throws Exception {
        assertEquals(16,FactorioIcons.FACTORIO.getIconWidth());assertEquals(16,FactorioIcons.FACTORIO.getIconHeight());
        assertSame(FactorioIcons.FACTORIO,new FactorioWizard.Type().getNodeIcon(false));
        assertSame(FactorioIcons.FACTORIO,new FactorioImport.Builder().getIcon());
        assertNotNull(ActionManager.getInstance().getAction("SoftwareforgeFactorio").getTemplatePresentation().getIcon());
        var form=new FactorioWizard.Form(getProject());
        form.name.setText("logistics-sensor");form.title.setText("Logistics Sensor");form.author.setText("Mod author");
        render(form.panel,"new-mod.png",620,330);
    }
    private JTextField field(JComponent component,String title) {
        return children(component,JLabel.class).stream().filter(l -> title.equals(l.getText())).map(JLabel::getLabelFor)
            .filter(JTextField.class::isInstance).map(JTextField.class::cast).findFirst().orElseThrow();
    }
    private static <T> List<T> children(Component component,Class<T> type) {
        var result=new ArrayList<T>();if(type.isInstance(component))result.add(type.cast(component));
        if(component instanceof Container container)for(var child:container.getComponents())result.addAll(children(child,type));
        return result;
    }
    private void render(JComponent component,String name,int width,int height) throws Exception {
        component.setSize(width,height);layout(component);
        var picture=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);var graphics=picture.createGraphics();
        component.printAll(graphics);graphics.dispose();
        Path target=Path.of(System.getProperty("factorio.test.workspace"),"intellij-factorio/build/reports/ui",name);
        Files.createDirectories(target.getParent());ImageIO.write(picture,"png",target.toFile());
    }
    private static void layout(Container parent) {parent.doLayout();for(var child:parent.getComponents())if(child instanceof Container c)layout(c);}
}
