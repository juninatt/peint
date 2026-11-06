package se.pbt.peint.ai;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Serves files from memory, standing in for a download site in tests.
 */
public final class FakeFileServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService handlers = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "fake-file-server");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final Map<String, Long> pausesPerChunk = new ConcurrentHashMap<>();

    public FakeFileServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] file = files.get(path);
            try (OutputStream out = exchange.getResponseBody()) {
                if (file == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                exchange.sendResponseHeaders(200, file.length);
                long pause = pausesPerChunk.getOrDefault(path, 0L);
                for (int offset = 0; offset < file.length; offset += 4096) {
                    out.write(file, offset, Math.min(4096, file.length - offset));
                    out.flush();
                    Thread.sleep(pause);
                }
            } catch (IOException | InterruptedException clientGoneOrClosing) {
                // The client cancelled, or the server is stopping.
            }
        });
        server.setExecutor(handlers);
        server.start();
    }

    /** Serves the given bytes at the given path. */
    public URI serve(String path, byte[] content) {
        return serve(path, content, 0);
    }

    /** Serves the given bytes at the given path, slowly: pausing after every 4 KB. */
    public URI serve(String path, byte[] content, long pauseMillisPerChunk) {
        files.put(path, content);
        pausesPerChunk.put(path, pauseMillisPerChunk);
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    @Override
    public void close() {
        handlers.shutdownNow();
        server.stop(0);
    }
}
