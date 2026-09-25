package de.softwareforge.factorio;

import com.intellij.openapi.components.*;
import com.intellij.openapi.module.Module;

@State(name="FactorioMod", storages=@Storage(StoragePathMacros.MODULE_FILE))
public final class FactorioModuleSettings implements PersistentStateComponent<FactorioModuleSettings.Data> {
    public static final class Data {
        public boolean overrideDependencies;
        public String dependencies="";
        public boolean overridePackageConfig;
        public String packageConfig="";
    }
    private Data data=new Data();
    public static Data get(Module module) { return module.getService(FactorioModuleSettings.class).getState(); }
    @Override public Data getState() { return data; }
    @Override public void loadState(Data state) { data=state; }
}
