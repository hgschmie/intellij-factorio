package de.softwareforge.factorio;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import java.nio.file.*;
import java.util.*;

public final class Toolkit {
    private Toolkit() {}
    public static Path root(Project p) { return Path.of(Objects.requireNonNull(p.getBasePath(), "Open a project directory first")); }
    public static Path cli(Project p) {
        String override = FactorioSettings.get(p).cli;
        return override.isBlank() ? PluginManagerCore.getPlugin(PluginId.getId("de.softwareforge.factorio")).getPluginPath().resolve("fmtk/fmtk-cli.js") : Path.of(override);
    }
    public static List<String> command(Project p, String... args) {
        var result = new ArrayList<String>(); result.add(FactorioSettings.get(p).node); result.add(cli(p).toString()); result.addAll(List.of(args)); return result;
    }
    public static ModDiscovery.Mod selectMod(com.intellij.openapi.actionSystem.AnActionEvent event) {
        Project p=Objects.requireNonNull(event.getProject());
        var registry=FactorioModules.get(p);
        var selected=event.getData(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE);
        if(selected!=null){var mod=registry.containing(Path.of(selected.getPath()));if(mod!=null)return mod;}
        var module=event.getData(com.intellij.openapi.actionSystem.LangDataKeys.MODULE);
        if(module!=null){var matches=registry.mods().stream().filter(m->m.module().equals(module.getName())).toList();if(matches.size()==1)return matches.getFirst();}
        var editor=com.intellij.openapi.fileEditor.FileEditorManager.getInstance(p).getSelectedFiles();
        if(editor.length==1){var mod=registry.containing(Path.of(editor[0].getPath()));if(mod!=null)return mod;}
        if(registry.mods().size()==1)return registry.mods().getFirst();
        if(registry.mods().isEmpty())throw new IllegalStateException("No valid Factorio mod module. Import a mod folder containing info.json.");
        String[] choices=registry.mods().stream().map(Object::toString).toArray(String[]::new);
        int index=com.intellij.openapi.ui.Messages.showChooseDialog(p,"Choose the mod for this action","Factorio Mod",null,choices,choices[0]);
        return index<0?null:registry.mods().get(index);
    }
}
