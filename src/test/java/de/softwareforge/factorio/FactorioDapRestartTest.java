package de.softwareforge.factorio;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FactorioDapRestartTest {
    @Test void preservesOpaqueDataAndAcceptsOnlyOneRestart() {
        var restart=new FactorioDapRestart();
        var data=Map.of("relaunchArgs",List.of("--config","a path/config.ini"),"nested",Map.of("value",42));
        assertTrue(restart.request(data));
        assertFalse(restart.request(true));
        assertSame(data,restart.take());
        assertNull(restart.take());
        assertFalse(restart.request(data));
    }

    @Test void normalExitDoesNotRestart() {
        var restart=new FactorioDapRestart();
        assertFalse(restart.request(null));
        assertFalse(restart.request(false));
        assertNull(restart.take());
        assertTrue(restart.request(true));
        assertEquals(true,restart.take());
    }

    @Test void emptyObjectIsStillARestartRequest() {
        var restart=new FactorioDapRestart();
        assertTrue(restart.request(Map.of()));
        assertEquals(Map.of(),restart.take());
    }

    @Test void stopCancelsPendingAndLateRequests() {
        var pending=new FactorioDapRestart();
        assertTrue(pending.request(true));
        pending.cancel();
        assertNull(pending.take());
        assertFalse(pending.request(true));
        var stopped=new FactorioDapRestart();
        stopped.cancel();
        assertFalse(stopped.request(true));
        assertNull(stopped.take());
    }
}
