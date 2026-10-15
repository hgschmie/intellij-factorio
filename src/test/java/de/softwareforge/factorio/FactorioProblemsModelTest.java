package de.softwareforge.factorio;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FactorioProblemsModelTest {
    private final FactorioProblemsModel model=new FactorioProblemsModel();
    private final FactorioProblemsModel.Module alpha=new FactorioProblemsModel.Module(Path.of("/mods/alpha"),"alpha");
    private final FactorioProblemsModel.Module beta=new FactorioProblemsModel.Module(Path.of("/mods/beta"),"beta");
    private FactorioProblemsModel.Problem problem(FactorioProblemsModel.Module module,String file,String source,String message) {
        return new FactorioProblemsModel.Problem(module,module.root().resolve(file),2,4,2,message,source,"test-warning");
    }
    private void update(Object owner,FactorioProblemsModel.Problem problem) { assertTrue(model.update(owner,problem.file(),List.of(problem))); }
    @Test void combinesLuaAndLocaleAcrossModulesAndReplacesOnlyTheReportingOwner() {
        model.configure(List.of(alpha,beta)); var lua=new Object(); var locale=new Object(); var other=new Object();
        model.register(lua,alpha); model.register(locale,alpha); model.register(other,beta);
        var first=problem(alpha,"control.lua","EmmyLua","undefined field");
        var second=problem(alpha,"control.lua","FMTK","unknown locale key");
        var third=problem(beta,"changelog.txt","FMTK","invalid version");
        update(lua,first);update(locale,second);update(other,third);
        assertEquals(3,model.snapshot().problems().size());
        var retained=model.snapshot();
        assertTrue(model.update(lua,first.file(),List.of()));
        assertEquals(List.of(second,third),model.snapshot().problems());
        assertEquals(3,retained.problems().size());
    }
    @Test void ignoresLateResultsFromStoppedOrRemovedModules() {
        model.configure(List.of(alpha,beta)); var old=new Object(); model.register(old,alpha);
        var p=problem(alpha,"control.lua","EmmyLua","warning"); update(old,p);
        assertTrue(model.remove(old));
        assertFalse(model.update(old,p.file(),List.of(p)));
        var restarted=new Object();model.register(restarted,alpha);update(restarted,p);
        model.configure(List.of(beta));
        assertFalse(model.update(restarted,p.file(),List.of(p)));
        assertTrue(model.snapshot().problems().isEmpty());
    }
    @Test void rejectsForeignAndNestedModuleFiles() {
        var nested=new FactorioProblemsModel.Module(alpha.root().resolve("child"),"child");
        model.configure(List.of(alpha,beta));var owner=new Object();model.register(owner,alpha);
        var child=problem(alpha,"child/control.lua","EmmyLua","wrong root");
        update(owner,child);
        model.configure(List.of(alpha,beta,nested));
        assertTrue(model.snapshot().problems().isEmpty());
        var foreign=problem(beta,"control.lua","EmmyLua","wrong server");
        assertFalse(model.update(owner,foreign.file(),List.of(foreign)));
        assertFalse(model.update(owner,child.file(),List.of(child)));
        assertTrue(model.snapshot().problems().isEmpty());
    }
    @Test void deletesFolderResultsWithoutRemovingOtherFiles() {
        model.configure(List.of(alpha));var owner=new Object();model.register(owner,alpha);
        update(owner,problem(alpha,"scripts/a.lua","EmmyLua","one"));
        update(owner,problem(alpha,"scripts/b.lua","EmmyLua","two"));
        var keep=problem(alpha,"control.lua","EmmyLua","three");update(owner,keep);
        assertTrue(model.removeUnder(alpha.root().resolve("scripts")));
        assertEquals(List.of(keep),model.snapshot().problems());
    }
    @Test void duplicateAndUnchangedReportsDoNotCauseRefreshes() {
        model.configure(List.of(alpha));var owner=new Object();model.register(owner,alpha);
        var p=problem(alpha,"control.lua","EmmyLua","warning");update(owner,p);
        assertFalse(model.update(owner,p.file(),List.of(p,p)));
        assertEquals(List.of(p),model.snapshot().problems());
    }
}
