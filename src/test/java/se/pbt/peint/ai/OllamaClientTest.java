package se.pbt.peint.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaClientTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    private final JsonMapper json = JsonMapper.builder().build();
    private FakeOllama ollama;
    private OllamaClient client;

    @BeforeEach
    void startFakeOllama() throws IOException {
        ollama = new FakeOllama();
        client = new OllamaClient(ollama.address());
    }

    @AfterEach
    void stopFakeOllama() {
        ollama.close();
    }

    @Test
    void isAvailableWhenOllamaAnswers() {
        assertTrue(client.isAvailable());
        assertEquals("/api/version", ollama.requests().getFirst().path());
    }

    @Test
    void isNotAvailableWhenNothingListens() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }

        assertFalse(new OllamaClient(URI.create("http://127.0.0.1:" + freePort)).isAvailable());
    }

    @Test
    void isNotAvailableWhenTheServerAnswersWithAnError() {
        ollama.answer("/api/version", 500, "oops");

        assertFalse(client.isAvailable());
    }

    @Test
    void generateSendsTheModelPromptAndImage() throws Exception {
        ollama.answer("/api/generate", 200, "{\"response\":\"A house.\"}");

        client.generate("qwen3.5:4b", "Describe this.", PNG, null);

        FakeOllama.Request request = ollama.requests().getLast();
        assertEquals("POST", request.method());
        assertEquals("/api/generate", request.path());
        JsonNode body = json.readTree(request.body());
        assertEquals("qwen3.5:4b", body.path("model").asString());
        assertEquals("Describe this.", body.path("prompt").asString());
        assertArrayEquals(PNG, Base64.getDecoder().decode(body.path("images").path(0).asString()));
        assertFalse(body.path("stream").asBoolean(true), "the whole answer should come at once");
        assertFalse(body.path("think").asBoolean(true), "thinking should be off, as it is several times slower");
        assertTrue(body.path("format").isMissingNode(), "no schema means a free-form answer");
    }

    @Test
    void generateSendsNoImageWhenThereIsNone() throws Exception {
        ollama.answer("/api/generate", 200, "{\"response\":\"<svg/>\"}");

        client.generate("m", "Draw a cat.", null, null);

        JsonNode body = json.readTree(ollama.requests().getLast().body());
        assertTrue(body.path("images").isMissingNode(), "a prompt without an image shouldn't send one");
    }

    @Test
    void generateSendsTheSchemaTheAnswerMustFollow() throws Exception {
        ollama.answer("/api/generate", 200, "{\"response\":\"{}\"}");
        ObjectNode schema = client.createObject().put("type", "object");

        client.generate("m", "p", PNG, schema);

        JsonNode body = json.readTree(ollama.requests().getLast().body());
        assertEquals("object", body.path("format").path("type").asString());
    }

    @Test
    void generateReturnsTheModelsAnswer() throws Exception {
        ollama.answer("/api/generate", 200, "{\"model\":\"m\",\"response\":\"A sunny house.\",\"done\":true}");

        assertEquals("A sunny house.", client.generate("m", "p", PNG, null));
    }

    @Test
    void generateReportsOllamasOwnErrorMessage() {
        ollama.answer("/api/generate", 404, "{\"error\":\"model \\\"llava\\\" not found, try pulling it first\"}");

        OllamaException error = assertThrows(OllamaException.class, () -> client.generate("llava", "p", PNG, null));

        assertEquals(404, error.status());
        assertEquals("model \"llava\" not found, try pulling it first", error.getMessage());
    }

    @Test
    void generateReportsAnErrorThatIsNotJson() {
        ollama.answer("/api/generate", 502, "Bad Gateway");

        OllamaException error = assertThrows(OllamaException.class, () -> client.generate("m", "p", PNG, null));

        assertEquals("Ollama answered with HTTP 502", error.getMessage());
    }

    @Test
    void generateRejectsAnAnswerWithoutResponseText() {
        ollama.answer("/api/generate", 200, "{\"done\":true}");

        assertThrows(IOException.class, () -> client.generate("m", "p", PNG, null));
    }

    @Test
    void interruptingTheCallingThreadCancelsTheRequest() throws Exception {
        ollama.answer("/api/generate", 200, "{\"response\":\"too late\"}", 10_000);
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                client.generate("m", "p", PNG, null);
            } catch (Throwable t) {
                outcome.set(t);
            }
        });

        caller.start();
        ollama.awaitRequest();
        long start = System.nanoTime();
        caller.interrupt();
        caller.join(5_000);

        assertFalse(caller.isAlive(), "the request should stop when the thread is interrupted");
        assertTrue(System.nanoTime() - start < 5_000_000_000L);
        assertInstanceOf(InterruptedException.class, outcome.get());
    }

    @Test
    void listModelsGivesTheInstalledModelsNames() throws Exception {
        ollama.answer("/api/tags", 200, "{\"models\":[{\"name\":\"qwen3.5:4b\",\"size\":3300000000},"
                + "{\"name\":\"llava:latest\"}]}");

        assertEquals(List.of("qwen3.5:4b", "llava:latest"), client.listModels());
    }

    @Test
    void hasModelTreatsANameWithoutATagAsLatest() throws Exception {
        ollama.answer("/api/tags", 200, "{\"models\":[{\"name\":\"llava:latest\"},{\"name\":\"qwen3.5:4b\"}]}");

        assertTrue(client.hasModel("llava"));
        assertTrue(client.hasModel("qwen3.5:4b"));
        assertFalse(client.hasModel("qwen3.5:9b"));
    }

    @Test
    void pullReportsProgressAddedUpOverTheModelsParts() throws Exception {
        ollama.answerStreamed("/api/pull", 0,
                "{\"status\":\"pulling manifest\"}",
                "{\"status\":\"pulling aaa\",\"digest\":\"sha256:aaa\",\"total\":3000,\"completed\":1000}",
                "{\"status\":\"pulling bbb\",\"digest\":\"sha256:bbb\",\"total\":1000,\"completed\":0}",
                "{\"status\":\"pulling aaa\",\"digest\":\"sha256:aaa\",\"total\":3000,\"completed\":3000}",
                "{\"status\":\"pulling bbb\",\"digest\":\"sha256:bbb\",\"total\":1000,\"completed\":1000}",
                "{\"status\":\"verifying sha256 digest\"}",
                "{\"status\":\"success\"}");
        List<PullProgress> reports = new ArrayList<>();

        client.pull("qwen3.5:4b", reports::add);

        assertEquals(new PullProgress("pulling manifest", 0, 0), reports.getFirst());
        assertEquals(-1, reports.getFirst().fraction(), "the size isn't known yet");
        assertEquals(new PullProgress("pulling bbb", 1000, 4000), reports.get(2), "both parts count");
        assertEquals(0.25, reports.get(2).fraction());
        assertEquals(new PullProgress("success", 4000, 4000), reports.getLast());
        JsonNode body = json.readTree(ollama.requests().getLast().body());
        assertEquals("qwen3.5:4b", body.path("model").asString());
    }

    @Test
    void pullReportsOllamasErrorPartWayThrough() {
        ollama.answerStreamed("/api/pull", 0,
                "{\"status\":\"pulling manifest\"}",
                "{\"error\":\"pull model manifest: file does not exist\"}");

        OllamaException error = assertThrows(OllamaException.class, () -> client.pull("no-such-model", progress -> { }));

        assertEquals("pull model manifest: file does not exist", error.getMessage());
    }

    @Test
    void pullFailsWhenTheDownloadStopsBeforeSuccess() {
        ollama.answerStreamed("/api/pull", 0,
                "{\"status\":\"pulling aaa\",\"digest\":\"sha256:aaa\",\"total\":3000,\"completed\":1000}");

        IOException error = assertThrows(IOException.class, () -> client.pull("qwen3.5:4b", progress -> { }));

        assertTrue(error.getMessage().contains("stopped before it was complete"), error.getMessage());
    }

    @Test
    void interruptingThePullingThreadStopsTheDownload() throws Exception {
        String[] slowReports = new String[200];
        for (int i = 0; i < slowReports.length; i++) {
            slowReports[i] = "{\"status\":\"pulling aaa\",\"digest\":\"sha256:aaa\",\"total\":200,\"completed\":" + i + "}";
        }
        ollama.answerStreamed("/api/pull", 50, slowReports); // ten seconds in all
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        Thread puller = new Thread(() -> {
            try {
                client.pull("qwen3.5:4b", progress -> started.countDown());
            } catch (Throwable t) {
                outcome.set(t);
            }
        });

        puller.start();
        started.await();
        puller.interrupt();
        puller.join(5_000);

        assertFalse(puller.isAlive(), "the download should stop soon after the interruption");
        assertInstanceOf(InterruptedException.class, outcome.get());
    }

    @Test
    void readTreeRejectsTextThatIsNotJson() {
        assertThrows(IOException.class, () -> client.readTree("not json"));
    }
}
