package se.pbt.peint.svg;

import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.shape.StrokeLineCap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SvgReaderTest {

    private static final String NS = "xmlns=\"http://www.w3.org/2000/svg\"";

    @TempDir
    Path tempDir;

    @Test
    void readsTheViewBox() throws Exception {
        SvgImage image = SvgReader.read("<svg viewBox=\"10 20 800 480\" " + NS + "/>");

        assertEquals(10, image.minX());
        assertEquals(20, image.minY());
        assertEquals(800, image.width());
        assertEquals(480, image.height());
    }

    @Test
    void usesTheWidthAndHeightWithoutAViewBox() throws Exception {
        SvgImage image = SvgReader.read("<svg width=\"640px\" height=\"360\" " + NS + "/>");

        assertEquals(640, image.width());
        assertEquals(360, image.height());
    }

    @Test
    void readsEveryKindOfShape() throws Exception {
        List<SvgShape> shapes = shapes("""
                <rect x="1" y="2" width="30" height="40" rx="5"/>
                <circle cx="50" cy="60" r="7"/>
                <ellipse cx="80" cy="90" rx="10" ry="4"/>
                <line x1="0" y1="0" x2="5" y2="5" stroke="black"/>
                <polygon points="200,400 220,380 240,400"/>
                <polyline points="0 0 10 10 20 0"/>
                <path d="M0 320 Q 150 360 300 320 Z"/>
                """);

        assertEquals(new SvgShape.Rect(1, 2, 30, 40, 5, appearanceOf(shapes.get(0))), shapes.get(0));
        assertEquals(new SvgShape.Ellipse(50, 60, 7, 7, appearanceOf(shapes.get(1))), shapes.get(1));
        assertEquals(new SvgShape.Ellipse(80, 90, 10, 4, appearanceOf(shapes.get(2))), shapes.get(2));
        assertInstanceOf(SvgShape.Line.class, shapes.get(3));
        SvgShape.Polygon polygon = assertInstanceOf(SvgShape.Polygon.class, shapes.get(4));
        assertEquals(List.of(200.0, 400.0, 220.0, 380.0, 240.0, 400.0), polygon.points());
        assertTrue(polygon.closed());
        assertFalse(((SvgShape.Polygon) shapes.get(5)).closed(), "a polyline isn't closed");
        assertEquals("M0 320 Q 150 360 300 320 Z", ((SvgShape.Path) shapes.get(6)).data());
    }

    @Test
    void readsPercentagesOfTheImageSize() throws Exception {
        SvgShape.Rect background = (SvgShape.Rect) shapes("<rect width=\"100%\" height=\"50%\"/>").getFirst();

        assertEquals(800, background.width());
        assertEquals(240, background.height());
    }

    @Test
    void readsColorsAsHexNamesOrRgb() throws Exception {
        List<SvgShape> shapes = shapes("""
                <rect width="1" height="1" fill="#E0F7FA"/>
                <rect width="1" height="1" fill="orange"/>
                <rect width="1" height="1" fill="rgb(255, 0, 0)"/>
                <rect width="1" height="1" fill="#333"/>
                """);

        assertEquals(Color.web("#E0F7FA"), shapes.get(0).appearance().fill());
        assertEquals(Color.ORANGE, shapes.get(1).appearance().fill());
        assertEquals(Color.RED, shapes.get(2).appearance().fill());
        assertEquals(Color.web("#333333"), shapes.get(3).appearance().fill());
    }

    @Test
    void fillsBlackAndOutlinesNothingByDefault() throws Exception {
        Appearance appearance = shapes("<circle r=\"5\"/>").getFirst().appearance();

        assertEquals(Color.BLACK, appearance.fill());
        assertNull(appearance.stroke());
    }

    @Test
    void noneMeansNoFillOrOutline() throws Exception {
        Appearance appearance = shapes("<path d=\"M0 0 L10 10\" fill=\"none\" stroke=\"#d59c85\" stroke-width=\"25\" "
                + "stroke-linecap=\"round\"/>").getFirst().appearance();

        assertNull(appearance.fill());
        assertEquals(Color.web("#d59c85"), appearance.stroke());
        assertEquals(25, appearance.strokeWidth());
        assertEquals(StrokeLineCap.ROUND, appearance.lineCap());
    }

    @Test
    void theStyleAttributeTakesPrecedence() throws Exception {
        Appearance appearance = shapes("<rect width=\"1\" height=\"1\" fill=\"red\" style=\"fill: blue; stroke:green\"/>")
                .getFirst().appearance();

        assertEquals(Color.BLUE, appearance.fill());
        assertEquals(Color.GREEN, appearance.stroke());
    }

    @Test
    void groupsPassTheirPaintOnToTheirShapes() throws Exception {
        List<SvgShape> shapes = shapes("""
                <g fill="green" stroke="black" opacity="0.5">
                  <circle r="5"/>
                  <g fill="red"><circle r="5" opacity="0.5"/></g>
                </g>
                """);

        assertEquals(Color.GREEN, shapes.get(0).appearance().fill());
        assertEquals(Color.BLACK, shapes.get(0).appearance().stroke());
        assertEquals(Color.RED, shapes.get(1).appearance().fill(), "a nested group's own fill should win");
        assertEquals(0.25, shapes.get(1).appearance().opacity(), 1e-9, "opacities should multiply");
    }

    @Test
    void translateTransformsAddUp() throws Exception {
        Appearance appearance = shapes("""
                <g transform="translate(100, 50)"><g transform="translate(10)"><circle r="5"/></g></g>
                """).getFirst().appearance();

        assertEquals(110, appearance.offsetX());
        assertEquals(50, appearance.offsetY());
    }

    @Test
    void fillOpacityMakesTheColorSeeThrough() throws Exception {
        Appearance appearance = shapes("<circle r=\"5\" fill=\"#ff0000\" fill-opacity=\"0.4\"/>").getFirst().appearance();

        // Colors store their channels as floats, so 0.4 comes back as 0.40000000596.
        assertEquals(0.4, ((Color) appearance.fill()).getOpacity(), 1e-6);
    }

    @Test
    void readsLinearAndRadialGradients() throws Exception {
        List<SvgShape> shapes = shapes("""
                <defs>
                  <linearGradient id="sky" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" style="stop-color:#FF99CC;stop-opacity:1"/>
                    <stop offset="100%" stop-color="#884422"/>
                  </linearGradient>
                  <radialGradient id="sun"><stop offset="0" stop-color="yellow"/><stop offset="1" stop-color="orange"/></radialGradient>
                </defs>
                <rect width="800" height="480" fill="url(#sky)"/>
                <circle r="60" fill="url( #sun )"/>
                """);

        LinearGradient sky = assertInstanceOf(LinearGradient.class, shapes.get(0).appearance().fill());
        assertEquals(1, sky.getEndY(), "the gradient should run top to bottom");
        assertEquals(Color.web("#FF99CC"), sky.getStops().getFirst().getColor());
        assertEquals(Color.web("#884422"), sky.getStops().getLast().getColor());
        assertInstanceOf(RadialGradient.class, shapes.get(1).appearance().fill());
    }

    @Test
    void aMissingGradientMeansNoFill() throws Exception {
        assertNull(shapes("<rect width=\"1\" height=\"1\" fill=\"url(#nowhere)\"/>").getFirst().appearance().fill());
    }

    @Test
    void leavesOutWhatItCantDraw() throws Exception {
        List<SvgShape> shapes = shapes("""
                <title>A cat</title>
                <text x="10" y="10">Hello</text>
                <filter id="blur"><feGaussianBlur stdDeviation="2"/></filter>
                <circle r="5" display="none"/>
                <rect width="0" height="10"/>
                <polygon points="1,2"/>
                <circle r="5"/>
                """);

        assertEquals(1, shapes.size(), "only the visible circle can be drawn");
    }

    @Test
    void readsShapesWithoutTheSvgNamespace() throws Exception {
        SvgImage image = SvgReader.read("<svg viewBox=\"0 0 10 10\"><circle r=\"5\"/></svg>");

        assertEquals(1, image.shapes().size());
    }

    @Test
    void rejectsTextThatIsNotXml() {
        assertThrows(SvgFormatException.class, () -> SvgReader.read("<svg><rect></svg>"));
        assertThrows(SvgFormatException.class, () -> SvgReader.read("Here is your drawing!"));
    }

    @Test
    void rejectsADocumentThatIsNotSvg() {
        SvgFormatException error = assertThrows(SvgFormatException.class, () -> SvgReader.read("<html/>"));

        assertTrue(error.getMessage().contains("<html>"), error.getMessage());
    }

    @Test
    void refusesExternalEntitiesSoAnImageCantReadLocalFiles() throws IOException {
        Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "top secret");
        String attack = "<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE svg [<!ENTITY secret SYSTEM \"" + secret.toUri() + "\">]>\n"
                + "<svg " + NS + "><title>&secret;</title><circle r=\"5\"/></svg>";

        SvgFormatException error = assertThrows(SvgFormatException.class, () -> SvgReader.read(attack));

        assertTrue(error.getMessage().contains("DOCTYPE"), error.getMessage());
    }

    @Test
    void readsARealCatDrawnByTheModel() throws Exception {
        SvgImage cat = SvgReader.read(resource("model-cat.svg"));

        assertEquals(800, cat.width());
        assertEquals(17, cat.shapes().size(), "every shape the model drew should be read");
        SvgShape tail = cat.shapes().stream().filter(SvgShape.Path.class::isInstance).findFirst().orElseThrow();
        assertNull(tail.appearance().fill(), "the tail is only an outline");
        assertEquals(StrokeLineCap.ROUND, tail.appearance().lineCap());
    }

    @Test
    void readsARealSailboatWithGradientsDrawnByTheModel() throws Exception {
        SvgImage boat = SvgReader.read(resource("model-boat.svg"));

        assertInstanceOf(LinearGradient.class, boat.shapes().getFirst().appearance().fill(), "the sunset sky");
        assertTrue(boat.shapes().size() >= 8, "the boat had " + boat.shapes().size() + " shapes");
    }

    private static List<SvgShape> shapes(String content) throws SvgFormatException {
        return SvgReader.read("<svg viewBox=\"0 0 800 480\" " + NS + ">" + content + "</svg>").shapes();
    }

    private static Appearance appearanceOf(SvgShape shape) {
        return shape.appearance();
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = SvgReaderTest.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
