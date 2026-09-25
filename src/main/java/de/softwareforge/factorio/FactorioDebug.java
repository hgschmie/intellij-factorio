package de.softwareforge.factorio;

import com.google.gson.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.components.BaseState;
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
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import java.awt.GridLayout;

public final class FactorioDebug {
    public static final String ID = "softwareforge.factorio.debug";
    public static final class Type extends ConfigurationTypeBase {
        public Type() { super("SoftwareforgeFactorio", "Factorio", "Run and debug a Factorio mod", NotNullLazyValue.createValue(() -> AllIcons.Debugger.Console)); addFactory(new Factory(this)); }
    }
    public static final class Factory extends ConfigurationFactory {
        public Factory(ConfigurationType type) { super(type); }
        @Override public String getId() { return "Factorio"; }
        @Override public RunConfiguration createTemplateConfiguration(Project project) { return new Configuration(project,this,"Factorio"); }
        @Override public Class<? extends BaseState> getOptionsClass() { return DAPRunConfigurationOptions.class; }
    }
    public static final class Configuration extends DAPRunConfiguration {
        public Configuration(Project project, ConfigurationFactory factory, String name) {
            super(project,factory,name); setServerId(ID); setServerName("Factorio");
            setCommand(FactorioSettings.get(project).factorio); setWorkingDirectory(project.getBasePath());
            setDebugMode(DebugMode.LAUNCH); setLaunchConfiguration("{\"request\":\"launch\",\"factorioArgs\":[],\"followSymlinks\":true,\"hookDebugConsole\":true}");
        }
        @Override public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() { return new Editor(); }
        @Override public void checkConfiguration() throws RuntimeConfigurationException {
            if (getCommand()==null || !Files.isExecutable(Path.of(getCommand()))) throw new RuntimeConfigurationError("Select an executable Factorio installation");
            try { JsonParser.parseString(getLaunchConfiguration()).getAsJsonObject().getAsJsonArray("factorioArgs"); }
            catch (Exception e) { throw new RuntimeConfigurationError("Invalid Factorio launch arguments"); }
        }
    }
    public static final class Editor extends SettingsEditor<DAPRunConfiguration> {
        private final JPanel panel = new JPanel(new GridLayout(0,1,4,4));
        private final JTextField executable=field("Factorio executable"), cwd=field("Working directory"), save=field("Save ZIP (optional)"), mods=field("Mod directory"), config=field("Factorio config.ini (use isolated write-data for tests)");
        private final JTextArea extra = new JTextArea(3,50);
        public Editor() { panel.add(new JLabel("Additional Factorio arguments (one argument per line)")); panel.add(new JScrollPane(extra)); }
        private JTextField field(String label) { panel.add(new JLabel(label)); var result=new JTextField(50); panel.add(result); return result; }
        @Override protected JComponent createEditor() { return panel; }
        @Override protected void resetEditorFrom(DAPRunConfiguration c) {
            executable.setText(c.getCommand()); cwd.setText(c.getWorkingDirectory()); save.setText(""); mods.setText(""); config.setText("");
            List<String> extras = new ArrayList<>();
            try {
                JsonArray args=JsonParser.parseString(c.getLaunchConfiguration()).getAsJsonObject().getAsJsonArray("factorioArgs");
                for(int i=0;i<args.size();i++) {
                    String arg=args.get(i).getAsString();
                    JTextField field=switch(arg) { case "--load-game" -> save; case "--mod-directory" -> mods; case "--config" -> config; default -> null; };
                    if(field!=null && i+1<args.size()) field.setText(args.get(++i).getAsString()); else extras.add(arg);
                }
            } catch(Exception ignored) {}
            extra.setText(String.join("\n",extras));
        }
        @Override protected void applyEditorTo(DAPRunConfiguration c) {
            c.setServerId(ID); c.setCommand(executable.getText().trim()); c.setWorkingDirectory(cwd.getText().trim()); c.setDebugMode(DebugMode.LAUNCH);
            JsonObject launch=new JsonObject(); launch.addProperty("request","launch"); launch.addProperty("followSymlinks",true); launch.addProperty("hookDebugConsole",true);
            JsonArray args=new JsonArray();
            for(var pair:List.of(Map.entry("--load-game",save),Map.entry("--mod-directory",mods),Map.entry("--config",config))) if(!pair.getValue().getText().isBlank()) { args.add(pair.getKey()); args.add(pair.getValue().getText().trim()); }
            extra.getText().lines().filter(s->!s.isBlank()).forEach(args::add); launch.add("factorioArgs",args); c.setLaunchConfiguration(launch.toString());
        }
    }
    public static final class DescriptorFactory extends DebugAdapterDescriptorFactory {
        @Override public DebugAdapterDescriptor createDebugAdapterDescriptor(RunConfigurationOptions options, ExecutionEnvironment environment) { return new Descriptor((DAPRunConfigurationOptions)options,environment,this); }
        @Override public SettingsEditor<? extends RunConfiguration> getConfigurationEditor(Project project) { return new Editor(); }
        @Override public boolean supportsBreakpointType(XBreakpointType type) { return type instanceof Breakpoint; }
        @Override public boolean isDebuggableFile(VirtualFile file, Project project) { return FactorioSettings.get(project).enabled && "lua".equals(file.getExtension()); }
    }
    public static final class Descriptor extends DebugAdapterDescriptor {
        private final DAPRunConfigurationOptions settings;
        public Descriptor(DAPRunConfigurationOptions options, ExecutionEnvironment environment, DescriptorFactory factory) { super(options,environment,factory.getServerDefinition()); settings=options; }
        @Override public ProcessHandler startServer() throws com.intellij.execution.ExecutionException {
            return startServer(new GeneralCommandLine(settings.getCommand(),"--dap").withWorkDirectory(settings.getWorkingDirectory()));
        }
        @Override public Map<String,Object> getDapParameters() {
            var type = new com.google.gson.reflect.TypeToken<Map<String,Object>>(){}.getType();
            return PathsAndMods.JSON.fromJson(settings.getLaunchConfiguration(),type);
        }
        @Override public FileType getFileType() { return FileTypeManager.getInstance().getFileTypeByExtension("lua"); }
        @Override public boolean isDebuggableFile(VirtualFile file, Project project) { return FactorioSettings.get(project).enabled && "lua".equals(file.getExtension()); }
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
        @Override public boolean canPutAt(VirtualFile file,int line,Project project) { return FactorioSettings.get(project).enabled && "lua".equals(file.getExtension()); }
        @Override public int getPriority() { return 1000; }
    }
    public static final class Handler extends DAPBreakpointHandlerBase<XLineBreakpoint<DAPBreakpointProperties>> {
        public Handler(XDebugSession session,DebugAdapterDescriptor descriptor,Project project) { super(Breakpoint.class,session,descriptor,project); }
    }
}
