package de.softwareforge.factorio;

import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Runtime-only completion. A cache belongs to exactly one paused frame and evaluation epoch. */
final class FactorioReplCompletion {
    static final int LIMIT = 1000;
    record Item(String key, boolean numeric, Variable variable) {}
    private final IDebugProtocolServer server;
    private final int frameId;
    private final BooleanSupplier current;
    private final Map<Integer,CompletableFuture<List<Variable>>> variables = new HashMap<>();
    private CompletableFuture<List<Item>> names;

    FactorioReplCompletion(IDebugProtocolServer server,int frameId,BooleanSupplier current) {
        this.server=server;this.frameId=frameId;this.current=current;
    }

    CompletableFuture<List<Item>> complete(FactorioReplExpression.Query query) {
        if (query==null || !current.getAsBoolean()) return CompletableFuture.completedFuture(List.of());
        CompletableFuture<List<Item>> result=names();
        for (var key:query.path()) {
            result=result.thenCompose(items -> {
                if (!current.getAsBoolean()) return CompletableFuture.completedFuture(List.of());
                var match=items.stream().filter(i -> i.key().equals(key.value()) && i.numeric()==key.numeric()).findFirst();
                if (match.isEmpty() || match.get().variable().getVariablesReference()==0) return CompletableFuture.completedFuture(List.of());
                return children(match.get().variable().getVariablesReference()).thenApply(values -> values.stream()
                        .map(v -> item(v,false)).filter(Objects::nonNull).toList());
            });
        }
        return result.thenApply(items -> current.getAsBoolean() ? items.stream()
                .filter(i -> query.accepts(i.key(),i.numeric())).limit(LIMIT).toList() : List.of());
    }

    synchronized void retry() { names=null;variables.clear(); }

    private synchronized CompletableFuture<List<Item>> names() {
        if (names!=null) return names;
        var args=new ScopesArguments();args.setFrameId(frameId);
        names=server.scopes(args).orTimeout(3,TimeUnit.SECONDS).thenCompose(response -> {
            if (!current.getAsBoolean()) return CompletableFuture.completedFuture(List.of());
            // The mod storage scope is a view into storage, not another lexical scope.
            var scopes=Arrays.stream(response.getScopes()).filter(s -> priority(s)<3)
                    .sorted(Comparator.comparingInt(FactorioReplCompletion::priority)).toList();
            var requests=scopes.stream().map(s -> children(s.getVariablesReference())).toList();
            return CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
                var merged=new LinkedHashMap<String,Item>();
                for (int s=0;s<requests.size();s++) {
                    var entries=requests.get(s).join();
                    // Factorio presents local slots oldest first; a later local shadows an earlier one.
                    for (int i=entries.size()-1;i>=0;i--) {
                        var item=item(entries.get(i),true);
                        if (item!=null && FactorioReplExpression.identifier(item.key())) merged.putIfAbsent(item.key(),item);
                    }
                }
                return List.copyOf(merged.values());
            });
        });
        return names;
    }

    private synchronized CompletableFuture<List<Variable>> children(int reference) {
        if (!current.getAsBoolean()) return CompletableFuture.completedFuture(List.of());
        return variables.computeIfAbsent(reference,key -> {
            var args=new VariablesArguments();args.setVariablesReference(key);
            // Factorio/LSP4IJ currently use unpaged variables. Never follow virtual range nodes
            // or enumerate an unbounded number of descendants to populate a popup.
            return server.variables(args).orTimeout(3,TimeUnit.SECONDS).thenApply(response ->
                    Arrays.stream(response.getVariables()).limit(LIMIT).toList());
        });
    }

    private static int priority(Scope scope) {
        return switch(scope.getName().toLowerCase(Locale.ROOT)) {
            case "locals" -> 0;
            case "upvals", "upvalues" -> 1;
            case "globals" -> 2;
            default -> 3;
        };
    }

    private static Item item(Variable variable,boolean scoped) {
        if (variable.getPresentationHint()!=null && ("virtual".equals(variable.getPresentationHint().getKind())
                || Boolean.TRUE.equals(variable.getPresentationHint().getLazy()))) return null;
        String name=variable.getName();
        if (scoped) name=name.replaceFirst("^\\[\\d+\\] ","");
        String decoded=FactorioReplExpression.unquote(name);
        if (decoded!=null) return new Item(decoded,false,variable);
        if (FactorioReplExpression.identifier(name)) return new Item(name,false,variable);
        if (!scoped && name.matches("-?\\d+")) return new Item(name,true,variable);
        // LuaCustomTable entries include a display label, e.g. game.surfaces: "[1] nauvis".
        if (!scoped) {
            var index=java.util.regex.Pattern.compile("^\\[(-?\\d+)\\](?: .*)?$").matcher(name);
            if (index.matches()) return new Item(index.group(1),true,variable);
        }
        return null;
    }
}
