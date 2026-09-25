package de.softwareforge.factorio;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import javax.swing.*;
import java.awt.*;

public final class FactorioConfigurable implements Configurable {
    private final Project project;
    private final JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
    private final JCheckBox enabled = new JCheckBox("Enable Factorio services in this project");
    private final JTextField node = field("Node executable"), factorio = field("Factorio executable"), mod = field("Active mod directory"), docs = field("API JSON directory (blank: detect from Factorio)"), cli = field("FMTK CLI override (blank: bundled)"), config = field("FMTK package config file (optional)");
    private final JTextArea dependencies = new JTextArea(4, 50);
    public FactorioConfigurable(Project project) { this.project = project; panel.add(enabled, 0); panel.add(new JLabel("Dependency mod directories or ZIPs (one absolute path per line)")); panel.add(new JScrollPane(dependencies)); }
    private JTextField field(String title) { panel.add(new JLabel(title)); var field = new JTextField(50); panel.add(field); return field; }
    @Override public String getDisplayName() { return "Factorio Modding Tool Kit"; }
    @Override public JComponent createComponent() { reset(); return panel; }
    @Override public boolean isModified() {
        var s = FactorioSettings.get(project);
        return enabled.isSelected()!=s.enabled || !node.getText().equals(s.node) || !factorio.getText().equals(s.factorio) || !mod.getText().equals(s.activeMod) || !docs.getText().equals(s.apiDocs) || !cli.getText().equals(s.cli) || !config.getText().equals(s.packageConfig) || !dependencies.getText().equals(s.dependencies);
    }
    @Override public void apply() {
        var s=FactorioSettings.get(project); s.enabled=enabled.isSelected(); s.node=node.getText().trim(); s.factorio=factorio.getText().trim(); s.activeMod=mod.getText().trim(); s.apiDocs=docs.getText().trim(); s.cli=cli.getText().trim(); s.packageConfig=config.getText().trim(); s.dependencies=dependencies.getText();
        var manager = com.redhat.devtools.lsp4ij.LanguageServerManager.getInstance(project);
        if (s.enabled) manager.start(FactorioLanguageServer.ID,new com.redhat.devtools.lsp4ij.LanguageServerManager.StartOptions().setForceRestart(true));
        else manager.stop(FactorioLanguageServer.ID);
    }
    @Override public void reset() {
        var s=FactorioSettings.get(project); enabled.setSelected(s.enabled); node.setText(s.node); factorio.setText(s.factorio); mod.setText(s.activeMod); docs.setText(s.apiDocs); cli.setText(s.cli); config.setText(s.packageConfig); dependencies.setText(s.dependencies);
        // Suggest a mod root in the form only; Cancel must leave persisted settings alone.
        if (s.activeMod.isBlank() && java.nio.file.Files.exists(Toolkit.root(project).resolve("info.json"))) mod.setText(Toolkit.root(project).toString());
    }
}
