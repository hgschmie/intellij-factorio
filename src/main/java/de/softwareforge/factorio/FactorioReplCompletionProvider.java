package de.softwareforge.factorio;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.util.textCompletion.*;
import java.util.Objects;
import java.util.List;
import java.util.function.Supplier;

/** IntelliJ's normal completion popup on a private plain-text REPL document, independent of Lua PSI/LSP. */
final class FactorioReplCompletionProvider implements TextCompletionProvider {
    private final Supplier<FactorioReplCompletion> completion;
    FactorioReplCompletionProvider(Supplier<FactorioReplCompletion> completion) { this.completion=completion; }

    Document document(Project project,String text) {
        return com.intellij.ui.LanguageTextField.createDocument(text,com.intellij.openapi.fileTypes.PlainTextLanguage.INSTANCE,project,
                new com.intellij.ui.LanguageTextField.SimpleDocumentCreator() {
                    @Override public void customizePsiFile(com.intellij.psi.PsiFile file) {
                        TextCompletionUtil.installProvider(file,FactorioReplCompletionProvider.this,true);
                    }
                });
    }

    @Override public String getAdvertisement() { return "Paused frame runtime values · Up to 1,000 entries per scope/table · Enter inserts; Enter again evaluates"; }
    @Override public String getPrefix(String text,int offset) {
        var query=FactorioReplExpression.parse(text,offset);
        return query==null ? null : query.prefix();
    }
    @Override public CompletionResultSet applyPrefixMatcher(CompletionResultSet result,String prefix) { return result.withPrefixMatcher(new PlainPrefixMatcher(prefix)); }
    @Override public CharFilter.Result acceptChar(char c) {
        return Character.isLetterOrDigit(c) || c=='_' ? CharFilter.Result.ADD_TO_PREFIX : CharFilter.Result.HIDE_LOOKUP;
    }
    @Override public void fillCompletionVariants(CompletionParameters parameters,String prefix,CompletionResultSet result) {
        var engine=completion.get();
        if (engine==null) { result.stopHere();return; }
        var query=FactorioReplExpression.parse(parameters.getOriginalFile().getText(),parameters.getOffset());
        if (query==null) { result.stopHere();return; }
        // Completion runs off the EDT. Waiting is cancellable, and DAP requests have a deadline.
        var future=engine.complete(query).handle(Outcome::new);
        var outcome=com.intellij.openapi.progress.util.ProgressIndicatorUtils.awaitWithCheckCanceled(future);
        if (outcome.failure()!=null) {
            result.addLookupAdvertisement("Runtime completion unavailable: "+FactorioDapErrors.message(outcome.failure()));
            engine.retry();result.stopHere();return;
        }
        var items=outcome.items();
        if (completion.get()!=engine) return;
        for (var item:items) {
            var variable=item.variable();
            String value=Objects.requireNonNullElse(variable.getValue(),"").replace('\n',' ');
            if (value.length()>100) value=value.substring(0,97)+"…";
            var element=LookupElementBuilder.create(query.insertion(item.key(),item.numeric()))
                    .withTypeText(Objects.requireNonNullElse(variable.getType(),""))
                    .withTailText(" = "+value,true)
                    .withInsertHandler((context,ignored) -> {
                        context.setAddCompletionChar(false);
                        finishInsertion(context,query);
                    });
            result.addElement(element.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE));
        }
        result.stopHere();
    }

    private record Outcome(List<FactorioReplCompletion.Item> items,Throwable failure) {}

    private static void finishInsertion(InsertionContext context,FactorioReplExpression.Query query) {
        var document=context.getDocument();
        int tail=context.getTailOffset(),end=tail;
        String text=document.getText();
        String closing=query.closing();
        if (closing.isEmpty()) {
            while (end<text.length() && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end)=='_')) end++;
        } else {
            if (query.kind()==FactorioReplExpression.Kind.QUOTED) {
                int quote=end;
                while (quote<text.length() && text.charAt(quote)!=query.quote() && text.charAt(quote)!='\n') {
                    if (text.charAt(quote)=='\\') quote++;
                    quote++;
                }
                if (quote<text.length() && text.charAt(quote)==query.quote()) end=quote+1;
            }
            if (end<text.length() && text.charAt(end)==']') end++;
        }
        document.replaceString(tail,end,closing);
        context.getEditor().getCaretModel().moveToOffset(tail+closing.length());
    }
}
