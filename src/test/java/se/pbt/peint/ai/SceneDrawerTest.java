package se.pbt.peint.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.pbt.peint.svg.SvgFormatException;
import se.pbt.peint.svg.SvgImage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneDrawerTest {

    private static final String CAT = "<svg viewBox=\"0 0 800 480\" xmlns=\"http://www.w3.org/2000/svg\">"
            + "<!-- A cat on a rug --><rect width=\"800\" height=\"480\" fill=\"#f5e6d3\"/>"
            + "<circle cx=\"380\" cy=\"220\" r=\"70\" fill=\"#d59c85\"/></svg>";

    private final JsonMapper json = JsonMapper.builder().build();
    private FakeOllama ollama;
    private SceneDrawer drawer;

    @BeforeEach
    void startFakeOllama() throws IOException {
        ollama = new FakeOllama();
        drawer = new SceneDrawer(new OllamaClient(ollama.address()), "qwen3.5:4b");
    }

    @AfterEach
    void stopFakeOllama() {
        ollama.close();
    }

    @Test
    void drawReturnsTheModelsImage() throws Exception {
        answerWith(CAT);

        SvgImage image = drawer.draw("a cat sitting on a rug");

        assertEquals(800, image.width());
        assertEquals(2, image.shapes().size());
    }

    @Test
    void drawAsksForTheSceneWithoutSendingAnImage() throws Exception {
        answerWith(CAT);

        drawer.draw("  a cat sitting on a rug \n");

        JsonNode body = json.readTree(ollama.requests().getLast().body());
        assertEquals("qwen3.5:4b", body.path("model").asString());
        assertTrue(body.path("prompt").asString().contains("viewBox \"0 0 800 480\": a cat sitting on a rug."),
                body.path("prompt").asString());
        assertTrue(body.path("images").isMissingNode());
    }

    @Test
    void drawFindsTheImageAmongTheModelsOtherText() throws Exception {
        answerWith("Here is your picture!\n```xml\n" + CAT + "\n```\nI hope you like it.");

        assertEquals(2, drawer.draw("a cat").shapes().size());
    }

    @Test
    void drawTriesAgainWhenTheFirstAnswerHasNoImage() throws Exception {
        ollama.answerInTurn("/api/generate", 200, wrapped("Sorry, I can't draw."), wrapped(CAT));

        SvgImage image = drawer.draw("a cat");

        assertEquals(2, image.shapes().size());
        assertEquals(SceneDrawer.ATTEMPTS, generateRequests(), "it should have asked twice");
    }

    @Test
    void drawGivesUpAfterEveryAttemptFails() {
        answerWith("<svg viewBox=\"0 0 800 480\"><text>A cat</text></svg>"); // nothing drawable

        IOException error = assertThrows(IOException.class, () -> drawer.draw("a cat"));

        assertTrue(error.getMessage().startsWith("The model didn't draw a picture Peint can show"), error.getMessage());
        assertTrue(error.getMessage().contains("no shapes"), error.getMessage());
        assertEquals(SceneDrawer.ATTEMPTS, generateRequests());
    }

    @Test
    void drawPassesOnOllamasOwnErrorsWithoutRetrying() {
        ollama.answer("/api/generate", 404, "{\"error\":\"model \\\"qwen3.5:4b\\\" not found\"}");

        assertThrows(OllamaException.class, () -> drawer.draw("a cat"));
        assertEquals(1, generateRequests(), "an error from Ollama won't go away by asking again");
    }

    @Test
    void drawRejectsABlankScene() {
        assertThrows(IllegalArgumentException.class, () -> drawer.draw("   "));
        assertEquals(0, generateRequests());
    }

    @Test
    void extractSvgRejectsAnAnswerWithoutAnImage() {
        assertThrows(SvgFormatException.class, () -> SceneDrawer.extractSvg("<svg viewBox=\"0 0 1 1\">"));
        assertThrows(SvgFormatException.class, () -> SceneDrawer.extractSvg("No picture today."));
    }

    private void answerWith(String modelAnswer) {
        ollama.answer("/api/generate", 200, wrapped(modelAnswer));
    }

    /**
     * Wraps a model's answer the way Ollama does.
     */
    private String wrapped(String modelAnswer) {
        return json.createObjectNode().put("response", modelAnswer).toString();
    }

    private long generateRequests() {
        return ollama.requests().stream().filter(request -> request.path().equals("/api/generate")).count();
    }
}
