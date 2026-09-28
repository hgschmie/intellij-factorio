package de.softwareforge.factorio;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.ui.components.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public final class FactorioConfigurable implements Configurable {
    private final Project project;
    private final JComponent panel;
    private final ComboBox<String> mode=new ComboBox<>(new String[]{"AUTO","ENABLED","DISABLED"});
    private final TextFieldWithBrowseButton node,factorio,docs,cli,config;
    private final FactorioForms.Dependencies dependencies;
    // All overrides stay staged until the parent Settings dialog is applied.
    private final DefaultTableModel model=new DefaultTableModel(new Object[]{"Module","Mod folder","Override dependencies","Dependencies","Override package config","Package config"},0) {
        @Override public boolean isCellEditable(int row,int column) { return false; }
    };
    private final JBTable table=new JBTable(model);
    private List<ModDiscovery.Mod> mods=List.of();
    private String initial="";
    private String libraryIgnoreDir=FactorioLibrary.IGNORE_DIR,libraryIgnoreGlobs=FactorioLibrary.IGNORE_GLOBS;
    public FactorioConfigurable(Project project) {
        this.project=project;
        node=FactorioForms.path(project,"Select Node Executable",false,"");
        factorio=FactorioForms.path(project,"Select Factorio Executable",false,"");
        docs=FactorioForms.path(project,"Select API JSON Folder",true,"Detect from Factorio installation");
        cli=FactorioForms.path(project,"Select FMTK CLI",false,"Use bundled toolkit");
        config=FactorioForms.path(project,"Select Package Configuration",false,"Use mod defaults");
        dependencies=new FactorioForms.Dependencies(project);
        mode.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus) {
                return super.getListCellRendererComponent(list,switch(Objects.toString(value,"")) {
                    case "AUTO" -> "Automatic"; case "ENABLED" -> "Enabled"; case "DISABLED" -> "Disabled"; default -> "";
                },index,selected,focus);
            }
        });
        mode.setToolTipText("Automatic enables language services when Factorio mods are detected");
        var form=new FactorioForms.Form();
        form.section("Toolchain");
        form.row("Node",node);form.row("Factorio",factorio);form.row("API docs",docs);form.row("FMTK CLI",cli);
        form.section("Project Defaults");
        form.row("Services",FactorioForms.left(mode));
        form.row("Dependencies",dependencies);
        form.row("Package config",config);
        var exclusions=new JButton("Edit Exclusions…");exclusions.addActionListener(event -> editExclusions());
        form.row("Data library",FactorioForms.left(exclusions));
        form.section("Mod Modules");
        var hint=new JBLabel("Attach mod folders using New Module from Existing Sources.");
        hint.setForeground(com.intellij.util.ui.UIUtil.getContextHelpForeground());form.full(hint);
        for(int column=5;column>=2;column--)table.removeColumn(table.getColumnModel().getColumn(column));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setPreferredScrollableViewportSize(JBUI.size(560,100));
        table.getColumnModel().getColumn(0).setPreferredWidth(JBUI.scale(160));
        table.getColumnModel().getColumn(1).setPreferredWidth(JBUI.scale(400));
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int column) {
                super.getTableCellRendererComponent(table,value,selected,focus,row,column);setIcon(FactorioIcons.FACTORIO);return this;
            }
        });
        table.getEmptyText().setText("No Factorio mod modules detected");
        form.full(new JBScrollPane(table));
        var edit=new JButton("Edit Module Overrides…");edit.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(event -> edit.setEnabled(table.getSelectedRow()>=0 && FactorioModules.get(project).module(mods.get(table.getSelectedRow()))!=null));
        edit.addActionListener(event -> editModule());
        table.addMouseListener(new MouseAdapter(){ @Override public void mouseClicked(MouseEvent e){if(e.getClickCount()==2 && edit.isEnabled())editModule();} });
        form.full(FactorioForms.left(edit));
        var scroll=new JBScrollPane(form.topAligned());scroll.setBorder(JBUI.Borders.empty());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);panel=scroll;
    }
    private void editModule() {
        int row=table.getSelectedRow();if(row<0)return;
        var overrideDependencies=new JBCheckBox("Override project dependencies",(Boolean)model.getValueAt(row,2));
        var paths=new FactorioForms.Dependencies(project);paths.setText((String)model.getValueAt(row,3));
        paths.setEnabled(overrideDependencies.isSelected());
        overrideDependencies.addActionListener(event -> paths.setEnabled(overrideDependencies.isSelected()));
        var overrideConfig=new JBCheckBox("Override project package config",(Boolean)model.getValueAt(row,4));
        var packageFile=FactorioForms.path(project,"Select Module Package Configuration",false,"Use mod defaults");
        packageFile.setText((String)model.getValueAt(row,5));packageFile.setEnabled(overrideConfig.isSelected());
        overrideConfig.addActionListener(event -> packageFile.setEnabled(overrideConfig.isSelected()));
        var form=new FactorioForms.Form();form.full(overrideDependencies);form.full(paths);
        form.full(overrideConfig);form.full(packageFile);
        var dialog=new DialogWrapper(project) {
            {setTitle("Module Overrides: "+mods.get(row).name());init();}
            @Override protected JComponent createCenterPanel(){return form;}
        };
        if(dialog.showAndGet()) {
            model.setValueAt(overrideDependencies.isSelected(),row,2);model.setValueAt(paths.getText(),row,3);
            model.setValueAt(overrideConfig.isSelected(),row,4);model.setValueAt(packageFile.getText(),row,5);
        }
    }
    private void editExclusions() {
        var directories=new JBTextArea(libraryIgnoreDir,9,48);
        var globs=new JBTextArea(libraryIgnoreGlobs,5,48);
        var form=new FactorioForms.Form();
        var pathsLabel=new JBLabel("Ignored paths relative to Factorio data:");pathsLabel.setLabelFor(directories);
        form.full(pathsLabel);form.full(new JBScrollPane(directories));
        var globsLabel=new JBLabel("Ignored globs relative to Factorio data:");globsLabel.setLabelFor(globs);
        form.full(globsLabel);form.full(new JBScrollPane(globs));
        var defaults=new JButton("Restore Defaults");defaults.addActionListener(event -> {directories.setText(FactorioLibrary.IGNORE_DIR);globs.setText(FactorioLibrary.IGNORE_GLOBS);});
        form.full(FactorioForms.left(defaults));
        var dialog=new DialogWrapper(project) {
            {setTitle("Factorio Data Library Exclusions");init();}
            @Override protected JComponent createCenterPanel(){return form;}
        };
        if(dialog.showAndGet()){libraryIgnoreDir=directories.getText();libraryIgnoreGlobs=globs.getText();}
    }
    @Override public String getDisplayName(){return "Factorio Modding Tool Kit";}
    @Override public JComponent createComponent(){reset();return panel;}
    private String state(){return PathsAndMods.JSON.toJson(List.of(mode.getSelectedItem(),node.getText(),factorio.getText(),docs.getText(),cli.getText(),config.getText(),dependencies.getText(),libraryIgnoreDir,libraryIgnoreGlobs,model.getDataVector()));}
    @Override public boolean isModified(){return !initial.equals(state());}
    @Override public void apply(){
        var s=FactorioSettings.get(project);s.serviceMode=(String)mode.getSelectedItem();s.enabled="ENABLED".equals(s.serviceMode);
        s.node=node.getText().trim();s.factorio=factorio.getText().trim();s.apiDocs=docs.getText().trim();s.cli=cli.getText().trim();s.packageConfig=config.getText().trim();s.dependencies=dependencies.getText();
        s.libraryIgnoreDir=libraryIgnoreDir;s.libraryIgnoreGlobs=libraryIgnoreGlobs;
        for(int i=0;i<mods.size();i++){
            var module=FactorioModules.get(project).module(mods.get(i));if(module==null)continue;
            var m=FactorioModuleSettings.get(module);m.overrideDependencies=(Boolean)model.getValueAt(i,2);m.dependencies=(String)model.getValueAt(i,3);m.overridePackageConfig=(Boolean)model.getValueAt(i,4);m.packageConfig=((String)model.getValueAt(i,5)).trim();
        }
        initial=state();FactorioModules.get(project).schedule();
    }
    @Override public void reset(){
        var s=FactorioSettings.get(project);mode.setSelectedItem(s.serviceMode);node.setText(s.node);factorio.setText(s.factorio);docs.setText(s.apiDocs);cli.setText(s.cli);config.setText(s.packageConfig);dependencies.setText(s.dependencies);
        libraryIgnoreDir=s.libraryIgnoreDir;libraryIgnoreGlobs=s.libraryIgnoreGlobs;
        table.clearSelection();mods=FactorioModules.get(project).mods();model.setRowCount(0);
        for(var mod:mods){var module=FactorioModules.get(project).module(mod);var m=module==null?new FactorioModuleSettings.Data():FactorioModuleSettings.get(module);model.addRow(new Object[]{mod.module(),mod.root().toString(),m.overrideDependencies,m.dependencies,m.overridePackageConfig,m.packageConfig});}
        initial=state();
    }
}
