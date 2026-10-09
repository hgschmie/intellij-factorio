package de.softwareforge.factorio;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.*;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import org.jetbrains.annotations.NotNull;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/** A frame-aware DAP REPL. It never writes user text to the adapter's protocol stdin. */
final class FactorioRepl extends JPanel implements Disposable {
    static final String CONTENT_ID = "factorio-lua-repl";
    private static final int RESULT_LIMIT = 100;
    private final XDebugSession session;
    private final XDebuggerExpressionComboBox input;
    private final XDebuggerTree tree;
    private final Results root;
    private final JBLabel status = new JBLabel();
    private final JButton evaluate = new JButton("Evaluate");
    private boolean busy;
    private volatile boolean disposed;
    private volatile long generation;

    static void install(DAPDebugProcess process) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (process.getProject().isDisposed()) return;
            process.getSession().runWhenUiReady(ui -> {
                if (ui.isDisposed() || ui.findContent(CONTENT_ID) != null) return;
                var repl = new FactorioRepl(process);
                var content = ui.createContent(CONTENT_ID,repl,"Lua REPL",FactorioIcons.FACTORIO,
                        repl.input.getPreferredFocusedComponent());
                content.setCloseable(false);
                content.setDisposer(repl);
                // Tabs 0 and 1 belong to Threads & Variables and the game Console.
                ui.addContent(content,2,com.intellij.execution.ui.layout.PlaceInGrid.bottom,false);
            });
        });
    }

    FactorioRepl(DAPDebugProcess process) {
        super(new BorderLayout(JBUI.scale(6),JBUI.scale(6)));
        session = process.getSession();
        var editors = process.getEditorsProvider();
        input = new XDebuggerExpressionComboBox(process.getProject(),editors,"factorio.lua.repl",null,true,false);
        input.fixEditorNotReleasedFalsePositiveException(process.getProject(),this);
        tree = new XDebuggerTree(process.getProject(),editors,null,"XDebugger.ValuePopup",null);
        Disposer.register(this,tree);
        root = new Results(tree);
        tree.setRoot(root,false);
        tree.getEmptyText().setText("Evaluate Lua expressions and statements in the selected stack frame");
        var controls = new JPanel(new BorderLayout(JBUI.scale(6),0));
        var label = new JBLabel("Lua:");
        label.setLabelFor(input.getPreferredFocusedComponent());
        controls.add(label,BorderLayout.WEST);
        controls.add(input.getComponent(),BorderLayout.CENTER);
        var buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING,JBUI.scale(4),0));
        evaluate.addActionListener(event -> submit());
        buttons.add(evaluate);
        var clear = new JButton("Clear");
        clear.addActionListener(event -> root.clearChildren());
        buttons.add(clear);
        controls.add(buttons,BorderLayout.EAST);
        var bottom = new JPanel(new BorderLayout(0,JBUI.scale(4)));
        bottom.add(controls,BorderLayout.CENTER);
        bottom.add(status,BorderLayout.SOUTH);
        setBorder(JBUI.Borders.empty(6));
        add(new JBScrollPane(tree),BorderLayout.CENTER);
        add(bottom,BorderLayout.SOUTH);
        var execute = new com.intellij.openapi.project.DumbAwareAction() {
            @Override public void actionPerformed(@NotNull AnActionEvent event) { submit(); }
            @Override public void update(@NotNull AnActionEvent event) { event.getPresentation().setEnabled(canEvaluate() && !input.getComboBox().isPopupVisible()); }
            @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
        };
        execute.registerCustomShortcutSet(CustomShortcutSet.fromString("ENTER"),input.getComponent(),this);
        session.addSessionListener(new XDebugSessionListener() {
            @Override public void sessionPaused() { later(FactorioRepl.this::updateState); }
            @Override public void stackFrameChanged() { later(FactorioRepl.this::updateState); }
            @Override public void beforeSessionResume() { expireResults(); }
            @Override public void sessionResumed() { expireResults(); }
            @Override public void sessionStopped() { expireResults(); }
        },this);
        updateState();
    }

    private boolean canEvaluate() { return !disposed && !busy && session.isSuspended() && session.getCurrentStackFrame() instanceof DAPStackFrame; }

    private void updateState() {
        evaluate.setEnabled(canEvaluate());
        input.setSourcePosition(session.getCurrentPosition());
        status.setText(busy ? "Evaluating…" : session.isSuspended()
                ? "Selected stack frame · Enter to evaluate · History in the dropdown · Expand results to inspect members"
                : "Pause at a Lua stack frame to evaluate. Command history is retained; results expire when execution resumes.");
    }

    void submit() {
        if (!canEvaluate()) return;
        String expression = input.getExpression().getExpression();
        if (expression.isBlank()) return;
        var frame = (DAPStackFrame)session.getCurrentStackFrame();
        long epoch = generation;
        busy = true;
        input.saveTextInHistory();
        input.setExpression(XExpressionImpl.fromText(""));
        var result = new Result();
        root.add(expression,result);
        updateState();
        frame.getClient().evaluate(expression,frame.getFrameId(),"repl").whenComplete((response,failure) -> later(() -> {
            if (generation != epoch) return;
            busy = false;
            if (failure != null) result.error = FactorioDapErrors.message(failure);
            else result.value = FactorioReplValue.fromResponse(response,frame,() -> !disposed && generation == epoch && session.isSuspended());
            result.refresh();
            updateState();
        }));
        input.requestFocusInEditor();
    }

    private void expireResults() {
        ++generation; // Invalidate references immediately, even if the UI callback must be queued.
        later(() -> { busy=false;tree.markNodesObsolete();root.clearChildren();updateState(); });
    }

    private void later(Runnable task) {
        ApplicationManager.getApplication().invokeLater(() -> { if (!disposed) task.run(); });
    }

    @Override public void dispose() { disposed=true;++generation; }

    private static final class Result extends XValue {
        private XValue value;
        private String error;
        private XValueNode node;
        @Override public void computePresentation(@NotNull XValueNode node,@NotNull XValuePlace place) {
            this.node=node;
            if (value!=null) value.computePresentation(node,place);
            else if (error!=null) node.setPresentation(AllIcons.General.Error,new XValuePresentation() {
                @Override public void renderValue(@NotNull XValueTextRenderer renderer) { renderer.renderError(error); }
            },false);
            else node.setPresentation(null,null,"Evaluating…",false);
        }
        void refresh() { if (node!=null) computePresentation(node,XValuePlace.TREE); }
        @Override public void computeChildren(@NotNull XCompositeNode node) { if (value!=null) value.computeChildren(node); }
    }

    private static final class Results extends XDebuggerTreeNode {
        private final List<XValueNodeImpl> children = new ArrayList<>();
        Results(XDebuggerTree tree) { super(tree,null,false); }
        void add(String expression,XValue value) {
            if (children.size()==RESULT_LIMIT) {
                var removed=children.removeFirst();removed.setObsolete();
                fireNodesRemoved(new int[]{0},new javax.swing.tree.TreeNode[]{removed});
            }
            var node=new XValueNodeImpl(myTree,this,expression,value);
            children.add(node);fireNodesInserted(List.of(node));
            myTree.expandPath(getPath());myTree.scrollPathToVisible(node.getPath());
        }
        @Override public List<XValueNodeImpl> getChildren() { return children; }
        @Override public List<XValueNodeImpl> getLoadedChildren() { return children; }
        @Override public void clearChildren() {
            children.forEach(XValueNodeImpl::setObsolete);children.clear();fireNodeStructureChanged();
        }
    }
}
