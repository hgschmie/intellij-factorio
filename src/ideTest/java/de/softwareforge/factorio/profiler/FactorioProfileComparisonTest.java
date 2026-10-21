package de.softwareforge.factorio.profiler;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.profiler.api.*;
import com.intellij.profiler.diff.ProfilerCallStackElementsCombiner;
import com.intellij.profiler.diff.ProfilerSnapshotComparator;
import com.intellij.profiler.model.CallTreeNode;
import com.intellij.testFramework.HeavyPlatformTestCase;
import de.softwareforge.factorio.FactorioProfile;
import de.softwareforge.factorio.FactorioSettings;
import java.util.*;

public class FactorioProfileComparisonTest extends HeavyPlatformTestCase {
    public void testComparisonRendersFramesUniqueToBothRecordings() {
        FactorioSettings.get(getProject()).serviceMode = "DISABLED";
        var first = data("onlyInFirst", "A");
        var second = data("onlyInSecond", "B");
        assertComparison(first.getTrees(), second.getTrees());
        assertComparison(second.getTrees(), first.getTrees());
        var filteredFirst = first.trees(first.modules.select(first.profile, Set.of("A"), () -> {}), false, () -> {});
        var filteredSecond = second.trees(second.modules.select(second.profile, Set.of("B"), () -> {}), false, () -> {});
        assertComparison(filteredFirst, filteredSecond);
        assertComparison(filteredSecond, filteredFirst);
    }

    private void assertComparison(List<CallTreeBuildingData> first, List<CallTreeBuildingData> second) {
        record Presentation(String text, String comment, String colorKey) {}
        var expected = new HashMap<BaseCallStackElement, Presentation>();
        for (var trees : List.of(first, second)) for (var tree : trees) for (var call : calls(tree)) {
            var renderer = tree.getRenderer();
            expected.put(call, new Presentation(renderer.getText(call), renderer.getCommentInfo(call),
                    renderer.getColorScheme(call).getPrefix()));
        }
        // Use the native comparator and wrappers used by actual Profiler tabs.
        var comparator = new ProfilerSnapshotComparator(getProject(), ProfilerCallStackElementsCombiner.Companion.find(null));
        var diff = comparator.compare(new MultipleCallTreesProfilerData(first), new MultipleCallTreesProfilerData(second));
        assertFalse(diff.getTrees().isEmpty());
        for (var tree : diff.getTrees()) {
            var found = new HashSet<String>();
            for (var call : calls(tree)) {
                var renderer = tree.getRenderer();
                var presentation = expected.get(call);
                assertNotNull(call.fullName(), presentation);
                assertEquals(presentation.text(), renderer.getText(call));
                assertEquals(presentation.comment(), renderer.getCommentInfo(call));
                assertEquals(presentation.colorKey(), renderer.getColorScheme(call).getPrefix());
                var lua = (FactorioProfileImporter.LuaFrame) call;
                // Presentation must not change the raw identity used for aggregation.
                var renamed = new FactorioProfileImporter.LuaFrame(lua.frame, "different display name");
                assertEquals(lua, renamed);
                assertEquals(lua.hashCode(), renamed.hashCode());
                found.add(lua.frame.name());
            }
            assertTrue(found.toString(), found.containsAll(Set.of("onlyInFirst", "onlyInSecond")));
        }
    }

    private List<BaseCallStackElement> calls(CallTreeBuildingData tree) {
        var provider = tree.getCallTreeModelProvider();
        if (provider instanceof LazyCallTreeModelProviderWithProgress lazy) lazy.installIndicator(new EmptyProgressIndicator());
        var queue = new ArrayDeque<CallTreeNode<BaseCallStackElement>>();
        for (var model : provider.getModel().values()) queue.add(model.getCallTree());
        var calls = new ArrayList<BaseCallStackElement>();
        while (!queue.isEmpty()) {
            var node = queue.removeFirst();
            if (node.getData() != null) calls.add(node.getData().getCall());
            queue.addAll(node.getChildren());
        }
        return calls;
    }

    private FactorioProfileImporter.Data data(String call, String mod) {
        var profile = new FactorioProfile(List.of(
                new FactorioProfile.Node(0, new FactorioProfile.Frame("(root)", "", -1), List.of(1), 10, 110),
                new FactorioProfile.Node(1, new FactorioProfile.Frame(call, "__" + mod + "__/control.lua", 0), List.of(), 100, 100)), 110);
        return (FactorioProfileImporter.Data) FactorioProfileImporter.data(getProject(), profile);
    }
}
