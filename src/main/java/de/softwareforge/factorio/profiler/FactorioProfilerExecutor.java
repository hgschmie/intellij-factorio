package de.softwareforge.factorio.profiler;

import com.intellij.execution.Executor;
import com.intellij.icons.AllIcons;
import de.softwareforge.factorio.FactorioProfiling;
import org.jetbrains.annotations.NotNull;

/** A language-specific choice in IntelliJ's standard Profile executor group. */
public final class FactorioProfilerExecutor extends Executor {
    @Override public @NotNull String getId() { return FactorioProfiling.EXECUTOR_ID; }
    @Override public @NotNull String getContextActionId() { return "FactorioLuaProfilerContextAction"; }
    @Override public @NotNull String getActionName() { return "Profile with Factorio Lua"; }
    @Override public @NotNull String getStartActionText() { return "Profile with Factorio Lua"; }
    @Override public @NotNull String getDescription() { return "Run Factorio and record Lua execution timings"; }
    @Override public @NotNull String getToolWindowId() { return "Run"; }
    @Override public javax.swing.Icon getToolWindowIcon() { return AllIcons.Toolwindows.ToolWindowRun; }
    @Override public @NotNull javax.swing.Icon getIcon() { return AllIcons.Actions.Profile; }
    @Override public javax.swing.Icon getDisabledIcon() { return null; }
    @Override public String getHelpId() { return null; }
}
