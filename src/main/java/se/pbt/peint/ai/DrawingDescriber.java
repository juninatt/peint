package se.pbt.peint.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Asks a model that can see images for a title and a description of a drawing.
 *
 * <p>Both come from a single request: the answer must follow a JSON schema
 * with a {@code title} and a {@code description}, so one wait, which takes
 * tens of seconds on a CPU, gives a file name and alt text alike.</p>
 */
public final class DrawingDescriber {

    /**
     * The longest side, in pixels, of the image sent to the model. Larger
     * images take the model longer to look at without making a simple
     * drawing any easier to recognize.
     */
    static final int MAX_IMAGE_SIZE = 512;

    static final String PROMPT = "This is a drawing made in a simple paint program. Give it a short, "
            + "fitting title of at most five words, and describe what it shows in one or two sentences, "
            + "as alt text for someone who can't see it. Answer in English.";

    private final OllamaClient client;
    private final String model;

    /**
     * @param client the Ollama server to ask
     * @param model the name of an installed model that can see images
     */
    public DrawingDescriber(OllamaClient client, String model) {
        this.client = client;
        this.model = model;
    }

    /**
     * Describes the given drawing. Blocks until the model has answered, so
     * call it from a background thread.
     *
     * @throws IOException if Ollama can't be reached, answers with an error,
     *                     or gives an answer without a title and description
     * @throws InterruptedException if the calling thread is interrupted,
     *                              which cancels the request
     */
    public Description describe(BufferedImage drawing) throws IOException, InterruptedException {
        String answer = client.generate(model, PROMPT, toPng(scaleForModel(drawing)), schema());

        JsonNode json = client.readTree(answer);
        String title = json.path("title").asString("").strip();
        String text = json.path("description").asString("").strip();
        if (title.isEmpty() || text.isEmpty()) {
            throw new IOException("The model's answer had no title or description");
        }
        return new Description(title, text);
    }

    /**
     * The JSON schema the model's answer must follow: an object with a
     * string {@code title} and a string {@code description}.
     */
    private ObjectNode schema() {
        ObjectNode schema = client.createObject().put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("title").put("type", "string");
        properties.putObject("description").put("type", "string");
        schema.putArray("required").add("title").add("description");
        return schema;
    }

    /**
     * Scales the drawing down so its longest side is at most
     * {@link #MAX_IMAGE_SIZE}, keeping its proportions. Smaller drawings are
     * returned as they are.
     */
    static BufferedImage scaleForModel(BufferedImage drawing) {
        int longest = Math.max(drawing.getWidth(), drawing.getHeight());
        if (longest <= MAX_IMAGE_SIZE) {
            return drawing;
        }
        double scale = (double) MAX_IMAGE_SIZE / longest;
        int width = Math.max(1, (int) Math.round(drawing.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(drawing.getHeight() * scale));

        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(drawing, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    private static byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        return png.toByteArray();
    }
}
