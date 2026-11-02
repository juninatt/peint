package se.pbt.peint.canvas;

import javafx.event.Event;
import javafx.event.EventType;
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

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void disableDrawingModeStopsDragsFromDrawing() {
        FxTestSupport.runAndWait(() -> {
            canvasManager.disableDrawingMode();

            drag(10, 50, 90, 50, 8);

            assertEquals(Color.WHITE, pixel(50, 50));
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

    private WritableImage snapshot() {
        WritableImage snapshot = new WritableImage((int) canvas.getWidth(), (int) canvas.getHeight());
        canvas.snapshot(null, snapshot);
        return snapshot;
    }
}
