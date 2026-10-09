package de.softwareforge.factorio;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

/** Factorio puts the useful diagnostic in DAP body.error, not the response's "error" message. */
final class FactorioDapErrors {
    private static final Gson JSON = new Gson();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");

    static String message(ResponseError error) {
        JsonElement data = JSON.toJsonTree(error.getData());
        if (data.isJsonObject()) {
            var detail = data.getAsJsonObject().get("error");
            if (detail != null && detail.isJsonObject()) {
                var object = detail.getAsJsonObject();
                var format = object.get("format");
                if (format != null && format.isJsonPrimitive() && format.getAsJsonPrimitive().isString()
                        && !format.getAsString().isBlank()) {
                    var variables = object.get("variables");
                    return PLACEHOLDER.matcher(format.getAsString()).replaceAll(match -> {
                        JsonElement value = variables != null && variables.isJsonObject()
                                ? variables.getAsJsonObject().get(match.group(1)) : null;
                        return java.util.regex.Matcher.quoteReplacement(value != null && value.isJsonPrimitive()
                                ? value.getAsString() : match.group());
                    });
                }
            }
        }
        return error.getMessage() == null || error.getMessage().isBlank() ? "Debug adapter request failed" : error.getMessage();
    }

    static Throwable detailed(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        if (failure instanceof ResponseErrorException exception) {
            var error = exception.getResponseError();
            // Copy only the exception; the raw protocol message and JSONL trace remain unchanged.
            return new ResponseErrorException(new ResponseError(error.getCode(), message(error), error.getData()));
        }
        return failure;
    }

    static String message(Throwable failure) {
        Throwable detailed = detailed(failure);
        if (detailed instanceof ResponseErrorException exception) return exception.getResponseError().getMessage();
        return java.util.Objects.toString(detailed.getMessage(), detailed.getClass().getSimpleName());
    }
}
