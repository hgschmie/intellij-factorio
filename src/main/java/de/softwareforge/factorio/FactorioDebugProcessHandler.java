package de.softwareforge.factorio;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import java.util.concurrent.TimeUnit;

/** Gives LSP4IJ's asynchronous disconnect time to finish before IDE process teardown. */
final class FactorioDebugProcessHandler extends OSProcessHandler {
    private volatile Runnable disconnect = () -> {};
    FactorioDebugProcessHandler(GeneralCommandLine command) throws ExecutionException {
        super(command);
    }

    void onStop(Runnable disconnect) { this.disconnect = disconnect; }

    @Override protected void doDestroyProcess() {
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
