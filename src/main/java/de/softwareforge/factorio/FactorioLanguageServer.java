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
    @Override public com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures createClientFeatures() {
        return new com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures().setWorkspaceFolderFeature(new com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceFolderFeature() {
            @Override protected com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy createStrategy() {
                return new com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy() {
                    @Override public boolean sendAllFoldersOnInitialization(){return true;}
                    @Override public java.util.List<org.eclipse.lsp4j.WorkspaceFolder> getWorkspaceFolders(Project project, com.redhat.devtools.lsp4ij.client.features.FileUriSupport uri){
                        return FactorioModules.get(project).mods().stream().map(m->new org.eclipse.lsp4j.WorkspaceFolder(m.root().toUri().toString(),m.name())).toList();
                    }
                    @Override public org.eclipse.lsp4j.WorkspaceFolder getWorkspaceFolderForFile(VirtualFile file,Project project,com.redhat.devtools.lsp4ij.client.features.FileUriSupport uri){
                        var mod=FactorioModules.get(project).containing(java.nio.file.Path.of(file.getPath()));
                        return mod==null?null:new org.eclipse.lsp4j.WorkspaceFolder(mod.root().toUri().toString(),mod.name());
                    }
                };
            }
        });
    }
    public static final class Lua implements DocumentMatcher {
        @Override public boolean match(VirtualFile file, Project project) { return FactorioSettings.servicesEnabled(project) && FactorioModules.get(project).containing(java.nio.file.Path.of(file.getPath()))!=null; }
    }
    public static final class Locale implements DocumentMatcher {
        @Override public boolean match(VirtualFile file, Project project) {
            return FactorioSettings.servicesEnabled(project) && FactorioModules.get(project).containing(java.nio.file.Path.of(file.getPath()))!=null && file.getPath().matches(".*/locale/[^/]+/[^/]+\\.cfg");
        }
    }
    public static final class Changelog implements DocumentMatcher {
        @Override public boolean match(VirtualFile file, Project project) { return FactorioSettings.servicesEnabled(project) && FactorioModules.get(project).containing(java.nio.file.Path.of(file.getPath()))!=null && file.getName().equals("changelog.txt"); }
    }
}
