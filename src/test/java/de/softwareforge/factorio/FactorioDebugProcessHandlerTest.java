package de.softwareforge.factorio;

import com.intellij.execution.configurations.GeneralCommandLine;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class FactorioDebugProcessHandlerTest {
    @Test void keepsInputOpenForGracefulTermination() throws Exception {
        var handler = new FactorioDebugProcessHandler(new GeneralCommandLine("/bin/sh", "-c", "read request; exit 0"));
        var requested = new java.util.concurrent.atomic.AtomicBoolean();
        handler.onStop(() -> {
            requested.set(true);
            try {
                handler.getProcessInput().write("terminate\n".getBytes(StandardCharsets.UTF_8));
                handler.getProcessInput().flush();
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        });
        handler.startNotify();
        try {
            handler.destroyProcess();
            assertTrue(handler.getProcess().waitFor(3, TimeUnit.SECONDS));
            assertTrue(requested.get());
            assertEquals(0, handler.getProcess().exitValue());
        } finally { handler.getProcess().destroyForcibly(); }
    }

    @Test void sigtermKeepsStreamsOpenForFinalCleanup() throws Exception {
        var output = new StringBuffer();
        var ready = new java.util.concurrent.CompletableFuture<Void>();
        var handler = new FactorioDebugProcessHandler(new GeneralCommandLine("/usr/bin/python3", "-u", "-c", """
                import os, select, signal, sys, time
                stopped = False
                def stop(*args):
                    global stopped
                    stopped = True
                signal.signal(signal.SIGTERM, stop)
                print('ready')
                while not stopped: time.sleep(.01)
                if select.select([sys.stdin], [], [], 0)[0] and not os.read(0, 1): sys.exit(97)
                print('Goodbye')
                """));
        handler.addProcessListener(new com.intellij.execution.process.ProcessAdapter() {
            @Override public void onTextAvailable(com.intellij.execution.process.ProcessEvent event, com.intellij.openapi.util.Key outputType) {
                if (!com.intellij.execution.process.ProcessOutputType.isStdout(outputType)) return;
                output.append(event.getText());
                if (output.indexOf("ready") >= 0) ready.complete(null);
            }
        });
        handler.startNotify();
        try {
            ready.get(5, TimeUnit.SECONDS);
            handler.destroyProcess();
            assertTrue(handler.waitFor(5_000));
            assertEquals(0, handler.getProcess().exitValue());
            assertTrue(output.toString().contains("Goodbye"));
        } finally { handler.getProcess().destroyForcibly(); }
    }
}
