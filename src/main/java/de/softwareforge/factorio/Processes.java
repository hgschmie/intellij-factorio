package de.softwareforge.factorio;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Service(Service.Level.PROJECT)
public final class Processes implements Disposable {
    private final java.util.function.Supplier<String> commandPath;
    public Processes(Project project) { commandPath = () -> FactorioSettings.get(project).commandPath; }
    Processes() { commandPath = () -> ""; }
    private final Set<Process> children = ConcurrentHashMap.newKeySet();
    private final Map<Process, Set<ProcessHandle>> descendants = new ConcurrentHashMap<>();
    private static final Set<Path> busy = ConcurrentHashMap.newKeySet();
    public static Processes get(Project project) { return project.getService(Processes.class); }
    private static Path canonical(Path path) {
        try { return path.toRealPath(); } catch(IOException e) { return path.toAbsolutePath().normalize(); }
    }
    public void acquire(Path directory) {
        if (!busy.add(canonical(directory))) throw new IllegalStateException("Another FMTK operation is running for " + directory);
    }
    public void release(Path directory) { busy.remove(canonical(directory)); }
    public String run(List<String> command, Path cwd, Map<String,String> env, ProgressIndicator indicator, Consumer<String> log) throws Exception {
        indicator.checkCanceled();
        var environment = CommandEnvironment.environment(commandPath.get());
        environment.putAll(env);
        var builder = CommandEnvironment.command(command, cwd, environment).withRedirectErrorStream(true);
        Path temp = com.intellij.openapi.application.PathManager.getSystemDir().resolve("softwareforge-factorio/tmp"); Files.createDirectories(temp);
        builder.withEnvironment("TMPDIR", temp.toString());
        Process process = builder.createProcess(); children.add(process); descendants.put(process,ConcurrentHashMap.newKeySet()); process.getOutputStream().close();
        StringBuilder output = new StringBuilder();
        CompletableFuture<Void> reader = CompletableFuture.runAsync(() -> {
            try (var stream = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line; while ((line = stream.readLine()) != null) {
                    for (var e : env.entrySet()) if (e.getKey().contains("KEY") && !e.getValue().isEmpty()) line = line.replace(e.getValue(), "[redacted]");
                    synchronized (output) { if (output.length() < 2_000_000) output.append(line).append('\n'); }
                    log.accept(line + "\n");
                }
            } catch (IOException e) { if (process.isAlive()) throw new CompletionException(e); }
        });
        try {
            while (process.isAlive()) {
                descendants.get(process).addAll(process.descendants().toList());
                indicator.checkCanceled(); process.waitFor(100, TimeUnit.MILLISECONDS);
            }
            reader.get(10, TimeUnit.SECONDS);
            if (process.exitValue() != 0) throw new IOException("Command failed (exit " + process.exitValue() + "). See FMTK output.\n" + output);
            return output.toString();
        } finally { stop(process); children.remove(process); descendants.remove(process); }
    }
    private void stop(Process p) {
        var owned = new HashSet<>(descendants.getOrDefault(p,Set.of()));
        owned.addAll(p.descendants().toList()); owned.forEach(ProcessHandle::destroy); p.destroy();
        try { if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroyForcibly(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); p.destroyForcibly(); }
        owned.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    }
    @Override public void dispose() { children.forEach(this::stop); }
}
