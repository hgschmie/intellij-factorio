package de.softwareforge.factorio;

import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.debug.json.DebugMessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** One session's protocol trace. Disk I/O never runs on the UI or transport threads. */
final class FactorioDapLog implements AutoCloseable {
    private final ArrayBlockingQueue<String> pending=new ArrayBlockingQueue<>(1024);
    private final AtomicBoolean failed=new AtomicBoolean();
    private final Consumer<Exception> reportFailure;
    private volatile boolean closed;
    private final CompletableFuture<Void> completion=new CompletableFuture<>();

    @FunctionalInterface interface WriterFactory { Writer open(Path path) throws IOException; }

    FactorioDapLog(Supplier<Path> directory,String name,Consumer<Path> opened,Consumer<Exception> failed) {
        this(directory,name,opened,failed,path -> Files.newBufferedWriter(path,StandardCharsets.UTF_8));
    }

    FactorioDapLog(Supplier<Path> directory,String name,Consumer<Path> opened,Consumer<Exception> failed,WriterFactory writerFactory) {
        reportFailure=failed;
        Thread.ofPlatform().daemon(true).name("Factorio DAP log").start(() -> {
            try {
                Path folder=Files.createDirectories(directory.get());
                String safe=name.replaceAll("[^a-zA-Z0-9._-]","_");
                if(safe.length()>64) safe=safe.substring(0,64);
                String timestamp=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
                Path file=Files.createTempFile(folder,safe+"-"+timestamp+"-",".jsonl");
                try(Writer writer=writerFactory.open(file)) {
                    opened.accept(file);
                    while(!closed || !pending.isEmpty()) {
                        String text=pending.poll(100,TimeUnit.MILLISECONDS);
                        if(text!=null && !this.failed.get()) {
                            writer.write(text);
                            writer.flush();
                        }
                    }
                }
            } catch(Exception failure) {
                fail(failure);
                if(failure instanceof InterruptedException) Thread.currentThread().interrupt();
            } finally { completion.complete(null); }
        });
    }

    /** File mode replaces console tracing, even if writing fails. Normal DAP delivery is unchanged. */
    static UnaryOperator<MessageConsumer> route(FactorioDapLog log,UnaryOperator<MessageConsumer> consoleWrapper,
                                                Consumer<String> reportProtocolError) {
        return log==null?consoleWrapper:log.wrap(reportProtocolError);
    }

    private UnaryOperator<MessageConsumer> wrap(Consumer<String> reportProtocolError) {
        // The DAP serializer preserves seq/request_seq and command/event/body, unlike
        // generic JSON-RPC serialization or LSP4IJ's human-readable trace formatter.
        var json=new DebugMessageJsonHandler(Map.of(),builder -> builder.disableHtmlEscaping());
        return consumer -> message -> {
            if(!failed.get() && !closed) {
                try {
                    synchronized(json) { append(json.serialize(message)+"\n"); }
                } catch(RuntimeException failure) { fail(failure); }
            }
            consumer.consume(message);
            // The replaced LSP4IJ wrapper also displays failed responses independently of its trace.
            if(message instanceof ResponseMessage response && response.getError()!=null) {
                String error=response.getError().getMessage();
                if(error!=null && !error.isBlank()) reportProtocolError.accept(error);
            }
        };
    }

    synchronized void append(String text) {
        if(closed || failed.get()) return;
        if(!pending.offer(text)) fail(new IOException("DAP log writer cannot keep up; the file is incomplete"));
    }

    private void fail(Exception failure) {
        if(failed.compareAndSet(false,true)) {
            close();
            reportFailure.accept(failure);
        }
    }

    @Override public synchronized void close() { closed=true; }
    CompletableFuture<Void> completion() { return completion; }
}
