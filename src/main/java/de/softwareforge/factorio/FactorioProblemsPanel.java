package de.softwareforge.factorio;

import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanelProvider;
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewTab;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.ui.*;
import com.intellij.ui.components.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.tree.*;

/** Project-wide FDE diagnostics inside the existing IntelliJ Problems window. */
public final class FactorioProblemsPanel extends JPanel implements ProblemsViewTab, Disposable, UiDataProvider {
    public static final String TAB_ID="SoftwareforgeFactorioProblems";
    private final Project project;
    private final FactorioProblems problems;
    private final Tree tree=new Tree(new DefaultMutableTreeNode());
    private final ComboBox<Choice> modules=new ComboBox<>();
    private final ComboBox<String> severity=new ComboBox<>(new String[]{"All severities","Errors and warnings","Errors","Warnings","Information","Hints"});
    private final SearchTextField search=new SearchTextField();
    private final JBLabel summary=new JBLabel();
    private final JButton start=new JButton("Start services");
    private Content content;
    private boolean rebuilding;
    private boolean selected;
    private boolean visible=true;
    private boolean disposed;
    private record Choice(Path root, String label) { @Override public String toString(){return label;} }
    private record ModuleGroup(FactorioProblemsModel.Module module, int count, long errors, long warnings, long information, long hints) {}
    private record FileGroup(Path path, String label, int count) {}

    public FactorioProblemsPanel(Project project) {
        super(new BorderLayout());
        this.project=project; problems=FactorioProblems.get(project);
        var filters=new JPanel(new FlowLayout(FlowLayout.LEFT,JBUI.scale(8),JBUI.scale(4)));
        filters.add(new JBLabel("Module:")); filters.add(modules); filters.add(severity);
        start.setToolTipText("Start services for all modules and keep them running for this project session; reuse servers already running");
        start.addActionListener(e->FactorioServerManager.get(project).startServices());
        filters.add(start);
        var expand=new JButton(AllIcons.Actions.Expandall);
        expand.setToolTipText("Expand all");expand.getAccessibleContext().setAccessibleName("Expand all");
        expand.addActionListener(e->{for(int row=0;row<tree.getRowCount();row++)tree.expandRow(row);});
        var collapse=new JButton(AllIcons.Actions.Collapseall);
        collapse.setToolTipText("Collapse all");collapse.getAccessibleContext().setAccessibleName("Collapse all");
        collapse.addActionListener(e->{for(int row=tree.getRowCount()-1;row>=0;row--)tree.collapseRow(row);});
        filters.add(expand);filters.add(collapse);
        modules.getAccessibleContext().setAccessibleName("Factorio module");
        severity.getAccessibleContext().setAccessibleName("Problem severity");
        search.getTextEditor().getEmptyText().setText("Filter problems");
        search.getTextEditor().getAccessibleContext().setAccessibleName("Filter Factorio problems");
        var toolbar=new JPanel(new BorderLayout()); toolbar.add(filters,BorderLayout.WEST); toolbar.add(search,BorderLayout.CENTER);
        toolbar.setBorder(JBUI.Borders.emptyRight(8)); add(toolbar,BorderLayout.NORTH);
        tree.setRootVisible(false); tree.setShowsRootHandles(true);
        tree.setCellRenderer(new Renderer()); tree.setRowHeight(0);
        ToolTipManager.sharedInstance().registerComponent(tree);
        add(new JBScrollPane(tree),BorderLayout.CENTER);
        summary.setBorder(JBUI.Borders.empty(4,8)); add(summary,BorderLayout.SOUTH);
        modules.addActionListener(e->{if(!rebuilding) refresh();});
        severity.addActionListener(e->refresh());
        search.addDocumentListener(new com.intellij.ui.DocumentAdapter() {
            @Override protected void textChanged(javax.swing.event.DocumentEvent e) { refresh(); }
        });
        new DoubleClickListener() {
            @Override protected boolean onDoubleClick(MouseEvent event) { return navigate(); }
        }.installOn(tree);
        var jump=new AbstractAction(){@Override public void actionPerformed(ActionEvent e){navigate();}};
        tree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"factorio.jump");
        tree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F4,0),"factorio.jump");
        tree.getActionMap().put("factorio.jump",jump);
        TreeSpeedSearch.installOn(tree);
        problems.listen(this,()->{if(selected && visible)refresh();});
        refresh();
    }
    @Override public String getName(int count) { return "Factorio"; }
    @Override public String getTabId() { return TAB_ID; }
    @Override public void customizeTabContent(Content content) {
        this.content=content; content.setIcon(FactorioIcons.FACTORIO); content.setDisposer(this); refresh();
    }
    @Override public void selectionChangedTo(boolean selected) { this.selected=selected; if(selected && visible){FactorioServerManager.get(project).startServices();refresh();} }
    @Override public void visibilityChangedTo(boolean visible) { this.visible=visible; if(visible && selected){FactorioServerManager.get(project).startServices();refresh();} }
    @Override public void dispose() { disposed=true; ToolTipManager.sharedInstance().unregisterComponent(tree); }

    void refresh() {
        ApplicationManager.getApplication().assertIsDispatchThread();
        if(disposed || rebuilding)return;
        rebuilding=true;
        try {
            var snapshot=problems.snapshot();
            start.setEnabled(!snapshot.modules().isEmpty() && FactorioSettings.servicesEnabled(project));
            Path chosen=modules.getSelectedItem() instanceof Choice choice?choice.root():null;
            modules.removeAllItems(); modules.addItem(new Choice(null,"All modules"));
            for(var module:snapshot.modules()) {
                var choice=new Choice(module.root(),module.name()); modules.addItem(choice);
                if(module.root().equals(chosen))modules.setSelectedItem(choice);
            }
            chosen=((Choice)Objects.requireNonNull(modules.getSelectedItem())).root();
            Path filterRoot=chosen;
            String query=search.getText().trim().toLowerCase(Locale.ROOT);
            int level=severity.getSelectedIndex();
            var visible=snapshot.problems().stream().filter(p->filterRoot==null || p.module().root().equals(filterRoot))
                .filter(p->level==0 || level==1 && p.severity()<=2 || level==2 && p.severity()==1 || level==3 && p.severity()==2 || level==4 && p.severity()==3 || level==5 && p.severity()==4)
                .filter(p->query.isEmpty() || (p.message()+" "+p.source()+" "+p.code()+" "+p.file()+" "+p.module().name()).toLowerCase(Locale.ROOT).contains(query)).toList();
            var expanded=new HashSet<Object>();
            var previousRoot=(DefaultMutableTreeNode)tree.getModel().getRoot();
            var paths=tree.getExpandedDescendants(new TreePath(previousRoot));
            if(paths!=null)while(paths.hasMoreElements())expanded.add(key((DefaultMutableTreeNode)paths.nextElement().getLastPathComponent()));
            Object selection=tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node?key(node):null;
            var root=new DefaultMutableTreeNode();
            for(var module:snapshot.modules()) {
                var entries=visible.stream().filter(p->p.module().equals(module)).toList();
                if(entries.isEmpty())continue;
                var moduleNode=new DefaultMutableTreeNode(new ModuleGroup(module,entries.size(),
                    entries.stream().filter(p->p.severity()==1).count(),entries.stream().filter(p->p.severity()==2).count(),
                    entries.stream().filter(p->p.severity()==3).count(),entries.stream().filter(p->p.severity()==4).count()));
                root.add(moduleNode);
                var files=new LinkedHashMap<Path,List<FactorioProblemsModel.Problem>>();
                for(var entry:entries)files.computeIfAbsent(entry.file(),p->new ArrayList<>()).add(entry);
                for(var entry:files.entrySet()) {
                    var fileNode=new DefaultMutableTreeNode(new FileGroup(entry.getKey(),module.root().relativize(entry.getKey()).toString(),entry.getValue().size()));
                    moduleNode.add(fileNode);
                    for(var problem:entry.getValue())fileNode.add(new DefaultMutableTreeNode(problem));
                }
            }
            tree.setModel(new DefaultTreeModel(root));
            var nodes=root.depthFirstEnumeration();
            while(nodes.hasMoreElements()) {
                var node=(DefaultMutableTreeNode)nodes.nextElement(); var path=new TreePath(node.getPath());
                if(expanded.contains(key(node)) || previousRoot.getChildCount()==0 && node.getUserObject() instanceof ModuleGroup)tree.expandPath(path);
                if(selection!=null && Objects.equals(selection,key(node)))tree.setSelectionPath(path);
            }
            tree.getEmptyText().setText(snapshot.modules().isEmpty()?"No Factorio modules configured":snapshot.problems().isEmpty()?"No problems reported by Factorio language services":"No problems match these filters");
            long errors=visible.stream().filter(p->p.severity()==1).count();
            long warnings=visible.stream().filter(p->p.severity()==2).count();
            long information=visible.stream().filter(p->p.severity()==3).count();
            long hints=visible.stream().filter(p->p.severity()==4).count();
            summary.setText(summary(visible.size(),errors,warnings,information,hints));
            summary.setToolTipText("Latest diagnostics received from Lua and locale/changelog services, including closed files. This view does not run an additional analysis.");
            if(content!=null)content.setDisplayName("Factorio"+(snapshot.problems().isEmpty()?"":" ("+snapshot.problems().size()+")"));
        } finally { rebuilding=false; }
    }
    private static String count(long count,String noun) { return count+" "+noun+(count==1?"":"s"); }
    private static String summary(long total,long errors,long warnings,long information,long hints) {
        return count(total,"problem")+" · "+count(errors,"error")+" · "+count(warnings,"warning")+" · "+information+" information · "+count(hints,"hint");
    }
    private static Object key(DefaultMutableTreeNode node) {
        Object value=node.getUserObject();
        return value instanceof ModuleGroup group?group.module().root():value instanceof FileGroup file?file.path():value;
    }
    private OpenFileDescriptor descriptor() {
        if(!(tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node))return null;
        Object value=node.getUserObject();
        Path path=value instanceof FactorioProblemsModel.Problem p?p.file():value instanceof FileGroup f?f.path():null;
        if(path==null)return null;
        var file=LocalFileSystem.getInstance().findFileByNioFile(path);
        if(file==null || !file.isValid())return null;
        return value instanceof FactorioProblemsModel.Problem p?new OpenFileDescriptor(project,file,p.line(),p.column()):new OpenFileDescriptor(project,file);
    }
    private boolean navigate() { var descriptor=descriptor(); if(descriptor==null)return false; descriptor.navigate(true); return true; }
    @Override public void uiDataSnapshot(DataSink sink) {
        sink.set(CommonDataKeys.PROJECT,project);
        var descriptor=descriptor();
        if(descriptor!=null) { sink.set(CommonDataKeys.NAVIGATABLE,descriptor); sink.set(CommonDataKeys.VIRTUAL_FILE,descriptor.getFile()); }
    }
    private static final class Renderer extends ColoredTreeCellRenderer {
        @Override public void customizeCellRenderer(JTree tree,Object value,boolean selected,boolean expanded,boolean leaf,int row,boolean focus) {
            setToolTipText(null);
            Object item=((DefaultMutableTreeNode)value).getUserObject();
            if(item instanceof ModuleGroup group) {
                setIcon(FactorioIcons.FACTORIO); append(group.module().name(),SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                append("  "+summary(group.count(),group.errors(),group.warnings(),group.information(),group.hints()),SimpleTextAttributes.GRAYED_ATTRIBUTES);
                setToolTipText(group.module().root().toString());
            }
            else if(item instanceof FileGroup file) { setIcon(AllIcons.FileTypes.Any_type); append(file.label()); append(" ("+file.count()+")",SimpleTextAttributes.GRAYED_ATTRIBUTES); setToolTipText(file.path().toString()); }
            else if(item instanceof FactorioProblemsModel.Problem p) {
                setIcon(p.severity()==1?AllIcons.General.Error:p.severity()==2?AllIcons.General.Warning:AllIcons.General.Information);
                append(p.message().replace('\n',' '));
                append("  "+(p.line()+1)+":"+(p.column()+1)+"  "+p.source()+(p.code().isEmpty()?"":" ("+p.code()+")"),SimpleTextAttributes.GRAYED_ATTRIBUTES);
                setToolTipText((p.severity()==1?"Error":p.severity()==2?"Warning":p.severity()==3?"Information":"Hint")+": "+p.message());
            }
        }
    }
    public static final class Provider implements ProblemsViewPanelProvider {
        private final Project project;
        public Provider(Project project) { this.project=project; }
        @Override public ProblemsViewTab create() { return new FactorioProblemsPanel(project); }
    }
}
