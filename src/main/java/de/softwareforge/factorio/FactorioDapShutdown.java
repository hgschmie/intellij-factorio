package de.softwareforge.factorio;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.TerminateArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

/** VS Code's terminate -> terminated -> disconnect sequence, with bounded failure recovery. */
final class FactorioDapShutdown {
    private final Supplier<IDebugProtocolServer> server;
    private final BooleanSupplier supportsTerminate;
    private final Runnable stopAdapter;
    private final CompletableFuture<Void> ended = new CompletableFuture<>();
    private boolean stopping;
    private boolean disconnecting;
    private boolean finished;

    FactorioDapShutdown(Supplier<IDebugProtocolServer> server, BooleanSupplier supportsTerminate, Runnable stopAdapter) {
        this.server = server;
        this.supportsTerminate = supportsTerminate;
        this.stopAdapter = stopAdapter;
    }

    synchronized void stop() {
        // IntelliJ may call both the process handler and DAPClient for the same Stop.
        if (stopping || disconnecting || finished) return;
        stopping = true;
        var target = server.get();
        if (target == null) { stopAdapter.run(); return; }
        if (!supportsTerminate.getAsBoolean()) { disconnect(false); return; }
        var args = new TerminateArguments();
        args.setRestart(false);
        try {
            target.terminate(args).whenCompleteAsync((ignored, failure) -> {
                if (failure != null) disconnect(false);
            });
        } catch (RuntimeException failure) { disconnect(false); }
        // A terminate response only acknowledges the request. Keep reading until the
        // terminated event (or process exit), including while paused at a breakpoint.
        ended.orTimeout(5, TimeUnit.SECONDS).whenCompleteAsync((ignored, failure) -> {
            if (failure != null) disconnect(false);
        });
    }

    synchronized void terminated(boolean restarting) {
        ended.complete(null);
        disconnect(restarting);
    }

    private synchronized void disconnect(boolean restarting) {
        if (disconnecting || finished) return;
        disconnecting = true;
        var target = server.get();
        if (target == null) { stopAdapter.run(); return; }
        var args = new DisconnectArguments();
        args.setRestart(restarting);
        // Like VS Code, omit terminateDebuggee: Factorio does not advertise
        // supportTerminateDebuggee. This is a launch session, not an attachment.
        try {
            target.disconnect(args).orTimeout(2, TimeUnit.SECONDS)
                    .whenCompleteAsync((ignored, failure) -> stopAdapter.run());
        } catch (RuntimeException failure) { stopAdapter.run(); }
    }

    synchronized void exited() {
        finished = true;
        ended.complete(null);
    }
}
