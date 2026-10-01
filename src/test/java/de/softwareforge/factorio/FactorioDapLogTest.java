package de.softwareforge.factorio;

import com.google.gson.JsonParser;
import org.eclipse.lsp4j.jsonrpc.*;
import org.eclipse.lsp4j.jsonrpc.debug.messages.*;
import org.eclipse.lsp4j.jsonrpc.json.*;
import org.eclipse.lsp4j.jsonrpc.messages.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import static org.junit.jupiter.api.Assertions.*;

class FactorioDapLogTest {
    private Path directory() throws IOException {
        return Files.createTempDirectory(Files.createDirectories(Path.of("build/test-work/dap")),"log-");
    }
    private void finish(FactorioDapLog log) throws Exception { log.close();log.completion().get(5,TimeUnit.SECONDS); }

    @Test void fileModeCapturesBothDirectionsWithoutConsoleTracesAndPreservesDelivery() throws Exception {
        var path=new CompletableFuture<Path>();var errors=new CopyOnWriteArrayList<Exception>();
        var log=new FactorioDapLog(this::uncheckedDirectory,"../Factorio / test",path::complete,errors::add);
        var delivered=new ArrayList<Message>();var console=new ArrayList<Message>();var protocolErrors=new ArrayList<String>();
        UnaryOperator<MessageConsumer> wrapped=FactorioDapLog.route(log,
            next -> message -> {console.add(message);next.consume(message);},protocolErrors::add);
        var outgoing=wrapped.apply(new StreamMessageConsumer(new MessageJsonHandler(Map.of())) {
            @Override public void consume(Message message) { delivered.add(message); }
        });
        var incoming=wrapped.apply(new RemoteEndpoint(message -> {},new Endpoint() {
            public void notify(String method,Object parameter) {}
            public CompletableFuture<?> request(String method,Object parameter) {return CompletableFuture.completedFuture(null);}
        }) { @Override public void consume(Message message) {delivered.add(message);} });
        var request=new DebugRequestMessage();request.setId(1);request.setMethod("evaluate");request.setParams(Map.of("expression","game.tick"));
        var response=new DebugResponseMessage();response.setId(1);response.setResponseId(7);response.setMethod("evaluate");response.setResult(Map.of("result","42"));
        String output="protocol game output — ✓\nquoted: \"text\"\r\n";
        var event=new DebugNotificationMessage();event.setId(8);event.setMethod("output");event.setParams(Map.of("output",output,"category","stdout"));
        var failure=new DebugResponseMessage();failure.setId(2);failure.setResponseId(9);failure.setMethod("evaluate");
        failure.setError(new ResponseError(-32603,"Evaluation failed",Map.of("error",Map.of("id",42,"format","Missing variable"))));
        try {
            outgoing.consume(request);incoming.consume(response);incoming.consume(event);incoming.consume(failure);
            finish(log);
            assertEquals(List.of(request,response,event,failure),delivered);assertTrue(console.isEmpty());
            assertEquals(List.of("Evaluation failed"),protocolErrors);
            var lines=Files.readAllLines(path.get());assertEquals(4,lines.size());
            var messages=lines.stream().map(line -> JsonParser.parseString(line).getAsJsonObject()).toList();
            for(int i=0;i<lines.size();i++) {
                assertEquals(messages.get(i).toString(),lines.get(i),"Every message must be compact JSON on one line");
                assertFalse(messages.get(i).has("jsonrpc"));assertTrue(messages.get(i).get("seq").getAsJsonPrimitive().isNumber());
            }
            assertEquals("request",messages.get(0).get("type").getAsString());
            assertEquals(1,messages.get(0).get("seq").getAsInt());
            assertEquals("evaluate",messages.get(0).get("command").getAsString());
            assertEquals("game.tick",messages.get(0).getAsJsonObject("arguments").get("expression").getAsString());
            assertEquals("response",messages.get(1).get("type").getAsString());
            assertEquals(7,messages.get(1).get("seq").getAsInt());assertEquals(1,messages.get(1).get("request_seq").getAsInt());
            assertTrue(messages.get(1).get("success").getAsBoolean());
            assertEquals("42",messages.get(1).getAsJsonObject("body").get("result").getAsString());
            assertEquals("event",messages.get(2).get("type").getAsString());
            assertEquals(8,messages.get(2).get("seq").getAsInt());
            assertEquals("output",messages.get(2).get("event").getAsString());
            assertEquals(output,messages.get(2).getAsJsonObject("body").get("output").getAsString());
            assertFalse(messages.get(3).get("success").getAsBoolean());
            assertEquals(9,messages.get(3).get("seq").getAsInt());assertEquals(2,messages.get(3).get("request_seq").getAsInt());
            assertEquals("Evaluation failed",messages.get(3).get("message").getAsString());
            assertEquals(42,messages.get(3).getAsJsonObject("body").getAsJsonObject("error").get("id").getAsInt());
            assertTrue(path.get().toString().endsWith(".jsonl"));
            assertTrue(errors.isEmpty(),errors.toString());
            assertFalse(path.get().getFileName().toString().contains("/"));
        } finally {finish(log);}
    }
    @Test void consoleModeRetainsExistingTraceWrapper() {
        var trace=new AtomicInteger();var delivered=new AtomicInteger();
        var consumer=FactorioDapLog.route(null,next -> message -> {trace.incrementAndGet();next.consume(message);},
            error -> fail(error)).apply(message -> delivered.incrementAndGet());
        consumer.consume(new NotificationMessage());
        assertEquals(1,trace.get());assertEquals(1,delivered.get());
    }
    private Path uncheckedDirectory() {try{return directory();}catch(IOException e){throw new UncheckedIOException(e);}}

    @Test void concurrentSessionsHaveUniqueFilesAndCloseDrainsInOrder() throws Exception {
        Path dir=directory();var paths=new ArrayList<Path>();
        var sessions=new ArrayList<FactorioDapLog>();var errors=new CopyOnWriteArrayList<Exception>();
        try {
            for(int i=0;i<3;i++) {
                var opened=new CompletableFuture<Path>();
                var log=new FactorioDapLog(() -> dir,"Factorio",opened::complete,errors::add);sessions.add(log);
                for(int j=0;j<200;j++) log.append(j+"\n");
                paths.add(opened.get(5,TimeUnit.SECONDS));
            }
            for(var log:sessions) finish(log);
            assertEquals(3,new HashSet<>(paths).size());
            for(Path path:paths) assertEquals(java.util.stream.IntStream.range(0,200).mapToObj(Integer::toString).toList(),Files.readAllLines(path));
            assertTrue(errors.isEmpty(),errors.toString());
        } finally {for(var log:sessions)finish(log);}
    }

    @Test void openingFailureReportsOnceAndDoesNotStopMessageDelivery() throws Exception {
        Path file=Files.writeString(directory().resolve("not-a-directory"),"keep");
        var errors=new CopyOnWriteArrayList<Exception>();
        var log=new FactorioDapLog(() -> file,"Factorio",p -> fail("Should not open"),errors::add);
        log.completion().get(5,TimeUnit.SECONDS);
        var delivered=new AtomicInteger();
        var console=new AtomicInteger();
        var consumer=FactorioDapLog.route(log,next -> message -> {console.incrementAndGet();next.consume(message);},
            error -> fail(error)).apply(message -> delivered.incrementAndGet());
        consumer.consume(new NotificationMessage());consumer.consume(new NotificationMessage());
        finish(log);assertEquals(2,delivered.get());assertEquals(1,errors.size());assertEquals(0,console.get());
        assertEquals("keep",Files.readString(file));
    }

    @Test void diskFailureClosesWriterAndDoesNotBlockShutdown() throws Exception {
        Path dir=directory();var errors=new CopyOnWriteArrayList<Exception>();var closed=new AtomicInteger();
        var log=new FactorioDapLog(() -> dir,"Factorio",p -> {},errors::add,path -> new Writer() {
            public void write(char[] text,int start,int length) throws IOException {throw new IOException("disk full");}
            public void flush() {}
            public void close() {closed.incrementAndGet();}
        });
        log.append("request\n");log.completion().get(5,TimeUnit.SECONDS);
        log.append("ignored\n");finish(log);
        assertEquals(1,errors.size());assertEquals("disk full",errors.getFirst().getMessage());assertEquals(1,closed.get());
    }

    @Test void boundedQueueDisablesLoggingWithoutBlockingTransport() throws Exception {
        Path dir=directory();var started=new CountDownLatch(1);var release=new CountDownLatch(1);
        var errors=new CopyOnWriteArrayList<Exception>();
        var log=new FactorioDapLog(() -> dir,"Factorio",p -> {},errors::add,path -> {
            started.countDown();try {if(!release.await(5,TimeUnit.SECONDS))throw new IOException("test timeout");}
            catch(InterruptedException e){throw new IOException(e);}
            return new StringWriter();
        });
        try {
            assertTrue(started.await(5,TimeUnit.SECONDS));
            for(int i=0;i<1100;i++)log.append("request\n");
            assertEquals(1,errors.size());assertTrue(errors.getFirst().getMessage().contains("cannot keep up"));
            log.close(); // Does not wait for the blocked writer.
        } finally {release.countDown();finish(log);}
    }

    @Test void closingDuringStartupDrainsAcceptedMessagesAndClosesWriter() throws Exception {
        Path dir=directory();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var output=new StringWriter();var closed=new AtomicInteger();var errors=new CopyOnWriteArrayList<Exception>();
        var log=new FactorioDapLog(() -> dir,"Failed initialization",p -> {},errors::add,path -> {
            entered.countDown();
            try {if(!release.await(5,TimeUnit.SECONDS))throw new IOException("test timeout");}
            catch(InterruptedException e){throw new IOException(e);}
            return new FilterWriter(output) { @Override public void close() throws IOException {super.close();closed.incrementAndGet();} };
        });
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            log.append("initialize request\ninitialize error\n");
            log.close();log.close();log.append("after disposal\n");
            assertFalse(log.completion().isDone(),"Close must return without waiting for disk I/O");
        } finally {release.countDown();finish(log);}
        assertEquals("initialize request\ninitialize error\n",output.toString());
        assertEquals(1,closed.get());assertTrue(errors.isEmpty(),errors.toString());
    }
}
