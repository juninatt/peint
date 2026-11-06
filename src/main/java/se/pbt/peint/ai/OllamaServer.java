package se.pbt.peint.ai;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An Ollama server Peint starts itself, in the background, and stops when
 * Peint quits.
 *
 * <p>It listens on a port of its own, so it never clashes with an Ollama the
 * user runs. It keeps its models where Ollama always does, so models are
 * shared with an Ollama installed later rather than downloaded twice.</p>
 */
public final class OllamaServer implements AutoCloseable {

    /** The port Peint's own Ollama listens on, next to Ollama's usual 11434. */
    public static final int PORT = 11435;

    /** How long Ollama gets to start answering before it's given up on. */
    static final Duration START_TIMEOUT = Duration.ofSeconds(60);

    private final Process process;
    private final URI address;

    private OllamaServer(Process process, URI address) {
        this.process = process;
        this.address = address;
    }

    /**
     * Starts {@code ollama serve} with the given program and waits until it
     * answers.
     *
     * @param executable the {@code ollama} program
     * @param log where to write Ollama's output, appended to
     */
    public static OllamaServer start(Path executable, Path log) throws IOException, InterruptedException {
        return start(List.of(executable.toString(), "serve"), PORT, log, START_TIMEOUT);
    }

    /**
     * Starts an Ollama server with the given command, telling it which port
     * to listen on, and waits until it answers.
     *
     * @param command the command that starts the server
     * @param port the port to listen on, given to it as {@code OLLAMA_HOST}
     * @param log where to write its output, appended to
     * @param timeout how long it may take to start answering
     * @throws IOException if it can't be started, stops by itself, or doesn't
     *                     answer within the timeout; it is stopped in that case
     * @throws InterruptedException if the calling thread is interrupted while
     *                              waiting; it is stopped in that case
     */
    static OllamaServer start(List<String> command, int port, Path log, Duration timeout)
            throws IOException, InterruptedException {
        Files.createDirectories(log.toAbsolutePath().getParent());
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectErrorStream(true)
                // To a file rather than a pipe: an unread pipe fills up and stalls Ollama.
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        builder.environment().put("OLLAMA_HOST", "127.0.0.1:" + port);
        Process process = builder.start();

        OllamaServer server = new OllamaServer(process, URI.create("http://127.0.0.1:" + port));
        OllamaClient client = new OllamaClient(server.address);
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (!client.isAvailable()) {
                if (!process.isAlive()) {
                    throw new IOException("Ollama stopped while starting (exit code " + process.exitValue() + "). "
                            + lastLines(log));
                }
                if (System.nanoTime() > deadline) {
                    throw new IOException("Ollama didn't start answering within " + timeout.toSeconds() + " seconds. "
                            + lastLines(log));
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException("Starting Ollama cancelled");
                }
                Thread.sleep(250);
            }
            return server;
        } catch (IOException | InterruptedException | RuntimeException e) {
            server.close();
            throw e;
        }
    }

    /**
     * @return where the server answers, e.g. {@code http://127.0.0.1:11435}
     */
    public URI address() {
        return address;
    }

    /**
     * @return whether the server's process is still running
     */
    public boolean isRunning() {
        return process.isAlive();
    }

    /**
     * Stops the server, and the processes it started for loaded models, which
     * would otherwise keep their memory after Peint quits.
     */
    @Override
    public void close() {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns the end of the log, to explain why Ollama didn't start.
     */
    private static String lastLines(Path log) {
        try {
            List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
            List<String> last = lines.subList(Math.max(0, lines.size() - 5), lines.size());
            return last.isEmpty() ? "" : "Its log ends with: " + String.join(" / ", last);
        } catch (IOException e) {
            return "";
        }
    }
}
