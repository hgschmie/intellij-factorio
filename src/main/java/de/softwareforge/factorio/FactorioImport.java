package de.softwareforge.factorio;

import com.intellij.openapi.module.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.Icon;

/** Loaded only when IntelliJ's Java UI (the existing-sources wizard) is present. */
public final class FactorioImport extends ProjectImportProvider {
    public FactorioImport(){super(new Builder());}
    @Override public String getName(){return "Factorio Mod";}
    @Override public boolean canImport(VirtualFile file,Project project){
        Path root=Path.of(file.isDirectory()?file.getPath():file.getParent().getPath());
        try { ModDiscovery.read("import",root);return file.isDirectory()||file.getName().equals("info.json"); }catch(Exception e){return false;}
    }
    @Override public String getPathToBeImported(VirtualFile file){return file.isDirectory()?file.getPath():file.getParent().getPath();}
    @Override public boolean canImportModule(){return true;}
    @Override public String getFileSample(){return "Factorio mod folder or info.json";}
    public static final class Builder extends ProjectImportBuilder<String> {
        @Override public String getName(){return "Factorio Mod";}
        @Override public Icon getIcon(){return FactorioIcons.FACTORIO;}
        @Override public boolean isMarked(String item){return true;}
        @Override public void setOpenProjectSettingsAfter(boolean open){}
        @Override public boolean validate(Project current,Project target){
            try { ModDiscovery.read("import",root());return true; }
            catch(Exception e){if(target!=null)FactorioActions.report(target,"Cannot import Factorio mod: "+e.getMessage(),true);return false;}
        }
        private Path root(){Path p=Path.of(getFileToImport());return p.getFileName().toString().equals("info.json")?p.getParent():p;}
        @Override public List<Module> commit(Project project,ModifiableModuleModel model,ModulesProvider provider,ModifiableArtifactModel artifacts){
            try {
                var mod=ModDiscovery.read("import",root());
                for(var m:ModuleManager.getInstance(project).getModules()) for(var r:com.intellij.openapi.roots.ModuleRootManager.getInstance(m).getContentRoots())
                    if(Path.of(r.getPath()).toRealPath().equals(mod.root()))return List.of(m);
                String name=mod.name();int suffix=2;
                while(ModuleManager.getInstance(project).findModuleByName(name)!=null || (model!=null&&model.findModuleByName(name)!=null)) name=mod.name()+"-"+suffix++;
                var builder=new FactorioWizard.Builder();builder.importing=true;builder.setName(name);builder.setContentEntryPath(mod.root().toString());
                Path modules=Toolkit.root(project).resolve(".idea/modules");Files.createDirectories(modules);
                builder.setModuleFilePath(modules.resolve(name+".iml").toString());
                return builder.commit(project,model,provider);
            } catch(Exception e){FactorioActions.report(project,"Cannot import Factorio mod: "+e.getMessage(),true);return List.of();}
        }
    }
}
