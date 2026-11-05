package se.pbt.peint.ai;

import se.pbt.peint.svg.SvgFormatException;
import se.pbt.peint.svg.SvgImage;
import se.pbt.peint.svg.SvgReader;

import java.io.IOException;

/**
 * Asks a model to draw a described scene, as an SVG image of simple shapes.
 *
 * <p>Small local models are poor at placing shapes through drawing tools,
 * but have seen a great deal of SVG; asked for SVG, qwen3.5:4b drew
 * recognizable scenes in a minute or two on a laptop CPU. Its pictures
 * vary a lot from one try to the next, though, so an attempt that yields
 * no drawable image is retried once.</p>
 */
public final class SceneDrawer {

    /** How many times to ask before giving up on getting a drawable image. */
    static final int ATTEMPTS = 2;

    /**
     * The request, with {@code %s} for the scene. Asking the model to plan
     * the picture in a comment first, and to draw back to front, made its
     * scenes recognizable more often in testing; letting it think instead
     * took minutes and ran out of room before drawing anything.
     */
    static final String PROMPT = """
            Draw this scene as an SVG image with viewBox "0 0 800 480": %s.

            Style: a clean children's book illustration made of simple filled shapes \
            (rect, circle, ellipse, polygon, path) in clear, fitting colors.

            Work like this:
            1. Start the SVG with a comment that plans the picture: list every object in the scene, \
            with where it goes (x, y), how big it is, and its colors. Each named object must be \
            clearly recognizable, built from several shapes (a house needs walls, a roof, a door and windows).
            2. Then draw, back to front: first the background (sky, ground, sea) covering the whole \
            canvas, then the large objects, then their details. Never draw a shape that covers \
            something drawn earlier unless it belongs in front of it.

            Answer with the SVG code only, starting with <svg and ending with </svg>.""";

    private final OllamaClient client;
    private final String model;

    /**
     * @param client the Ollama server to ask
     * @param model the name of an installed model
     */
    public SceneDrawer(OllamaClient client, String model) {
        this.client = client;
        this.model = model;
    }

    /**
     * Asks the model to draw the given scene. Blocks until it has, so call
     * it from a background thread.
     *
     * @param scene what to draw, e.g. "a cat sitting on a rug"
     * @return the drawing, with at least one shape
     * @throws IllegalArgumentException if the scene is blank
     * @throws IOException if Ollama can't be reached or answers with an
     *                     error, or no attempt gave an image Peint can draw
     * @throws InterruptedException if the calling thread is interrupted,
     *                              which cancels the request
     */
    public SvgImage draw(String scene) throws IOException, InterruptedException {
        if (scene.isBlank()) {
            throw new IllegalArgumentException("Describe the scene to draw");
        }
        String prompt = PROMPT.formatted(scene.strip());

        SvgFormatException lastProblem = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            String answer = client.generate(model, prompt, null, null);
            try {
                SvgImage image = SvgReader.read(extractSvg(answer));
                if (!image.shapes().isEmpty()) {
                    return image;
                }
                lastProblem = new SvgFormatException("the image had no shapes Peint can draw");
            } catch (SvgFormatException problem) {
                lastProblem = problem;
            }
        }
        throw new IOException("The model didn't draw a picture Peint can show ("
                + lastProblem.getMessage() + "). Try again, or describe the scene differently.", lastProblem);
    }

    /**
     * Picks the SVG image out of a model's answer, which may have text or
     * a Markdown code fence around it.
     *
     * @throws SvgFormatException if the answer has no {@code <svg>...</svg>}
     */
    static String extractSvg(String answer) throws SvgFormatException {
        int start = answer.indexOf("<svg");
        int end = answer.lastIndexOf("</svg>");
        if (start < 0 || end < start) {
            throw new SvgFormatException("the answer had no SVG image");
        }
        return answer.substring(start, end + "</svg>".length());
    }
}
