package de.softwareforge.factorio;

import com.intellij.execution.configurations.GeneralCommandLine;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class FactorioDebugProcessHandlerTest {
    @Test void keepsInputOpenForGracefulDisconnect() throws Exception {
        var handler = new FactorioDebugProcessHandler(new GeneralCommandLine("/bin/sh", "-c", "read request; exit 0"));
        var requested = new java.util.concurrent.atomic.AtomicBoolean();
        handler.onStop(() -> {
            requested.set(true);
            try {
                handler.getProcessInput().write("disconnect\n".getBytes(StandardCharsets.UTF_8));
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

    @Test void killsAnAdapterThatIgnoresDisconnect() throws Exception {
        var handler = new FactorioDebugProcessHandler(new GeneralCommandLine("/bin/sh", "-c", "read request"));
        handler.startNotify();
        try {
            handler.destroyProcess();
            assertTrue(handler.getProcess().isAlive(), "Stop must allow the DAP request to finish");
            assertTrue(handler.getProcess().waitFor(10, TimeUnit.SECONDS), "Stop must have a bounded fallback");
            assertNotEquals(0, handler.getProcess().exitValue());
        } finally { handler.getProcess().destroyForcibly(); }
    }
}
