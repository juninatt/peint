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
import java.util.Base64;
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
    void readTreeRejectsTextThatIsNotJson() {
        assertThrows(IOException.class, () -> client.readTree("not json"));
    }
}
