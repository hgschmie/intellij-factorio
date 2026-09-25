package de.softwareforge.factorio;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.redhat.devtools.lsp4ij.dap.console.DAPConsoleView;
import org.jetbrains.annotations.NotNull;

/** Factorio --dap reserves stdout for protocol frames, regardless of chunk boundaries. */
final class FactorioDebugConsole extends DAPConsoleView {
    FactorioDebugConsole(Project project) {
        super(project, GlobalSearchScope.allScope(project), false, true);
    }

    @Override public void print(@NotNull String text, @NotNull ConsoleViewContentType type) {
        // DAPClient renders decoded output events as LOG_*; raw stderr and IDE status
        // also have distinct types. Do not parse JSON here or modify transport input.
        if (type != ConsoleViewContentType.NORMAL_OUTPUT) super.print(text, type);
    }
}
