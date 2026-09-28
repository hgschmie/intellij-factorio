package de.softwareforge.factorio;

import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.*;
import com.intellij.util.ui.JBUI;
import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashSet;
import java.util.List;

/** Java forms with natural-height controls and scaled label/row spacing. */
final class FactorioForms {
    private FactorioForms() {}
    static TextFieldWithBrowseButton path(Project project, String title, boolean directory, String placeholder) {
        var text=new JBTextField(36);
        text.getEmptyText().setText(placeholder);
        var field=new TextFieldWithBrowseButton(text);
        var descriptor=directory ? FileChooserDescriptorFactory.createSingleFolderDescriptor() : FileChooserDescriptorFactory.createSingleFileDescriptor();
        descriptor.setTitle(title);
        field.addBrowseFolderListener(project,descriptor);
        field.getTextField().getAccessibleContext().setAccessibleName(title);
        return field;
    }
    static JComponent left(JComponent component) {
        var panel=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));panel.add(component);return panel;
    }
    static final class Form extends JPanel {
        private int row;
        Form() { super(new GridBagLayout()); }
        void section(String title) {
            var label=new JBLabel(title);label.setFont(label.getFont().deriveFont(Font.BOLD));
            var c=constraints(0);c.gridwidth=2;c.insets=JBUI.insets(row==0?0:18,0,8,0);add(label,c);row++;
        }
        void row(String title,JComponent field) {
            var label=new JBLabel(title+":");
            label.setLabelFor(field instanceof TextFieldWithBrowseButton browse ? browse.getTextField() : field);
            var c=constraints(0);c.anchor=field instanceof Dependencies || field instanceof JScrollPane ? GridBagConstraints.NORTHWEST : GridBagConstraints.WEST;c.insets=JBUI.insets(0,0,6,8);add(label,c);
            c=constraints(1);c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;c.insets=JBUI.insets(0,0,6,0);add(field,c);row++;
        }
        void full(JComponent component) {
            var c=constraints(0);c.gridwidth=2;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;c.insets=JBUI.insets(0,0,6,0);add(component,c);row++;
        }
        private GridBagConstraints constraints(int column) {
            var c=new GridBagConstraints();c.gridx=column;c.gridy=row;c.anchor=GridBagConstraints.NORTHWEST;return c;
        }
        JComponent topAligned() {
            var panel=new JPanel(new BorderLayout());panel.add(this,BorderLayout.NORTH);return panel;
        }
    }
    static final class Dependencies extends JPanel {
        private final JBTextArea text=new JBTextArea(3,36);
        private final JButton browse=new JButton("Add…");
        Dependencies(Project project) {
            super(new BorderLayout(JBUI.scale(6),0));
            text.getAccessibleContext().setAccessibleName("Dependency paths");
            text.setToolTipText("One mod directory or ZIP per line");
            add(new JBScrollPane(text),BorderLayout.CENTER);
            var side=new JPanel(new BorderLayout());side.add(browse,BorderLayout.NORTH);add(side,BorderLayout.EAST);
            browse.addActionListener(event -> {
                var descriptor=new FileChooserDescriptor(true,true,true,false,false,true);
                descriptor.setTitle("Add Mod Dependencies");
                descriptor.setDescription("Select individual mod directories or ZIP files");
                FileChooser.chooseFiles(descriptor,project,null,files -> {
                    var paths=new LinkedHashSet<>(List.of(text.getText().split("\\R")));
                    paths.removeIf(String::isBlank);
                    files.forEach(file -> paths.add(file.getPath()));
                    text.setText(String.join("\n",paths));
                });
            });
        }
        String getText() { return text.getText(); }
        void setText(String value) { text.setText(value); }
        @Override public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            if(text!=null)text.setEnabled(enabled);
            if(browse!=null)browse.setEnabled(enabled);
        }
    }
}
