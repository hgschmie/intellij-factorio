package de.softwareforge.factorio;

import com.intellij.xdebugger.frame.*;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import org.eclipse.lsp4j.debug.*;
import org.jetbrains.annotations.NotNull;
import java.util.function.BooleanSupplier;

/** Expandable runtime values with the same presentation as the Variables view. */
final class FactorioReplValue extends XValue {
    private final Variable variable;
    private final DAPStackFrame frame;
    private final BooleanSupplier current;

    private FactorioReplValue(Variable variable,DAPStackFrame frame,BooleanSupplier current) {
        this.variable=variable;this.frame=frame;this.current=current;
    }

    static FactorioReplValue fromResponse(EvaluateResponse response,DAPStackFrame frame,BooleanSupplier current) {
        var variable=new Variable();
        variable.setName("");variable.setValue(response.getResult());variable.setType(response.getType());
        variable.setVariablesReference(response.getVariablesReference());
        variable.setNamedVariables(response.getNamedVariables());variable.setIndexedVariables(response.getIndexedVariables());
        variable.setPresentationHint(response.getPresentationHint());
        return new FactorioReplValue(variable,frame,current);
    }

    @Override public void computePresentation(@NotNull XValueNode node,@NotNull XValuePlace place) {
        var support=frame.getClient().getServerDescriptor().getVariableSupport();
        node.setPresentation(support.getIcon(variable),support.getValuePresentation(variable),variable.getVariablesReference()>0);
    }

    @Override public void computeChildren(@NotNull XCompositeNode node) {
        if (!current.getAsBoolean() || node.isObsolete()) return;
        var args=new VariablesArguments();args.setVariablesReference(variable.getVariablesReference());
        frame.getClient().getDebugProtocolServer().variables(args).whenComplete((response,failure) -> {
            if (!current.getAsBoolean() || node.isObsolete()) return;
            if (failure!=null) { node.setErrorMessage(FactorioDapErrors.message(failure));return; }
            var children=new XValueChildrenList();
            for (var child:response.getVariables()) children.add(child.getName(),new FactorioReplValue(child,frame,current));
            node.addChildren(children,true);
        });
    }
}
