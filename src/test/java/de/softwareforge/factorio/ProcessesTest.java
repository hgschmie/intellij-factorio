package de.softwareforge.factorio;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ProcessesTest {
    @TempDir Path root;
    private static ProgressIndicator indicator() {
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        return (ProgressIndicator)java.lang.reflect.Proxy.newProxyInstance(ProgressIndicator.class.getClassLoader(),new Class<?>[]{ProgressIndicator.class},(proxy,method,args)->{
            if(method.getName().equals("cancel")){cancelled.set(true);return null;}
            if(method.getName().equals("checkCanceled")){if(cancelled.get())throw new ProcessCanceledException();return null;}
            if(method.getName().equals("isCanceled"))return cancelled.get();
            throw new UnsupportedOperationException(method.getName());
        });
    }
    @Test void serializesMutatingCommandsPerDirectory() {
        var runner=new Processes();runner.acquire(root);
        assertThrows(IllegalStateException.class,()->runner.acquire(root.resolve(".")));
        runner.release(root);runner.acquire(root);runner.release(root);
    }
    @Test void redactsSecretsAndReportsNonzeroExit() throws Exception {
        var runner=new Processes();var log=new StringBuilder();
        var error=assertThrows(java.io.IOException.class,()->runner.run(List.of("/bin/sh","-c","echo \"$FACTORIO_UPLOAD_API_KEY\"; exit 7"),root,Map.of("FACTORIO_UPLOAD_API_KEY","fake-secret"),indicator(),log::append));
        assertFalse(log.toString().contains("fake-secret"));assertTrue(log.toString().contains("[redacted]"));assertTrue(error.getMessage().contains("exit 7"));
    }
    @Test void commandPathControlsExecutableLookupAndChildTools() throws Exception {
        Path tools = Files.createDirectories(root.resolve("custom tools"));
        Path launcher = tools.resolve("fmtk-test-launcher");
        Path signer = tools.resolve("fmtk-test-signer");
        Files.writeString(launcher, "#!/bin/sh\nfmtk-test-signer\n");
        Files.writeString(signer, "#!/bin/sh\necho found-custom-signer\n");
        assertTrue(launcher.toFile().setExecutable(true));
        assertTrue(signer.toFile().setExecutable(true));
        var env = CommandEnvironment.environment(tools.toString());
        assertEquals(tools.toString(), env.get("PATH"));
        var process = CommandEnvironment.command(List.of("fmtk-test-launcher"), root, env).createProcess();
        assertTrue(process.waitFor(5, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
        assertEquals("found-custom-signer", new String(process.getInputStream().readAllBytes()).trim());
        assertEquals(com.intellij.util.EnvironmentUtil.getEnvironmentMap(), CommandEnvironment.environment(""));
    }
    @Test void cancellationStopsOwnedChild() throws Exception {
        var runner=new Processes();var indicator=indicator();
        Path pid=root.resolve("child.pid");
        var future=CompletableFuture.runAsync(()->{
            try { runner.run(List.of("/bin/sh","-c","sleep 60 & echo $! > child.pid; wait"),root,Map.of(),indicator,s->{});fail("Expected cancellation"); }
            catch(ProcessCanceledException expected) {}
            catch(Exception failure) {throw new CompletionException(failure);}
        });
        try {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(!Files.exists(pid) && System.nanoTime()<deadline)Thread.sleep(20);
            assertTrue(Files.exists(pid));Thread.sleep(200);indicator.cancel();future.get(5,TimeUnit.SECONDS);
            long child=Long.parseLong(Files.readString(pid).trim());
            assertFalse(ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false));
        } finally { indicator.cancel();runner.dispose(); }
    }
}
