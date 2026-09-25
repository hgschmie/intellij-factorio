package de.softwareforge.factorio;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.LocalFileSystem;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;

public final class FactorioWizard {
    public static final String MODULE_TYPE="SOFTWAREFORGE_FACTORIO_MOD";
    public static final class Type extends ModuleType<Builder> {
        public Type(){ super(MODULE_TYPE); }
        @Override public Builder createModuleBuilder(){ return new Builder(); }
        @Override public String getName(){ return "Factorio Mod"; }
        @Override public String getDescription(){ return "Create a Factorio mod with runtime, settings and data stages"; }
        @Override public Icon getNodeIcon(boolean opened){ return AllIcons.Nodes.Module; }
    }
    public static final class Form {
        final JPanel panel=new JPanel(new GridLayout(0,2,8,8));
        final JTextField name=field("Mod name"),title=field("Title"),author=field("Author"),description=field("Description"),version=field("Factorio version (major.minor)");
        Form(Project project) {
            var settings=project==null?new FactorioSettings.Data():FactorioSettings.get(project);
            try {
                Path docs=settings.apiDocs.isBlank()?Path.of(settings.factorio).getParent().getParent().resolve("doc-html"):Path.of(settings.apiDocs);
                String detected=PathsAndMods.read(docs.resolve("runtime-api.json")).get("application_version").getAsString();
                version.setText(detected.replaceFirst("^(\\d+\\.\\d+).*", "$1"));
            } catch(Exception ignored) { /* Explicit input required if detection fails. */ }
        }
        JTextField field(String label){ panel.add(new JLabel(label)); var f=new JTextField(28);panel.add(f);return f; }
        ModSkeleton.Metadata metadata(){return new ModSkeleton.Metadata(name.getText().trim(),title.getText().trim(),author.getText().trim(),description.getText().trim(),version.getText().trim());}
    }
    public static final class Builder extends ModuleBuilder {
        private ModSkeleton.Metadata metadata;
        boolean importing;
        @Override public ModuleType<?> getModuleType(){return new Type();}
        @Override public String getPresentableName(){return "Factorio Mod";}
        @Override public String getBuilderId(){return MODULE_TYPE;}
        @Override public ModuleWizardStep getCustomOptionsStep(WizardContext context, Disposable parent){
            var form=new Form(context.getProject());
            return new ModuleWizardStep(){
                @Override public JComponent getComponent(){return form.panel;}
                @Override public void updateDataModel(){metadata=form.metadata();}
                @Override public boolean validate() throws ConfigurationException {
                    try {
                        form.metadata().validate();
                        String path=getContentEntryPath();
                        if(path!=null)ModSkeleton.checkDestination(Path.of(path));
                        return true;
                    } catch(Exception e){throw new ConfigurationException(e.getMessage());}
                }
            };
        }
        @Override public void setupRootModel(ModifiableRootModel model) throws ConfigurationException {
            try {
                Path root=Path.of(getContentEntryPath());
                if(importing) ModDiscovery.read(getName(),root);
                else ModSkeleton.createForIde(root,metadata);
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root);
                doAddContentEntry(model);
            } catch(Exception e){throw new ConfigurationException(e.getMessage());}
        }
    }
}
