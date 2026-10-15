package de.softwareforge.factorio;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.Alarm;
import com.redhat.devtools.lsp4ij.client.features.LSPDiagnosticFeature;
import org.eclipse.lsp4j.Diagnostic;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Collects only results received by FDE-owned servers; never requests diagnostics. */
@Service(Service.Level.PROJECT)
public final class FactorioProblems implements Disposable {
    private final FactorioProblemsModel model=new FactorioProblemsModel();
    private final List<Runnable> listeners=new CopyOnWriteArrayList<>();
    private final Alarm alarm=new Alarm(Alarm.ThreadToUse.SWING_THREAD,this);
    private boolean notificationPending;
    private volatile boolean disposed;
    public FactorioProblems(Project project) {
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES,new BulkFileListener() {
            @Override public void before(List<? extends VFileEvent> events) {
                for(var event:events) if(event instanceof VFileDeleteEvent || event instanceof VFileMoveEvent
                    || event instanceof VFilePropertyChangeEvent change && change.getPropertyName().equals(VirtualFile.PROP_NAME)) {
                    Path path=Path.of(event.getPath());
                    var file=event.getFile();
                    if(file!=null && file.getCanonicalPath()!=null) path=Path.of(file.getCanonicalPath());
                    if(model.removeUnder(path)) changed();
                }
            }
        });
    }
    static FactorioProblems get(Project project) { return project.getService(FactorioProblems.class); }
    void configure(List<FactorioProblemsModel.Module> modules) { if(model.configure(modules)) changed(); }
    Object register(FactorioProblemsModel.Module module) {
        Object session=new Object(); model.register(session,module); return session;
    }
    void remove(Object session) { if(session!=null && model.remove(session)) changed(); }
    void update(Object session, FactorioProblemsModel.Module module, VirtualFile file,
                List<Diagnostic> diagnostics, LSPDiagnosticFeature feature, String fallbackSource) {
        if(disposed || session==null || !file.isValid()) return;
        String path=file.getCanonicalPath();
        Path location=Path.of(path==null?file.getPath():path);
        var problems=new ArrayList<FactorioProblemsModel.Problem>();
        for(var d:diagnostics) {
            if(d.getRange()==null || d.getRange().getStart()==null || feature.getHighlightSeverity(d)==null) continue;
            var start=d.getRange().getStart();
            String source=d.getSource()==null || d.getSource().isBlank()?fallbackSource:d.getSource();
            String code=d.getCode()==null?"":d.getCode().isLeft()?d.getCode().getLeft():String.valueOf(d.getCode().getRight());
            problems.add(new FactorioProblemsModel.Problem(module,location,Math.max(0,start.getLine()),
                Math.max(0,start.getCharacter()),d.getSeverity()==null?1:d.getSeverity().getValue(),feature.getMessage(d),source,code));
        }
        if(model.update(session,location,problems)) changed();
    }
    FactorioProblemsModel.Snapshot snapshot() { return model.snapshot(); }
    void listen(Disposable parent, Runnable listener) { listeners.add(listener); Disposer.register(parent,()->listeners.remove(listener)); }
    private synchronized void changed() {
        if(disposed || notificationPending) return;
        notificationPending=true;
        // Coalesce bursts, but do not postpone indefinitely while reports arrive.
        alarm.addRequest(()->{
            synchronized(this) { notificationPending=false; }
            if(!disposed) listeners.forEach(Runnable::run);
        },100);
    }
    @Override public void dispose() { disposed=true; listeners.clear(); }
}
