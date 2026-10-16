package de.softwareforge.factorio;

import com.cppcxy.ide.lsp.EmmyLuaServerRouting;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.*;
import org.eclipse.lsp4j.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Exercises the registered definitions, real child processes, and workspace boundaries. */
public class PerModServersTest extends HeavyPlatformTestCase {
    private Path temporary;
    private Path workspace;
    @Override protected void setUp() throws Exception {
        super.setUp();
        workspace=Path.of(System.getProperty("factorio.test.workspace"));
        temporary=Files.createTempDirectory(Files.createDirectories(Path.of(System.getProperty("factorio.test.work"))),"server-manager-");
        Files.createDirectories(Toolkit.root(getProject()));
        var settings=FactorioSettings.get(getProject());
        settings.serviceMode="DISABLED";
        settings.cli=workspace.resolve("upstream/vscode-factoriomod-debug/dist/fmtk-cli.js").toString();
    }
    @Override protected void tearDown() throws Exception {
        try { FactorioServerManager.get(getProject()).configure(List.of()); }
        finally { super.tearDown(); }
    }
    private Path mod(String name,String member) throws Exception {
        Path root=Files.createDirectories(temporary.resolve(name));
        Files.writeString(root.resolve("info.json"),"{\"name\":\""+name+"\",\"version\":\"0.1.0\",\"factorio_version\":\"2.1\"}");
        Files.writeString(root.resolve("init.lua"),"---@class "+name+".State\n---@field "+member+" string\nThis = {}\n");
        Files.writeString(root.resolve("control.lua"),"local value = This."+member+"\nlocal key = 'same.key'\n");
        Files.createDirectories(root.resolve("locale/en"));
        Files.writeString(root.resolve("locale/en/test.cfg"),"[same]\nkey="+name+"\n");
        return root.toRealPath();
    }
    private void attach(List<Path> roots) {
        WriteAction.run(() -> {
            var modules=ModuleManager.getInstance(getProject());
            for(Path root:roots) {
                var module=modules.newModule(temporary.resolve(root.getFileName()+".iml"),"SOFTWAREFORGE_FACTORIO_MOD");
                ModuleRootModificationUtil.addContentRoot(module,root.toString());
            }
        });
        for(Path root:roots) file(root);
        FactorioModules.get(getProject()).refresh();
    }
    private ModLanguageScope scope(Path root,List<Path> dependencies) throws Exception {
        Path config=Files.createDirectories(temporary.resolve("config-"+root.getFileName()));
        var json=ModLanguageScope.configuration(root,List.of(),dependencies);
        return new ModLanguageScope(root,root.getFileName().toString(),config,List.of(),dependencies,json);
    }
    private <T> T await(CompletableFuture<T> future) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        while (!future.isDone() && System.nanoTime()<deadline) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue(); Thread.sleep(20);
        }
        return future.get(1,TimeUnit.SECONDS);
    }
    private void eventually(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        do {
            if (condition.getAsBoolean()) return;
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue(); Thread.sleep(100);
        } while (System.nanoTime()<deadline);
        fail("Timed out waiting for server state");
    }
    private LanguageServerItem server(String id) throws Exception {
        var result = new LanguageServerItem[1];
        eventually(() -> { try { result[0] = await(LanguageServerManager.getInstance(getProject()).getLanguageServer(id)); return result[0] != null; } catch (Exception e) { return false; } });
        return result[0];
    }
    private VirtualFile file(Path path) { return Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)); }
    private String definitions(LanguageServerItem item,Path file,int line,int column) throws Exception {
        var result=await(item.getServer().getTextDocumentService().definition(new DefinitionParams(new TextDocumentIdentifier(file.toUri().toString()),new Position(line,column))));
        return String.valueOf(result);
    }
    private String completions(LanguageServerItem item, Path path) throws Exception {
        return String.valueOf(await(item.getServer().getTextDocumentService().completion(new CompletionParams(new TextDocumentIdentifier(path.toUri().toString()),new Position(1,21)))));
    }
    public void testDefaultServerRespectsRootOwnershipAndRelease() throws Exception {
        // Keep LSP4IJ progress consumption asynchronous in the IDE test environment.
        String previous = System.getProperty("intellij.progress.task.ignoreHeadless");
        System.setProperty("intellij.progress.task.ignoreHeadless", "true");
        var manager = FactorioServerManager.get(getProject());
        try {
            Path parent = Toolkit.root(getProject()).toRealPath();
            Path owned = Files.move(mod("owned", "ownedValue"), parent.resolve("nested-mod"));
            Path external = mod("external", "externalValue");
            Path plain = parent.resolve("plain.lua");
            Files.writeString(parent.resolve("globals.lua"), "PlainOnlyMarker = {}\n");
            Files.writeString(owned.resolve("init.lua"), "OwnedOnlyMarker = {}\n");
            Files.writeString(owned.resolve("control.lua"), "local value = OwnedOnlyMarker\n");
            Files.writeString(plain, "local own = PlainOnlyMarker\nlocal foreign = OwnedOnlyMarker\nlocal ignored = VendorOnlyMarker\n");
            Files.writeString(Files.createDirectories(parent.resolve("vendor")).resolve("ignored.lua"), "VendorOnlyMarker = {}\n");
            // EmmyLua supports dotted as well as nested native configuration keys.
            Files.writeString(parent.resolve(".luarc.json"), "{\"workspace.ignoreDir\":[\"vendor\"]}");
            WriteAction.run(() -> ModuleRootModificationUtil.addContentRoot(getModule(), parent.toString()));
            attach(List.of(owned, external));
            for (int n=0;n<60;n++) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue(); Thread.sleep(20); }
            FactorioSettings.get(getProject()).serviceMode = "ENABLED";
            // Ownership is reserved before definition generation has finished.
            assertTrue(EmmyLuaServerRouting.getOwnedRoots(getProject()).contains(owned));
            manager.configure(List.of(scope(owned,List.of()), scope(external,List.of())));
            var ownServer = server(manager.luaServer(owned.resolve("control.lua")));
            var plainServer = server("EmmyLua");
            var alive = plainServer.keepAlive();
            try {
                eventually(() -> { try { return definitions(plainServer,plain,0,16).contains("globals.lua"); } catch(Exception e) { return false; } });
                assertFalse("Nested owned module must not be indexed by the default server",
                    definitions(plainServer,plain,1,20).contains("init.lua"));
                assertFalse("Native exclusions must survive the ownership overlay",
                    definitions(plainServer,plain,2,20).contains("ignored.lua"));
                eventually(() -> { try { return definitions(ownServer,owned.resolve("control.lua"),0,20).contains("init.lua"); } catch(Exception e) { return false; } });
                // Saved updates underneath the broad parent must remain excluded too.
                WriteAction.run(() -> file(owned.resolve("init.lua")).setBinaryContent("OwnedOnlyMarker = { updated = true }\n".getBytes()));
                Thread.sleep(600);
                assertFalse(definitions(plainServer,plain,1,20).contains("init.lua"));
            } finally { alive.dispose(); }

            // An explicit dependency is intentional and still usable despite source ownership.
            var config = new JsonObject(); var ws = new JsonObject(); var libraries = new com.google.gson.JsonArray();
            libraries.add(owned.toString()); ws.add("library",libraries); config.add("workspace",ws);
            Path rc = parent.resolve(".luarc.json"); Files.writeString(rc,config.toString());
            EmmyLuaServerRouting.ownershipChanged(getProject());
            var importing = server("EmmyLua"); var importingAlive = importing.keepAlive();
            try {
                eventually(() -> { try { return definitions(importing,plain,1,20).contains("init.lua"); } catch(Exception e) { return false; } });
            } finally { importingAlive.dispose(); }
            Files.delete(rc);

            FactorioSettings.get(getProject()).serviceMode = "DISABLED";
            manager.configure(List.of());
            assertTrue(EmmyLuaServerRouting.getOwnedRoots(getProject()).isEmpty());
            assertEquals("EmmyLua",EmmyLuaServerRouting.getServerId(getProject(),file(owned.resolve("control.lua"))));
            var released = server("EmmyLua"); var releasedAlive = released.keepAlive();
            try {
                eventually(() -> { try { return definitions(released,plain,1,20).contains("init.lua"); } catch(Exception e) { return false; } });
            } finally { releasedAlive.dispose(); }
        } finally {
            LanguageServerManager.getInstance(getProject()).stop("EmmyLua",new LanguageServerManager.StopOptions().setWillDisable(false));
            if (previous == null) System.clearProperty("intellij.progress.task.ignoreHeadless");
            else System.setProperty("intellij.progress.task.ignoreHeadless", previous);
        }
    }

    public void testFormattingStaysWithinEachMod() throws Exception {
        Path a=mod("alpha","alphaValue"), b=mod("beta","betaValue");
        for(Path root:List.of(a,b)) {
            Files.writeString(root.resolve("format.lua"),"local  text = \"hello\"\n\n\nreturn   text\n");
            Files.writeString(root.resolve(".luafmt.toml"),"[output]\nquote_style = \"Single\"\n[layout]\nmax_blank_lines = 1\n");
        }
        attach(List.of(a,b));
        // Finish queued project/module startup while automatic services are disabled,
        // before installing the explicit test scopes below.
        for (int n=0;n<60;n++) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue(); Thread.sleep(20); }
        FactorioSettings.get(getProject()).serviceMode="ENABLED";
        var manager=FactorioServerManager.get(getProject());
        manager.configure(List.of(scope(a,List.of()),scope(b,List.of())));
        var as=server(manager.luaServer(a.resolve("format.lua")));
        var al=as.keepAlive();
        var bs=server(manager.luaServer(b.resolve("format.lua")));
        var bl=bs.keepAlive();
        var editors=com.intellij.openapi.fileEditor.FileEditorManager.getInstance(getProject());
        try {
            for(Path root:List.of(a,b)) {
                var vf=file(root.resolve("format.lua"));
                var psi=Objects.requireNonNull(PsiManager.getInstance(getProject()).findFile(vf));
                var own=root.equals(a) ? as : bs;
                var other=root.equals(a) ? bs : as;
                assertTrue(own.getClientFeatures().getFormattingFeature().isEnabled(psi));
                assertFalse("Other mod must not format this file",other.getClientFeatures().getFormattingFeature().isEnabled(psi));
                await(LanguageServiceAccessor.getInstance(getProject()).getLanguageServers(psi,null,null));
                editors.openFile(vf,true);
                var document=Objects.requireNonNull(FileDocumentManager.getInstance().getDocument(vf));
                com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(), () -> {
                    com.intellij.psi.codeStyle.CodeStyleManager.getInstance(getProject()).reformat(psi);
                });
                eventually(() -> document.getText().contains("'hello'"));
                assertEquals("local text = 'hello'\n\nreturn text\n",document.getText());
                // A selection must use the same owner and leave the following line alone.
                com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(), () -> {
                    document.setText("local  text = \"range\"\nreturn   text\n");
                    com.intellij.psi.PsiDocumentManager.getInstance(getProject()).commitDocument(document);
                    com.intellij.psi.codeStyle.CodeStyleManager.getInstance(getProject()).reformatText(psi,0,document.getLineEndOffset(0));
                });
                eventually(() -> document.getText().equals("local text = 'range'\nreturn   text\n"));
                editors.closeFile(vf);
            }
        } finally {
            for (var opened : editors.getOpenFiles()) editors.closeFile(opened);
            al.dispose(); bl.dispose();
        }
    }

    public void testBackgroundRegistryLifecycleRunsOnEdt() throws Exception {
        Path root=mod("alpha","alphaValue");
        attach(List.of(root));
        FactorioSettings.get(getProject()).serviceMode="ENABLED";
        var manager=FactorioServerManager.get(getProject());
        var initial=scope(root,List.of());
        var callbacks=new java.util.concurrent.CopyOnWriteArrayList<Boolean>();
        var added=new java.util.concurrent.atomic.AtomicInteger();
        var removed=new java.util.concurrent.atomic.AtomicInteger();
        var listener=new com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinitionListener() {
            @Override public void handleChanged(LanguageServerChangedEvent event) {}
            @Override public void handleAdded(LanguageServerAddedEvent event) {
                callbacks.add(com.intellij.openapi.application.ApplicationManager.getApplication().isDispatchThread());
                added.addAndGet(event.serverDefinitions.size());
            }
            @Override public void handleRemoved(LanguageServerRemovedEvent event) {
                callbacks.add(com.intellij.openapi.application.ApplicationManager.getApplication().isDispatchThread());
                removed.addAndGet(event.serverDefinitions.size());
            }
        };
        var registry=LanguageServersRegistry.getInstance();
        registry.addLanguageServerDefinitionListener(listener);
        try {
            await(CompletableFuture.runAsync(() -> manager.configure(List.of(initial))));
            eventually(() -> added.get()==2);
            assertFalse("Background registration notified listeners off EDT",callbacks.contains(false));
            var replacement=new ModLanguageScope(root,"renamed",initial.workspace(),List.of(),List.of(),initial.config());
            await(CompletableFuture.runAsync(() -> manager.configure(List.of(replacement))));
            eventually(() -> added.get()==4 && removed.get()==2);
            await(CompletableFuture.runAsync(() -> manager.retain(List.of())));
            eventually(() -> removed.get()==4);
            manager.configure(List.of(initial));
            eventually(() -> added.get()==6);
            String id=manager.luaServer(root.resolve("control.lua"));
            CompletableFuture.runAsync(() -> {
                manager.configure(List.of(replacement)); // queued before disposal; must be ignored
                manager.dispose();
                manager.configure(List.of(initial)); // must not resurrect definitions
            }).get(5,TimeUnit.SECONDS);
            eventually(() -> removed.get()==6);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            assertEquals(6,added.get());
            assertNull(registry.getServerDefinition(id));
            assertNull(registry.getServerDefinition(id.replace(".lua.",".locale.")));
            assertFalse("Registry notifications must all be on EDT: "+callbacks,callbacks.contains(false));
        } finally { registry.removeLanguageServerDefinitionListener(listener); }
    }

    public void testIndependentLuaAndLocaleServersAndRemoval() throws Exception {
        Path a=mod("alpha","alphaValue"),b=mod("beta","betaValue");
        var manager=FactorioServerManager.get(getProject());
        var one=scope(a,List.of()); var two=scope(b,List.of());
        WriteAction.run(() -> {
            var modules=ModuleManager.getInstance(getProject());
            var am=modules.newModule(temporary.resolve("alpha.iml"),"SOFTWAREFORGE_FACTORIO_MOD");
            var bm=modules.newModule(temporary.resolve("beta.iml"),"SOFTWAREFORGE_FACTORIO_MOD");
            ModuleRootModificationUtil.addContentRoot(am,a.toString());
            ModuleRootModificationUtil.addContentRoot(bm,b.toString());
        });
        file(a); file(b);
        FactorioModules.get(getProject()).refresh();
        FactorioSettings.get(getProject()).serviceMode="ENABLED";
        assertEquals(FactorioModules.get(getProject()).errors().toString(),2,FactorioModules.get(getProject()).mods().size());
        manager.configure(List.of(one,two));
        String aid=EmmyLuaServerRouting.getServerId(getProject(),file(a.resolve("control.lua")));
        String bid=EmmyLuaServerRouting.getServerId(getProject(),file(b.resolve("control.lua")));
        assertNotSame(aid,bid); assertFalse(aid.equals(bid));
        var as=server(aid); var bs=server(bid);
        assertNotSame(as.getServer(),bs.getServer());
        // Wait for asynchronous initial analysis to finish, then request real definitions.
        eventually(() -> { try { return definitions(as,a.resolve("control.lua"),0,16).contains("alpha/init.lua"); } catch(Exception e) { return false; } });
        assertTrue(definitions(bs,b.resolve("control.lua"),0,16).contains("beta/init.lua"));
        assertFalse(definitions(as,a.resolve("control.lua"),0,16).contains("beta/init.lua"));
        var al=server(aid.replace(".lua.",".locale.")); var bl=server(bid.replace(".lua.",".locale."));
        for(var pair:List.of(Map.entry(al,a),Map.entry(bl,b))) {
            Path path=pair.getValue().resolve("control.lua");
            pair.getKey().getServer().getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(path.toUri().toString(),"lua",1,Files.readString(path))));
        }
        eventually(() -> { try { return definitions(al,a.resolve("control.lua"),1,18).contains("alpha/locale/en/test.cfg"); } catch(Exception e) { return false; } });
        assertFalse(definitions(al,a.resolve("control.lua"),1,18).contains("beta/locale"));
        assertTrue(definitions(bl,b.resolve("control.lua"),1,18).contains("beta/locale/en/test.cfg"));
        var psi=Objects.requireNonNull(PsiManager.getInstance(getProject()).findFile(file(a.resolve("control.lua"))));
        var routed=await(LanguageServiceAccessor.getInstance(getProject()).getLanguageServers(psi,null,null));
        assertEquals(Set.of(aid,aid.replace(".lua.",".locale.")),new HashSet<>(routed.stream().map(i -> i.getServerDefinition().getId()).toList()));
        // Exercise real VFS watch delivery, not manually injected protocol notifications.
        VirtualFile localeDirectory=file(b.resolve("locale/en"));
        VirtualFile added=WriteAction.compute(() -> {
            var f=localeDirectory.createChildData(this,"added.cfg");
            f.setBinaryContent("[same]\nbetaAdded=Only beta\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)); return f;
        });
        eventually(() -> { try { return completions(bl,b.resolve("control.lua")).contains("same.betaAdded"); } catch(Exception e) { return false; } });
        assertFalse(completions(al,a.resolve("control.lua")).contains("same.betaAdded"));
        WriteAction.run(() -> added.delete(this));
        eventually(() -> { try { return !completions(bl,b.resolve("control.lua")).contains("same.betaAdded"); } catch(Exception e) { return false; } });
        var oldServer=as.getServer(); manager.restart();
        var restarted=server(aid); assertNotSame(oldServer,restarted.getServer());
        eventually(() -> { try { return definitions(restarted,a.resolve("control.lua"),0,16).contains("alpha/init.lua"); } catch(Exception e) { return false; } });
        WriteAction.run(() -> ModuleManager.getInstance(getProject()).disposeModule(Objects.requireNonNull(ModuleManager.getInstance(getProject()).findModuleByName("beta"))));
        FactorioModules.get(getProject()).refresh();
        manager.retain(FactorioModules.get(getProject()).mods());
        assertNull(LanguageServersRegistry.getInstance().getServerDefinition(bid));
        assertNull(LanguageServersRegistry.getInstance().getServerDefinition(bid.replace(".lua.",".locale.")));
        assertEquals("EmmyLua",EmmyLuaServerRouting.getServerId(getProject(),file(b.resolve("control.lua"))));
        assertEquals(aid,EmmyLuaServerRouting.getServerId(getProject(),file(a.resolve("control.lua"))));
        assertTrue(EmmyLuaServerRouting.getOwnedRoots(getProject()).contains(a));
        assertFalse(EmmyLuaServerRouting.getOwnedRoots(getProject()).contains(b));
    }
    public void testUnsavedDependencyReachesBothConsumers() throws Exception {
        Path a=mod("alpha","alphaValue"),b=mod("beta","betaValue"),dep=mod("dependency","unused");
        Files.delete(dep.resolve("init.lua")); Files.delete(dep.resolve("control.lua"));
        Path helper=dep.resolve("helper.lua"); Files.writeString(helper,"return { before_value = true }\n");
        for(Path root:List.of(a,b)) Files.writeString(root.resolve("control.lua"),"local dep = require('__dependency__/helper')\nlocal result = dep.\n");
        attach(List.of(a,b));
        var manager=FactorioServerManager.get(getProject()); FactorioSettings.get(getProject()).serviceMode="ENABLED";
        manager.configure(List.of(scope(a,List.of(dep)),scope(b,List.of(dep))));
        String aid=manager.luaServer(a.resolve("control.lua")),bid=manager.luaServer(b.resolve("control.lua"));
        var as=server(aid); var bs=server(bid);
        java.util.function.Function<LanguageServerItem,String> members=item -> {
            try {
                Path path=item==as ? a.resolve("control.lua") : b.resolve("control.lua");
                return String.valueOf(await(item.getServer().getTextDocumentService().completion(new CompletionParams(new TextDocumentIdentifier(path.toUri().toString()),new Position(1,19)))));
            } catch(Exception e) { throw new RuntimeException(e); }
        };
        eventually(() -> members.apply(bs).contains("before_value"));
        var vf=file(helper); var psi=Objects.requireNonNull(PsiManager.getInstance(getProject()).findFile(vf));
        var routed=await(LanguageServiceAccessor.getInstance(getProject()).getLanguageServers(psi,null,null));
        assertEquals(Set.of(aid,aid.replace(".lua.",".locale.")),new HashSet<>(routed.stream().map(i -> i.getServerDefinition().getId()).toList()));
        var document=Objects.requireNonNull(FileDocumentManager.getInstance().getDocument(vf));
        WriteAction.run(() -> document.setText("function unsaved_marker() end\nreturn { before_value = true, after_value = true }\n"));
        assertFalse(Files.readString(helper).contains("after_value"));
        // Verify buffer delivery directly. Analyzer 0.25.1 does not invalidate an
        // importer's cached table type until that importer is reanalyzed.
        for(var item:List.of(as,bs)) eventually(() -> {
            try { return String.valueOf(await(item.getServer().getTextDocumentService().documentSymbol(
                new DocumentSymbolParams(new TextDocumentIdentifier(helper.toUri().toString()))))).contains("unsaved_marker"); }
            catch(Exception e) { return false; }
        });
        for(var pair:List.of(Map.entry(as,a),Map.entry(bs,b))) {
            Path control=pair.getValue().resolve("control.lua");
            pair.getKey().getServer().getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
                new TextDocumentItem(control.toUri().toString(),"lua",1,Files.readString(control))));
        }
        eventually(() -> members.apply(as).contains("after_value"));
        eventually(() -> members.apply(bs).contains("after_value"));
        FileDocumentManager.getInstance().saveDocument(document);
        eventually(() -> members.apply(bs).contains("after_value"));
        // A disk replacement after save proves that the mirrored unsaved document was closed.
        WriteAction.run(() -> vf.setBinaryContent("function saved_marker() end\nreturn { saved_value = true }\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        eventually(() -> {
            try { return String.valueOf(await(bs.getServer().getTextDocumentService().documentSymbol(
                new DocumentSymbolParams(new TextDocumentIdentifier(helper.toUri().toString()))))).contains("saved_marker"); }
            catch(Exception e) { return false; }
        });
    }

    /** Observe LSP4IJ's cache, without requesting dependent-file diagnostics ourselves. */
    public void testWorkspaceDiagnosticsFollowUnsavedCrossFileEdits() throws Exception {
        Path root = mod("workspacepull", "value");
        Path types = root.resolve("types.lua"), control = root.resolve("control.lua");
        String optional = "---@class Provider\n";
        String required = optional + "---@field set_values fun()\n";
        Files.writeString(types, optional);
        Files.writeString(control, "---@type Provider\nlocal settings_table = {}\nreturn settings_table\n");
        attach(List.of(root));
        for (int n = 0; n < 60; n++) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue(); Thread.sleep(20); }
        FactorioSettings.get(getProject()).serviceMode = "ENABLED";
        var manager = FactorioServerManager.get(getProject());
        manager.configure(List.of(scope(root, List.of())));
        var item = server(manager.luaServer(control));
        var lease = item.keepAlive();
        var wrapper = item.getServerWrapper();
        var editors = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(getProject());
        var typesFile = file(types);
        var controlFile = file(control);
        try {
            var psi = Objects.requireNonNull(PsiManager.getInstance(getProject()).findFile(typesFile));
            await(LanguageServiceAccessor.getInstance(getProject()).getLanguageServers(psi, null, null));
            editors.openFile(typesFile, true);
            var document = Objects.requireNonNull(FileDocumentManager.getInstance().getDocument(typesFile));
            // Initial workspace pull populates the closed consumer's diagnostics.
            eventually(() -> wrapper.getClosedDocument(control.toUri(), false) != null);
            BooleanSupplier missing = () -> {
                var open = wrapper.getOpenedDocument(control.toUri());
                var cached = open != null ? open : wrapper.getClosedDocument(control.toUri(), false);
                return cached != null && cached.getDiagnostics().stream().anyMatch(d ->
                        d.getCode() != null && "missing-fields".equals(d.getCode().getLeft()));
            };
            assertFalse(missing.getAsBoolean());
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText(required));
            eventually(missing);
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText(optional));
            eventually(() -> !missing.getAsBoolean());
            // The same cross-file update must refresh a consumer open in the editor.
            var consumerPsi = Objects.requireNonNull(PsiManager.getInstance(getProject()).findFile(controlFile));
            await(LanguageServiceAccessor.getInstance(getProject()).getLanguageServers(consumerPsi, null, null));
            editors.openFile(controlFile, true);
            eventually(() -> wrapper.getOpenedDocument(control.toUri()) != null);
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText(required));
            eventually(missing);
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText(optional));
            eventually(() -> !missing.getAsBoolean());
        } finally {
            editors.closeFile(typesFile);
            editors.closeFile(controlFile);
            lease.dispose();
        }
    }

    private String diagnostics(LanguageServerItem item, Path path) throws Exception {
        var params = new DocumentDiagnosticParams();
        params.setTextDocument(new TextDocumentIdentifier(path.toUri().toString()));
        return String.valueOf(await(item.getServer().getTextDocumentService().diagnostic(params)));
    }

    private LanguageServerItem afterConfigRestart(FactorioServerManager manager, Path control, LanguageServerItem old) throws Exception {
        var current = new LanguageServerItem[1];
        eventually(() -> {
            try {
                current[0] = server(manager.luaServer(control));
                return current[0].getServer() != old.getServer();
            } catch (Exception e) { return false; }
        });
        return current[0];
    }

    public void testNativeModuleConfigurationsAndVfsReload() throws Exception {
        var names = List.of(".luarc.json", ".emmyrc.json", ".emmyrc.lua");
        var roots = new ArrayList<Path>();
        for (int i = 0; i < names.size(); i++) {
            Path root = mod("config"+i, "ownValue");
            Files.writeString(root.resolve("control.lua"), "print(missing_config_global)\n");
            Files.createDirectories(root.resolve("excluded"));
            Files.writeString(root.resolve("excluded/marker.lua"), "ConfigHidden = {}\n");
            Files.writeString(root.resolve("probe.lua"), "local x = ConfigHidden\n");
            String config = names.get(i).endsWith(".lua")
                ? "return { diagnostics = { disable = { 'undefined-global' } }, workspace = { ignoreDir = { './excluded' } } }"
                : "{\"diagnostics\":{\"disable\":[\"undefined-global\"]},\"workspace\":{\"ignoreDir\":[\"./excluded\"]}}";
            Files.writeString(root.resolve(names.get(i)), config);
            roots.add(root);
        }
        Path baseline = mod("baseline", "ownValue");
        Files.writeString(baseline.resolve("control.lua"), "print(missing_config_global)\n");
        roots.add(baseline);
        attach(roots);
        FactorioSettings.get(getProject()).serviceMode = "ENABLED";
        var manager = FactorioServerManager.get(getProject());
        FactorioSettings.get(getProject()).apiDocs = System.getProperty("factorio.test.apiDocs");
        await(CompletableFuture.runAsync(() -> {
            try { Definitions.generate(getProject(),new com.intellij.openapi.progress.EmptyProgressIndicator(),message -> {}); }
            catch(Exception e) { throw new CompletionException(e); }
        }));
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        var other = server(manager.luaServer(baseline.resolve("control.lua")));
        var locale = server(manager.luaServer(baseline.resolve("control.lua")).replace(".lua.", ".locale."));
        eventually(() -> { try { return diagnostics(other, baseline.resolve("control.lua")).contains("undefined-global"); } catch (Exception e) { return false; } });
        for (int i = 0; i < names.size(); i++) {
            Path root = roots.get(i), control = root.resolve("control.lua");
            var item = server(manager.luaServer(control));
            // Positive request establishes that initial indexing has completed.
            eventually(() -> { try { return definitions(item,root.resolve("init.lua"),2,1).contains("init.lua"); } catch (Exception e) { return false; } });
            assertFalse(diagnostics(item, control), diagnostics(item, control).contains("undefined-global"));
            assertFalse(definitions(item, root.resolve("probe.lua"), 0, 13).contains("marker.lua"));
            VirtualFile config = file(root.resolve(names.get(i)));
            WriteAction.run(() -> config.delete(this));
            var deleted = afterConfigRestart(manager, control, item);
            eventually(() -> { try { return diagnostics(deleted,control).contains("undefined-global") && definitions(deleted,root.resolve("probe.lua"),0,13).contains("marker.lua"); } catch (Exception e) { return false; } });
            String disabled = names.get(i).endsWith(".lua")
                ? "return { diagnostics = { disable = { 'undefined-global' } } }"
                : "{\"diagnostics\":{\"disable\":[\"undefined-global\"]}}";
            String empty = names.get(i).endsWith(".lua") ? "return {}" : "{}";
            String filename = names.get(i);
            VirtualFile restored = WriteAction.compute(() -> {
                var created = file(root).createChildData(this, filename);
                created.setBinaryContent(disabled.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return created;
            });
            var reloaded = afterConfigRestart(manager, control, deleted);
            eventually(() -> { try { return !diagnostics(reloaded,control).contains("undefined-global"); } catch (Exception e) { return false; } });
            WriteAction.run(() -> restored.setBinaryContent(empty.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var edited = afterConfigRestart(manager, control, reloaded);
            eventually(() -> { try { return diagnostics(edited,control).contains("undefined-global"); } catch (Exception e) { return false; } });
            if (filename.equals(".emmyrc.json")) {
                WriteAction.run(() -> restored.setBinaryContent(disabled.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                var beforeRename = afterConfigRestart(manager, control, edited);
                eventually(() -> { try { return !diagnostics(beforeRename,control).contains("undefined-global"); } catch (Exception e) { return false; } });
                WriteAction.run(() -> restored.rename(this, "emmy-config.backup"));
                var renamed = afterConfigRestart(manager, control, beforeRename);
                eventually(() -> { try { return diagnostics(renamed,control).contains("undefined-global"); } catch (Exception e) { return false; } });
            }
            assertSame("Reload must preserve the other module's server", other.getServer(), server(manager.luaServer(baseline.resolve("control.lua"))).getServer());
            assertSame("Locale services must not restart", locale.getServer(), server(manager.luaServer(baseline.resolve("control.lua")).replace(".lua.", ".locale.")).getServer());
            assertTrue(diagnostics(other, baseline.resolve("control.lua")).contains("undefined-global"));
        }
    }

    public void testSemanticTokensObserveImmediateEdits() throws Exception {
        Path root = mod("fresh_tokens", "state"), control = root.resolve("control.lua");
        String prefix = "---@type table?\nlocal port\n";
        Files.writeString(control, prefix + "    ---\nlocal value = port\n");
        attach(List.of(root));
        var manager = FactorioServerManager.get(getProject());
        FactorioSettings.get(getProject()).serviceMode = "ENABLED";
        manager.configure(List.of(scope(root, List.of())));
        var item = server(manager.luaServer(control));
        var service = item.getServer().getTextDocumentService();
        var identifier = new TextDocumentIdentifier(control.toUri().toString());
        var params = new SemanticTokensParams(identifier);
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(identifier.getUri(), "lua", 1, Files.readString(control))));
        var legend = item.getServerCapabilities().getSemanticTokensProvider().getLegend().getTokenTypes();
        int version = 1;
        try {
            for (int round = 0; round < 10; round++) {
                for (String annotation : List.of("    --- @c", "    --- @cast port -?")) {
                    service.didChange(new DidChangeTextDocumentParams(
                        new VersionedTextDocumentIdentifier(identifier.getUri(), ++version),
                        List.of(new TextDocumentContentChangeEvent(prefix + annotation + "\nlocal value = port\n"))));
                    // No diagnostic pull, retry or sleep between edit and token request.
                    var tokens = await(service.semanticTokensFull(params)).getData();
                    var names = new HashSet<String>();
                    int line = 0, column = 0;
                    for (int i = 0; i < tokens.size(); i += 5) {
                        int delta = tokens.get(i);
                        line += delta;
                        column = delta == 0 ? column + tokens.get(i + 1) : tokens.get(i + 1);
                        if (line != 2) continue;
                        int end = column + tokens.get(i + 2);
                        assertTrue("Token exceeds current annotation: " + tokens, end <= annotation.length());
                        names.add(annotation.substring(column, end) + ":" + legend.get(tokens.get(i + 3)));
                    }
                    assertTrue(names.toString(), names.contains("@:keyword"));
                    if (annotation.endsWith("@c")) assertTrue(names.toString(), names.contains("c:keyword"));
                    else for (String expected : List.of("cast:keyword", "port:variable", "-:operator", "?:operator"))
                        assertTrue(names.toString(), names.contains(expected));
                }
            }
        } finally {
            service.didClose(new DidCloseTextDocumentParams(identifier));
        }
    }

    public void testProblemsCollectLuaWorkspaceAndFmtkPushDiagnostics() throws Exception {
        Path a=mod("problemsalpha","value"),b=mod("problemsbeta","value");
        Files.writeString(a.resolve("control.lua"),"return MissingAlphaGlobal\n");
        Files.writeString(b.resolve("control.lua"),"return MissingBetaGlobal\n");
        Files.writeString(a.resolve("locale/en/test.cfg"),"[same]\nkey=first\n[same]\nother=second\n");
        Files.writeString(b.resolve("changelog.txt"),"invalid changelog line\n");
        attach(List.of(a,b));
        for(int n=0;n<60;n++){PlatformTestUtil.dispatchAllEventsInIdeEventQueue();Thread.sleep(20);}
        FactorioSettings.get(getProject()).serviceMode="ENABLED";
        var manager=FactorioServerManager.get(getProject());
        manager.configure(List.of(scope(a,List.of()),scope(b,List.of())));
        var store=FactorioProblems.get(getProject());
        var sa=server(manager.luaServer(a.resolve("control.lua")));
        var sb=server(manager.luaServer(b.resolve("control.lua")));
        var leaseA=sa.keepAlive(); var leaseB=sb.keepAlive();
        try {
            // No editor is opened: workspace reports supply both Lua files.
            eventually(()->store.snapshot().problems().stream().filter(p->p.code().equals("undefined-global")).count()>=2);
            for(Path path:List.of(a.resolve("locale/en/test.cfg"),b.resolve("changelog.txt"))) {
                var psi=Objects.requireNonNull(PsiManager.getInstance(getProject()).findFile(file(path)));
                await(LanguageServiceAccessor.getInstance(getProject()).getLanguageServers(psi,null,null));
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(getProject()).openFile(file(path),true);
            }
            eventually(()->store.snapshot().problems().stream().anyMatch(p->p.source().equals("factorio-locale")));
            eventually(()->store.snapshot().problems().stream().anyMatch(p->p.source().equals("factorio-changelog")));
            var localeFile=file(a.resolve("locale/en/test.cfg"));
            var document=Objects.requireNonNull(FileDocumentManager.getInstance().getDocument(localeFile));
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(),()->document.setText("[same]\nkey=fixed\n"));
            eventually(()->store.snapshot().problems().stream().noneMatch(p->p.file().equals(a.resolve("locale/en/test.cfg"))));
            assertTrue(store.snapshot().problems().stream().anyMatch(p->p.file().equals(a.resolve("control.lua"))));
            manager.configure(List.of(scope(a,List.of())));
            eventually(()->store.snapshot().modules().size()==1);
            assertTrue(store.snapshot().problems().stream().noneMatch(p->p.file().startsWith(b)));
        } finally { leaseA.dispose();leaseB.dispose(); }
    }

    public void testStartServicesWhileClientFeaturesAwaitWrapperAttachment() throws Exception {
        Path root=mod("attaching","value");
        Files.writeString(root.resolve("control.lua"),"return MissingAttachmentGlobal\n");
        attach(List.of(root));
        for(int n=0;n<60;n++){PlatformTestUtil.dispatchAllEventsInIdeEventQueue();Thread.sleep(20);}
        FactorioSettings.get(getProject()).serviceMode="ENABLED";
        var manager=FactorioServerManager.get(getProject());
        manager.configure(List.of(scope(root,List.of())));
        var definition=LanguageServersRegistry.getInstance().getServerDefinition(manager.luaServer(root.resolve("control.lua")));
        assertNotNull(definition);
        // Reproduce the interval between LSP4IJ's factory call and setServerWrapper.
        var unattached=definition.createClientFeatures();
        assertNull(unattached.getServerWrapper());
        try {
            manager.startServices();
            eventually(()->FactorioProblems.get(getProject()).snapshot().problems().stream()
                .anyMatch(p->p.code().equals("undefined-global") && p.file().equals(root.resolve("control.lua"))));
            eventually(()->LanguageServiceAccessor.getInstance(getProject()).getStartedServers().stream()
                .filter(s->s.getServerDefinition().getId().startsWith("de.softwareforge.factorio.")
                    && s.getServerStatus()==ServerStatus.started).count()==2);
            var services=List.copyOf(LanguageServiceAccessor.getInstance(getProject()).getStartedServers());
            var processes=services.stream().map(LanguageServerWrapper::getCurrentProcessId).toList();
            // Settings can create another wrapper-less preview while the server runs.
            var preview=definition.createClientFeatures();
            assertNull(preview.getServerWrapper());
            try {
                manager.startServices();manager.startServices();
                for(int n=0;n<20;n++){PlatformTestUtil.dispatchAllEventsInIdeEventQueue();Thread.sleep(20);}
                assertEquals(processes,services.stream().map(LanguageServerWrapper::getCurrentProcessId).toList());
            } finally { preview.dispose(); }
        } finally { unattached.dispose(); }
    }

    public void testProblemsStartAllServicesWithoutEditorsAndKeepClosedFiles() throws Exception {
        Path a=mod("coldalpha","value"), b=mod("coldbeta","value");
        Files.writeString(a.resolve("control.lua"),"return MissingAlphaGlobal\n");
        Path nested=Files.createDirectories(a.resolve("scripts")).resolve("closed.lua");
        Files.writeString(nested,"return MissingClosedGlobal\n");
        Files.writeString(b.resolve("control.lua"),"return MissingBetaGlobal\n");
        attach(List.of(a,b));
        for(int n=0;n<60;n++){PlatformTestUtil.dispatchAllEventsInIdeEventQueue();Thread.sleep(20);}
        FactorioSettings.get(getProject()).serviceMode="ENABLED";
        var manager=FactorioServerManager.get(getProject());
        manager.configure(List.of(scope(a,List.of()),scope(b,List.of())));
        var store=FactorioProblems.get(getProject());
        var panel=new FactorioProblemsPanel(getProject());
        try {
            assertEmpty(com.intellij.openapi.fileEditor.FileEditorManager.getInstance(getProject()).getOpenFiles());
            // Exercise the actual UI entry point, never server(id), which starts servers itself.
            panel.selectionChangedTo(true);
            eventually(()->store.snapshot().problems().stream().filter(p->p.code().equals("undefined-global")).count()==3);
            assertTrue(store.snapshot().problems().stream().anyMatch(p->p.file().equals(nested)));
            var accessor=LanguageServiceAccessor.getInstance(getProject());
            eventually(()->accessor.getStartedServers().stream()
                .filter(s->s.getServerDefinition().getId().startsWith("de.softwareforge.factorio.") && s.getServerStatus()==ServerStatus.started).count()==4);
            var services=accessor.getStartedServers().stream()
                .filter(s->s.getServerDefinition().getId().startsWith("de.softwareforge.factorio.")).toList();
            assertTrue(services.stream().allMatch(s->s.getClientFeatures().keepServerAlive()));
            var processes=services.stream().map(LanguageServerWrapper::getCurrentProcessId).toList();
            manager.startServices();manager.startServices();
            for(int n=0;n<20;n++){PlatformTestUtil.dispatchAllEventsInIdeEventQueue();Thread.sleep(20);}
            assertEquals(processes,services.stream().map(LanguageServerWrapper::getCurrentProcessId).toList());
            var editorFile=file(a.resolve("control.lua"));
            var psi=Objects.requireNonNull(PsiManager.getInstance(getProject()).findFile(editorFile));
            await(LanguageServiceAccessor.getInstance(getProject()).getLanguageServers(psi,null,null));
            var editors=com.intellij.openapi.fileEditor.FileEditorManager.getInstance(getProject());
            editors.openFile(editorFile,true);editors.closeFile(editorFile);
            for(int n=0;n<20;n++){PlatformTestUtil.dispatchAllEventsInIdeEventQueue();Thread.sleep(20);}
            assertTrue(services.stream().allMatch(s->s.getServerStatus()==ServerStatus.started));
            assertTrue(store.snapshot().problems().stream().anyMatch(p->p.file().equals(nested)));
            manager.configure(List.of(scope(a,List.of())));
            eventually(()->services.stream().filter(s->s.getServerDefinition().getDisplayName().startsWith("coldbeta"))
                .allMatch(LanguageServerWrapper::isDisposed));
            assertTrue(store.snapshot().problems().stream().noneMatch(p->p.file().startsWith(b)));
            // New module definitions inherit the project-wide diagnostics request.
            manager.configure(List.of(scope(a,List.of()),scope(b,List.of())));
            eventually(()->store.snapshot().problems().stream().anyMatch(p->p.file().equals(b.resolve("control.lua"))));
        } finally { com.intellij.openapi.util.Disposer.dispose(panel); }
    }

    public void testGeneratedApiWithSeparateWorkspaces() throws Exception {
        Path docs=Path.of(System.getProperty("factorio.test.apiDocs"));
        org.junit.Assume.assumeTrue(Files.isRegularFile(docs.resolve("runtime-api.json")));
        Path a=mod("alpha","alphaValue"),b=mod("beta","betaValue");
        for(Path root:List.of(a,b)) Files.writeString(root.resolve("control.lua"),"local surface = game.get_surface(1)\nstorage.sensor_data = { sensors = {} }\nlocal saved = storage\nlocal util = require('util')\nlocal copy = util.table.deepcopy({})\n");
        String typePrefix = "---@type data.La";
        for (Path root : List.of(a,b)) Files.writeString(root.resolve("type-completion.lua"), typePrefix + "\nlocal lamp\n");
        Path data=docs.getParent().resolve("data");
        var imports=new ArrayList<String>();
        var targets=new ArrayList<Path>();
        for(String name:List.of("base","core","elevated-rails","quality","recycler","space-age")) {
            if(!Files.isRegularFile(data.resolve(name+"/info.json"))) continue;
            for(String separator:List.of(".","/")) {
                imports.add("__"+name+"__"+separator+"data"); targets.add(data.resolve(name+"/data.lua"));
            }
        }
        imports.add("__base__.prototypes.entity.rail-pictures"); targets.add(data.resolve("base/prototypes/entity/rail-pictures.lua"));
        imports.add("__core__/lualib/collision-mask-util"); targets.add(data.resolve("core/lualib/collision-mask-util.lua"));
        for(Path root:List.of(a,b)) for(String name:imports)
            Files.writeString(root.resolve("control.lua"),"local bundled = require('"+name+"')\n",StandardOpenOption.APPEND);
        attach(List.of(a,b));
        var settings=FactorioSettings.get(getProject());
        settings.serviceMode="ENABLED"; settings.apiDocs=docs.toString();
        await(CompletableFuture.runAsync(() -> {
            try { Definitions.generate(getProject(),new com.intellij.openapi.progress.EmptyProgressIndicator(),message -> {}); }
            catch(Exception e) { throw new CompletionException(e); }
        }));
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        var manager=FactorioServerManager.get(getProject());
        for(Path root:List.of(a,b)) {
            var item=server(manager.luaServer(root.resolve("control.lua")));
            eventually(() -> {
                try { return String.valueOf(await(item.getServer().getTextDocumentService().hover(new HoverParams(
                    new TextDocumentIdentifier(root.resolve("control.lua").toUri().toString()),new Position(0,24))))).contains("LuaSurface"); }
                catch(Exception e) { return false; }
            });
            Path config=Definitions.cache(getProject()).resolve("language-servers/"+ModLanguageScope.identity(Toolkit.root(getProject()),root)+"/workspace/managed-emmy-config.json");
            String json=Files.readString(config);
            assertTrue(json.contains(root.toString()));
            assertFalse(json.contains((root.equals(a)?b:a).toString()));
            assertTrue(json.contains("ignoreDir"));
            var storageHover=String.valueOf(await(item.getServer().getTextDocumentService().hover(new HoverParams(
                new TextDocumentIdentifier(root.resolve("control.lua").toUri().toString()),new Position(2,17)))));
            assertTrue(storageHover,storageHover.contains("sensor_data"));
            for(String foreign:List.of("space_finish_script","silo_script","last_built_position","story_index","no_victory"))
                assertFalse(storageHover,storageHover.contains(foreign));
            String utilDefinition=definitions(item,root.resolve("control.lua"),4,26);
            assertTrue(utilDefinition,utilDefinition.contains("factorio/library/core/lualib/util.lua"));
            for(int i=0;i<imports.size();i++) {
                String target=definitions(item,root.resolve("control.lua"),5+i,27);
                assertTrue(imports.get(i)+": "+target,target.contains(targets.get(i).toUri().toString()));
            }
            var response = await(item.getServer().getTextDocumentService().completion(new CompletionParams(
                new TextDocumentIdentifier(root.resolve("type-completion.lua").toUri().toString()),
                new Position(0, typePrefix.length()))));
            assertNotNull("Generated API annotation completions", response);
            var items = response.isLeft() ? response.getLeft() : response.getRight().getItems();
            var lamp = items.stream().filter(candidate -> "LampPrototype".equals(candidate.getLabel())).findFirst();
            assertTrue("Missing data.LampPrototype in " + items, lamp.isPresent());
            assertEquals("data.LampPrototype", lamp.get().getFilterText());
            assertNotNull(lamp.get().getTextEdit());
            assertTrue(lamp.get().getTextEdit().isLeft());
            var edit = lamp.get().getTextEdit().getLeft();
            assertEquals(new Range(new Position(0, 9), new Position(0, typePrefix.length())), edit.getRange());
            assertEquals("---@type data.LampPrototype", typePrefix.substring(0, edit.getRange().getStart().getCharacter())
                + edit.getNewText() + typePrefix.substring(edit.getRange().getEnd().getCharacter()));
        }
    }

    public void testTypedRemoteCallReturnShapes() throws Exception {
        Path docs=Path.of(System.getProperty("factorio.test.apiDocs"));
        org.junit.Assume.assumeTrue(Files.isRegularFile(docs.resolve("runtime-api.json")));
        Path root=mod("remote-return","state"), control=root.resolve("control.lua");
        String source="""
            ---@class (partial) LuaRemote.InterfaceMap
            ---@field ['fde-probe'] FdeRemoteInterface
            ---@class FdeRemoteInterface
            ---@field event fun(): defines.events
            ---@field pair fun(): string, integer?
            ---@field many fun(): integer...
            script.on_event(remote.call('fde-probe', 'event'), function(event) end)
            local event, extra = remote.call('fde-probe', 'event')
            local first, second = remote.call('fde-probe', 'pair')
            local one, two = remote.call('fde-probe', 'many')
            local fallback = remote.call('untyped-interface', 'get')
            ---@param value string
            local function needs_string(value) end
            needs_string(remote.call('fde-probe', 'event'))
            """;
        Files.writeString(control,source);
        attach(List.of(root));
        var settings=FactorioSettings.get(getProject());
        settings.serviceMode="ENABLED"; settings.apiDocs=docs.toString();
        await(CompletableFuture.runAsync(() -> {
            try { Definitions.generate(getProject(),new com.intellij.openapi.progress.EmptyProgressIndicator(),message -> {}); }
            catch(Exception e) { throw new CompletionException(e); }
        }));
        var item=server(FactorioServerManager.get(getProject()).luaServer(control));
        var document=new TextDocumentIdentifier(control.toUri().toString());
        eventually(() -> {
            try { return diagnostics(item,control).contains("param-type-mismatch"); }
            catch(Exception e) { return false; }
        });
        var lines=source.lines().toList();
        for(var expected:Map.of("event", "defines.events", "first", "string",
                "second", "integer?", "two", "integer").entrySet()) {
            int line=expected.getKey().equals("event") ? 7
                    : expected.getKey().equals("two") ? 9 : 8;
            String hover=String.valueOf(await(item.getServer().getTextDocumentService().hover(new HoverParams(
                    document,new Position(line,lines.get(line).indexOf(expected.getKey())+1)))));
            assertTrue(expected.getKey()+": "+hover,hover.contains(expected.getKey()+": "+expected.getValue()));
        }
        var params=new DocumentDiagnosticParams(); params.setTextDocument(document);
        var report=await(item.getServer().getTextDocumentService().diagnostic(params)).getLeft();
        var mismatches=report.getItems().stream().filter(d -> d.getCode()!=null && "param-type-mismatch".equals(d.getCode().getLeft())).toList();
        assertEquals(mismatches.toString(),1,mismatches.size());
        assertEquals(13,mismatches.getFirst().getRange().getStart().getLine());
        // Extra slots of ordinary scalar calls currently infer any in EmmyLua.
        // The known event result must not be repeated as if it were Event....
        String extra=String.valueOf(await(item.getServer().getTextDocumentService().hover(new HoverParams(
                document,new Position(7,lines.get(7).indexOf("extra")+1)))));
        assertFalse(extra,extra.contains("defines.events"));
    }

    public void testLocalisedStringArraysAndOptionalFields() throws Exception {
        Path docs=Path.of(System.getProperty("factorio.test.apiDocs"));
        org.junit.Assume.assumeTrue(Files.isRegularFile(docs.resolve("runtime-api.json")));
        Path root=mod("localised-arrays","state"), control=root.resolve("control.lua");
        // Thread is a reliable negative control: generated LuaObject currently admits
        // plain functions through callable class alternatives (an independent issue).
        String source="""
            ---@param parts LocalisedString[]
            ---@return LocalisedString
            local function _message(parts)
                game.print(parts)
                return parts
            end
            ---@param parts LocalisedString[]
            local function _cast(parts)
                ---@cast parts LocalisedString
                return parts
            end
            ---@type LocalisedString
            local _nil = nil
            ---@class FdeCaptionOptions
            ---@field caption? LocalisedString
            ---@class FdeRequiredOptions
            ---@field required true
            ---@type FdeCaptionOptions|FdeRequiredOptions
            local _options = {}
            ---@type LocalisedString
            local _invalid = coroutine.create(function() end)
            """;
        String dataSource="""
            ---@param parts data.LocalisedString[]
            ---@return data.LocalisedString
            local function _message(parts) return parts end
            ---@param parts data.LocalisedString[]
            local function _cast(parts)
                ---@cast parts data.LocalisedString
                return parts
            end
            ---@type data.LocalisedString
            local _invalid = false
            """;
        Files.writeString(control,source);
        Path data=root.resolve("data.lua"); Files.writeString(data,dataSource);
        attach(List.of(root));
        var settings=FactorioSettings.get(getProject());
        settings.serviceMode="ENABLED"; settings.apiDocs=docs.toString();
        await(CompletableFuture.runAsync(() -> {
            try { Definitions.generate(getProject(),new com.intellij.openapi.progress.EmptyProgressIndicator(),message -> {}); }
            catch(Exception e) { throw new CompletionException(e); }
        }));
        var item=server(FactorioServerManager.get(getProject()).luaServer(control));
        for(var entry:Map.of(control,source,data,dataSource).entrySet()) {
            eventually(() -> {
                try { return diagnostics(item,entry.getKey()).contains("assign-type-mismatch"); }
                catch(Exception e) { return false; }
            });
            var params=new DocumentDiagnosticParams();
            params.setTextDocument(new TextDocumentIdentifier(entry.getKey().toUri().toString()));
            var report=await(item.getServer().getTextDocumentService().diagnostic(params)).getLeft();
            var problems=report.getItems().stream().filter(d -> d.getCode()!=null && !"unused".equals(d.getCode().getLeft())).toList();
            assertEquals(problems.toString(),1,problems.size());
            assertEquals("assign-type-mismatch",problems.getFirst().getCode().getLeft());
            assertTrue(entry.getValue().lines().toList().get(problems.getFirst().getRange().getStart().getLine()).contains("_invalid"));
        }
    }

    public void testAsymmetricApiAttributes() throws Exception {
        Path docs=Path.of(System.getProperty("factorio.test.apiDocs"));
        org.junit.Assume.assumeTrue(Files.isRegularFile(docs.resolve("runtime-api.json")));
        Path root=mod("asymmetric","state"), control=root.resolve("control.lua");
        Files.writeString(control,"---@param burner LuaBurner\nlocal function probe(burner)\n  burner.currently_burning = 'coal'\n  local burning = burner.currently_burning\n  if burning then\n    local name = burning.name\n  end\n  burner.currently_burning = function() end\nend\n");
        attach(List.of(root));
        var settings=FactorioSettings.get(getProject());
        settings.serviceMode="ENABLED"; settings.apiDocs=docs.toString();
        await(CompletableFuture.runAsync(() -> {
            try { Definitions.generate(getProject(),new com.intellij.openapi.progress.EmptyProgressIndicator(),message -> {}); }
            catch(Exception e) { throw new CompletionException(e); }
        }));
        var item=server(FactorioServerManager.get(getProject()).luaServer(control));
        var document=new TextDocumentIdentifier(control.toUri().toString());
        eventually(() -> {
            try { return diagnostics(item,control).contains("assign-type-mismatch"); }
            catch(Exception e) { return false; }
        });
        String hover=String.valueOf(await(item.getServer().getTextDocumentService().hover(new HoverParams(document,new Position(3,32)))));
        for(String expected:List.of("Read:","Write:","ItemIDAndQualityIDPair")) assertTrue(hover,hover.contains(expected));
        assertTrue(hover,hover.contains("ItemWithQualityID") || (hover.contains("LuaItemPrototype") && hover.contains("string")));
        String completion=String.valueOf(await(item.getServer().getTextDocumentService().completion(new CompletionParams(document,new Position(5,25)))));
        assertTrue(completion,completion.contains("quality"));
        String readDefinition=definitions(item,control,3,32),writeDefinition=definitions(item,control,2,18);
        assertTrue(readDefinition,readDefinition.contains("LuaBurner.lua"));
        assertTrue(writeDefinition,writeDefinition.contains("LuaBurner.lua"));
        assertFalse(readDefinition.equals(writeDefinition));
        var params=new DocumentDiagnosticParams(); params.setTextDocument(document);
        var report=await(item.getServer().getTextDocumentService().diagnostic(params)).getLeft();
        var mismatches=report.getItems().stream().filter(d -> d.getCode()!=null && "assign-type-mismatch".equals(d.getCode().getLeft())).toList();
        assertEquals(mismatches.toString(),1,mismatches.size());
        assertEquals(7,mismatches.getFirst().getRange().getStart().getLine());
        // Regeneration reuses the new-format cache and still configures the same mod.
        await(CompletableFuture.runAsync(() -> {
            try { Definitions.generate(getProject(),new com.intellij.openapi.progress.EmptyProgressIndicator(),message -> {}); }
            catch(Exception e) { throw new CompletionException(e); }
        }));
    }

    public void testProblemsForClosedFilesInReportedMod() throws Exception {
        String originalPath=System.getProperty("factorio.test.realMods","");
        org.junit.Assume.assumeTrue("Pass -PrealMods to test a copy of LTN",!originalPath.isBlank());
        Path source=Path.of(originalPath).resolve("LogisticTrainNetwork");
        Path target=temporary.resolve("LogisticTrainNetwork");
        Files.walkFileTree(source,EnumSet.of(FileVisitOption.FOLLOW_LINKS),Integer.MAX_VALUE,new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path directory,java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                if(Set.of(".git",".portal").contains(directory.getFileName().toString()))return FileVisitResult.SKIP_SUBTREE;
                Files.createDirectories(target.resolve(source.relativize(directory)));return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file,java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                if(file.toString().matches(".*\\.(lua|json|cfg)$"))Files.copy(file,target.resolve(source.relativize(file)),StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
        // Deterministic markers in the disposable copy, never in the user's mod.
        Files.writeString(target.resolve("script/fde-closed-test.lua"),"return MissingClosedFileMarker\n");
        attach(List.of(target));
        for(int n=0;n<60;n++){PlatformTestUtil.dispatchAllEventsInIdeEventQueue();Thread.sleep(20);}
        var settings=FactorioSettings.get(getProject());settings.serviceMode="ENABLED";
        settings.apiDocs=System.getProperty("factorio.test.apiDocs");
        await(CompletableFuture.runAsync(()->{
            try { Definitions.generate(getProject(),new com.intellij.openapi.progress.EmptyProgressIndicator(),message->{}); }
            catch(Exception e){throw new CompletionException(e);}
        }));
        var panel=new FactorioProblemsPanel(getProject());
        try {
            assertEmpty(com.intellij.openapi.fileEditor.FileEditorManager.getInstance(getProject()).getOpenFiles());
            panel.selectionChangedTo(true);
            var store=FactorioProblems.get(getProject());
            eventually(()->store.snapshot().problems().stream().anyMatch(p->p.file().equals(target.resolve("script/fde-closed-test.lua"))));
            assertTrue("Closed real-mod files should be reported",store.snapshot().problems().stream().map(FactorioProblemsModel.Problem::file).distinct().count()>1);
        } finally { com.intellij.openapi.util.Disposer.dispose(panel); }
    }

    public void testReportedNavigationWithCopiesOfActualMods() throws Exception {
        String originalPath=System.getProperty("factorio.test.realMods","");
        org.junit.Assume.assumeTrue("Pass -PrealMods to test copies of the reported mods",!originalPath.isBlank());
        Path original=Path.of(originalPath);
        var roots=new ArrayList<Path>();
        for(String name:List.of("inventory-sensor-improved","logistics-sensor")) {
            Path source=original.resolve(name); Path target=temporary.resolve(name);
            Files.walkFileTree(source,EnumSet.of(FileVisitOption.FOLLOW_LINKS),Integer.MAX_VALUE,new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult preVisitDirectory(Path directory,java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                    if (Set.of(".git",".portal").contains(directory.getFileName().toString())) return FileVisitResult.SKIP_SUBTREE;
                    Files.createDirectories(target.resolve(source.relativize(directory))); return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file,java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                    if (file.toString().matches(".*\\.(lua|json|cfg)$")) Files.copy(file,target.resolve(source.relativize(file)),StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
            roots.add(target.toRealPath());
        }
        attach(roots);
        var manager=FactorioServerManager.get(getProject()); FactorioSettings.get(getProject()).serviceMode="ENABLED";
        FactorioSettings.get(getProject()).apiDocs=System.getProperty("factorio.test.apiDocs");
        await(CompletableFuture.runAsync(() -> {
            try { Definitions.generate(getProject(),new com.intellij.openapi.progress.EmptyProgressIndicator(),message -> {}); }
            catch(Exception e) { throw new CompletionException(e); }
        }));
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        for(Path root:roots) {
            Path controller=root.resolve("scripts/controller.lua");
            var lines=Files.readAllLines(controller); int line=0;
            while(line<lines.size() && !lines.get(line).contains("This:storage()")) line++;
            assertTrue(line<lines.size());
            int column=lines.get(line).indexOf("storage")+2; final int at=line;
            var item=server(manager.luaServer(controller));
            eventually(() -> { try { return definitions(item,controller,at,column).contains(root.getFileName()+"/lib/this.lua"); } catch(Exception e) { return false; } });
            String result=definitions(item,controller,line,column);
            Path other=root.equals(roots.get(0)) ? roots.get(1) : roots.get(0);
            assertFalse(result.contains(other.getFileName()+"/lib/this.lua"));
            Path thisFile=root.resolve("lib/this.lua");
            var source=Files.readAllLines(thisFile); int storageLine=0;
            while(storageLine<source.size() && !source.get(storageLine).contains("storage.")) storageLine++;
            assertTrue(storageLine<source.size());
            var hover=String.valueOf(await(item.getServer().getTextDocumentService().hover(new HoverParams(
                new TextDocumentIdentifier(thisFile.toUri().toString()),new Position(storageLine,source.get(storageLine).indexOf("storage")+2)))));
            assertFalse(hover,hover.contains("space_finish_script"));
            assertFalse(hover,hover.contains("last_built_position"));
            if(root.getFileName().toString().equals("logistics-sensor")) assertTrue(hover,hover.contains("sensor_data"));
        }
    }

}
