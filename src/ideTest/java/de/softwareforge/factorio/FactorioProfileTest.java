package de.softwareforge.factorio;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.profiler.api.Success;
import de.softwareforge.factorio.profiler.FactorioProfileImporter;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class FactorioProfileTest extends HeavyPlatformTestCase {
    public void testProfileMenuOffersNativeRecorderOnlyForFactorio() {
        var config = new FactorioDebug.Configuration(getProject(), new FactorioDebug.Type().getConfigurationFactories()[0], "Profile routing");
        var executors = com.intellij.profiler.DefaultProfilerExecutorGroup.Companion.getInstance().childExecutors();
        assertEquals(1L, executors.stream().filter(e -> e.getId().equals(FactorioProfiling.EXECUTOR_ID)).count());
        for (var executor : executors) {
            assertEquals(executor.getId(), executor.getId().equals(FactorioProfiling.EXECUTOR_ID), config.canRun(executor.getId()));
        }
        assertNotNull(com.intellij.execution.runners.ProgramRunner.getRunner(FactorioProfiling.EXECUTOR_ID, config));
        var dapType = java.util.Arrays.stream(com.intellij.execution.configurations.ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var unrelated = (com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration)
                dapType.getConfigurationFactories()[0].createTemplateConfiguration(getProject());
        unrelated.setCommand("python3 -m debugpy.adapter");
        assertFalse(unrelated.canRun(FactorioProfiling.EXECUTOR_ID));
        assertNull(com.intellij.execution.runners.ProgramRunner.getRunner(FactorioProfiling.EXECUTOR_ID, unrelated));
        assertNotNull(com.intellij.execution.runners.ProgramRunner.getRunner(com.intellij.execution.executors.DefaultDebugExecutor.EXECUTOR_ID, unrelated));
        assertTrue(config.canRun(com.intellij.execution.executors.DefaultRunExecutor.EXECUTOR_ID));
        assertTrue(config.canRun(com.intellij.execution.executors.DefaultDebugExecutor.EXECUTOR_ID));
    }
    public void testSharedCpuProfileFormatRequiresExplicitProviderChoice() throws Exception {
        var generic = Files.createTempFile(Path.of(System.getProperty("factorio.test.work")), "javascript-", ".cpuprofile");
        Files.writeString(generic, """
                {"nodes":[
                  {"id":1,"callFrame":{"functionName":"(root)","scriptId":"0","url":"","lineNumber":-1},"children":[2]},
                  {"id":2,"callFrame":{"functionName":"javascriptFunction","scriptId":"1","url":"/missing/app.js","lineNumber":0}}
                ],"samples":[1,2,1],"timeDeltas":[0,10,20],"startTime":0,"endTime":30}
                """);
        var nativeFile = Path.of(System.getProperty("factorio.test.workspace"),
                "intellij-factorio/src/test/resources/profiling/factorio-2.1.23.cpuprofile");
        var providers = com.intellij.openapi.extensions.ExtensionPointName.<com.intellij.profiler.api.ProfilerDumpParserProvider>
                create("com.intellij.profiler.api.ProfilerDumpParserProvider").getExtensionList();
        var factorio = providers.stream().filter(p -> p.getId().equals("factorio.lua")).findFirst().orElseThrow();
        for (var file : List.of(generic, nativeFile)) {
            var finder = new com.intellij.profiler.ProfilerDumpParserProviderFinder();
            var found = finder.findParserProvider(file.toFile(), providers);
            if (found instanceof com.intellij.profiler.ProfilerDumpParserProviderFinder.Result.Success automatic)
                assertFalse(automatic.getProvider() instanceof FactorioProfileImporter);
            var isolated = finder.findParserProvider(file.toFile(), List.of(factorio));
            assertTrue(isolated instanceof com.intellij.profiler.ProfilerDumpParserProviderFinder.Result.Failure);
            assertEquals(List.of(factorio), ((com.intellij.profiler.ProfilerDumpParserProviderFinder.Result.Failure) isolated)
                    .getProvidersWithoutSignaturesAndExtension());
        }
        assertTrue(factorio.createParser(getProject()).parse(nativeFile.toFile(), new EmptyProgressIndicator()) instanceof Success);
    }
    public void testSourceNavigationAndUnresolvedNativeFrames() throws Exception {
        FactorioSettings.get(getProject()).serviceMode = "DISABLED";
        var root = Files.createTempDirectory(Path.of(System.getProperty("factorio.test.work")), "profile-source-");
        Files.writeString(root.resolve("control.lua"), "local a = 1\nlocal b = 2\n");
        Files.writeString(root.resolve("info.json"), "{\"name\":\"profile-test\",\"version\":\"1.0.0\",\"title\":\"Profile test\",\"factorio_version\":\"2.0\"}");
        FactorioSettings.get(getProject()).activeMod = root.toString();
        FactorioModules.get(getProject()).refresh();
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(), root.toString());
        com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.resolve("control.lua"));
        var element = new FactorioProfileImporter.LuaFrame(new FactorioProfile.Frame("second", root.resolve("control.lua").toString(), 1));
        var targets = element.calcNavigatables(getProject());
        assertEquals(1, targets.length);
        targets[0].navigate(true);
        assertEquals(1, com.intellij.openapi.fileEditor.FileEditorManager.getInstance(getProject()).getSelectedTextEditor().getCaretModel().getLogicalPosition().line);
        assertEquals(root.resolve("control.lua").toString(), FactorioProfileSources.resolve(getProject(), "@__profile-test__/control.lua").getPath());
        assertFalse(new FactorioProfileImporter.LuaFrame(new FactorioProfile.Frame("pairs", "=[C]", -1)).isNavigatable());
        assertEquals(0, new FactorioProfileImporter.LuaFrame(new FactorioProfile.Frame("missing", "__missing__/control.lua", 0)).calcNavigatables(getProject()).length);
    }
    public void testSourceNavigationFromProfilerBackgroundThread() throws Exception {
        FactorioSettings.get(getProject()).serviceMode = "DISABLED";
        var root = Files.createTempDirectory(Path.of(System.getProperty("factorio.test.work")), "profile-background-source-");
        Files.writeString(root.resolve("control.lua"), "local a = 1\nlocal b = 2\n");
        Files.writeString(root.resolve("info.json"), "{\"name\":\"profile-background-test\",\"version\":\"1.0.0\",\"factorio_version\":\"2.1\"}");
        FactorioSettings.get(getProject()).activeMod = root.toString();
        FactorioModules.get(getProject()).refresh();
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(), root.toString());
        var file = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.resolve("control.lua"));
        assertNotNull(file);
        var element = new FactorioProfileImporter.LuaFrame(new FactorioProfile.Frame("second", "@__profile-background-test__/control.lua", 1));
        // The common profiler resolves a clicked frame on a worker without a read action.
        var future = CompletableFuture.supplyAsync(() -> {
            var application = com.intellij.openapi.application.ApplicationManager.getApplication();
            assertFalse(application.isDispatchThread());
            assertFalse(application.isReadAccessAllowed());
            var targets = element.calcNavigatables(getProject());
            assertEquals(1, targets.length);
            assertEquals("second", targets[0].getName());
            assertEquals("local a = 1\n".length(), targets[0].getTextOffset());
            assertTrue(targets[0].canNavigateToSource());
            return targets[0];
        });
        var target = await(future);
        target.navigate(true);
        var editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
        assertNotNull(editor);
        assertEquals(file, com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getFile(editor.getDocument()));
        assertEquals(1, editor.getCaretModel().getLogicalPosition().line);
    }
    public void testUnknownFrameNamesUseExactSourceDeclarations() throws Exception {
        FactorioSettings.get(getProject()).serviceMode = "DISABLED";
        var root = Files.createTempDirectory(Path.of(System.getProperty("factorio.test.work")), "profile-names-");
        var path = root.resolve("dispatcher.lua");
        Files.writeString(path, """
                --- Dispatch trains
                local function DispatcherDispatchTrains(event)
                    return event
                end
                function OnTick(event) end
                function RailCache.update() end
                function RailCache:reset() end
                local callback = function() end
                local function left() end; local function right() end
                local function outer() return function() end end
                -- function NotAFunction() end
                local text = 'function AlsoNotAFunction() end'
                """);
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(), root.toString());
        assertNotNull(com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path));
        var frames = new ArrayList<FactorioProfile.Frame>();
        for (int line : new int[]{1, 4, 5, 6, 7, 8, 9, 0, 2, 10, 11, 99})
            frames.add(new FactorioProfile.Frame("(unknown)", path.toString(), line));
        frames.add(new FactorioProfile.Frame("recorded-name", path.toString(), 1));
        frames.add(new FactorioProfile.Frame("", path.toString(), 4));
        frames.add(new FactorioProfile.Frame("(anonymous)", path.toString(), 7));
        frames.add(new FactorioProfile.Frame("(unknown)", root.resolve("missing.lua").toString(), 4));
        frames.add(new FactorioProfile.Frame("(unknown)", "=[C]", -1));
        frames.add(new FactorioProfile.Frame("(tail call)", path.toString(), 1));
        var expected = List.of("DispatcherDispatchTrains", "OnTick", "RailCache.update", "RailCache:reset",
                "dispatcher.lua:8", "dispatcher.lua:9", "dispatcher.lua:10", "dispatcher.lua:1",
                "dispatcher.lua:3", "dispatcher.lua:11", "dispatcher.lua:12", "dispatcher.lua:100",
                "recorded-name", "OnTick", "dispatcher.lua:8", "missing.lua:5", "(unknown)", "(tail call)");
        var children = java.util.stream.IntStream.rangeClosed(1, frames.size()).boxed().toList();
        var nodes = new ArrayList<FactorioProfile.Node>();
        nodes.add(new FactorioProfile.Node(0, new FactorioProfile.Frame("(root)", "", -1), children, 0, frames.size()));
        for (int i = 0; i < frames.size(); i++) nodes.add(new FactorioProfile.Node(i + 1, frames.get(i), List.of(), 1, 1));
        var profile = new FactorioProfile(List.copyOf(nodes), frames.size());
        await(CompletableFuture.runAsync(() -> {
            assertFalse(com.intellij.openapi.application.ApplicationManager.getApplication().isReadAccessAllowed());
            var data = FactorioProfileImporter.data(getProject(), profile);
            var tree = data.getTrees().getFirst();
            var calls = tree.getCallTreeModelProvider().getModel().values().iterator().next()
                    .getCallTree().getChildren();
            assertEquals(frames.size(), calls.size());
            for (int i = 0; i < calls.size(); i++) {
                var call = calls.get(i).getData().getCall();
                assertEquals("Frame " + i, expected.get(i), tree.getRenderer().getText(call));
                assertTrue(call.fullName(), call.fullName().startsWith(expected.get(i) + " — "));
                // Display names must not change the frame's identity or aggregation.
                assertEquals(new FactorioProfileImporter.LuaFrame(frames.get(i)), call);
            }
            var target = ((FactorioProfileImporter.LuaFrame) calls.getFirst().getData().getCall()).calcNavigatables(getProject());
            assertEquals("DispatcherDispatchTrains", target[0].getName());
            assertEquals("(unknown)", profile.nodes().get(1).frame().name());
        }));
    }
    private static <T> T await(CompletableFuture<T> future) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!future.isDone() && System.nanoTime() < deadline) {
            com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        return future.get(1, TimeUnit.SECONDS);
    }
    public void testSnapshotRemainsAvailableWithoutViewer() throws Exception {
        FactorioSettings.get(getProject()).serviceMode = "DISABLED";
        com.intellij.testFramework.ExtensionTestUtil.maskExtensions(FactorioProfileViewer.EP, java.util.List.of(), getTestRootDisposable());
        var received = new java.util.concurrent.atomic.AtomicReference<com.intellij.notification.Notification>();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(com.intellij.notification.Notifications.TOPIC,
                new com.intellij.notification.Notifications() {
                    @Override public void notify(com.intellij.notification.Notification notification) { received.set(notification); }
                });
        var snapshot = Path.of(System.getProperty("factorio.test.workspace"), "intellij-factorio/src/test/resources/profiling/factorio-2.1.23.cpuprofile");
        FactorioProfiling.open(getProject(), snapshot);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (received.get() == null && System.nanoTime() < deadline) {
            com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        assertNotNull(received.get());
        assertEquals("Factorio profile saved", received.get().getTitle());
        assertTrue(received.get().getContent().contains(snapshot.toString()));
        assertEquals(snapshot.getParent().toString(), FactorioSettings.get(getProject()).lastProfileDirectory);
        assertEquals(1, received.get().getActions().size());
        received.get().expire();
    }
    public void testNativeProfilerView() throws Exception {
        FactorioSettings.get(getProject()).serviceMode = "DISABLED";
        assertEquals(1, FactorioProfileViewer.EP.getExtensionList().size());
        var snapshot = Path.of(System.getenv().getOrDefault("FACTORIO_TEST_PROFILE",
                Path.of(System.getProperty("factorio.test.workspace"), "intellij-factorio/src/test/resources/profiling/factorio-2.1.23.cpuprofile").toString()));
        String sourceRoots = System.getenv("FACTORIO_TEST_PROFILE_SOURCE_ROOTS");
        if (sourceRoots != null && !sourceRoots.isBlank())
            com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(), sourceRoots.split(java.io.File.pathSeparator));
        var result = new FactorioProfileImporter().createParser(getProject()).parse(snapshot.toFile(), new EmptyProgressIndicator());
        assertTrue(result instanceof Success);
        var data = ((Success) result).getData();
        assertFalse(data.isEmpty());
        var trees = ((com.intellij.profiler.api.SamplingProfilerData) data).getTrees();
        assertEquals(List.of("Lua execution", "Including unattributed time"),
                trees.stream().map(com.intellij.profiler.api.CallTreeBuildingData::getReadableStateName).toList());
        assertFalse(trees.getFirst().getCallTreeModelProvider().getModel().isEmpty());
        System.out.println("Profile view totals for " + snapshot + ": " + trees.stream().map(t ->
                t.getReadableStateName() + "=" + t.getCallTreeModelProvider().getModel().values().iterator().next()
                        .getCallTree().getChildren().stream().mapToLong(n -> n.getData().getValue()).sum() + " ns").toList());
        var disposable = Disposer.newDisposable();
        var window = new javax.swing.JFrame("FDE profiling test");
        window.setGlassPane(new com.intellij.openapi.wm.impl.IdeGlassPaneImpl(window.getRootPane()));
        try {
            var descriptor = new com.intellij.profiler.api.ProfilerDumpDescriptor(getProject(), "Native viewer test", new java.util.Date(),
                    snapshot.toFile(), new FactorioProfileImporter(), new java.util.concurrent.atomic.AtomicInteger());
            var dump = new com.intellij.profiler.ProfilerDumpPanel(getProject(), data, "Native viewer test", null, descriptor, snapshot.toString());
            com.intellij.profiler.ProfilerToolWindowManager.getInstance(getProject()).addProfilerDumpTab(dump, false);
            var component = dump.getTopLevelComponent();
            assertNotNull(component);
            window.setContentPane(component);
            window.setSize(1100, 700);
            window.setVisible(true);
            var nativeView = com.intellij.util.ui.UIUtil.findComponentOfType(component, com.intellij.profiler.ui.MainCallTreeDataComponent.class);
            assertNotNull(nativeView);
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
            while (nativeView.getRichCallModel().isEmpty() && System.nanoTime() < deadline) {
                com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
                Thread.sleep(20);
            }
            assertFalse("Profiler must build its call model", nativeView.getRichCallModel().isEmpty());
            var tabs = com.intellij.util.ui.UIUtil.findComponentOfType(component, com.intellij.profiler.ui.JBRunnerClosableTabs.class);
            assertNotNull(tabs);
            var names = tabs.getTabs().stream().map(com.intellij.ui.tabs.TabInfo::getText).toList();
            assertTrue(names.toString(), names.contains("Flame Graph"));
            assertTrue(names.toString(), names.contains("Call Tree"));
            assertTrue(names.toString(), names.contains("Method List"));
            var selector = com.intellij.util.ui.UIUtil.findComponentOfType(component, com.intellij.profiler.ui.LabeledComboBox.class);
            assertNotNull(selector);
            var combo = com.intellij.util.ui.UIUtil.findComponentOfType(selector, javax.swing.JComboBox.class);
            assertNotNull(combo);
            assertEquals(2, combo.getItemCount());
            assertEquals(0, combo.getSelectedIndex());
            // Switch the real native control both ways and across all three tabs.
            for (int mode : new int[]{0, 1, 0}) {
                combo.setSelectedIndex(mode);
                long expectedTotal = trees.get(mode).getCallTreeModelProvider().getModel().values().iterator().next().getCallTree().getChildren().stream().mapToLong(n -> n.getData().getValue()).sum();
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while ((nativeView.getRichCallModel().isEmpty()
                        || nativeView.getRichCallModel().values().iterator().next().getCallTree().getChildren().stream().mapToLong(n -> n.getData().getValue()).sum() != expectedTotal)
                        && System.nanoTime() < deadline) {
                    com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
                    Thread.sleep(20);
                }
                assertFalse(nativeView.getRichCallModel().isEmpty());
                assertEquals(expectedTotal, nativeView.getRichCallModel().values().iterator().next().getCallTree().getChildren().stream().mapToLong(n -> n.getData().getValue()).sum());
                for (var tab : tabs.getTabs()) {
                    tabs.select(tab, true);
                    com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
                    assertEquals(expectedTotal, nativeView.getRichCallModel().values().iterator().next().getCallTree().getChildren().stream().mapToLong(n -> n.getData().getValue()).sum());
                    component.setSize(1100, 700);
                    layout(component);
                    var picture = new java.awt.image.BufferedImage(1100, 700, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    var graphics = picture.createGraphics();
                    component.printAll(graphics);
                    graphics.dispose();
                    var output = Path.of(System.getProperty("factorio.test.workspace"), "intellij-factorio/build/reports/ui/profiling-" + (mode == 0 ? "execution-" : "entire-") + tab.getText().replace(' ', '-') + ".png");
                    Files.createDirectories(output.getParent());
                    javax.imageio.ImageIO.write(picture, "png", output.toFile());
                }
            }
        } finally { window.dispose(); Disposer.dispose(disposable); }
    }
    private static void layout(java.awt.Container parent) {
        parent.doLayout();
        for (var child : parent.getComponents()) if (child instanceof java.awt.Container c) layout(c);
    }
}
