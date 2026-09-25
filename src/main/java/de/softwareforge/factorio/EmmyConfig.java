package de.softwareforge.factorio;

import com.google.gson.*;
import java.util.*;

/** Merge only entries owned by the previous generation; preserve unrelated user settings. */
public final class EmmyConfig {
    private EmmyConfig() {}
    public static JsonObject merge(JsonObject original, JsonObject previous, List<String> libraries, List<String> roots, Map<String,String> modules) {
        JsonObject result = original.deepCopy();
        JsonObject workspace = object(result, "workspace");
        replaceManaged(workspace, "library", previous.getAsJsonArray("library"), strings(libraries));
        replaceManaged(workspace, "workspaceRoots", previous.getAsJsonArray("workspaceRoots"), strings(roots));
        JsonArray maps = new JsonArray();
        modules.forEach((directory, name) -> { var map = new JsonObject(); map.addProperty("pattern", "^" + escapePattern(directory) + "[.](.*)$"); map.addProperty("replace", "__" + name + "__.$1"); maps.add(map); });
        replaceManaged(workspace, "moduleMap", previous.getAsJsonArray("moduleMap"), maps);
        JsonObject runtime = object(result, "runtime");
        if (!runtime.has("version")) runtime.addProperty("version", "Lua 5.2");
        if (!runtime.has("requirePattern")) runtime.add("requirePattern", strings(List.of("?.lua")));
        return result;
    }
    public static JsonObject ownership(JsonObject before, JsonObject after) {
        var result = new JsonObject();
        JsonObject old = before.has("workspace") ? before.getAsJsonObject("workspace") : new JsonObject();
        JsonObject next = after.getAsJsonObject("workspace");
        for (String key : List.of("library", "workspaceRoots", "moduleMap")) {
            JsonArray added = new JsonArray();
            for (JsonElement entry : next.getAsJsonArray(key)) if (!old.has(key) || !old.getAsJsonArray(key).contains(entry)) added.add(entry.deepCopy());
            result.add(key, added);
        }
        return result;
    }
    private static String escapePattern(String value) {
        StringBuilder escaped = new StringBuilder();
        for (char c : value.toCharArray()) { if ("\\.^$|?*+()[]{}".indexOf(c) >= 0) escaped.append('\\'); escaped.append(c); }
        return escaped.toString();
    }
    private static JsonObject object(JsonObject root, String key) { if (!root.has(key)) root.add(key,new JsonObject()); return root.getAsJsonObject(key); }
    private static JsonArray strings(List<String> values) { var a = new JsonArray(); values.forEach(a::add); return a; }
    private static void replaceManaged(JsonObject target, String key, JsonArray previous, JsonArray next) {
        JsonArray merged = target.has(key) ? target.getAsJsonArray(key).deepCopy() : new JsonArray();
        if (previous != null) previous.forEach(merged::remove);
        next.forEach(entry -> { if (!merged.contains(entry)) merged.add(entry); });
        target.add(key, merged);
    }
}
