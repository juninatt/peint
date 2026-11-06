package se.pbt.peint.ai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

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
     * Lists the models installed on the Ollama server.
     *
     * @return their full names, such as {@code qwen3.5:4b} or {@code llava:latest}
     * @throws IOException if Ollama can't be reached or answers with an error
     * @throws InterruptedException if the calling thread is interrupted
     */
    public List<String> listModels() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(address.resolve("/api/tags"))
                .timeout(AVAILABILITY_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode answer = parse(response.body());
        if (response.statusCode() != 200) {
            throw new OllamaException(response.statusCode(), "Ollama answered with HTTP " + response.statusCode());
        }
        List<String> names = new ArrayList<>();
        for (JsonNode model : answer.path("models")) {
            names.add(model.path("name").asString(""));
        }
        return names;
    }

    /**
     * Checks whether a model is installed on the Ollama server. A name
     * without a tag means its {@code latest} tag, as in Ollama itself.
     *
     * @throws IOException if Ollama can't be reached or answers with an error
     * @throws InterruptedException if the calling thread is interrupted
     */
    public boolean hasModel(String model) throws IOException, InterruptedException {
        return listModels().contains(withTag(model));
    }

    /**
     * Waits for the next line of a streamed answer. The HTTP client reports
     * problems while reading a stream as unchecked exceptions; this turns
     * them back into an {@link InterruptedException} when the thread was
     * interrupted, and an {@link IOException} otherwise.
     */
    private static boolean hasNext(Iterator<String> lines) throws IOException, InterruptedException {
        try {
            return lines.hasNext();
        } catch (UncheckedIOException e) {
            if (Thread.interrupted() || e.getCause() instanceof InterruptedIOException) {
                throw new InterruptedException("Download cancelled");
            }
            throw e.getCause();
        }
    }

    static String withTag(String model) {
        return model.contains(":") ? model : model + ":latest";
    }

    /**
     * Downloads a model to the Ollama server, reporting progress as it goes.
     * Ollama keeps what it has downloaded, so a cancelled download resumes
     * where it stopped the next time.
     *
     * <p>Interrupting the calling thread cancels the download: progress
     * arrives several times a second, and the download stops at the next
     * report after the interruption.</p>
     *
     * @param model the model to download, e.g. {@code qwen3.5:4b}
     * @param progress told how far the download has come, on the calling thread
     * @throws OllamaException if Ollama reports an error, such as an unknown model
     * @throws IOException if Ollama can't be reached or the download breaks off
     * @throws InterruptedException if the calling thread is interrupted
     */
    public void pull(String model, Consumer<PullProgress> progress) throws IOException, InterruptedException {
        ObjectNode body = json.createObjectNode().put("model", model).put("stream", true);
        HttpRequest request = HttpRequest.newBuilder(address.resolve("/api/pull"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<Stream<String>> response = http.send(request, HttpResponse.BodyHandlers.ofLines());

        // Each part (layer) of the model reports its own size and progress.
        Map<String, long[]> parts = new LinkedHashMap<>();
        boolean succeeded = false;
        try (Stream<String> lines = response.body()) {
            Iterator<String> reports = lines.iterator();
            while (hasNext(reports)) {
                if (Thread.interrupted()) {
                    throw new InterruptedException("Download of " + model + " cancelled");
                }
                JsonNode report = parse(reports.next());
                String error = report.path("error").asString("");
                if (!error.isEmpty()) {
                    throw new OllamaException(response.statusCode(), error);
                }
                String status = report.path("status").asString("");
                String digest = report.path("digest").asString("");
                if (!digest.isEmpty() && report.path("total").asLong(0) > 0) {
                    parts.put(digest, new long[] {report.path("completed").asLong(0), report.path("total").asLong()});
                }
                long completed = parts.values().stream().mapToLong(part -> part[0]).sum();
                long total = parts.values().stream().mapToLong(part -> part[1]).sum();
                progress.accept(new PullProgress(status, completed, total));
                succeeded |= status.equals("success");
            }
        }
        if (response.statusCode() != 200) {
            throw new OllamaException(response.statusCode(), "Ollama answered with HTTP " + response.statusCode());
        }
        if (!succeeded) {
            throw new IOException("The download of " + model + " stopped before it was complete");
        }
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
