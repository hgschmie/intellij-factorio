package de.softwareforge.factorio;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.tang.intellij.lua.lang.LuaLanguageLevel;
import com.tang.intellij.lua.lang.LuaLanguageLevelProvider;
import java.nio.file.Path;

/** Mirrors the managed server's Lua 5.2 runtime for native PSI parsing. */
public final class FactorioLuaLanguageLevel implements LuaLanguageLevelProvider {
    @Override public LuaLanguageLevel getLanguageLevel(Project project, VirtualFile file) {
        if (project.isDisposed()) return null;
        // Content roots are available before asynchronous server/module reconciliation.
        var module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(file);
        if (module != null && FactorioWizard.MODULE_TYPE.equals(ModuleType.get(module).getId())) return LuaLanguageLevel.LUA52;
        if (FactorioModules.get(project).containing(Path.of(file.getPath())) != null) return LuaLanguageLevel.LUA52;
        return null;
    }
}
