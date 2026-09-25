package de.softwareforge.factorio;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
@State(name = "FactorioToolkit", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class FactorioSettings implements PersistentStateComponent<FactorioSettings.Data> {
    public static class Data {
        public boolean enabled;
        public String node = detect("/opt/homebrew/bin/node", "/usr/local/bin/node", "node");
        public String factorio = detect("/Applications/factorio.app/Contents/MacOS/factorio", "factorio");
        public String cli = "";
        public String activeMod = "";
        public String dependencies = "";
        public String apiDocs = "";
        public String packageConfig = "";
        private static String detect(String... paths) {
            for (String path : paths) if (java.nio.file.Files.isExecutable(java.nio.file.Path.of(path))) return path;
            return paths[paths.length - 1];
        }
    }
    private Data data = new Data();
    public static Data get(Project project) { return project.getService(FactorioSettings.class).getState(); }
    @Override public @NotNull Data getState() { return data; }
    @Override public void loadState(@NotNull Data state) { data = state; }
}
