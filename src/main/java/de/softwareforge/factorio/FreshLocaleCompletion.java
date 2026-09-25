package de.softwareforge.factorio;

import com.intellij.codeInsight.completion.*;
import com.redhat.devtools.lsp4ij.LSPFileSupport;

/** Locale files may change without changing the Lua document or caret offset. */
public final class FreshLocaleCompletion extends CompletionContributor {
    @Override public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
        if (FactorioSettings.servicesEnabled(parameters.getOriginalFile().getProject())) {
            LSPFileSupport.getSupport(parameters.getOriginalFile()).getCompletionSupport().cancel();
        }
    }
}
