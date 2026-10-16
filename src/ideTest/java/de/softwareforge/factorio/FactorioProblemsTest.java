package de.softwareforge.factorio;

import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanelProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.client.features.LSPDiagnosticFeature;
import org.eclipse.lsp4j.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.swing.tree.*;

public class FactorioProblemsTest extends HeavyPlatformTestCase {
    private Path root;
    @Override protected void setUp() throws Exception {
        super.setUp();
        root=Files.createTempDirectory(Files.createDirectories(Path.of(System.getProperty("factorio.test.work"))),"problems-");
        FactorioSettings.get(getProject()).serviceMode="DISABLED";
    }
    private VirtualFile file(String relative) throws Exception {
        Path path=root.resolve(relative);Files.createDirectories(path.getParent());Files.writeString(path,"-- first\n-- second\n-- third\n");
        return Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path));
    }
    private Diagnostic diagnostic(String text,DiagnosticSeverity severity) {
        var d=new Diagnostic(new Range(new Position(1,3),new Position(1,6)),text);d.setSeverity(severity);d.setCode("test");return d;
    }
    private List<FactorioProblemsModel.Problem> leaves(Tree tree) {
        var result=new ArrayList<FactorioProblemsModel.Problem>();
        var nodes=((DefaultMutableTreeNode)tree.getModel().getRoot()).depthFirstEnumeration();
        while(nodes.hasMoreElements())if(((DefaultMutableTreeNode)nodes.nextElement()).getUserObject() instanceof FactorioProblemsModel.Problem p)result.add(p);
        return result;
    }
    public void testRegisteredTabCombinesModulesAndFiltersAndNavigates() throws Exception {
        assertTrue(ProblemsViewPanelProvider.getEP().getExtensionList(getProject()).stream().anyMatch(p->p instanceof FactorioProblemsPanel.Provider));
        var a=new FactorioProblemsModel.Module(root.resolve("alpha"),"alpha");
        var b=new FactorioProblemsModel.Module(root.resolve("beta"),"beta");
        var store=FactorioProblems.get(getProject());store.configure(List.of(a,b));
        var lua=store.register(a);var locale=store.register(a);var other=store.register(b);
        var af=file("alpha/control.lua");var bf=file("beta/changelog.txt");
        var feature=new LSPDiagnosticFeature();
        store.update(lua,a,af,List.of(diagnostic("Lua warning",DiagnosticSeverity.Warning)),feature,"EmmyLua");
        store.update(locale,a,af,List.of(diagnostic("Locale error",DiagnosticSeverity.Error)),feature,"factorio-locale");
        store.update(other,b,bf,List.of(diagnostic("Changelog error",DiagnosticSeverity.Error)),feature,"factorio-changelog");
        var panel=new FactorioProblemsPanel(getProject());
        try {
            assertEquals("Factorio",panel.getName(0));
            var tree=Objects.requireNonNull(UIUtil.findComponentOfType(panel,Tree.class));
            assertEquals(3,leaves(tree).size());
            var combos=UIUtil.findComponentsOfType(panel,JComboBox.class);
            var modules=combos.stream().filter(c->"Factorio module".equals(c.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();
            var severity=combos.stream().filter(c->"Problem severity".equals(c.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();
            modules.setSelectedIndex(1);assertEquals(2,leaves(tree).size());
            severity.setSelectedItem("Warnings");assertEquals("Lua warning",leaves(tree).get(0).message());
            severity.setSelectedIndex(0);
            var search=Objects.requireNonNull(UIUtil.findComponentOfType(panel,SearchTextField.class));search.setText("Locale");
            assertEquals(1,leaves(tree).size());
            var node=(DefaultMutableTreeNode)((DefaultMutableTreeNode)((DefaultMutableTreeNode)tree.getModel().getRoot()).getChildAt(0)).getChildAt(0);
            tree.setSelectionPath(new TreePath(((DefaultMutableTreeNode)node.getChildAt(0)).getPath()));
            tree.getActionMap().get("factorio.jump").actionPerformed(new java.awt.event.ActionEvent(tree,0,""));
            var editor=com.intellij.openapi.fileEditor.FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
            assertNotNull(editor);assertEquals(1,editor.getCaretModel().getLogicalPosition().line);assertEquals(3,editor.getCaretModel().getLogicalPosition().column);
            search.setText("");modules.setSelectedIndex(0);
            // A new panel/view uses existing results rather than opening each source file.
            store.remove(lua);panel.refresh();assertEquals(2,leaves(tree).size());
            store.update(lua,a,af,List.of(diagnostic("late",DiagnosticSeverity.Error)),feature,"EmmyLua");panel.refresh();assertEquals(2,leaves(tree).size());
            store.configure(List.of(b));panel.refresh();assertEquals(1,leaves(tree).size());
            assertEquals("beta",leaves(tree).get(0).module().name());
            // Render both services/modules expanded for a representative preview.
            store.configure(List.of(a,b));
            lua=store.register(a);locale=store.register(a);
            store.update(lua,a,af,List.of(diagnostic("Undefined field: storage.trains",DiagnosticSeverity.Warning)),feature,"EmmyLua");
            store.update(locale,a,af,List.of(diagnostic("Unknown locale key: locomotive.name",DiagnosticSeverity.Error)),feature,"factorio-locale");
            panel.refresh();
            UIUtil.findComponentsOfType(panel,JButton.class).stream().filter(c->"Expand all".equals(c.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow().doClick();
            assertTrue(tree.getRowCount()>3);
            // Save a native look-and-feel preview in the build output for review.
            panel.setSize(1050,420);layout(panel);
            var image=new java.awt.image.BufferedImage(1050,420,java.awt.image.BufferedImage.TYPE_INT_RGB);
            var graphics=image.createGraphics();panel.paint(graphics);graphics.dispose();
            javax.imageio.ImageIO.write(image,"png",root.resolve("factorio-problems.png").toFile());
        } finally { Disposer.dispose(panel); }
    }
    public void testAllSeverityCountsAndFilters() throws Exception {
        var module=new FactorioProblemsModel.Module(root.resolve("mod"),"mod");
        var store=FactorioProblems.get(getProject());store.configure(List.of(module));
        var owner=store.register(module);var file=file("mod/control.lua");
        store.update(owner,module,file,List.of(
            diagnostic("error",DiagnosticSeverity.Error),diagnostic("warning",DiagnosticSeverity.Warning),
            diagnostic("information",DiagnosticSeverity.Information),diagnostic("hint",DiagnosticSeverity.Hint)),new LSPDiagnosticFeature(),"EmmyLua");
        var panel=new FactorioProblemsPanel(getProject());
        try {
            var tree=Objects.requireNonNull(UIUtil.findComponentOfType(panel,Tree.class));
            assertEquals(4,leaves(tree).size());
            assertTrue(UIUtil.findComponentsOfType(panel,JLabel.class).stream().anyMatch(label->
                "4 problems · 1 error · 1 warning · 1 information · 1 hint".equals(label.getText())));
            var severity=UIUtil.findComponentsOfType(panel,JComboBox.class).stream().filter(c->
                "Problem severity".equals(c.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();
            severity.setSelectedItem("Hints");assertEquals(1,leaves(tree).size());assertEquals("hint",leaves(tree).get(0).message());
            severity.setSelectedItem("Information");assertEquals(1,leaves(tree).size());assertEquals("information",leaves(tree).get(0).message());
            severity.setSelectedItem("Errors and warnings");assertEquals(2,leaves(tree).size());
        } finally { Disposer.dispose(panel); }
    }
    private static void layout(java.awt.Container component) { component.doLayout();for(var child:component.getComponents())if(child instanceof java.awt.Container c)layout(c); }
}
