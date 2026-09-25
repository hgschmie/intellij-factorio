package de.softwareforge.factorio;

import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class FactorioConfigurableTest {
    @TempDir Path root;

    @Test void openingAndEditingFormDoesNotEnableServicesOrPersistSuggestedMod() throws Exception {
        Files.writeString(root.resolve("info.json"), "{}");
        var settings = new FactorioSettings();
        Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(), new Class<?>[]{Project.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getService" -> settings;
            case "getBasePath" -> root.toString();
            default -> throw new UnsupportedOperationException(method.getName());
        });
        SwingUtilities.invokeAndWait(() -> {
            var form = new FactorioConfigurable(project);
            var panel = (JPanel) form.createComponent();
            assertFalse(((JCheckBox)panel.getComponent(0)).isSelected());
            assertTrue(form.isModified(), "Suggested mod root is staged for Apply");
            ((JCheckBox)panel.getComponent(0)).setSelected(true);
            assertFalse(settings.getState().enabled);
            assertEquals("", settings.getState().activeMod);
            form.reset();
            assertFalse(((JCheckBox)panel.getComponent(0)).isSelected());
            assertEquals("", settings.getState().activeMod);
        });
    }
}
