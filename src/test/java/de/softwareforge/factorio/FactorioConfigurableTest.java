package de.softwareforge.factorio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactorioConfigurableTest {
    @Test void newProjectsUseAutomaticServices() {
        var settings=new FactorioSettings();assertEquals("AUTO",settings.getState().serviceMode);assertEquals(1,settings.getState().schemaVersion);
    }
    @Test void preservesLegacyExplicitEnableAndDisable() {
        var settings=new FactorioSettings();var disabled=new FactorioSettings.Data();disabled.activeMod="/legacy/mod";
        settings.loadState(disabled);assertEquals("DISABLED",settings.getState().serviceMode);assertEquals("/legacy/mod",settings.getState().activeMod);
        var enabled=new FactorioSettings.Data();enabled.enabled=true;settings.loadState(enabled);assertEquals("ENABLED",settings.getState().serviceMode);
    }
    @Test void preservesNewAutomaticPolicyAcrossReload() {
        var settings=new FactorioSettings();var data=settings.getState();settings.loadState(data);assertEquals("AUTO",settings.getState().serviceMode);
    }
}
