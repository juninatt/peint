package se.pbt.peint.ai;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Which Ollama server the AI menu talks to, and which model it asks.
 *
 * <p>Stored in the user's {@link Preferences} (the registry on Windows, a
 * file in the home directory elsewhere), so the choice survives restarts.</p>
 *
 * @param address the Ollama server's base address
 * @param model the name of an installed model that can see images
 */
public record AiSettings(URI address, String model) {

    /** The model asked unless configured otherwise: small, and able to see images. */
    public static final String DEFAULT_MODEL = "qwen3.5:4b";

    /** The settings used until the user changes them. */
    public static final AiSettings DEFAULTS = new AiSettings(OllamaClient.DEFAULT_ADDRESS, DEFAULT_MODEL);

    private static final String ADDRESS_KEY = "ollamaAddress";
    private static final String MODEL_KEY = "ollamaModel";

    /**
     * Reads the settings from the given preferences, using the default for
     * any that are missing or no longer valid.
     */
    public static AiSettings load(Preferences preferences) {
        URI address = parseAddress(preferences.get(ADDRESS_KEY, "")).orElse(DEFAULTS.address());
        String model = preferences.get(MODEL_KEY, "").strip();
        return new AiSettings(address, model.isEmpty() ? DEFAULTS.model() : model);
    }

    /**
     * Writes these settings to the given preferences.
     */
    public void save(Preferences preferences) {
        preferences.put(ADDRESS_KEY, address.toString());
        preferences.put(MODEL_KEY, model);
    }

    /**
     * Parses an Ollama address as typed by the user: an {@code http} or
     * {@code https} URL with a host, such as {@code http://localhost:11434}.
     *
     * @return the address, or empty if the text isn't one
     */
    public static Optional<URI> parseAddress(String text) {
        try {
            URI address = new URI(text.strip());
            boolean web = "http".equalsIgnoreCase(address.getScheme()) || "https".equalsIgnoreCase(address.getScheme());
            return web && address.getHost() != null ? Optional.of(address) : Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}
