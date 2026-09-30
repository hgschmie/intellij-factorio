package de.softwareforge.factorio;

import com.intellij.credentialStore.*;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.redhat.devtools.lsp4ij.LanguageServerManager;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

public final class FactorioActions {
    private static final String GROUP="Softwareforge Factorio";
    public static void report(Project p,String text,boolean error) { NotificationGroupManager.getInstance().getNotificationGroup(GROUP).createNotification(text,error?NotificationType.ERROR:NotificationType.INFORMATION).notify(p); }
    public abstract static class ProjectAction extends AnAction {
        @Override public void update(AnActionEvent e) { e.getPresentation().setEnabled(e.getProject()!=null); }
        @Override public ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
    }
    public static final class Setup extends ProjectAction {
        @Override public void actionPerformed(AnActionEvent e) {
            Project p=e.getProject(); if(p==null)return;
            ShowSettingsUtil.getInstance().showSettingsDialog(p,FactorioConfigurable.class);
        }
    }
    public static final class Generate extends ProjectAction {
        @Override public void actionPerformed(AnActionEvent e) { Project p=e.getProject(); if(p!=null) background(p,"Generate Factorio API",Toolkit.root(p),(indicator,log)->Definitions.generate(p,indicator,log)); }
    }
    public static final class Diagnose extends ProjectAction {
        @Override public void actionPerformed(AnActionEvent e) {
            Project p=e.getProject();if(p==null)return;
            background(p,"Check Factorio Toolchain",Toolkit.root(p),(indicator,log)->{
                var s=FactorioSettings.get(p);
                Processes.get(p).run(List.of(s.node,"--version"),Toolkit.root(p),Map.of(),indicator,log);
                Processes.get(p).run(List.of(s.node,"-e", CommandEnvironment.TOOL_PATHS),Toolkit.root(p),Map.of(),indicator,log);
                // FMTK exposes its version in help; its "version" command edits a mod.
                Processes.get(p).run(Toolkit.command(p,"--help"),Toolkit.root(p),Map.of(),indicator,log);
                Processes.get(p).run(List.of(s.factorio,"--version"),Toolkit.root(p),Map.of(),indicator,log);
                String help=Processes.get(p).run(List.of(s.factorio,"--help"),Toolkit.root(p),Map.of(),indicator,log);
                if(!help.contains("--dap"))throw new IllegalStateException("This Factorio executable does not advertise native --dap support");
            });
        }
    }
    public static final class Restart extends ProjectAction {
        @Override public void actionPerformed(AnActionEvent e) {
            Project p=e.getProject(); if(p==null)return;
            if(FactorioSettings.servicesEnabled(p)) FactorioServerManager.get(p).restart();
        }
    }
    private interface Job { void run(ProgressIndicator indicator,Consumer<String> output)throws Exception; }
    private static void background(Project p,String title,Path directory,Job job) {
        var documents=FileDocumentManager.getInstance();
        for(var document:documents.getUnsavedDocuments()) {
            var file=documents.getFile(document);
            if(file!=null && (Path.of(file.getPath()).normalize().startsWith(directory) || (directory.equals(Toolkit.root(p)) && FactorioModules.get(p).containing(Path.of(file.getPath()))!=null))) documents.saveDocument(document);
        }
        ProgressManager.getInstance().run(new Task.Backgroundable(p,title,true) {
            @Override public void run(ProgressIndicator indicator) {
                boolean acquired=false;
                try {
                    Processes.get(p).acquire(directory); acquired=true;
                    Path logs=Definitions.cache(p).resolve("logs"); Files.createDirectories(logs);
                    Path path=logs.resolve(System.currentTimeMillis()+".log");
                    try(var writer=Files.newBufferedWriter(path)) {
                        job.run(indicator,line->{ try { synchronized(writer) { writer.write(line); writer.flush(); } } catch(Exception ex) { throw new RuntimeException(ex); } });
                    }
                    report(p,title+" completed. Log: "+path,false);
                } catch(com.intellij.openapi.progress.ProcessCanceledException cancelled) { report(p,title+" cancelled. Review the log and working tree before retrying.",false); throw cancelled; }
                catch(Exception error) { report(p,title+" failed: "+error.getMessage(),true); }
                finally { if(acquired)Processes.get(p).release(directory); VirtualFileManager.getInstance().asyncRefresh(null); }
            }
        });
    }
    public static class Command extends ProjectAction {
        private final String command;
        protected Command(String command) { this.command=command; }
        @Override public void actionPerformed(AnActionEvent e) {
            Project p=e.getProject(); if(p==null)return;
            final ModDiscovery.Mod selected;
            try { selected=Toolkit.selectMod(e); } catch(Exception ex) { report(p,ex.getMessage(),true); return; }
            if(selected==null)return;
            final Path mod=selected.root();
            List<String> args=new ArrayList<>(); args.add(command);
            Map<String,String> env=new HashMap<>();
            String config=FactorioModules.get(p).packageConfig(selected);
            if(!config.isBlank())env.put("FMTK_CONFIG",config);
            if(command.equals("run")) { String script=Messages.showInputDialog(p,"Script name from info.json package.scripts","Run Package Script",null); if(script==null||script.isBlank())return; args.add(script); }
            if(command.equals("upload")) { String zip=Messages.showInputDialog(p,"Absolute path of ZIP to upload","Upload Mod ZIP",null); if(zip==null||zip.isBlank())return; args.add(zip); }
            background(p,"FMTK "+command,mod,(indicator,log)->{
                if(Set.of("publish","upload","details").contains(command)) {
                    String summary=ReleaseSummary.create(mod,command,config,FactorioSettings.get(p).commandPath);
                    int[] decision={Messages.CANCEL};
                    ApplicationManager.getApplication().invokeAndWait(()->decision[0]=Messages.showOkCancelDialog(p,summary,"Publish Mod","Continue","Cancel",Messages.getWarningIcon()));
                    if(decision[0]!=Messages.OK)throw new ProcessCanceledException();
                    var attributes=new CredentialAttributes("Softwareforge Factorio Mod Portal");
                    String key=PasswordSafe.getInstance().getPassword(attributes);
                    if(key==null || key.isBlank()) {
                        final String[] entered={null};
                        ApplicationManager.getApplication().invokeAndWait(()->entered[0]=Messages.showPasswordDialog(p,"Mod Portal API key (stored in PasswordSafe)","Factorio Mod Portal",null));
                        key=entered[0]; if(key==null||key.isBlank())throw new IllegalStateException("No portal API key supplied");
                        PasswordSafe.getInstance().setPassword(attributes,key);
                    }
                    env.put("FACTORIO_UPLOAD_API_KEY",key.trim());
                }
                Processes.get(p).run(Toolkit.command(p,args.toArray(String[]::new)),mod,env,indicator,log);
            });
        }
    }
    public static final class Package extends Command { public Package(){super("package");} }
    public static final class Version extends Command { public Version(){super("version");} }
    public static final class Datestamp extends Command { public Datestamp(){super("datestamp");} }
    public static final class Script extends Command { public Script(){super("run");} }
    public static final class Upload extends Command { public Upload(){super("upload");} }
    public static final class Details extends Command { public Details(){super("details");} }
    public static final class Publish extends Command { public Publish(){super("publish");} }
}
