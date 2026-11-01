package se.pbt.peint.canvas;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.pbt.peint.FxTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasManagerTest {

    private static final int SIZE = 100;

    private Canvas canvas;
    private CanvasManager canvasManager;

    @BeforeAll
    static void initJfx() {
        FxTestSupport.initToolkit();
    }

    @BeforeEach
    void setUp() {
        FxTestSupport.runAndWait(() -> {
            canvas = new Canvas(SIZE, SIZE);
            canvasManager = new CanvasManager(canvas, new Pane(canvas));
            canvasManager.setDrawingColor(Color.BLACK);
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

    /**
     * Reads one pixel of the canvas as rendered, i.e. on the white background
     * a default snapshot uses.
     */
    private Color pixel(int x, int y) {
        WritableImage snapshot = new WritableImage(SIZE, SIZE);
        canvas.snapshot(null, snapshot);
        return snapshot.getPixelReader().getColor(x, y);
    }
}
