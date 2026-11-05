package se.pbt.peint.ai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Talks to a local Ollama server over its HTTP API.
 *
 * <p>Uses the JDK's own {@link HttpClient} and Jackson's tree model rather
 * than an AI client library: those libraries are not proper Java modules,
 * which the jlink release build requires.</p>
 *
 * <p>Calls block until Ollama answers, which takes from seconds to minutes
 * when a model runs on the CPU, so they belong on a background thread.
 * Interrupting that thread, as cancelling a JavaFX {@code Task} does, cancels
 * the request.</p>
 */
public final class OllamaClient {

    /** Where Ollama listens unless configured otherwise. */
    public static final URI DEFAULT_ADDRESS = URI.create("http://localhost:11434");

    // Checking whether Ollama runs should answer quickly either way.
    private static final Duration AVAILABILITY_TIMEOUT = Duration.ofSeconds(2);

    // A model on the CPU can take minutes to answer; this only guards against
    // a server that never does.
    private static final Duration GENERATE_TIMEOUT = Duration.ofMinutes(15);

    private final URI address;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * Creates a client for the Ollama server at the given address.
     *
     * @param address the server's base address, e.g. {@link #DEFAULT_ADDRESS}
     */
    public OllamaClient(URI address) {
        this.address = address;
        this.http = HttpClient.newBuilder()
                .connectTimeout(AVAILABILITY_TIMEOUT)
                .build();
    }

    /**
     * @return the server's base address
     */
    public URI address() {
        return address;
    }

    /**
     * Checks whether an Ollama server answers at this client's address.
     *
     * @return {@code true} if it answered, {@code false} if it is not running,
     *         cannot be reached or answered with an error
     */
    public boolean isAvailable() {
        HttpRequest request = HttpRequest.newBuilder(address.resolve("/api/version"))
                .timeout(AVAILABILITY_TIMEOUT)
                .GET()
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Asks a model for an answer to a prompt, about an image if one is
     * given, and waits for the whole answer.
     *
     * <p>Thinking is turned off: it made answers several times slower in
     * testing, without making them better for Peint's short questions.</p>
     *
     * @param model the name of an installed model that can see images,
     *              e.g. {@code "qwen3.5:4b"}
     * @param prompt what to ask about the image
     * @param png the image, PNG-encoded, or {@code null} for a prompt about
     *            no image
     * @param schema a JSON schema the answer must follow, or {@code null}
     *               for a free-form text answer
     * @return the model's answer; JSON following the schema, if one was given
     * @throws OllamaException if Ollama answered with an error, such as an
     *                         unknown model
     * @throws IOException if Ollama could not be reached or its answer read
     * @throws InterruptedException if the calling thread was interrupted,
     *                              which cancels the request
     */
    public String generate(String model, String prompt, byte[] png, JsonNode schema)
            throws IOException, InterruptedException {
        ObjectNode body = json.createObjectNode()
                .put("model", model)
                .put("prompt", prompt)
                .put("stream", false)
                .put("think", false);
        if (png != null) {
            body.putArray("images").add(Base64.getEncoder().encodeToString(png));
        }
        body.putObject("options").put("temperature", 0.2);
        if (schema != null) {
            body.set("format", schema);
        }

        HttpRequest request = HttpRequest.newBuilder(address.resolve("/api/generate"))
                .timeout(GENERATE_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode answer = parse(response.body());
        if (response.statusCode() != 200) {
            String error = answer.path("error").asString("");
            throw new OllamaException(response.statusCode(),
                    error.isEmpty() ? "Ollama answered with HTTP " + response.statusCode() : error);
        }
        JsonNode text = answer.path("response");
        if (!text.isString()) {
            throw new IOException("Ollama's answer had no response text");
        }
        return text.asString();
    }

    /**
     * Parses a JSON body, or returns an empty object for a body that isn't
     * JSON, such as a plain-text error page.
     */
    private JsonNode parse(String body) {
        try {
            return json.readTree(body);
        } catch (JacksonException e) {
            return json.createObjectNode();
        }
    }

    /**
     * Creates a JSON object node, e.g. for building a schema to pass to
     * {@link #generate(String, String, byte[], JsonNode)}.
     */
    public ObjectNode createObject() {
        return json.createObjectNode();
    }

    /**
     * Parses JSON text, such as an answer that followed a schema.
     *
     * @throws IOException if the text is not valid JSON
     */
    public JsonNode readTree(String text) throws IOException {
        try {
            return json.readTree(text);
        } catch (JacksonException e) {
            throw new IOException("Not valid JSON: " + e.getOriginalMessage(), e);
        }
    }
}
