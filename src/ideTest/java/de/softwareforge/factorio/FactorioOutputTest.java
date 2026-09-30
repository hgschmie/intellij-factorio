package de.softwareforge.factorio;

import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.execution.ui.ConsoleViewContentType;

public class FactorioOutputTest extends HeavyPlatformTestCase {
    public void testConsoleLifecycleAndCancellation() {
        FactorioSettings.get(getProject()).serviceMode = "DISABLED";
        // Platform tests do not initialize declarative tool windows automatically.
        ToolWindowManager.getInstance(getProject()).registerToolWindow(
            com.intellij.openapi.wm.RegisterToolWindowTask.closable("Factorio", FactorioIcons.FACTORIO));
        var output = FactorioOutput.open(getProject(), "Test execution");
        var indicator = new EmptyProgressIndicator();
        output.start(indicator);
        output.print("Visible execution output\n", ConsoleViewContentType.NORMAL_OUTPUT);
        var manager = ToolWindowManager.getInstance(getProject()).getToolWindow("Factorio").getContentManager();
        var content = manager.getSelectedContent();
        assertNotNull(content);
        assertEquals("Test execution", content.getDisplayName());
        manager.removeContent(content, true);
        assertTrue(indicator.isCanceled());
        output.finish("Cancelled", false);
        ToolWindowManager.getInstance(getProject()).unregisterToolWindow("Factorio");
    }
}
