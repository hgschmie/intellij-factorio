package de.softwareforge.factorio.profiler;

import com.intellij.openapi.project.Project;
import com.intellij.profiler.*;
import com.intellij.profiler.api.*;
import com.intellij.profiler.model.ThreadInfo;
import com.intellij.profiler.ui.BaseCallStackElementRenderer;
import com.intellij.profiler.ui.threadview.ThreadMetric;
import de.softwareforge.factorio.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Common-profiler integration, loaded only when the IDE supplies that module. */
public final class FactorioProfileImporter implements ProfilerDumpParserProvider, FactorioProfileViewer {
    @Override public String getId() { return "factorio.lua"; }
    @Override public String getName() { return "Factorio Lua"; }
    // Chrome CPU profiles have no reliable Factorio producer signature. A null
    // extension keeps us in the explicit chooser, never automatic file routing.
    @Override public String getRequiredFileExtension() { return null; }
    @Override public ProfilerDumpFileParser createParser(Project project) {
        return new ProfilerDumpFileParser() {
            @Override public String getHelpId() { return null; }
            @Override public ProfilerDumpFileParsingResult parse(java.io.File file, com.intellij.openapi.progress.ProgressIndicator indicator) {
                try {
                    var profile = FactorioProfile.read(file.toPath(), indicator::checkCanceled);
                    return new Success(data(project, profile, indicator::checkCanceled));
                } catch (java.io.IOException e) { return new Failure("Cannot read Factorio profile: " + e.getMessage()); }
            }
        };
    }
    @Override public void open(Project project, Path snapshot) {
        // FDE's own Open action and completed captures explicitly select this parser.
        new ProfilerDumpDescriptor(project, snapshot.getFileName().toString(), new Date(),
                snapshot.toFile(), this, new AtomicInteger()).open();
    }
    public static SamplingProfilerData data(Project project, FactorioProfile profile) {
        return data(project, profile, com.intellij.openapi.progress.ProgressManager::checkCanceled);
    }
    private static SamplingProfilerData data(Project project, FactorioProfile profile, Runnable checkCancelled) {
        var names = FactorioProfileSources.displayNames(project, profile, checkCancelled);
        var modules = new FactorioProfileModules(profile, FactorioModules.get(project).mods(), checkCancelled);
        var styles = FactorioProfileColors.create(profile, modules, checkCancelled);
        var frames = new HashMap<FactorioProfile.Frame, LuaFrame>();
        for (var entry : styles.entrySet()) {
            checkCancelled.run();
            var frame = entry.getKey();
            frames.put(frame, new LuaFrame(frame, names.getOrDefault(frame, frame.name()), entry.getValue()));
        }
        var renderer = new BaseCallStackElementRenderer() {
            @Override public String getText(BaseCallStackElement element) {
                var frame = (LuaFrame) element;
                return frame.frame.name().equals("(root)") ? "(root — entire recording)" : frame.displayName;
            }
            @Override public String getCommentInfo(BaseCallStackElement element) {
                var frame = (LuaFrame) element;
                if (frame.frame.name().equals("(root)"))
                    return "Total: entire recording. Self: time outside recorded Lua calls.";
                var description = frame.style.description();
                return frame.frame.source().isEmpty() ? description : description + " — " + frame.location();
            }
            @Override public com.intellij.profiler.ui.flamechart.FlameChartColorScheme getColorScheme(BaseCallStackElement element) {
                return ((LuaFrame) element).style.colors();
            }
        };
        return new Data(profile, modules, Map.copyOf(frames), renderer, checkCancelled);
    }
    /** Immutable import context, reused by per-viewer module selections. */
    static final class Data extends SingleCallTreeProfilerData {
        final FactorioProfile profile;
        final FactorioProfileModules modules;
        final Map<FactorioProfile.Frame, LuaFrame> frames;
        final BaseCallStackElementRenderer renderer;
        private final List<CallTreeBuildingData> trees;
        Data(FactorioProfile profile, FactorioProfileModules modules, Map<FactorioProfile.Frame, LuaFrame> frames,
             BaseCallStackElementRenderer renderer, Runnable checkCancelled) {
            super(new CallTreeBuildingData("Lua execution", renderer,
                    new Builder(profile, frames, false, checkCancelled), "factorio.lua.execution"));
            this.profile = profile; this.modules = modules; this.frames = frames; this.renderer = renderer;
            trees = profile.durationNanos() == 0 ? List.of() : List.of(super.getTrees().getFirst(),
                    new CallTreeBuildingData("Including unattributed time", renderer,
                            new Builder(profile, frames, true, checkCancelled), "factorio.lua.recording"));
        }
        List<CallTreeBuildingData> trees(FactorioProfile filtered, boolean all, Runnable checkCancelled) {
            // A selected module cannot own time outside recorded Lua stacks.
            var execution = new CallTreeBuildingData("Lua execution", renderer,
                    new Builder(filtered, frames, false, checkCancelled), "factorio.lua.execution");
            if (!all) return List.of(execution); // Retain the filter control for an empty selection.
            if (filtered.durationNanos() == 0) return List.of();
            return List.of(execution, new CallTreeBuildingData("Including unattributed time", renderer,
                    new Builder(filtered, frames, true, checkCancelled), "factorio.lua.recording"));
        }
        @Override public boolean isEmpty() { return trees.isEmpty(); }
        @Override public List<CallTreeBuildingData> getTrees() { return trees; }
        @Override public SamplingProfilerData createSubTreeProfilerData(CallTreeBuildingData tree) {
            return new SingleCallTreeProfilerData(tree);
        }
        @Override public javax.swing.JComponent doCreateTopLevelComponent(Project project, com.intellij.openapi.Disposable parent) {
            return new FactorioProfileModulePanel(project, this, trees, null, false, null, parent).nativeView();
        }
    }
    /** One immutable filtered view; the original import remains available for All. */
    static final class FilteredData extends SingleCallTreeProfilerData {
        private final Data original;
        private final List<CallTreeBuildingData> trees;
        private final Set<String> selection;
        private final boolean lastAllIncludedUnattributed;
        private final String selectedTab;
        FilteredData(Data original, List<CallTreeBuildingData> trees, Set<String> selection,
                     boolean lastAllIncludedUnattributed, String selectedTab) {
            super(trees.getFirst());
            this.original = original; this.trees = List.copyOf(trees); this.selection = selection;
            this.lastAllIncludedUnattributed = lastAllIncludedUnattributed; this.selectedTab = selectedTab;
        }
        @Override public boolean isEmpty() { return false; } // Keep controls for Modules: None.
        @Override public List<CallTreeBuildingData> getTrees() { return trees; }
        @Override public javax.swing.JComponent doCreateTopLevelComponent(Project project, com.intellij.openapi.Disposable parent) {
            return new FactorioProfileModulePanel(project, original, trees, selection,
                    lastAllIncludedUnattributed, selectedTab, parent).nativeView();
        }
    }
    private static final class TimeMetric implements ValueMetric {
        final boolean entireRecording;
        TimeMetric(boolean entireRecording) { this.entireRecording = entireRecording; }
        @Override public String getName() { return "Time"; }
        @Override public String getColumnTitle() { return "Time (ms)"; }
        @Override public String getUnit() { return "ms"; }
        @Override public ThreadMetric<Float> getThreadMetric() { return null; }
        @Override public String formatValue(long value) { return String.format(Locale.ROOT, "%.6f", value / 1_000_000.0); }
        @Override public String formatUnit(long value) { return "ms"; }
    }
    private static final ValueMetric EXECUTION_TIME = new TimeMetric(false);
    private static final ValueMetric RECORDING_TIME = new TimeMetric(true);
    static boolean includesUnattributed(ValueMetric metric) {
        return metric instanceof TimeMetric time && time.entireRecording;
    }
    private static final class Builder extends BaseCallTreeBuilder<BaseCallStackElement> {
        Builder(FactorioProfile profile, Map<FactorioProfile.Frame, LuaFrame> frames,
                boolean includeUnattributed, Runnable checkCancelled) {
            setMetric(includeUnattributed ? RECORDING_TIME : EXECUTION_TIME);
            if (profile.nodes().isEmpty()) return;
            var recordedRoot = profile.nodes().getFirst();
            long total = includeUnattributed ? profile.durationNanos()
                    : profile.durationNanos() - recordedRoot.selfNanos();
            if (total == 0) return;
            // In the execution view, promote the recorded root's children to the
            // invisible platform root. Exclude root self time from the denominator,
            // not merely its rectangle. All child self/inclusive timings stay intact.
            var root = new MainCallTreeNode<BaseCallStackElement>(null, total, 0, null);
            var tree = new HashMap<Integer, MainCallTreeNode<BaseCallStackElement>>();
            for (var node : profile.nodes()) {
                checkCancelled.run();
                if (!includeUnattributed && node.id() == recordedRoot.id()) continue;
                tree.put(node.id(), new MainCallTreeNode<>(Objects.requireNonNull(frames.get(node.frame())), node.totalNanos(), 0, null));
            }
            if (includeUnattributed) {
                var first = tree.get(recordedRoot.id());
                first.setParent(root);
                first.setDepth(1);
                root.addChild(first);
            } else {
                tree.put(recordedRoot.id(), root);
            }
            for (var node : profile.nodes()) {
                checkCancelled.run();
                var parent = tree.get(node.id());
                for (int childId : node.children()) {
                    var child = tree.get(childId);
                    child.setParent(parent);
                    child.setDepth(parent.getDepth() + 1);
                    parent.addChild(child);
                }
            }
            getForest().put(new ThreadInfo() {
                @Override public String getNativeId() { return "factorio.lua"; }
                @Override public String getName() { return "Lua (all contexts)"; }
            }, root);
        }
    }
    public static final class LuaFrame extends BaseCallStackElement {
        final FactorioProfile.Frame frame;
        final String displayName;
        // Native comparisons reuse one renderer for frames from both imports.
        // Keep presentation with the frame, without affecting its raw identity.
        final FactorioProfileColors.Style style;
        public LuaFrame(FactorioProfile.Frame frame) { this(frame, frame.name()); }
        public LuaFrame(FactorioProfile.Frame frame, String displayName) {
            this(frame, displayName, FactorioProfileColors.fallback(frame));
        }
        private LuaFrame(FactorioProfile.Frame frame, String displayName, FactorioProfileColors.Style style) {
            this.frame = frame; this.displayName = displayName; this.style = style;
        }
        @Override public String fullName() { return displayName + " — " + location(); }
        String location() { return frame.nativeCall() ? "Native" : frame.source().replaceFirst("^@", "") + (frame.line() >= 0 ? ":" + (frame.line() + 1) : ""); }
        @Override public boolean isNavigatable() { return !frame.nativeCall() && !frame.source().isBlank() && frame.line() >= 0; }
        @Override public com.intellij.psi.NavigatablePsiElement[] calcNavigatables(Project project) {
            return FactorioProfileSources.navigatables(project, frame, displayName);
        }
        @Override public boolean equals(Object other) { return other instanceof LuaFrame f && frame.equals(f.frame); }
        @Override public int hashCode() { return frame.hashCode(); }
    }
}
