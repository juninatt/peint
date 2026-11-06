package se.pbt.peint.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaServerTest {

    @TempDir
    Path dataDirectory;

    @Test
    void startWaitsUntilTheServerAnswers() throws Exception {
        int port = freePort();

        try (OllamaServer server = OllamaServer.start(fakeOllama(), port, log(), Duration.ofSeconds(30))) {
            assertEquals(URI.create("http://127.0.0.1:" + port), server.address());
            assertTrue(new OllamaClient(server.address()).isAvailable(), "it should answer once started");
            assertTrue(server.isRunning());
        }
    }

    @Test
    void closeStopsTheServer() throws Exception {
        int port = freePort();
        OllamaServer server = OllamaServer.start(fakeOllama(), port, log(), Duration.ofSeconds(30));

        server.close();

        assertFalse(server.isRunning());
        assertFalse(new OllamaClient(server.address()).isAvailable());
    }

    @Test
    void theServersOutputGoesToTheLog() throws Exception {
        try (OllamaServer ignored = OllamaServer.start(fakeOllama(), freePort(), log(), Duration.ofSeconds(30))) {
            assertTrue(Files.readString(log()).contains("fake ollama listening on"), Files.readString(log()));
        }
    }

    @Test
    void startReportsAServerThatStopsByItself() {
        List<String> exitsAtOnce = List.of(java(), "-version");

        IOException error = assertThrows(IOException.class,
                () -> OllamaServer.start(exitsAtOnce, freePort(), log(), Duration.ofSeconds(30)));

        assertTrue(error.getMessage().contains("stopped while starting"), error.getMessage());
        assertTrue(error.getMessage().contains("Its log ends with"), "the log should help explain why: " + error.getMessage());
    }

    @Test
    void startGivesUpOnAServerThatNeverAnswersAndStopsIt() throws Exception {
        List<String> neverAnswers = new ArrayList<>(fakeOllama());
        neverAnswers.add("silent");
        long started = System.nanoTime();

        IOException error = assertThrows(IOException.class,
                () -> OllamaServer.start(neverAnswers, freePort(), log(), Duration.ofSeconds(3)));

        assertTrue(error.getMessage().contains("didn't start answering within 3 seconds"), error.getMessage());
        assertTrue(System.nanoTime() - started < 20_000_000_000L, "it should give up around the timeout");
        assertTrue(ProcessHandle.current().descendants().noneMatch(p -> p.info().arguments()
                .map(args -> List.of(args).contains("silent")).orElse(false)), "the hanging server should be stopped");
    }

    /**
     * A command that runs FakeOllamaServe in a JVM of its own, as Ollama's
     * program would run.
     */
    private static List<String> fakeOllama() throws Exception {
        Path testClasses = Path.of(FakeOllamaServe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return new ArrayList<>(List.of(java(), "-cp", testClasses.toString(), FakeOllamaServe.class.getName()));
    }

    private static String java() {
        return ProcessHandle.current().info().command().orElseThrow();
    }

    private Path log() {
        return dataDirectory.resolve("logs").resolve("ollama.log");
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
