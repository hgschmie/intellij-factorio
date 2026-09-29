package de.softwareforge.factorio;

import com.cppcxy.ide.lsp.*;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Alarm;
import com.redhat.devtools.lsp4ij.*;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.client.features.*;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.server.*;
import com.redhat.devtools.lsp4ij.server.definition.*;
import com.tang.intellij.lua.editor.LuaGutterCacheManager;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageServer;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** One independent analyzer and locale index per mod; definitions live only for this project. */
@Service(Service.Level.PROJECT)
public final class FactorioServerManager implements Disposable {
    private final Project project;
    private final Alarm configAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    private final Set<Path> pendingConfigRoots = new HashSet<>();
    private final Alarm syncAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    private volatile List<ModLanguageScope> scopes = List.of();
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private volatile boolean disposed;
    public FactorioServerManager(Project project) {
        this.project = project;
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
            @Override public void documentChanged(DocumentEvent event) { scheduleSync(); }
        }, this);
        project.getMessageBus().connect(this).subscribe(com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES,
            new com.intellij.openapi.vfs.newvfs.BulkFileListener() {
                private final Set<Path> changedConfigs = new HashSet<>();
                private void collect(List<? extends com.intellij.openapi.vfs.newvfs.events.VFileEvent> events) {
                    for (var event : events) {
                        Path path = Path.of(event.getPath());
                        if (Set.of(".emmyrc.json", ".luarc.json", ".emmyrc.lua").contains(path.getFileName().toString()))
                            changedConfigs.add(ModLanguageScope.canonical(path.getParent()));
                        // For rename/move events, getPath can still refer to the old location.
                        var file = event.getFile();
                        if (file != null && file.isValid() && Set.of(".emmyrc.json", ".luarc.json", ".emmyrc.lua").contains(file.getName()))
                            changedConfigs.add(ModLanguageScope.canonical(Path.of(file.getParent().getPath())));
                    }
                }
                @Override public void before(List<? extends com.intellij.openapi.vfs.newvfs.events.VFileEvent> events) {
                    changedConfigs.clear();
                    collect(events);
                }
                @Override public void after(List<? extends com.intellij.openapi.vfs.newvfs.events.VFileEvent> events) {
                    collect(events);
                    if (changedConfigs.isEmpty()) return;
                    var roots = Set.copyOf(changedConfigs);
                    changedConfigs.clear();
                    onEdt(() -> {
                        if (disposed || project.isDisposed()) return;
                        pendingConfigRoots.addAll(roots);
                        configAlarm.cancelAllRequests();
                        configAlarm.addRequest(() -> {
                            var pending = Set.copyOf(pendingConfigRoots);
                            pendingConfigRoots.clear();
                            restartLuaRoots(pending);
                        }, 300);
                    });
                }
            });
        project.getMessageBus().connect(this).subscribe(FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {
            @Override public void beforeDocumentSaving(com.intellij.openapi.editor.Document document) { scheduleSync(); }
        });
    }
    public static FactorioServerManager get(Project project) { return project.getService(FactorioServerManager.class); }
    private String id(ModLanguageScope scope, boolean lua) {
        return "de.softwareforge.factorio." + (lua ? "lua." : "locale.") + ModLanguageScope.identity(Toolkit.root(project),scope.root());
    }
    public String luaServer(Path file) {
        if (disposed || !FactorioSettings.servicesEnabled(project)) return null;
        var owner = ModLanguageScope.owner(scopes,file,true);
        if (owner != null) return id(owner,true);
        // Reserve discovered mod files during configuration so the default server cannot claim them.
        var pending = FactorioModules.get(project).mods().stream().filter(m -> file.startsWith(m.root())).max(Comparator.comparingInt(m -> m.root().getNameCount())).orElse(null);
        return pending == null ? null : "de.softwareforge.factorio.lua." + ModLanguageScope.identity(Toolkit.root(project),pending.root());
    }
    public static final class Routing implements EmmyLuaServerProvider {
        @Override public String getServerId(Project project, VirtualFile file) {
            return get(project).luaServer(filePath(file));
        }
    }
    private boolean owns(ModLanguageScope scope, Path file, boolean lua) {
        return scope.equals(ModLanguageScope.owner(scopes,file,lua));
    }
    // Registry listeners update Swing synchronously. Queue before acquiring any
    // manager lock; waiting for the EDT while holding it can deadlock disposal.
    private static void onEdt(Runnable operation) {
        var application = ApplicationManager.getApplication();
        if (application.isDispatchThread()) operation.run();
        else application.invokeLater(operation, ModalityState.any());
    }
    public void configure(List<ModLanguageScope> next) {
        var snapshot = List.copyOf(next);
        onEdt(() -> configureOnEdt(snapshot));
    }
    private synchronized void configureOnEdt(List<ModLanguageScope> next) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        if (disposed || project.isDisposed()) return;
        var old = scopes;
        scopes = List.copyOf(next);
        var desired = new LinkedHashMap<String,Definition>();
        for (var scope : next) for (boolean lua : List.of(true,false)) {
            String id = id(scope,lua);
            var existing = definitions.get(id);
            var replacement = new Definition(scope,lua);
            desired.put(id, existing != null && existing.fingerprint.equals(replacement.fingerprint) ? existing : replacement);
        }
        var registry = LanguageServersRegistry.getInstance();
        for (var entry : definitions.entrySet()) if (desired.get(entry.getKey()) != entry.getValue()) {
            entry.getValue().active = false;
            registry.removeServerDefinition(project,entry.getValue());
        }
        for (var entry : desired.entrySet()) if (definitions.get(entry.getKey()) != entry.getValue()) {
            var d = entry.getValue();
            DocumentMatcher matcher = (file,p) -> d.isEnabled(p) && owns(d.scope,filePath(file),d.lua) && accepts(file,d.lua);
            registry.registerAssociation(d,new ServerLanguageMapping(Objects.requireNonNull(Language.findLanguageByID("Lua")),d.getId(),"lua",matcher));
            if (!d.lua) {
                registry.registerAssociation(d,new ServerFileNamePatternMapping(List.of("*.cfg"),d.getId(),"factorio-locale",matcher));
                registry.registerAssociation(d,new ServerFileNamePatternMapping(List.of("changelog.txt"),d.getId(),"factorio-changelog",matcher));
            }
            registry.addServerDefinition(project,d,null);
        }
        definitions.clear(); definitions.putAll(desired);
        if (!old.equals(next)) {
            // Drop existing default-server document connections; it may reconnect only unclaimed Lua files.
            LanguageServerManager.getInstance(project).stop("EmmyLua",new LanguageServerManager.StopOptions().setWillDisable(false));
            refreshEditors();
        }
        scheduleSync();
    }
    private static Path filePath(VirtualFile file) {
        String path = file.getCanonicalPath();
        return Path.of(path == null ? file.getPath() : path);
    }
    private static boolean accepts(VirtualFile file, boolean lua) {
        return lua ? "lua".equals(file.getExtension()) : "lua".equals(file.getExtension()) ||
            file.getName().equals("changelog.txt") || file.getPath().matches(".*/locale/[^/]+/[^/]+\\.cfg");
    }
    public void retain(List<ModDiscovery.Mod> mods) {
        var snapshot = List.copyOf(mods);
        onEdt(() -> configureOnEdt(scopes.stream().filter(s -> snapshot.stream().anyMatch(m -> m.root().equals(s.root()))).toList()));
    }
    public void restart() {
        onEdt(this::restartOnEdt);
    }
    private synchronized void restartOnEdt() {
        if (disposed || project.isDisposed()) return;
        configureOnEdt(scopes);
        for (var d : definitions.values()) LanguageServerManager.getInstance(project).start(d,new LanguageServerManager.StartOptions().setForceRestart(true));
        refreshEditors();
    }
    private synchronized void restartLuaRoots(Set<Path> roots) {
        if (disposed || project.isDisposed()) return;
        for (var definition : definitions.values()) {
            if (definition.lua && roots.contains(definition.scope.root()))
                LanguageServerManager.getInstance(project).start(definition,
                    new LanguageServerManager.StartOptions().setForceRestart(true));
        }
    }
    private void refreshEditors() {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (disposed || project.isDisposed()) return;
            for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
                LuaGutterCacheManager.INSTANCE.clearCache(file.getUrl());
                var psi = PsiManager.getInstance(project).findFile(file);
                if (psi != null) {
                    if (LSPFileSupport.hasSupport(psi)) LSPFileSupport.getSupport(psi).dispose();
                    DaemonCodeAnalyzer.getInstance(project).restart(psi);
                }
            }
        });
    }
    private void scheduleSync() {
        if (disposed || project.isDisposed()) return;
        syncAlarm.cancelAllRequests(); syncAlarm.addRequest(this::syncDependencies,250);
    }
    private record Buffer(String uri, Path path, String text, long stamp, String language) {}
    private void syncDependencies() {
        if (disposed || project.isDisposed()) return;
        var buffers = ReadAction.compute(() -> {
            var result = new ArrayList<Buffer>();
            var documents = FileDocumentManager.getInstance();
            for (var document : documents.getUnsavedDocuments()) {
                var file = documents.getFile(document);
                if (file != null && accepts(file,false)) result.add(new Buffer(file.getUrl(),filePath(file),document.getText(),document.getModificationStamp(),
                    file.getName().endsWith(".lua") ? "lua" : file.getName().equals("changelog.txt") ? "factorio-changelog" : "factorio-locale"));
            }
            return result;
        });
        synchronized (this) {
            for (var d : definitions.values()) if (d.features != null) d.features.sync(buffers);
        }
    }
    @Override public void dispose() {
        List<Definition> removed;
        synchronized (this) {
            disposed = true;
            scopes = List.of();
            removed = List.copyOf(definitions.values());
            removed.forEach(d -> d.active = false);
            definitions.clear();
        }
        // Do not expire this callback with the project: global registry cleanup
        // must still run when project disposal originated on a background thread.
        onEdt(() -> removed.forEach(d -> LanguageServersRegistry.getInstance().removeServerDefinition(project,d)));
    }
    private final class Definition extends LanguageServerDefinition {
        final ModLanguageScope scope;
        final boolean lua;
        final List<String> command;
        final String fingerprint;
        volatile boolean active = true;
        volatile Features features;
        Definition(ModLanguageScope scope, boolean lua) {
            super(id(scope,lua), scope.name() + (lua ? " — EmmyLua" : " — Factorio locale"), "Per-mod Factorio language service",false,60,false);
            this.scope=scope; this.lua=lua;
            command = lua ? List.of(EmmyLuaAnalyzerAdaptor.INSTANCE.getEmmyLuaLanguageServer(),"--editor","intellij","--log-level",ApplicationManager.getApplication().isUnitTestMode() ? "debug" : "info","--log-path",scope.workspace().getParent().resolve("logs/"+scope.name()).toString(),"--resources-path",scope.workspace().getParent().resolve("data-"+scope.name()).resolve("emmylua_ls/resources").toString())
                : List.copyOf(Toolkit.command(project,"lsp","--stdio"));
            fingerprint = scope.toString()+command;
        }
        @Override public boolean isEnabled(Project p) { return p == project && active && !disposed && FactorioSettings.servicesEnabled(p) && super.isEnabled(p); }
        @Override public StreamConnectionProvider createConnectionProvider(Project p) {
            var provider = new OSProcessStreamConnectionProvider();
            var line = new GeneralCommandLine(command).withWorkDirectory((lua ? scope.root() : scope.workspace()).toFile());
            // Match the analyzer's default resource location as well as --resources-path:
            // its extraction guard checks the default version marker.
            String data=scope.workspace().getParent().resolve("data-"+scope.name()).toString();
            if (lua) {
                line.withEnvironment("XDG_DATA_HOME",data).withEnvironment("LOCALAPPDATA",data);
                // Global analyzer configs append roots/libraries; keep this process's
                // config search separate so they cannot reconnect unrelated mods.
                String home=scope.workspace().getParent().resolve("home-"+scope.name()).toString();
                line.withEnvironment("HOME",home).withEnvironment("USERPROFILE",home)
                    .withEnvironment("XDG_CONFIG_HOME",home).withEnvironment("APPDATA",home)
                    .withEnvironment("EMMYLUALS_CONFIG",scope.workspace().resolve("no-external-config").toString());
            }
            provider.setCommandLine(line); return provider;
        }
        @Override public Class<? extends LanguageServer> getServerInterface() { return lua ? EmmyLuaCustomApi.class : LanguageServer.class; }
        @Override public LSPClientFeatures createClientFeatures() { features = new Features(this); return features; }
        @Override public LanguageClientImpl createLanguageClient(Project p) {
            return new LanguageClientImpl(p) {
                @Override public CompletableFuture<List<Object>> configuration(ConfigurationParams params) {
                    // Never inherit project-wide LSP4IJ settings into an isolated mod server.
                    var settings = new ArrayList<Object>();
                    for (var item : params.getItems()) {
                        boolean matches = item.getScopeUri() == null;
                        if (!matches) {
                            try { matches = scope.root().equals(ModLanguageScope.canonical(Path.of(URI.create(item.getScopeUri())))); }
                            catch (IllegalArgumentException ignored) { /* Unsupported resource scope. */ }
                        }
                        settings.add(lua && matches && "emmylua".equals(item.getSection()) ? scope.config().deepCopy() : null);
                    }
                    return CompletableFuture.completedFuture(settings);
                }
                @Override public CompletableFuture<Void> registerCapability(RegistrationParams params) {
                    for (var registration : params.getRegistrations()) if (registration.getMethod().equals("workspace/didChangeWatchedFiles")) registration.setRegisterOptions(scope.watchers(lua));
                    return super.registerCapability(params);
                }
                @Override public void publishDiagnostics(PublishDiagnosticsParams params) {
                    try { if (active && owns(scope,Path.of(URI.create(params.getUri())),lua)) super.publishDiagnostics(params); }
                    catch (IllegalArgumentException ignored) { /* Non-file server resources have no editor owner. */ }
                }
            };
        }
    }
    private final class Features extends LSPClientFeatures {
        private final Definition definition;
        private final Map<String,Long> stamps = new HashMap<>();
        private final Map<String,Integer> versions = new HashMap<>();
        private boolean ready;
        Features(Definition definition) {
            this.definition=definition;
            setWorkspaceFolderFeature(new LSPWorkspaceFolderFeature() {
                @Override protected WorkspaceFolderStrategy createStrategy() {
                    return new WorkspaceFolderStrategy() {
                        @Override public boolean sendAllFoldersOnInitialization() { return true; }
                        @Override public List<WorkspaceFolder> getWorkspaceFolders(Project p, FileUriSupport uri) {
                            var roots = definition.lua ? List.of(definition.scope.root()) : definition.scope.roots(false);
                            return roots.stream().map(path -> new WorkspaceFolder(path.toUri().toString(),path.getFileName().toString())).toList();
                        }
                        @Override public WorkspaceFolder getWorkspaceFolderForFile(VirtualFile f,Project p,FileUriSupport uri) { return null; }
                    };
                }
            });
        }
        @Override public boolean isEnabled(VirtualFile file) { return definition.active && owns(definition.scope,filePath(file),definition.lua) && accepts(file,definition.lua); }
        @Override public void initializeParams(InitializeParams params) {
            Path root = definition.lua ? definition.scope.root() : definition.scope.workspace();
            params.setRootUri(root.toUri().toString());
            params.setRootPath(root.toString());
            // Native module files supply preferences; this client supplies only Factorio integration.
            params.getCapabilities().getWorkspace().setConfiguration(definition.lua);
            // IntelliJ runs Backgroundable progress synchronously in unit-test mode;
            // LSP4IJ's progress consumer then blocks its own notification queue.
            if (ApplicationManager.getApplication().isUnitTestMode()) params.getCapabilities().getWindow().setWorkDoneProgress(false);
        }
        @Override public synchronized void handleServerStatusChanged(ServerStatus status) {
            ready = status == ServerStatus.started;
            stamps.clear(); versions.clear();
            if (ready) scheduleSync();
        }
        synchronized void sync(List<Buffer> buffers) {
            if (!ready || !definition.active) return;
            var server = getLanguageServer(); if (server == null) return;
            var retained = new HashSet<String>();
            for (var buffer : buffers) {
                if (definition.lua && !buffer.language.equals("lua")) continue;
                if (!definition.scope.includes(buffer.path,definition.lua) || owns(definition.scope,buffer.path,definition.lua)) continue;
                retained.add(buffer.uri);
                if (Objects.equals(stamps.get(buffer.uri),buffer.stamp)) continue;
                int version = versions.getOrDefault(buffer.uri,0)+1;
                if (!stamps.containsKey(buffer.uri)) server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(buffer.uri,buffer.language,version,buffer.text)));
                else server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(buffer.uri,version),List.of(new TextDocumentContentChangeEvent(buffer.text))));
                stamps.put(buffer.uri,buffer.stamp); versions.put(buffer.uri,version);
            }
            for (String uri : new ArrayList<>(stamps.keySet())) if (!retained.contains(uri)) {
                server.getTextDocumentService().didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));
                stamps.remove(uri); versions.remove(uri);
            }
        }
    }
}
