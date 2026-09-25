package de.softwareforge.factorio;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.ProjectTopics;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.Alarm;
import com.redhat.devtools.lsp4ij.LanguageServerManager;
import java.nio.file.*;
import java.util.*;

@Service(Service.Level.PROJECT)
public final class FactorioModules implements Disposable {
    private final Project project;
    private final Alarm alarm=new Alarm(Alarm.ThreadToUse.POOLED_THREAD,this);
    private volatile ModDiscovery.Result snapshot=new ModDiscovery.Result(List.of(),List.of());
    private String previousFingerprint="";
    private boolean started;
    public FactorioModules(Project project) { this.project=project; }
    public static FactorioModules get(Project project) { return project.getService(FactorioModules.class); }
    public List<ModDiscovery.Mod> mods() { return snapshot.mods(); }
    public List<String> errors() { return snapshot.errors(); }
    public ModDiscovery.Mod containing(Path path) { return ModDiscovery.containing(mods(),path); }
    public Module module(ModDiscovery.Mod mod) { return ModuleManager.getInstance(project).findModuleByName(mod.module()); }
    public String dependencies(ModDiscovery.Mod mod) {
        Module m=module(mod); var s=m==null?null:FactorioModuleSettings.get(m);
        return s!=null&&s.overrideDependencies?s.dependencies:FactorioSettings.get(project).dependencies;
    }
    public String packageConfig(ModDiscovery.Mod mod) {
        Module m=module(mod); var s=m==null?null:FactorioModuleSettings.get(m);
        return s!=null&&s.overridePackageConfig?s.packageConfig:FactorioSettings.get(project).packageConfig;
    }
    public void start() {
        if(started)return; started=true;
        var bus=project.getMessageBus().connect(this);
        bus.subscribe(ProjectTopics.PROJECT_ROOTS,new ModuleRootListener(){ @Override public void rootsChanged(ModuleRootEvent event){ schedule(); } });
        bus.subscribe(VirtualFileManager.VFS_CHANGES,new BulkFileListener(){ @Override public void after(List<? extends VFileEvent> events){
            if(events.stream().anyMatch(e->e.getPath().endsWith("/info.json") || e instanceof com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent || e instanceof com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent || e instanceof com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent)) schedule();
        }});
        schedule();
    }
    public void schedule() { if(!project.isDisposed()){ alarm.cancelAllRequests(); alarm.addRequest(this::reconcile,500); } }
    public void refresh() {
        var candidates=ReadAction.compute(()->{
            var roots=new ArrayList<ModDiscovery.Candidate>();
            for(Module m:ModuleManager.getInstance(project).getModules()) for(var root:ModuleRootManager.getInstance(m).getContentRoots()) roots.add(new ModDiscovery.Candidate(m.getName(),Path.of(root.getPath())));
            return roots;
        });
        String legacy=FactorioSettings.get(project).activeMod;
        if(!legacy.isBlank()) {
            boolean attached=candidates.stream().anyMatch(c->{try{return c.root().toRealPath().equals(Path.of(legacy).toRealPath());}catch(Exception e){return false;}});
            if(attached)FactorioSettings.get(project).activeMod="";
            else candidates.add(new ModDiscovery.Candidate("Legacy mod",Path.of(legacy)));
        }
        if(candidates.isEmpty()) candidates.add(new ModDiscovery.Candidate(project.getName(),Toolkit.root(project)));
        snapshot=ModDiscovery.discover(candidates);
    }
    private void reconcile() {
        if(project.isDisposed())return;
        try {
            refresh();
            var s=FactorioSettings.get(project);
            String fingerprint=snapshot.toString()+s.serviceMode+s.node+s.factorio+s.apiDocs+s.cli+s.dependencies+mods().stream().map(m->m.root()+dependencies(m)).toList();
            if(fingerprint.equals(previousFingerprint))return;
            previousFingerprint=fingerprint;
            for(String error:errors()) FactorioActions.report(project,error,true);
            var manager=LanguageServerManager.getInstance(project);
            if(mods().isEmpty() || !FactorioSettings.servicesEnabled(project)) { manager.stop(FactorioLanguageServer.ID); if(mods().isEmpty())Definitions.clearManaged(project); return; }
            manager.start(FactorioLanguageServer.ID,new LanguageServerManager.StartOptions().setForceRestart(true));
            ProgressManager.getInstance().run(new Task.Backgroundable(project,"Configure Factorio modules",true) {
                @Override public void run(ProgressIndicator indicator) {
                    try { Definitions.generate(project,indicator,message->{}); }
                    catch(ProcessCanceledException cancelled) { throw cancelled; }
                    catch(Exception error) { FactorioActions.report(project,"Factorio modules detected. Configure the Factorio toolchain or regenerate definitions: "+error.getMessage(),true); }
                }
            });
        } catch(Exception error) { FactorioActions.report(project,"Cannot configure Factorio modules: "+error.getMessage(),true); }
    }
    @Override public void dispose() {}
    public static final class Startup implements StartupActivity.DumbAware {
        @Override public void runActivity(Project project) { get(project).start(); }
    }
}
