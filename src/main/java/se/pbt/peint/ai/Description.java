package se.pbt.peint.ai;

import java.text.Normalizer;
import java.util.Locale;

/**
 * What a model saw in a drawing.
 *
 * @param title a short title, e.g. "Sunny House Adventure"
 * @param text one or two sentences describing the drawing, usable as alt
 *             text for someone who can't see it
 */
public record Description(String title, String text) {

    /** The longest file name, without extension, that {@link #fileName()} makes. */
    static final int MAX_FILE_NAME_LENGTH = 40;

    /**
     * Turns the title into a file name, without extension: lowercase letters
     * and digits separated by hyphens, e.g. {@code sunny-house-adventure}.
     *
     * <p>Accents are dropped ({@code Bästa} becomes {@code basta}) and other
     * characters become separators, so the name is safe on every operating
     * system. A title with nothing usable in it gives {@code drawing}.</p>
     */
    public String fileName() {
        String withoutAccents = Normalizer.normalize(title, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String name = withoutAccents.toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (name.length() > MAX_FILE_NAME_LENGTH) {
            name = name.substring(0, MAX_FILE_NAME_LENGTH).replaceAll("-[^-]*$|-$", "");
        }
        return name.isEmpty() ? "drawing" : name;
    }
}
