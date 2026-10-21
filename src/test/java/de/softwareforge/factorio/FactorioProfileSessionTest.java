package de.softwareforge.factorio;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class FactorioProfileSessionTest {
    @Test void eventsBeforeResponsesWinAndDuplicateClicksDoNotSendRequests() {
        var paths = new ArrayList<String>(); var errors = new ArrayList<String>();
        var requests = new ArrayList<String>(); var responses = new ArrayList<CompletableFuture<?>>();
        var session = new FactorioProfileSession(paths::add, errors::add);
        assertFalse(session.canStart());
        session.connect(method -> { requests.add(method); var f = new CompletableFuture<>(); responses.add(f); return f; });
        var start = session.start();
        session.start();
        assertEquals(List.of("startProfile"), requests);
        session.running();
        responses.getFirst().completeExceptionally(new RuntimeException("late response"));
        assertTrue(start.isDone()); assertTrue(session.canStop()); assertTrue(errors.isEmpty());
        session.stop(); session.stop(); session.running();
        assertEquals(FactorioProfileSession.State.STOPPING, session.state());
        session.complete("one.cpuprofile");
        assertEquals(List.of("one.cpuprofile"), paths);
        assertTrue(session.canStart());
    }
    @Test void failureRecoversAndLuaCanInitiateRecording() {
        var errors = new ArrayList<String>(); var paths = new ArrayList<String>();
        var session = new FactorioProfileSession(paths::add, errors::add);
        session.connect(method -> CompletableFuture.failedFuture(new RuntimeException("unsupported")));
        session.start(); assertTrue(session.canStart()); assertEquals(1, errors.size());
        session.running(); assertTrue(session.canStop());
        session.complete("lua.cpuprofile"); assertEquals(List.of("lua.cpuprofile"), paths);
        session.close(); session.running(); session.complete("old.cpuprofile");
        assertEquals(FactorioProfileSession.State.CLOSED, session.state());
        assertEquals(1, paths.size());
    }
    @Test void shutdownWaitsForStartingRecordingThenStopsIt() {
        var requests = new ArrayList<String>();
        var session = new FactorioProfileSession(path -> {}, error -> fail(error));
        session.connect(method -> { requests.add(method); return CompletableFuture.completedFuture(null); });
        session.start(); var finished = session.finish();
        assertFalse(finished.isDone());
        session.running();
        assertEquals(List.of("startProfile", "stopProfile"), requests);
        session.complete("saved.cpuprofile");
        assertTrue(finished.isDone()); assertFalse(session.canStart());
    }
    @Test void slowSaveStaysPendingAndItsLateCompletionIsSuccessful() throws Exception {
        var status = new LinkedBlockingQueue<String>();
        var errors = new CopyOnWriteArrayList<String>();
        var paths = new ArrayList<String>(); var requests = new ArrayList<String>();
        var session = new FactorioProfileSession(paths::add, errors::add, status::add);
        session.connect(method -> { requests.add(method); return CompletableFuture.completedFuture(null); });
        session.running();
        var stopped = session.stop();
        assertNotNull(status.poll(15, TimeUnit.SECONDS), "A slow save should report progress");
        assertFalse(stopped.isDone());
        assertEquals(FactorioProfileSession.State.STOPPING, session.state());
        session.start(); session.stop();
        assertEquals(List.of("stopProfile"), requests);
        assertTrue(errors.isEmpty());
        session.complete("large.cpuprofile");
        stopped.join();
        assertEquals(List.of("large.cpuprofile"), paths);
        assertTrue(session.canStart());
    }
    @Test void emptyErrorMessagesAreExplainedAndUnsavedExitIsReportedOnce() {
        var errors = new ArrayList<String>();
        var session = new FactorioProfileSession(path -> fail("No snapshot was sent"), errors::add);
        session.connect(method -> CompletableFuture.failedFuture(new CompletionException(new TimeoutException())));
        session.running(); session.stop();
        assertEquals("Could not stop profiling: TimeoutException", errors.getFirst());
        session.close(); session.close();
        assertEquals(2, errors.size());
        assertTrue(errors.get(1).contains("exited before the Lua profile was saved"));
    }
}
