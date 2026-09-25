package de.softwareforge.factorio;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class FactorioConfigurable implements Configurable {
    private final Project project;
    private final JPanel panel=new JPanel(new BorderLayout(8,8)), fields=new JPanel(new GridLayout(0,2,6,6));
    private final JComboBox<String> mode=new JComboBox<>(new String[]{"AUTO","ENABLED","DISABLED"});
    private final JTextField node=field("Node executable"),factorio=field("Factorio executable"),docs=field("API JSON directory (blank: detect)"),cli=field("FMTK CLI override (blank: bundled)"),config=field("Default package config file");
    private final JTextArea dependencies=new JTextArea(3,40);
    private final DefaultTableModel model=new DefaultTableModel(new Object[]{"Module","Mod root","Override dependencies","Dependency paths (one per line)","Override package config","Package config"},0){
        @Override public Class<?> getColumnClass(int col){return col==2||col==4?Boolean.class:String.class;}
        @Override public boolean isCellEditable(int row,int col){return col>=2&&FactorioModules.get(project).module(mods.get(row))!=null;}
    };
    private final JTable table=new JTable(model);
    private List<ModDiscovery.Mod> mods=List.of();
    private String initial="";
    public FactorioConfigurable(Project project){
        this.project=project;
        table.getColumnModel().getColumn(3).setCellEditor(new DependencyEditor());
        fields.add(new JLabel("Services: AUTO enables detected mod modules"));fields.add(mode);
        fields.add(new JLabel("Default dependency paths (one per line)"));fields.add(new JScrollPane(dependencies));
        panel.add(fields,BorderLayout.NORTH);
        var modules=new JPanel(new BorderLayout(4,4));
        modules.add(new JLabel("Detected mods — attach folders using New Module from Existing Sources"),BorderLayout.NORTH);
        modules.add(new JScrollPane(table),BorderLayout.CENTER); panel.add(modules,BorderLayout.CENTER);
    }
    /** Keep multiline dependency paths editable without opening a second settings dialog. */
    private static final class DependencyEditor extends AbstractCellEditor implements javax.swing.table.TableCellEditor {
        private final JTextArea text=new JTextArea(4,30);
        private final JScrollPane scroll=new JScrollPane(text);
        @Override public Component getTableCellEditorComponent(JTable table,Object value,boolean selected,int row,int column) {
            text.setText(Objects.toString(value,""));
            table.setRowHeight(row,Math.max(table.getRowHeight(),80));
            return scroll;
        }
        @Override public Object getCellEditorValue(){return text.getText();}
    }
    private JTextField field(String title){fields.add(new JLabel(title));var f=new JTextField(40);fields.add(f);return f;}
    @Override public String getDisplayName(){return "Factorio Modding Tool Kit";}
    @Override public JComponent createComponent(){reset();return panel;}
    private String state(){return PathsAndMods.JSON.toJson(List.of(mode.getSelectedItem(),node.getText(),factorio.getText(),docs.getText(),cli.getText(),config.getText(),dependencies.getText(),model.getDataVector()));}
    @Override public boolean isModified(){return table.isEditing()||!initial.equals(state());}
    @Override public void apply(){
        if(table.isEditing())table.getCellEditor().stopCellEditing();
        var s=FactorioSettings.get(project);s.serviceMode=(String)mode.getSelectedItem();s.enabled="ENABLED".equals(s.serviceMode);
        s.node=node.getText().trim();s.factorio=factorio.getText().trim();s.apiDocs=docs.getText().trim();s.cli=cli.getText().trim();s.packageConfig=config.getText().trim();s.dependencies=dependencies.getText();
        for(int i=0;i<mods.size();i++){
            var module=FactorioModules.get(project).module(mods.get(i));if(module==null)continue;
            var m=FactorioModuleSettings.get(module);m.overrideDependencies=(Boolean)model.getValueAt(i,2);m.dependencies=(String)model.getValueAt(i,3);m.overridePackageConfig=(Boolean)model.getValueAt(i,4);m.packageConfig=((String)model.getValueAt(i,5)).trim();
        }
        initial=state();FactorioModules.get(project).schedule();
    }
    @Override public void reset(){
        var s=FactorioSettings.get(project);mode.setSelectedItem(s.serviceMode);node.setText(s.node);factorio.setText(s.factorio);docs.setText(s.apiDocs);cli.setText(s.cli);config.setText(s.packageConfig);dependencies.setText(s.dependencies);
        mods=FactorioModules.get(project).mods();model.setRowCount(0);
        for(var mod:mods){var module=FactorioModules.get(project).module(mod);var m=module==null?new FactorioModuleSettings.Data():FactorioModuleSettings.get(module);model.addRow(new Object[]{mod.module(),mod.root().toString(),m.overrideDependencies,m.dependencies,m.overridePackageConfig,m.packageConfig});}
        initial=state();
    }
}
