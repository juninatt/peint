package se.pbt.peint.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A stand-in for an Ollama server, so the AI features can be tested without
 * Ollama or a model: it records each request and answers with whatever the
 * test told it to, for any path.
 */
public final class FakeOllama implements AutoCloseable {

    /** A request the fake server received. */
    public record Request(String method, String path, String body) {
    }

    private record Answer(int status, String body, long delayMillis) {
    }

    private final HttpServer server;
    // Requests are handled on their own threads, so a slow answer doesn't hold
    // up other requests, and close() can interrupt it instead of waiting.
    private final ExecutorService handlers = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "fake-ollama");
        thread.setDaemon(true);
        return thread;
    });
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    // Per path, the answers still to give: each request takes the first,
    // and the last one is repeated for any requests after it.
    private final Map<String, Deque<Answer>> answers = new ConcurrentHashMap<>();
    private volatile CountDownLatch requestReceived = new CountDownLatch(1);

    /**
     * Starts a fake server on a free port of this machine. By default it
     * answers {@code /api/version} like Ollama does, and every other path
     * with 404.
     */
    public FakeOllama() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(handlers);
        server.start();
        answer("/api/version", 200, "{\"version\":\"0.0.0-fake\"}");
    }

    /** @return the address to point an {@link OllamaClient} at */
    public URI address() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /** Answers requests to the given path with the given status and body. */
    public FakeOllama answer(String path, int status, String body) {
        return answer(path, status, body, 0);
    }

    /** Answers requests to the given path after a delay, as a slow model would. */
    public FakeOllama answer(String path, int status, String body, long delayMillis) {
        answers.put(path, new ArrayDeque<>(List.of(new Answer(status, body, delayMillis))));
        return this;
    }

    /**
     * Answers successive requests to the given path with the given bodies in
     * turn, all with the given status; the last body answers any further ones.
     */
    public FakeOllama answerInTurn(String path, int status, String... bodies) {
        Deque<Answer> inTurn = new ArrayDeque<>();
        for (String body : bodies) {
            inTurn.add(new Answer(status, body, 0));
        }
        answers.put(path, inTurn);
        return this;
    }

    /** @return the requests received so far, in order */
    public List<Request> requests() {
        return List.copyOf(requests);
    }

    /** Waits until a request to a path other than /api/version arrives. */
    public void awaitRequest() throws InterruptedException {
        requestReceived.await();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Request(exchange.getRequestMethod(), path, body));
        if (!path.equals("/api/version")) {
            requestReceived.countDown();
        }

        Answer answer = nextAnswer(path);
        try {
            Thread.sleep(answer.delayMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] bytes = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(answer.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException clientGone) {
            // The client cancelled the request while the "model" was thinking.
        }
    }

    private Answer nextAnswer(String path) {
        Deque<Answer> inTurn = answers.get(path);
        if (inTurn == null) {
            return new Answer(404, "{\"error\":\"not found\"}", 0);
        }
        synchronized (inTurn) {
            return inTurn.size() > 1 ? inTurn.removeFirst() : inTurn.getFirst();
        }
    }

    @Override
    public void close() {
        handlers.shutdownNow();
        server.stop(0);
    }
}
