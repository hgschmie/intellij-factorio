package de.softwareforge.factorio;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import java.util.concurrent.TimeUnit;

/** Keeps DAP streams open through graceful process shutdown and final output. */
final class FactorioDebugProcessHandler extends OSProcessHandler {
    private volatile Runnable shutdown = this::stopAdapter;
    private volatile Runnable stopRequested = () -> {};
    private final java.util.concurrent.atomic.AtomicBoolean adapterStopping = new java.util.concurrent.atomic.AtomicBoolean();
    FactorioDebugProcessHandler(GeneralCommandLine command) throws ExecutionException {
        super(command);
    }

    void onStop(Runnable shutdown) { this.shutdown = shutdown; }
    void onStopRequested(Runnable action) { this.stopRequested = action; }

    @Override protected void doDestroyProcess() {
        // Cancel an adapter-requested restart immediately, before asynchronous cleanup.
        stopRequested.run();
        executeTask(() -> {
            try { shutdown.run(); }
            catch (RuntimeException failedRequest) { stopAdapter(); }
        });
    }

    void stopAdapter() {
        if (!adapterStopping.compareAndSet(false, true)) return;
        executeTask(() -> {
            try {
                if (!getProcess().isAlive()) return;
                if (com.intellij.openapi.util.SystemInfo.isWindows) {
                    // VS Code uses a forced tree kill on Windows as well.
                    super.doDestroyProcess();
                    return;
                }
                // Process.destroy() also closes the Java streams. ProcessHandle sends
                // SIGTERM without closing stdin/stdout while Factorio is cleaning up.
                getProcess().toHandle().destroy();
                if (getProcess().waitFor(5, TimeUnit.SECONDS)) return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException failedSignal) {
                // A failed protocol request still needs process cleanup.
            }
            // Last resort for a game that ignores SIGTERM.
            super.doDestroyProcess();
        });
    }
}
