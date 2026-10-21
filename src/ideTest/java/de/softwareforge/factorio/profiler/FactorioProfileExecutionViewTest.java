package de.softwareforge.factorio.profiler;

import com.intellij.profiler.api.BaseCallStackElement;
import com.intellij.profiler.api.CallTreeBuildingData;
import com.intellij.profiler.model.CallTreeNode;
import com.intellij.testFramework.HeavyPlatformTestCase;
import de.softwareforge.factorio.FactorioProfile;
import de.softwareforge.factorio.FactorioSettings;
import java.util.List;

public class FactorioProfileExecutionViewTest extends HeavyPlatformTestCase {
    public void testExecutionTotalsExcludeOnlyRootSelfTime() {
        FactorioSettings.get(getProject()).serviceMode = "DISABLED";
        var profile = new FactorioProfile(List.of(
                node(0, "(root)", 1_000, 1_500, 1, 2),
                node(1, "on_tick", 50, 300, 3),
                node(2, "on_nth_tick", 200, 200),
                node(3, "dispatch", 150, 250, 4, 5, 6),
                node(4, "native call", 50, 50),
                node(5, "(pause)", 40, 40),
                node(6, "(hook: line)", 10, 10)), 1_500);
        var trees = FactorioProfileImporter.data(getProject(), profile).getTrees();
        assertEquals(2, trees.size());
        var execution = root(trees.getFirst());
        var entire = root(trees.get(1));
        assertNull(execution.getData());
        assertEquals(500L, total(execution));
        assertEquals(1_500L, total(entire));
        assertEquals(2, execution.getChildren().size());
        var recordedRoot = entire.getChildren().getFirst();
        assertEquals("(root — entire recording)", trees.get(1).getRenderer().getText(recordedRoot.getData().getCall()));
        assertEquals(1_500L, recordedRoot.getData().getValue());
        assertEquals(1_000L, recordedRoot.getData().getValue() - recordedRoot.getChildren().stream().mapToLong(n -> n.getData().getValue()).sum());
        assertEquals(0.6, (double) execution.getChildren().getFirst().getData().getValue() / total(execution), 0.00001);
        assertEquals(0.2, (double) recordedRoot.getChildren().getFirst().getData().getValue() / total(entire), 0.00001);
        for (int i = 0; i < execution.getChildren().size(); i++)
            assertSameCallsAndTimes(recordedRoot.getChildren().get(i), execution.getChildren().get(i));
        // Pause, hook and native timings are retained; this option excludes only
        // time outside recorded calls. The immutable source recording is intact.
        assertEquals(1_500L, profile.durationNanos());
        assertEquals(1_000L, profile.nodes().getFirst().selfNanos());
    }

    public void testIdleOnlyRecordingStillOffersEntireRecording() {
        FactorioSettings.get(getProject()).serviceMode = "DISABLED";
        var idle = new FactorioProfile(List.of(node(0, "(root)", 1_000, 1_000)), 1_000);
        var data = FactorioProfileImporter.data(getProject(), idle);
        assertFalse("Keep the selector available when only the default view is empty", data.isEmpty());
        assertEquals(2, data.getTrees().size());
        assertTrue(data.getTrees().getFirst().getCallTreeModelProvider().getModel().isEmpty());
        assertEquals(1_000L, total(root(data.getTrees().get(1))));
        var empty = new FactorioProfile(List.of(node(0, "(root)", 0, 0)), 0);
        assertTrue(FactorioProfileImporter.data(getProject(), empty).isEmpty());
    }

    private static FactorioProfile.Node node(int id, String name, long self, long total, Integer... children) {
        return new FactorioProfile.Node(id, new FactorioProfile.Frame(name, "", -1), List.of(children), self, total);
    }
    private static CallTreeNode<BaseCallStackElement> root(CallTreeBuildingData tree) {
        return tree.getCallTreeModelProvider().getModel().values().iterator().next().getCallTree();
    }
    private static long total(CallTreeNode<BaseCallStackElement> root) {
        return root.getChildren().stream().mapToLong(n -> n.getData().getValue()).sum();
    }
    private static void assertSameCallsAndTimes(CallTreeNode<BaseCallStackElement> expected, CallTreeNode<BaseCallStackElement> actual) {
        assertEquals(expected.getData().getCall(), actual.getData().getCall());
        assertEquals(expected.getData().getValue(), actual.getData().getValue());
        assertEquals(expected.getChildren().size(), actual.getChildren().size());
        for (int i = 0; i < expected.getChildren().size(); i++)
            assertSameCallsAndTimes(expected.getChildren().get(i), actual.getChildren().get(i));
    }
}
