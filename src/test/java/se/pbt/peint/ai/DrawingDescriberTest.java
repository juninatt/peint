package se.pbt.peint.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawingDescriberTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private FakeOllama ollama;
    private DrawingDescriber describer;

    @BeforeEach
    void startFakeOllama() throws IOException {
        ollama = new FakeOllama();
        describer = new DrawingDescriber(new OllamaClient(ollama.address()), "qwen3.5:4b");
    }

    @AfterEach
    void stopFakeOllama() {
        ollama.close();
    }

    @Test
    void describeReturnsTheTitleAndDescriptionFromTheAnswer() throws Exception {
        answerWith("{\"title\": \"Sunny House\", \"description\": \"A yellow house under an orange sun.\"}");

        Description description = describer.describe(new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB));

        assertEquals("Sunny House", description.title());
        assertEquals("A yellow house under an orange sun.", description.text());
    }

    @Test
    void describeAsksTheConfiguredModelForATitleAndDescription() throws Exception {
        answerWith("{\"title\": \"t\", \"description\": \"d\"}");

        describer.describe(new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB));

        JsonNode body = json.readTree(ollama.requests().getLast().body());
        assertEquals("qwen3.5:4b", body.path("model").asString());
        assertEquals(DrawingDescriber.PROMPT, body.path("prompt").asString());
        JsonNode schema = body.path("format");
        assertEquals("string", schema.path("properties").path("title").path("type").asString());
        assertEquals("string", schema.path("properties").path("description").path("type").asString());
        assertEquals(2, schema.path("required").size());
    }

    @Test
    void describeSendsALargeDrawingScaledDown() throws Exception {
        answerWith("{\"title\": \"t\", \"description\": \"d\"}");

        describer.describe(new BufferedImage(1600, 960, BufferedImage.TYPE_INT_RGB));

        BufferedImage sent = sentImage();
        assertEquals(DrawingDescriber.MAX_IMAGE_SIZE, sent.getWidth());
        assertEquals(307, sent.getHeight(), "the proportions should be kept");
    }

    @Test
    void aSmallDrawingIsSentAsItIs() {
        BufferedImage small = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);

        assertSame(small, DrawingDescriber.scaleForModel(small));
    }

    @Test
    void aTallDrawingIsScaledByItsHeight() {
        BufferedImage tall = DrawingDescriber.scaleForModel(new BufferedImage(400, 2048, BufferedImage.TYPE_INT_RGB));

        assertEquals(100, tall.getWidth());
        assertEquals(DrawingDescriber.MAX_IMAGE_SIZE, tall.getHeight());
    }

    @Test
    void describeTrimsTheAnswer() throws Exception {
        answerWith("{\"title\": \"  Sunny House \\n\", \"description\": \" A house. \"}");

        Description description = describer.describe(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));

        assertEquals("Sunny House", description.title());
        assertEquals("A house.", description.text());
    }

    @Test
    void describeRejectsAnAnswerWithoutATitle() {
        answerWith("{\"title\": \"\", \"description\": \"A house.\"}");

        assertThrows(IOException.class, () -> describer.describe(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)));
    }

    @Test
    void describeRejectsAnAnswerThatIsNotJson() {
        answerWith("Sure! Here is a description of your drawing.");

        IOException error = assertThrows(IOException.class,
                () -> describer.describe(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)));
        assertTrue(error.getMessage().startsWith("Not valid JSON"), error.getMessage());
    }

    /**
     * Makes the fake model answer with the given text, wrapped the way
     * Ollama wraps a model's answer.
     */
    private void answerWith(String modelAnswer) {
        ollama.answer("/api/generate", 200, json.createObjectNode().put("response", modelAnswer).toString());
    }

    private BufferedImage sentImage() throws IOException {
        JsonNode body = json.readTree(ollama.requests().getLast().body());
        byte[] png = Base64.getDecoder().decode(body.path("images").path(0).asString());
        return ImageIO.read(new ByteArrayInputStream(png));
    }
}
