package se.pbt.peint.ai;

import java.io.IOException;

/**
 * Ollama answered a request with an error, such as an unknown model.
 *
 * <p>The message is Ollama's own explanation, e.g. {@code model "llava" not
 * found, try pulling it first}, so it can be shown to the user as it is.</p>
 */
public class OllamaException extends IOException {

    private final int status;

    /**
     * @param status the HTTP status code Ollama answered with
     * @param message Ollama's explanation of the error
     */
    public OllamaException(int status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * @return the HTTP status code Ollama answered with
     */
    public int status() {
        return status;
    }
}
