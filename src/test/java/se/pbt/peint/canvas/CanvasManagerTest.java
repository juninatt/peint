package se.pbt.peint.canvas;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.pbt.peint.FxTestSupport;
import se.pbt.peint.svg.SvgFormatException;
import se.pbt.peint.svg.SvgImage;
import se.pbt.peint.svg.SvgReader;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CanvasManagerTest {

    private static final int SIZE = 100;

    private Canvas canvas;
    private Pane container;
    private CanvasManager canvasManager;

    @BeforeAll
    static void initJfx() {
        FxTestSupport.initToolkit();
    }

    @BeforeEach
    void setUp() {
        FxTestSupport.runAndWait(() -> {
            canvas = new Canvas(SIZE, SIZE);
            container = new Pane(canvas);
            canvasManager = new CanvasManager(canvas, container);
            canvasManager.setDrawingColor(Color.BLACK);
        });
    }

    @Test
    void newCanvasIsOpaqueWhite() {
        FxTestSupport.runAndWait(() ->
                assertEquals(Color.WHITE, canvasPixel(50, 50), "the empty canvas should be white, not transparent"));
    }

    @Test
    void areaAddedByGrowingIsOpaqueWhite() {
        FxTestSupport.runAndWait(() -> {
            container.resize(200, 150);

            assertEquals(Color.WHITE, canvasPixel(180, 140));
        });
    }

    @Test
    void clearCanvasLeavesAnOpaqueWhiteCanvas() {
        FxTestSupport.runAndWait(() -> {
            drag(10, 50, 90, 50, 8);

            canvasManager.clearCanvas();

            assertEquals(Color.WHITE, canvasPixel(50, 50));
        });
    }

    @Test
    void transparentPartsOfALoadedImageShowTheWhiteBackground() {
        FxTestSupport.runAndWait(() -> {
            canvasManager.loadImage(new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB)); // fully transparent

            assertEquals(Color.WHITE, canvasPixel(10, 10));
        });
    }

    @Test
    void canvasGrowsWithItsContainer() {
        FxTestSupport.runAndWait(() -> {
            container.resize(150, 120);

            assertEquals(150, canvas.getWidth());
            assertEquals(120, canvas.getHeight());
        });
    }

    @Test
    void canvasDoesNotShrinkWithItsContainer() {
        FxTestSupport.runAndWait(() -> {
            container.resize(150, 150);

            container.resize(60, 40);

            assertEquals(150, canvas.getWidth());
            assertEquals(150, canvas.getHeight());
        });
    }

    @Test
    void drawingSurvivesAShrinkAndRegrowOfTheContainer() {
        FxTestSupport.runAndWait(() -> {
            container.resize(SIZE, SIZE);
            drag(10, 80, 90, 80, 8);

            container.resize(SIZE, 40); // hides the stroke...
            container.resize(SIZE, SIZE); // ...and shows it again

            assertTrue(pixel(50, 80).getBrightness() < 0.9, "a stroke outside the shrunk area should be kept");
        });
    }

    @Test
    void drawingSurvivesTheCanvasGrowing() {
        FxTestSupport.runAndWait(() -> {
            drag(10, 50, 90, 50, 8);

            container.resize(200, 200);

            assertTrue(pixel(50, 50).getBrightness() < 0.9, "growing the canvas should keep what was drawn");
        });
    }

    @Test
    void undoRemovesTheLastStroke() {
        FxTestSupport.runAndWait(() -> {
            drag(10, 50, 90, 50, 8);

            canvasManager.undo();

            assertEquals(Color.WHITE, pixel(50, 50));
        });
    }

    @Test
    void redoReappliesAnUndoneStroke() {
        FxTestSupport.runAndWait(() -> {
            drag(10, 50, 90, 50, 8);
            canvasManager.undo();

            canvasManager.redo();

            assertTrue(pixel(50, 50).getBrightness() < 0.9, "redo should bring the stroke back");
        });
    }

    @Test
    void aStrokeRecordsOnlyTheRectangleItWasDrawnIn() {
        FxTestSupport.runAndWait(() -> {
            container.resize(1000, 1000);
            drag(600, 700, 640, 700, 4);

            // A 40px horizontal stroke: about 44x4 pixels, before and after,
            // rather than two copies of the whole 1000x1000 canvas.
            long bytes = canvasManager.history().totalBytes();
            assertTrue(bytes < 2 * 50 * 10 * 4, "the stroke's edit took " + bytes + " bytes");
        });
    }

    @Test
    void undoAndRedoOfAStrokeAwayFromTheCornerRestoreItsPixelsExactly() {
        FxTestSupport.runAndWait(() -> {
            drag(10, 20, 40, 20, 4); // an earlier stroke that must stay put
            WritableImage beforeStroke = snapshot();
            drag(55, 70, 90, 85, 6);
            WritableImage afterStroke = snapshot();

            canvasManager.undo();
            assertSameImage(beforeStroke, snapshot(), "undo should restore exactly the canvas before the stroke");

            canvasManager.redo();
            assertSameImage(afterStroke, snapshot(), "redo should restore exactly the canvas after the stroke");
        });
    }

    @Test
    void aPressWithoutDraggingRecordsNothing() {
        FxTestSupport.runAndWait(() -> {
            fireMouse(MouseEvent.MOUSE_PRESSED, 50, 50);
            fireMouse(MouseEvent.MOUSE_RELEASED, 50, 50);

            assertEquals(0, canvasManager.history().undoCount());
        });
    }

    @Test
    void bucketFillRecordsOnlyTheFilledArea() {
        FxTestSupport.runAndWait(() -> {
            container.resize(1000, 1000);
            // A closed 20x20 box; filling inside it should record about that much.
            drag(500, 500, 520, 500, 2);
            drag(520, 500, 520, 520, 2);
            drag(520, 520, 500, 520, 2);
            drag(500, 520, 500, 500, 2);
            long beforeFill = canvasManager.history().totalBytes();

            canvasManager.bucketFill(510, 510, Color.RED);

            long fillBytes = canvasManager.history().totalBytes() - beforeFill;
            assertTrue(fillBytes <= 2 * 20 * 20 * 4, "the fill's edit took " + fillBytes + " bytes");
        });
    }

    @Test
    void undoAcrossAGrowRemovesStrokesInTheNewArea() {
        FxTestSupport.runAndWait(() -> {
            container.resize(SIZE, SIZE);
            drag(10, 50, 90, 50, 8); // records the blank 100x100 canvas

            container.resize(200, 200);
            drag(110, 150, 190, 150, 8); // records 200x200 with the first stroke
            canvasManager.undo();
            canvasManager.undo();

            assertEquals(Color.WHITE, pixel(50, 50), "the first stroke should be undone");
            assertEquals(Color.WHITE, pixel(150, 150), "the second stroke should be undone");
        });
    }

    @Test
    void redoAfterUndoAcrossAGrowRestoresBothStrokes() {
        FxTestSupport.runAndWait(() -> {
            container.resize(SIZE, SIZE);
            drag(10, 50, 90, 50, 8);
            container.resize(200, 200);
            drag(110, 150, 190, 150, 8);
            canvasManager.undo();
            canvasManager.undo();

            canvasManager.redo();
            canvasManager.redo();

            assertTrue(pixel(50, 50).getBrightness() < 0.9, "the first stroke should be back");
            assertTrue(pixel(150, 150).getBrightness() < 0.9, "the second stroke should be back");
        });
    }

    @Test
    void clearCanvasRemovesEverythingAndCanBeUndone() {
        FxTestSupport.runAndWait(() -> {
            drag(10, 50, 90, 50, 8);

            canvasManager.clearCanvas();
            assertEquals(Color.WHITE, pixel(50, 50), "clear should remove the stroke");

            canvasManager.undo();
            assertTrue(pixel(50, 50).getBrightness() < 0.9, "undoing the clear should bring the stroke back");
        });
    }

    @Test
    void loadImageDrawsTheImageAtTheTopLeftAndCanBeUndone() {
        FxTestSupport.runAndWait(() -> {
            canvasManager.loadImage(blueImage(20, 20));
            assertEquals(Color.BLUE, pixel(10, 10));
            assertEquals(Color.WHITE, pixel(50, 50), "the canvas outside the image should be untouched");

            canvasManager.undo();
            assertEquals(Color.WHITE, pixel(10, 10), "undo should remove the loaded image");
        });
    }

    @Test
    void loadImageDrawsACmykImageInTheRightColors() throws IOException {
        // Decoded from a CMYK JPEG, the image has a CMYK color model, which
        // must be converted rather than copied when drawn on the canvas.
        BufferedImage cmyk;
        try (InputStream in = CanvasManagerTest.class.getResourceAsStream("/se/pbt/peint/support/cmyk.jpg")) {
            cmyk = ImageIO.read(in);
        }

        FxTestSupport.runAndWait(() -> {
            canvasManager.loadImage(cmyk);

            Color left = pixel(5, 10);
            Color right = pixel(35, 10);
            assertTrue(left.getRed() > 0.8 && left.getBlue() < 0.2, "left side should be red, was " + left);
            assertTrue(right.getBlue() > 0.8 && right.getRed() < 0.2, "right side should be blue, was " + right);
        });
    }

    @Test
    void loadImageGrowsTheCanvasToFitALargerImage() {
        FxTestSupport.runAndWait(() -> {
            canvasManager.loadImage(blueImage(250, 180));

            assertEquals(250, canvas.getWidth());
            assertEquals(180, canvas.getHeight());
            assertEquals(Color.BLUE, pixel(240, 170), "the part beyond the old canvas size should be kept");
        });
    }

    @Test
    void loadImageKeepsTheCanvasSizeForASmallerImage() {
        FxTestSupport.runAndWait(() -> {
            canvasManager.loadImage(blueImage(20, 20));

            assertEquals(SIZE, canvas.getWidth());
            assertEquals(SIZE, canvas.getHeight());
        });
    }

    @Test
    void undoingALoadThatGrewTheCanvasRemovesTheWholeImage() {
        FxTestSupport.runAndWait(() -> {
            canvasManager.loadImage(blueImage(250, 180));

            canvasManager.undo();

            assertEquals(Color.WHITE, pixel(50, 50));
            assertEquals(Color.WHITE, pixel(240, 170), "the part beyond the old canvas size should be undone too");
        });
    }

    @Test
    void bucketFillFillsTheClickedAreaAndCanBeUndone() {
        FxTestSupport.runAndWait(() -> {
            canvasManager.bucketFill(50, 50, Color.RED);
            assertEquals(Color.RED, pixel(50, 50));
            assertEquals(Color.RED, pixel(0, 0), "the whole blank canvas is one area");

            canvasManager.undo();
            assertEquals(Color.WHITE, pixel(50, 50), "undo should remove the fill");
        });
    }

    @Test
    void bucketFillStopsAtStrokes() {
        FxTestSupport.runAndWait(() -> {
            drag(0, 50, SIZE, 50, 8);

            canvasManager.bucketFill(50, 20, Color.RED);

            assertEquals(Color.RED, pixel(50, 20), "the clicked side should be filled");
            assertEquals(Color.WHITE, pixel(50, 80), "the fill shouldn't cross the stroke");
        });
    }

    @Test
    void bucketFillLeavesNoPaleOutlineAlongAStroke() {
        FxTestSupport.runAndWait(() -> {
            // A diagonal stroke is antialiased into many shades of gray.
            drag(0, 0, SIZE, SIZE, 20);

            canvasManager.bucketFill(SIZE - 10, 10, Color.RED);

            // Every pixel on the filled side is now either filled or part of
            // the stroke's dark core; no pale, unfilled edge pixels remain.
            PixelReader pixels = snapshot().getPixelReader();
            for (int x = 0; x < SIZE; x++) {
                for (int y = 0; y < x; y++) {
                    Color c = pixels.getColor(x, y);
                    boolean filled = c.equals(Color.RED);
                    boolean darkStroke = c.getBrightness() < 0.65;
                    assertTrue(filled || darkStroke, "pixel " + x + "," + y + " was left pale: " + c);
                }
            }
        });
    }

    @Test
    void containerClipsTheCanvasToItsOwnSize() {
        FxTestSupport.runAndWait(() -> {
            container.resize(150, 150);
            container.resize(60, 40);

            Rectangle clip = (Rectangle) container.getClip();
            assertEquals(60, clip.getWidth());
            assertEquals(40, clip.getHeight());
        });
    }

    @Test
    void dragDrawsAStrokeAlongTheMousePath() {
        FxTestSupport.runAndWait(() -> {
            drag(10, 50, 90, 50, 8);

            assertTrue(pixel(50, 50).getBrightness() < 0.9, "the stroke should have darkened the canvas under it");
            assertEquals(Color.WHITE, pixel(50, 20), "pixels away from the stroke should stay untouched");
        });
    }

    @Test
    void strokeKeepsTheSameIntensityAlongItsLength() {
        FxTestSupport.runAndWait(() -> {
            // A horizontal 1px line on an integer y is antialiased across two
            // pixel rows. Re-stroking the whole path on every drag event used
            // to draw the start of the stroke many more times than its end,
            // making it visibly darker.
            drag(10, 50, 90, 50, 40);

            double nearStart = pixel(15, 50).getBrightness();
            double nearEnd = pixel(85, 50).getBrightness();
            assertEquals(nearEnd, nearStart, 0.05, "the start of the stroke shouldn't be darker than its end");
        });
    }

    @Test
    void strokeUsesTheDrawingColor() {
        FxTestSupport.runAndWait(() -> {
            canvasManager.setDrawingColor(Color.RED);

            drag(10, 50, 90, 50, 8);

            Color stroked = pixel(50, 50);
            assertTrue(stroked.getRed() > stroked.getGreen() && stroked.getRed() > stroked.getBlue(),
                    "the stroke should be drawn in the drawing color, was " + stroked);
        });
    }

    @Test
    void separateStrokesAreNotJoined() {
        FxTestSupport.runAndWait(() -> {
            drag(10, 20, 40, 20, 4);
            drag(60, 80, 90, 80, 4);

            // A line from the end of the first stroke to the start of the
            // second would cross the canvas's center.
            assertEquals(Color.WHITE, pixel(50, 50), "a new press should start a new stroke");
        });
    }

    @Test
    void dragsDoNotDrawWithTheFillTool() {
        FxTestSupport.runAndWait(() -> {
            canvasManager.setTool(new FillTool());

            drag(10, 50, 90, 50, 8);

            assertEquals(Color.WHITE, pixel(50, 50));
        });
    }

    @Test
    void theEraserErasesWhatWasDrawn() {
        FxTestSupport.runAndWait(() -> {
            drag(10, 50, 90, 50, 8);
            canvasManager.setTool(new EraserTool());

            drag(10, 50, 90, 50, 8); // over the same line

            assertEquals(Color.WHITE, canvasPixel(50, 50), "the line should be erased to the background");
        });
    }

    @Test
    void theEraserIgnoresTheDrawingColor() {
        FxTestSupport.runAndWait(() -> {
            canvasManager.setDrawingColor(Color.RED);
            canvasManager.loadImage(blueImage(SIZE, SIZE));
            canvasManager.setTool(new EraserTool());

            drag(10, 50, 90, 50, 8);

            Color erased = canvasPixel(50, 50);
            assertTrue(erased.getRed() > 0.9 && erased.getGreen() > 0.9 && erased.getBlue() > 0.9,
                    "erasing should paint the white background, not the drawing color: " + erased);
        });
    }

    @Test
    void erasingCanBeUndone() {
        FxTestSupport.runAndWait(() -> {
            drag(10, 50, 90, 50, 8);
            canvasManager.setTool(new EraserTool());
            drag(10, 50, 90, 50, 8);

            canvasManager.undo();

            assertTrue(pixel(50, 50).getBrightness() < 0.9, "undo should bring the erased line back");
        });
    }

    @Test
    void aWiderBrushPaintsAWiderLine() {
        FxTestSupport.runAndWait(() -> {
            drag(10, 30, 90, 30, 8);
            BrushTool wide = new BrushTool();
            wide.setWidth(12);
            canvasManager.setTool(wide);
            drag(10, 70, 90, 70, 8);

            assertEquals(Color.WHITE, pixel(50, 34), "4px beside the default 1px line should be untouched");
            assertTrue(pixel(50, 74).getBrightness() < 0.2, "4px beside the 12px line should be painted");
        });
    }

    @Test
    void theFillToolFillsWithItsOwnTolerance() {
        FxTestSupport.runAndWait(() -> {
            // White on the left, a slightly darker white on the right.
            BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, SIZE / 2, SIZE);
            g.setColor(new java.awt.Color(240, 240, 240));
            g.fillRect(SIZE / 2, 0, SIZE / 2, SIZE);
            g.dispose();
            canvasManager.loadImage(image);
            canvasManager.setDrawingColor(Color.RED);

            FillTool exact = new FillTool();
            exact.setTolerance(0);
            canvasManager.setTool(exact);
            fireMouse(MouseEvent.MOUSE_CLICKED, 10, 50);

            assertEquals(Color.RED, pixel(10, 50));
            assertEquals(Color.rgb(240, 240, 240), pixel(90, 50), "with no tolerance the darker half isn't filled");

            canvasManager.undo();
            canvasManager.setTool(new FillTool()); // default tolerance
            fireMouse(MouseEvent.MOUSE_CLICKED, 10, 50);

            assertEquals(Color.RED, pixel(90, 50), "with the default tolerance both halves are filled");
        });
    }

    @Test
    void everyChangeToTheDrawingIsCounted() {
        FxTestSupport.runAndWait(() -> {
            assertEquals(0, canvasManager.changeCount());

            drag(10, 50, 90, 50, 8);
            canvasManager.bucketFill(50, 20, Color.RED);
            canvasManager.undo();
            canvasManager.redo();
            canvasManager.clearCanvas();
            canvasManager.loadImage(blueImage(20, 20));

            assertEquals(6, canvasManager.changeCount(), "stroke, fill, undo, redo, clear and open");
        });
    }

    @Test
    void thingsThatDontChangeTheDrawingAreNotCounted() {
        FxTestSupport.runAndWait(() -> {
            fireMouse(MouseEvent.MOUSE_PRESSED, 50, 50); // a press without a drag draws nothing
            fireMouse(MouseEvent.MOUSE_RELEASED, 50, 50);
            canvasManager.undo(); // nothing to undo
            container.resize(200, 200); // growing keeps the drawing as it is

            assertEquals(0, canvasManager.changeCount());
        });
    }

    @Test
    void drawSvgScalesTheImageToFillACanvasOfTheSameProportions() {
        FxTestSupport.runAndWait(() -> {
            container.resize(200, 120); // the canvas grows to 200x120, the image's 5:3

            canvasManager.drawSvg(svg("""
                    <rect x="0" y="0" width="400" height="480" fill="red"/>
                    <rect x="400" y="0" width="400" height="480" fill="blue"/>
                    """));

            assertEquals(Color.RED, canvasPixel(50, 60));
            assertEquals(Color.BLUE, canvasPixel(150, 60));
            assertEquals(Color.RED, canvasPixel(1, 1), "the image should reach the canvas's corner");
        });
    }

    @Test
    void drawSvgKeepsTheImagesProportionsAndCentersIt() {
        FxTestSupport.runAndWait(() -> {
            // An 800x480 image on the 100x100 canvas: 100x60, with 20 px above and below.
            canvasManager.drawSvg(svg("<rect width=\"800\" height=\"480\" fill=\"red\"/>"));

            assertEquals(Color.RED, canvasPixel(50, 50));
            assertEquals(Color.WHITE, canvasPixel(50, 5), "the space above the image keeps the canvas");
            assertEquals(Color.WHITE, canvasPixel(50, 95), "and so does the space below");
        });
    }

    @Test
    void drawSvgDrawsEveryKindOfShape() {
        FxTestSupport.runAndWait(() -> {
            container.resize(200, 120);

            canvasManager.drawSvg(svg("""
                    <circle cx="100" cy="100" r="60" fill="red"/>
                    <ellipse cx="300" cy="100" rx="60" ry="40" fill="lime"/>
                    <polygon points="440,40 560,40 500,160" fill="blue"/>
                    <path d="M640 40 L760 40 L760 160 L640 160 Z" fill="#ffff00"/>
                    <line x1="0" y1="300" x2="800" y2="300" stroke="black" stroke-width="40"/>
                    <rect x="300" y="380" width="200" height="80" rx="20" fill="none" stroke="magenta" stroke-width="20"/>
                    """));

            assertEquals(Color.RED, canvasPixel(25, 25), "circle");
            assertEquals(Color.LIME, canvasPixel(75, 25), "ellipse");
            assertEquals(Color.BLUE, canvasPixel(125, 20), "polygon");
            assertEquals(Color.YELLOW, canvasPixel(175, 25), "path");
            assertEquals(Color.BLACK, canvasPixel(100, 75), "line");
            assertEquals(Color.MAGENTA, canvasPixel(100, 95), "the outline of a rounded rectangle");
            assertEquals(Color.WHITE, canvasPixel(100, 105), "an unfilled rectangle's inside");
        });
    }

    @Test
    void drawSvgPaintsGradientsAndOpacity() {
        FxTestSupport.runAndWait(() -> {
            container.resize(200, 120);

            canvasManager.drawSvg(svg("""
                    <defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0" stop-color="white"/><stop offset="1" stop-color="black"/>
                    </linearGradient></defs>
                    <rect width="400" height="480" fill="url(#g)"/>
                    <rect x="400" width="400" height="480" fill="red" opacity="0.5"/>
                    """));

            assertTrue(canvasPixel(50, 10).getBrightness() > canvasPixel(50, 110).getBrightness() + 0.5,
                    "the gradient should run from light at the top to dark at the bottom");
            Color halfRed = canvasPixel(150, 60);
            assertEquals(1.0, halfRed.getRed(), 0.02);
            assertEquals(0.5, halfRed.getGreen(), 0.02, "half-transparent red over white is pink");
        });
    }

    @Test
    void drawSvgMovesShapesByTheirTranslateTransforms() {
        FxTestSupport.runAndWait(() -> {
            container.resize(200, 120);

            canvasManager.drawSvg(svg("<g transform=\"translate(400, 0)\"><rect width=\"400\" height=\"480\" fill=\"red\"/></g>"));

            assertEquals(Color.WHITE, canvasPixel(50, 60));
            assertEquals(Color.RED, canvasPixel(150, 60));
        });
    }

    @Test
    void drawSvgLeavesOutAPathItCantRead() {
        FxTestSupport.runAndWait(() -> {
            container.resize(200, 120);

            canvasManager.drawSvg(svg("""
                    <path d="this is not path data" fill="blue"/>
                    <rect width="800" height="480" fill="red"/>
                    """));

            assertEquals(Color.RED, canvasPixel(100, 60), "the rest of the image should still be drawn");
        });
    }

    @Test
    void oneUndoRemovesAWholeSvgDrawing() {
        FxTestSupport.runAndWait(() -> {
            container.resize(200, 120);
            long changesBefore = canvasManager.changeCount();

            canvasManager.drawSvg(svg("""
                    <rect width="800" height="480" fill="red"/>
                    <circle cx="400" cy="240" r="100" fill="blue"/>
                    """));
            canvasManager.undo();

            assertEquals(Color.WHITE, canvasPixel(100, 60));
            assertEquals(Color.WHITE, canvasPixel(10, 10));
            assertEquals(changesBefore + 2, canvasManager.changeCount(), "drawing and undoing are one change each");
        });
    }

    @Test
    void drawSvgLeavesTheBrushAsItWas() {
        FxTestSupport.runAndWait(() -> {
            canvasManager.drawSvg(svg("<line x1=\"0\" y1=\"0\" x2=\"800\" y2=\"480\" stroke=\"red\" stroke-width=\"80\" opacity=\"0.3\"/>"));
            canvasManager.clearCanvas();

            drag(10, 50, 90, 50, 8);

            assertTrue(pixel(50, 50).getBrightness() < 0.2, "the brush should draw opaque black again: " + pixel(50, 50));
        });
    }

    /**
     * Reads an 800x480 SVG image with the given shapes.
     */
    private static SvgImage svg(String shapes) {
        try {
            return SvgReader.read("<svg viewBox=\"0 0 800 480\" xmlns=\"http://www.w3.org/2000/svg\">" + shapes + "</svg>");
        } catch (SvgFormatException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void theBrushIsSelectedByDefault() {
        assertInstanceOf(BrushTool.class, canvasManager.getTool());
    }

    @Test
    void theFillToolStaysSelectedAndFillsEachClick() {
        FxTestSupport.runAndWait(() -> {
            drag(0, 50, SIZE, 50, 8); // splits the canvas into a top and a bottom area
            canvasManager.setDrawingColor(Color.RED);
            canvasManager.setTool(new FillTool());

            fireMouse(MouseEvent.MOUSE_CLICKED, 50, 20);
            fireMouse(MouseEvent.MOUSE_CLICKED, 50, 80);

            assertEquals(Color.RED, pixel(50, 20), "the first click should fill the top area");
            assertEquals(Color.RED, pixel(50, 80), "the second click should fill the bottom area too");
            assertInstanceOf(FillTool.class, canvasManager.getTool());
        });
    }

    @Test
    void clicksDoNothingWithTheBrush() {
        FxTestSupport.runAndWait(() -> {
            fireMouse(MouseEvent.MOUSE_CLICKED, 50, 50);

            assertEquals(Color.WHITE, pixel(50, 50));
            assertEquals(0, canvasManager.history().undoCount());
        });
    }

    @Test
    void switchingBackToTheBrushDrawsAgain() {
        FxTestSupport.runAndWait(() -> {
            canvasManager.setTool(new FillTool());
            canvasManager.setTool(new BrushTool());

            drag(10, 50, 90, 50, 8);

            assertTrue(pixel(50, 50).getBrightness() < 0.9, "the brush should draw again");
        });
    }

    /**
     * Presses at the start point, drags to the end point in the given number
     * of evenly spaced steps, and releases.
     */
    private void drag(double fromX, double fromY, double toX, double toY, int steps) {
        fireMouse(MouseEvent.MOUSE_PRESSED, fromX, fromY);
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            fireMouse(MouseEvent.MOUSE_DRAGGED, fromX + (toX - fromX) * t, fromY + (toY - fromY) * t);
        }
        fireMouse(MouseEvent.MOUSE_RELEASED, toX, toY);
    }

    private void fireMouse(EventType<MouseEvent> type, double x, double y) {
        Event.fireEvent(canvas, new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null));
    }

    private static BufferedImage blueImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(java.awt.Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    /**
     * Reads one pixel of the canvas as rendered, i.e. on the white background
     * a default snapshot uses.
     */
    private Color pixel(int x, int y) {
        return snapshot().getPixelReader().getColor(x, y);
    }

    /**
     * Reads one pixel of the canvas itself, without the white background a
     * default snapshot adds, so transparent pixels read as transparent.
     */
    private Color canvasPixel(int x, int y) {
        SnapshotParameters transparent = new SnapshotParameters();
        transparent.setFill(Color.TRANSPARENT);
        WritableImage snapshot = new WritableImage((int) canvas.getWidth(), (int) canvas.getHeight());
        canvas.snapshot(transparent, snapshot);
        return snapshot.getPixelReader().getColor(x, y);
    }

    private static void assertSameImage(WritableImage expected, WritableImage actual, String message) {
        assertEquals(expected.getWidth(), actual.getWidth(), message);
        assertEquals(expected.getHeight(), actual.getHeight(), message);
        for (int x = 0; x < (int) expected.getWidth(); x++) {
            for (int y = 0; y < (int) expected.getHeight(); y++) {
                int want = expected.getPixelReader().getArgb(x, y);
                int got = actual.getPixelReader().getArgb(x, y);
                if (want != got) {
                    fail(message + ": pixel " + x + "," + y + " was " + Integer.toHexString(got)
                            + ", expected " + Integer.toHexString(want));
                }
            }
        }
    }

    private WritableImage snapshot() {
        WritableImage snapshot = new WritableImage((int) canvas.getWidth(), (int) canvas.getHeight());
        canvas.snapshot(null, snapshot);
        return snapshot;
    }
}
