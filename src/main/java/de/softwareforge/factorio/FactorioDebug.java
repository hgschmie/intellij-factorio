package de.softwareforge.factorio;

import com.google.gson.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.ExecutionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.components.StoredProperty;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.redhat.devtools.lsp4ij.dap.*;
import com.redhat.devtools.lsp4ij.dap.breakpoints.*;
import com.redhat.devtools.lsp4ij.dap.configurations.*;
import com.redhat.devtools.lsp4ij.dap.descriptors.*;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;


public final class FactorioDebug {
    public static final String ID = "softwareforge.factorio.debug";
    public static final class Type extends ConfigurationTypeBase {
        public Type() { super("SoftwareforgeFactorio", "Factorio", "Run and debug a Factorio mod", NotNullLazyValue.createValue(() -> FactorioIcons.FACTORIO)); addFactory(new Factory(this)); }
    }
    public static final class Factory extends ConfigurationFactory {
        public Factory(ConfigurationType type) { super(type); }
        @Override public String getId() { return "Factorio"; }
        @Override public RunConfiguration createTemplateConfiguration(Project project) { return new Configuration(project,this,"Factorio"); }
        @Override public Class<? extends BaseState> getOptionsClass() { return Options.class; }
    }
    public static final class Options extends DAPRunConfigurationOptions {
        private final StoredProperty<Boolean> dapLogToFile = property(false).provideDelegate(this,"dapLogToFile");
        private final StoredProperty<String> dapLogDirectory = string("").provideDelegate(this,"dapLogDirectory");
        public boolean getDapLogToFile() { return dapLogToFile.getValue(this); }
        public void setDapLogToFile(boolean value) { dapLogToFile.setValue(this,value); }
        public String getDapLogDirectory() { return Objects.toString(dapLogDirectory.getValue(this),""); }
        public void setDapLogDirectory(String value) { dapLogDirectory.setValue(this,value); }
        static Path defaultLogDirectory() { return Path.of(PathManager.getLogPath(),"factorio","dap"); }
        Path logDirectory() {
            if(getDapLogDirectory().isBlank()) return defaultLogDirectory();
            Path directory=Path.of(getDapLogDirectory());
            if(directory.isAbsolute()) return directory;
            return Path.of(Objects.toString(getWorkingDirectory(),".")).resolve(directory).toAbsolutePath().normalize();
        }
    }
    public static final class Configuration extends DAPRunConfiguration {
        @Override public Options getOptions() { return (Options)super.getOptions(); }
        private String modModule="";
        @Override public void writeExternal(org.jdom.Element e){ super.writeExternal(e);e.setAttribute("factorioModule",modModule); }
        @Override public void readExternal(org.jdom.Element e) throws com.intellij.openapi.util.InvalidDataException { super.readExternal(e);modModule=e.getAttributeValue("factorioModule",""); }
        public Configuration(Project project, ConfigurationFactory factory, String name) {
            super(project,factory,name); setServerId(ID); setServerName("Factorio");
            setCommand(FactorioSettings.get(project).factorio); setWorkingDirectory(project.getBasePath());
            var detected=FactorioModules.get(project).mods();
            if(detected.size()==1){modModule=detected.getFirst().module();setWorkingDirectory(detected.getFirst().root().toString());}
            setDebugMode(DebugMode.LAUNCH); setLaunchConfiguration("{\"request\":\"launch\",\"factorioArgs\":[],\"followSymlinks\":true,\"hookDebugConsole\":true}");
        }
        @Override public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() { return new Editor(getProject()); }
        @Override public RunProfileState getState(com.intellij.execution.Executor executor, ExecutionEnvironment environment) {
            var state = (DAPCommandLineState) super.getState(executor, environment);
            state.setConsoleBuilder(new com.redhat.devtools.lsp4ij.dap.console.DAPTextConsoleBuilderImpl(getProject()) {
                @Override protected com.intellij.execution.ui.ConsoleView createConsole() {
                    return new FactorioDebugConsole(getProject());
                }
            });
            return state;
        }
        @Override public void checkConfiguration() throws RuntimeConfigurationException {
            if (!modModule.isBlank() && FactorioModules.get(getProject()).mods().stream().noneMatch(m->m.module().equals(modModule))) throw new RuntimeConfigurationError("Factorio module is missing or invalid: " + modModule);
            if (getCommand()==null || !Files.isExecutable(Path.of(getCommand()))) throw new RuntimeConfigurationError("Select an executable Factorio installation");
            try { JsonParser.parseString(getLaunchConfiguration()).getAsJsonObject().getAsJsonArray("factorioArgs"); }
            catch (Exception e) { throw new RuntimeConfigurationError("Invalid Factorio launch arguments"); }
        }
    }
    public static final class Editor extends SettingsEditor<DAPRunConfiguration> {
        private final JComponent panel;
        private final TextFieldWithBrowseButton executable,cwd,save,mods,config,logDirectory;
        private final JCheckBox trace=new com.intellij.ui.components.JBCheckBox("Enable DAP logging");
        private final JCheckBox logToFile=new com.intellij.ui.components.JBCheckBox("Save DAP log to file");
        private final com.intellij.ui.components.JBTextArea extra = new com.intellij.ui.components.JBTextArea(3,36);
        private final com.intellij.openapi.ui.ComboBox<String> module=new com.intellij.openapi.ui.ComboBox<>();
        private final Project project;
        public Editor(Project project) {
            this.project=project;
            executable=FactorioForms.path(project,"Select Factorio Executable",false,"");
            cwd=FactorioForms.path(project,"Select Working Directory",true,"");
            save=FactorioForms.path(project,"Select Save ZIP",false,"Optional");
            mods=FactorioForms.path(project,"Select Mod Directory",true,"");
            config=FactorioForms.path(project,"Select Factorio Configuration",false,"Optional config.ini");
            logDirectory=FactorioForms.path(project,"Select DAP Log Output Folder",true,Options.defaultLogDirectory().toString());
            trace.setToolTipText("Verbose protocol messages in the debug console, or only in a file when saving is enabled. Applies to the next session.");
            logToFile.setToolTipText("Save full DAP messages as JSON Lines in a new file for each session; normal console output remains visible.");
            logDirectory.setToolTipText("Leave blank for "+Options.defaultLogDirectory()+". Relative paths use the working directory.");
            trace.addActionListener(e -> updateLoggingControls());
            logToFile.addActionListener(e -> updateLoggingControls());
            module.addItem("");FactorioModules.get(project).mods().forEach(m->module.addItem(m.module()));
            module.addActionListener(e->FactorioModules.get(project).mods().stream().filter(m->m.module().equals(module.getSelectedItem())).findFirst().ifPresent(m->cwd.setText(m.root().toString())));
            var form=new FactorioForms.Form();
            form.row("Mod module",FactorioForms.left(module));form.row("Factorio",executable);
            form.row("Working directory",cwd);form.row("Save ZIP",save);form.row("Mod directory",mods);form.row("Config file",config);
            extra.setToolTipText("One argument per line");
            form.row("Additional arguments",new com.intellij.ui.components.JBScrollPane(extra));
            form.section("DAP logging");
            form.full(trace);form.full(logToFile);form.row("Output folder",logDirectory);
            updateLoggingControls();
            panel=form.topAligned();
        }
        private void updateLoggingControls() {
            logToFile.setEnabled(trace.isSelected());
            logDirectory.setEnabled(trace.isSelected() && logToFile.isSelected());
        }
        @Override protected JComponent createEditor() { return panel; }
        @Override protected void resetEditorFrom(DAPRunConfiguration c) {
            trace.setSelected(c.getServerTrace()!=ServerTrace.off);
            if(c instanceof Configuration own) {
                logToFile.setSelected(own.getOptions().getDapLogToFile());
                logDirectory.setText(own.getOptions().getDapLogDirectory());
            }
            updateLoggingControls();
            if(c instanceof Configuration own){ if(!own.modModule.isBlank() && java.util.stream.IntStream.range(0,module.getItemCount()).noneMatch(i->module.getItemAt(i).equals(own.modModule))) module.addItem(own.modModule); module.setSelectedItem(own.modModule); }
            executable.setText(c.getCommand()); cwd.setText(c.getWorkingDirectory()); save.setText(""); mods.setText(""); config.setText("");
            List<String> extras = new ArrayList<>();
            try {
                JsonArray args=JsonParser.parseString(c.getLaunchConfiguration()).getAsJsonObject().getAsJsonArray("factorioArgs");
                for(int i=0;i<args.size();i++) {
                    String arg=args.get(i).getAsString();
                    TextFieldWithBrowseButton field=switch(arg) { case "--load-game" -> save; case "--mod-directory" -> mods; case "--config" -> config; default -> null; };
                    if(field!=null && i+1<args.size()) field.setText(args.get(++i).getAsString()); else extras.add(arg);
                }
            } catch(Exception ignored) {}
            extra.setText(String.join("\n",extras));
        }
        @Override protected void applyEditorTo(DAPRunConfiguration c) {
            c.setServerTrace(trace.isSelected()?ServerTrace.verbose:ServerTrace.off);
            if(c instanceof Configuration own) {
                own.getOptions().setDapLogToFile(logToFile.isSelected());
                own.getOptions().setDapLogDirectory(logDirectory.getText().trim());
            }
            if(c instanceof Configuration own) own.modModule=Objects.toString(module.getSelectedItem(),"");
            c.setServerId(ID); c.setCommand(executable.getText().trim()); c.setWorkingDirectory(cwd.getText().trim()); c.setDebugMode(DebugMode.LAUNCH);
            JsonObject launch=new JsonObject(); launch.addProperty("request","launch"); launch.addProperty("followSymlinks",true); launch.addProperty("hookDebugConsole",true);
            JsonArray args=new JsonArray();
            for(var pair:List.of(Map.entry("--load-game",save),Map.entry("--mod-directory",mods),Map.entry("--config",config))) if(!pair.getValue().getText().isBlank()) { args.add(pair.getKey()); args.add(pair.getValue().getText().trim()); }
            extra.getText().lines().filter(s->!s.isBlank()).forEach(args::add); launch.add("factorioArgs",args); c.setLaunchConfiguration(launch.toString());
        }
    }
    public static final class DescriptorFactory extends DebugAdapterDescriptorFactory {
        @Override public DebugAdapterDescriptor createDebugAdapterDescriptor(RunConfigurationOptions options, ExecutionEnvironment environment) { return new Descriptor((DAPRunConfigurationOptions)options,environment,this); }
        @Override public SettingsEditor<? extends RunConfiguration> getConfigurationEditor(Project project) { return new Editor(project); }
        @Override public boolean supportsBreakpointType(XBreakpointType type) { return type instanceof Breakpoint; }
        @Override public boolean isDebuggableFile(VirtualFile file, Project project) { return FactorioSettings.servicesEnabled(project) && "lua".equals(file.getExtension()); }
    }
    public static final class Descriptor extends DebugAdapterDescriptor {
        private static final Key<Object> RESTART_DATA = Key.create("factorio.dap.restartData");
        private final DAPRunConfigurationOptions settings;
        private final String configurationName;
        private final FactorioDapRestart restart = new FactorioDapRestart();
        private final Object restartData;
        private FactorioDebugProcessHandler handler;
        private FactorioDapLog dapLog;
        public Descriptor(DAPRunConfigurationOptions options, ExecutionEnvironment environment, DescriptorFactory factory) {
            super(options,environment,factory.getServerDefinition());
            settings=options;
            configurationName=environment.getRunProfile().getName();
            restartData=environment.getUserData(RESTART_DATA);
            // Ephemeral launch data must not survive a later manual rerun or be persisted.
            environment.putUserData(RESTART_DATA,null);
        }
        private synchronized FactorioDapLog fileLog(DAPDebugProcess process, ServerTrace trace) {
            if(trace==ServerTrace.off || !(settings instanceof Options options) || !options.getDapLogToFile()) return null;
            if(dapLog==null) dapLog=new FactorioDapLog(options::logDirectory,configurationName,
                path -> process.print("DAP log: "+path,com.intellij.execution.ui.ConsoleViewContentType.SYSTEM_OUTPUT),
                failure -> process.print("DAP file logging disabled: "+failure.getMessage(),com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT));
            return dapLog;
        }
        private synchronized void closeFileLog() { if(dapLog!=null) dapLog.close(); }
        @Override public ProcessHandler startServer() throws com.intellij.execution.ExecutionException {
            handler = new FactorioDebugProcessHandler(new GeneralCommandLine(settings.getCommand(),"--dap").withWorkDirectory(settings.getWorkingDirectory()).withCharset(java.nio.charset.StandardCharsets.UTF_8));
            handler.onStopRequested(restart::cancel);
            com.intellij.execution.process.ProcessTerminatedListener.attach(handler);
            return handler;
        }
        @Override public com.redhat.devtools.lsp4ij.dap.client.DAPClient createClient(DAPDebugProcess process, Map<String,Object> parameters, boolean debug, DebugMode mode, com.redhat.devtools.lsp4ij.settings.ServerTrace trace, com.redhat.devtools.lsp4ij.dap.client.DAPClient parent) {
            var client = new com.redhat.devtools.lsp4ij.dap.client.DAPClient(process, parameters, debug, mode, trace, parent) {
                private final java.util.concurrent.atomic.AtomicBoolean disposing = new java.util.concurrent.atomic.AtomicBoolean();
                private final java.util.concurrent.CompletableFuture<Void> protocolClosed = new java.util.concurrent.CompletableFuture<>();
                private volatile boolean listeningStarted;
                private final FactorioDapShutdown shutdown = new FactorioDapShutdown(this::getDebugProtocolServer,
                        this::isSupportsTerminateRequest, () -> { if (handler != null) handler.stopAdapter(); else dispose(); });
                @Override protected org.eclipse.lsp4j.jsonrpc.Launcher<? extends org.eclipse.lsp4j.debug.services.IDebugProtocolServer> createLauncher(
                        java.util.function.UnaryOperator<org.eclipse.lsp4j.jsonrpc.MessageConsumer> wrapper,
                        java.io.InputStream in,java.io.OutputStream out,java.util.concurrent.ExecutorService executor) {
                    var log=fileLog(process,trace);
                    var delegate = super.createLauncher(FactorioDapLog.route(log,wrapper,
                        error -> process.print(error,com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT)),in,out,executor);
                    return new org.eclipse.lsp4j.jsonrpc.Launcher<org.eclipse.lsp4j.debug.services.IDebugProtocolServer>() {
                        @Override public org.eclipse.lsp4j.debug.services.IDebugProtocolServer getRemoteProxy() { return delegate.getRemoteProxy(); }
                        @Override public org.eclipse.lsp4j.jsonrpc.RemoteEndpoint getRemoteEndpoint() { return delegate.getRemoteEndpoint(); }
                        @Override public java.util.concurrent.Future<Void> startListening() {
                            var listening = delegate.startListening();
                            listeningStarted = true;
                            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                                try { listening.get(); }
                                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                                catch (java.util.concurrent.ExecutionException | java.util.concurrent.CancellationException closed) { /* Transport ended. */ }
                                finally {
                                    if (handler == null || !handler.getProcess().isAlive()) shutdown.exited();
                                    protocolClosed.complete(null);
                                    dispose();
                                }
                            });
                            return listening;
                        }
                    };
                }
                @Override public java.util.concurrent.CompletableFuture<Void> connectToServer(com.intellij.openapi.progress.ProgressIndicator indicator) {
                    try {
                        return super.connectToServer(indicator).whenComplete((ignored,failure) -> {
                            if(failure!=null) dispose();
                        });
                    } catch(RuntimeException failure) { dispose(); throw failure; }
                }
                @Override public void dispose() {
                    if (!disposing.compareAndSet(false, true)) return;
                    if (parent != null || handler == null) { releaseClient(); return; }
                    if (handler.getProcess().isAlive()) shutdown.stop();
                    ApplicationManager.getApplication().executeOnPooledThread(() -> {
                        // waitFor includes IntelliJ's stdout readers. The DAP parser must
                        // then consume their final messages before its pipes are closed.
                        handler.waitFor(15_000);
                        shutdown.exited();
                        try { if (listeningStarted) protocolClosed.get(5, java.util.concurrent.TimeUnit.SECONDS); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                        catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException closed) { /* Bounded cleanup. */ }
                        finally { releaseClient(); }
                    });
                }
                private void releaseClient() {
                    try { super.dispose(); } finally { if(parent==null) closeFileLog(); }
                }
                @Override public void terminate() {
                    shutdown.stop();
                }
                @Override public void terminated(org.eclipse.lsp4j.debug.TerminatedEventArguments args) {
                    if (parent==null && handler!=null && FactorioDapRestart.isRequested(args.getRestart())) {
                        if (restart.request(args.getRestart())) {
                            process.print("Factorio requested a restart. Waiting for the game to exit...",
                                    com.intellij.execution.ui.ConsoleViewContentType.SYSTEM_OUTPUT);
                            restartAfterExit(process);
                            shutdown.terminated(true);
                        } else shutdown.terminated(false);
                        // Duplicate events and events arriving after Stop must not launch again.
                        return;
                    }
                    shutdown.terminated(false);
                    super.terminated(args);
                }
            };
            // The IDE's Stop action can destroy the process before DAPDebugProcess.stop.
            if (parent == null && handler != null) handler.onStop(client::terminate);
            return client;
        }
        @Override public Map<String,Object> getDapParameters() {
            var type = new com.google.gson.reflect.TypeToken<Map<String,Object>>(){}.getType();
            Map<String,Object> parameters=PathsAndMods.JSON.fromJson(settings.getLaunchConfiguration(),type);
            if (restartData!=null) parameters.put("__restart",restartData);
            return parameters;
        }
        private void restartAfterExit(DAPDebugProcess process) {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                // Wait for the process handler as well as the OS process: IDEA must finish
                // the old session and Factorio must release its write-data lock first.
                if (!handler.waitFor(15_000)) {
                    restart.cancel();
                    process.print("Factorio did not exit within 15 seconds; automatic restart cancelled.",
                            com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT);
                    handler.destroyProcess();
                    return;
                }
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (environment.getProject().isDisposed() || !environment.getProject().isOpen()) {
                        restart.cancel();
                        return;
                    }
                    var content=process.getSession().getRunContentDescriptor();
                    if (com.intellij.openapi.util.Disposer.isDisposed(content)) { restart.cancel(); return; }
                    Object data=restart.take();
                    if (data==null) return;
                    var next=restartEnvironment(environment,content,data);
                    ExecutionManager.getInstance(environment.getProject()).restartRunProfile(next);
                });
            });
        }
        private static ExecutionEnvironment restartEnvironment(ExecutionEnvironment previous,
                com.intellij.execution.ui.RunContentDescriptor content,Object data) {
            var next=new ExecutionEnvironmentBuilder(previous).contentToReuse(content).build();
            next.putUserData(RESTART_DATA,data);
            return next;
        }
        @Override public FileType getFileType() { return FileTypeManager.getInstance().getFileTypeByExtension("lua"); }
        @Override public boolean isDebuggableFile(VirtualFile file, Project project) { return FactorioSettings.servicesEnabled(project) && "lua".equals(file.getExtension()); }
        @Override public DAPBreakpointHandlerBase<?> createBreakpointHandler(XDebugSession session,Project project) { return new Handler(session,this,project); }
        @Override public XDebuggerEditorsProvider createDebuggerEditorsProvider(FileType type,DAPDebugProcess process) {
            // Lua PSI fragments produced mismatched documents in the spike. DAP evaluates raw text.
            return new DAPDebuggerEditorsProvider(PlainTextFileType.INSTANCE,process) {
                @Override public com.intellij.openapi.editor.Document createDocument(Project project, XExpression expression, com.intellij.psi.PsiElement context, com.intellij.xdebugger.evaluation.EvaluationMode mode) {
                    return com.intellij.openapi.editor.EditorFactory.getInstance().createDocument(expression.getExpression());
                }
            };
        }
    }
    public static final class Breakpoint extends DAPBreakpointTypeBase<DAPBreakpointProperties> {
        public Breakpoint() { super("softwareforge-factorio-line", "Factorio Lua Breakpoints"); }
        @Override public DAPBreakpointProperties createBreakpointProperties(VirtualFile file,int line) { return new DAPBreakpointProperties(); }
        @Override public boolean canPutAt(VirtualFile file,int line,Project project) { return FactorioSettings.servicesEnabled(project) && "lua".equals(file.getExtension()); }
        @Override public int getPriority() { return 1000; }
    }
    public static final class Handler extends DAPBreakpointHandlerBase<XLineBreakpoint<DAPBreakpointProperties>> {
        public Handler(XDebugSession session,DebugAdapterDescriptor descriptor,Project project) { super(Breakpoint.class,session,descriptor,project); }
    }
}
