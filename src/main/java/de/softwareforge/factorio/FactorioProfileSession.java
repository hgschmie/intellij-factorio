package de.softwareforge.factorio;

import java.util.concurrent.*;
import java.util.function.*;

/** One adapter connection's recording lifecycle; events are authoritative, even before responses. */
final class FactorioProfileSession {
    enum State { UNAVAILABLE, IDLE, STARTING, RECORDING, STOPPING, CLOSED }
    private State state = State.UNAVAILABLE;
    private boolean closing;
    private long generation;
    private Function<String, CompletableFuture<?>> request;
    private CompletableFuture<Void> pending = CompletableFuture.completedFuture(null);
    private final Consumer<String> completed;
    private final Consumer<String> error;
    private final Consumer<String> status;

    FactorioProfileSession(Consumer<String> completed, Consumer<String> error) {
        this(completed, error, message -> {});
    }
    FactorioProfileSession(Consumer<String> completed, Consumer<String> error, Consumer<String> status) {
        this.completed = completed;
        this.error = error;
        this.status = status;
    }
    synchronized void connect(Function<String, CompletableFuture<?>> request) {
        if (state == State.CLOSED) return;
        this.request = request;
        state = State.IDLE;
    }
    synchronized State state() { return state; }
    synchronized boolean canStart() { return !closing && state == State.IDLE; }
    synchronized boolean canStop() { return !closing && state == State.RECORDING; }
    synchronized CompletableFuture<Void> start() {
        if (!canStart()) return pending;
        return command(true);
    }
    synchronized CompletableFuture<Void> stop() {
        if (state != State.RECORDING) return pending;
        return command(false);
    }
    private CompletableFuture<Void> command(boolean start) {
        state = start ? State.STARTING : State.STOPPING;
        long token = ++generation;
        var operation = new CompletableFuture<Void>();
        pending = operation;
        // Saving large native recordings can take much longer than ten seconds. A slow
        // response does not mean recording resumed: keep this operation pending and do
        // not allow another stop/start to race with the native writer.
        CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS).execute(() -> {
            synchronized (this) {
                if (token != generation || operation.isDone() || state == State.CLOSED) return;
            }
            status.accept(start ? "Waiting for Factorio to start Lua profiling."
                    : "Factorio is still saving the Lua profile. Large recordings can take several minutes; keep the game open.");
        });
        operation.whenComplete((unused, failure) -> {
            boolean report;
            synchronized (this) {
                if (failure == null || token != generation || state == State.CLOSED) return;
                ++generation;
                state = start ? State.IDLE : State.RECORDING;
                report = !closing;
            }
            if (report) error.accept("Could not " + (start ? "start" : "stop") + " profiling: " + failureMessage(failure));
        });
        try {
            request.apply(start ? "startProfile" : "stopProfile").whenComplete((unused, failure) -> {
                if (failure != null) operation.completeExceptionally(failure);
            });
        } catch (RuntimeException failure) { operation.completeExceptionally(failure); }
        return operation;
    }
    synchronized void running() {
        if (state == State.CLOSED) return;
        // A duplicate running event must not cancel an outstanding stop.
        if (state == State.STOPPING) return;
        ++generation;
        state = State.RECORDING;
        pending.complete(null);
    }
    void complete(String path) {
        synchronized (this) {
            if (state == State.CLOSED) return;
            ++generation;
            state = State.IDLE;
            pending.complete(null);
        }
        if (path == null || path.isBlank()) error.accept("Factorio completed profiling without a snapshot path.");
        else completed.accept(path);
    }
    synchronized CompletableFuture<Void> finish() {
        closing = true;
        if (state == State.STARTING) {
            return pending.handle((unused, failure) -> null).thenCompose(unused -> stop());
        }
        return stop();
    }
    void close() {
        boolean unsaved;
        synchronized (this) {
            if (state == State.CLOSED) return;
            unsaved = state == State.STARTING || state == State.RECORDING || state == State.STOPPING;
            ++generation;
            closing = true;
            state = State.CLOSED;
            pending.complete(null);
            request = null;
        }
        if (unsaved) error.accept("Factorio exited before the Lua profile was saved. "
                + "Use Stop Lua Profiling and wait for the saved snapshot before quitting inside Factorio.");
    }
    private static String failureMessage(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause() != null)
            failure = failure.getCause();
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
