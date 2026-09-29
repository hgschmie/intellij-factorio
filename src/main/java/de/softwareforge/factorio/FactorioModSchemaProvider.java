package de.softwareforge.factorio;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.nio.file.Path;
import java.util.List;

/** Matches the mod-root candidates used by FactorioModules, even while metadata is invalid. */
public final class FactorioModSchemaProvider implements JsonSchemaProviderFactory, DumbAware {
    @Override public @NotNull List<JsonSchemaFileProvider> getProviders(@NotNull Project project) {
        return List.of(new JsonSchemaFileProvider() {
            @Override public boolean isAvailable(@NotNull VirtualFile file) {
                if (project.isDisposed() || !file.getName().equals("info.json") || file.getParent() == null) return false;
                var parent = file.getParent();
                var roots = ProjectRootManager.getInstance(project).getContentRoots();
                // Do not parse info.json here: malformed or incomplete metadata needs schema help too.
                for (var root : roots) if (root.equals(parent)) return true;
                String legacy = FactorioSettings.get(project).activeMod;
                if (!legacy.isBlank() && samePath(parent, legacy)) return true;
                return roots.length == 0 && project.getBasePath() != null && samePath(parent, project.getBasePath());
            }
            @Override public @NotNull String getName() { return "Factorio Mod info.json"; }
            @Override public @Nullable VirtualFile getSchemaFile() {
                return JsonSchemaProviderFactory.getResourceFile(FactorioModSchemaProvider.class, "/schemas/factorio/modinfo.json");
            }
            @Override public @NotNull SchemaType getSchemaType() { return SchemaType.embeddedSchema; }
            @Override public JsonSchemaVersion getSchemaVersion() { return JsonSchemaVersion.SCHEMA_7; }
        }, new JsonSchemaFileProvider() {
            @Override public boolean isAvailable(@NotNull VirtualFile file) { return false; }
            @Override public boolean isUserVisible() { return false; }
            @Override public @NotNull String getName() { return "Factorio Data metadata (supporting schema)"; }
            @Override public @Nullable VirtualFile getSchemaFile() {
                return JsonSchemaProviderFactory.getResourceFile(FactorioModSchemaProvider.class, "/schemas/factorio/datainfo.json");
            }
            @Override public @NotNull SchemaType getSchemaType() { return SchemaType.embeddedSchema; }
            @Override public JsonSchemaVersion getSchemaVersion() { return JsonSchemaVersion.SCHEMA_7; }
        });
    }
    private static boolean samePath(VirtualFile file, String path) {
        try { return Path.of(file.getPath()).toRealPath().equals(Path.of(path).toRealPath()); }
        catch (Exception ignored) { return false; }
    }
}
