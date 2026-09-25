package de.softwareforge.factorio;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.*;
import com.redhat.devtools.lsp4ij.server.*;

public final class FactorioLanguageServer implements LanguageServerFactory {
    public static final String ID = "softwareforge.factorio.locale";
    @Override public StreamConnectionProvider createConnectionProvider(Project project) {
        var provider = new OSProcessStreamConnectionProvider();
        provider.setCommandLine(new GeneralCommandLine(Toolkit.command(project,"lsp","--stdio")).withWorkDirectory(Toolkit.root(project).toFile()));
        return provider;
    }
    public static final class Lua implements DocumentMatcher {
        @Override public boolean match(VirtualFile file, Project project) { return FactorioSettings.get(project).enabled; }
    }
    public static final class Locale implements DocumentMatcher {
        @Override public boolean match(VirtualFile file, Project project) {
            return FactorioSettings.get(project).enabled && file.getPath().matches(".*/locale/[^/]+/[^/]+\\.cfg");
        }
    }
    public static final class Changelog implements DocumentMatcher {
        @Override public boolean match(VirtualFile file, Project project) { return FactorioSettings.get(project).enabled && file.getName().equals("changelog.txt"); }
    }
}
