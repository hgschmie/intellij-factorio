package de.softwareforge.factorio;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import java.util.concurrent.TimeUnit;

/** Gives LSP4IJ's asynchronous disconnect time to finish before IDE process teardown. */
final class FactorioDebugProcessHandler extends OSProcessHandler {
    private volatile Runnable disconnect = () -> {};
    private volatile Runnable stopRequested = () -> {};
    FactorioDebugProcessHandler(GeneralCommandLine command) throws ExecutionException {
        super(command);
    }

    void onStop(Runnable disconnect) { this.disconnect = disconnect; }
    void onStopRequested(Runnable action) { this.stopRequested = action; }

    @Override protected void doDestroyProcess() {
        // Cancel an adapter-requested restart immediately, before asynchronous cleanup.
        stopRequested.run();
        // The default OSProcessHandler kills the process tree immediately. Keep stdin
        // open while the DAP client sends disconnect; never block the UI waiting for it.
        executeTask(() -> {
            try {
                disconnect.run();
                if (getProcess().waitFor(5, TimeUnit.SECONDS)) return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException failedDisconnect) {
                // A failed protocol request still needs process cleanup.
            }
            // Also covers failed initialization and an unresponsive adapter.
            super.doDestroyProcess();
        });
    }
}
