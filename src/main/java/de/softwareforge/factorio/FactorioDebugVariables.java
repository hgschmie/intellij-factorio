package de.softwareforge.factorio;

import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterVariableSupport;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.VariablesResponse;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/** Presentation only: preserve the protocol values and references used for expansion/evaluation. */
final class FactorioDebugVariables extends DebugAdapterVariableSupport {
    private static final Pattern REFERENCE = Pattern.compile(
            "\\[(table|C function|C closure|Lua(?: [^\\[\\]\\r\\n]*?)?|userdata|thread) \\*0x[0-9a-fA-F]+\\]");
    private static final Pattern ADDRESS = Pattern.compile("\\*0x[0-9a-fA-F]+");
    private final BooleanSupplier hideAddresses;

    FactorioDebugVariables(BooleanSupplier hideAddresses) { this.hideAddresses=hideAddresses; }

    /** Filter before any debugger tree builds its children, but after raw protocol logging. */
    IDebugProtocolServer filterServer(IDebugProtocolServer delegate) {
        return (IDebugProtocolServer)Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(),
                new Class<?>[]{IDebugProtocolServer.class},(proxy,method,args) -> {
                    if (method.getDeclaringClass()==Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy==args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            default -> "Factorio debugger variable display";
                        };
                    }
                    if (method.getName().equals("variables")) {
                        return delegate.variables((VariablesArguments)args[0]).thenApply(this::visibleVariables);
                    }
                    try { return method.invoke(delegate,args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
    }

    private VariablesResponse visibleVariables(VariablesResponse response) {
        if (!hideAddresses.getAsBoolean() || response==null || response.getVariables()==null) return response;
        var visible=Arrays.stream(response.getVariables()).filter(variable -> !isAddress(variable)).toArray(Variable[]::new);
        if (visible.length==response.getVariables().length) return response;
        // Never modify the decoded response retained by the trace or other consumers.
        var filtered=new VariablesResponse();filtered.setVariables(visible);return filtered;
    }

    private static boolean isAddress(Variable variable) {
        var hint=variable.getPresentationHint();
        return "[address]".equals(variable.getName()) && hint!=null && "virtual".equals(hint.getKind())
                && !"string".equalsIgnoreCase(variable.getType())
                && ADDRESS.matcher(Objects.requireNonNullElse(variable.getValue(),"")).matches();
    }

    @Override protected @NotNull String formatValue(@NotNull Variable variable) {
        // Let LSP4IJ handle its normal string quoting and highlighting.
        if (isStringType(Objects.requireNonNullElse(variable.getType(),""))) return super.formatValue(variable);
        return preview(variable,hideAddresses.getAsBoolean());
    }

    /** Also used by completion, where the original string quotes must remain visible. */
    static String preview(Variable variable,boolean hideAddresses) {
        String value=Objects.requireNonNullElse(variable.getValue(),"");
        if (!hideAddresses || "string".equalsIgnoreCase(variable.getType())) return value;
        return withoutAddresses(value);
    }

    private static String withoutAddresses(String value) {
        // Native previews quote string keys/values. Never strip a reference-looking literal inside them.
        if (!value.contains("*0x")) return value;
        var result=new StringBuilder(value.length());
        var reference=REFERENCE.matcher(value);
        for (int i=0;i<value.length();) {
            char c=value.charAt(i);
            if (c=='\"' || c=='\'') {
                int start=i++;
                while (i<value.length()) {
                    char next=value.charAt(i++);
                    if (next=='\\' && i<value.length()) i++;
                    else if (next==c) break;
                }
                result.append(value,start,i);
            } else if (c=='[' && reference.region(i,value.length()).lookingAt()) {
                result.append('[').append(reference.group(1)).append(']');
                i=reference.end();
            } else {
                result.append(c);i++;
            }
        }
        return result.toString();
    }
}
