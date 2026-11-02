package se.pbt.peint.canvas;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.pbt.peint.FxTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

class FloodFillProcessorTest {

    private static final int SIZE = 10;

    @BeforeAll
    static void initJfx() {
        FxTestSupport.initToolkit();
    }

    @Test
    void fillsTheWholeImageWhenItIsOneColor() {
        FxTestSupport.runAndWait(() -> {
            WritableImage image = filledImage(Color.WHITE);

            fill(image, 5, 5, Color.RED);

            for (int x = 0; x < SIZE; x++) {
                for (int y = 0; y < SIZE; y++) {
                    assertEquals(Color.RED, image.getPixelReader().getColor(x, y), "pixel " + x + "," + y);
                }
            }
        });
    }

    @Test
    void fillsOnlyInsideAnEnclosedArea() {
        FxTestSupport.runAndWait(() -> {
            WritableImage image = filledImage(Color.WHITE);
            drawBox(image, 2, 2, 7, 7, Color.BLACK);

            fill(image, 4, 4, Color.RED);

            assertEquals(Color.RED, color(image, 4, 4), "inside the box should be filled");
            assertEquals(Color.BLACK, color(image, 2, 4), "the box's border should be kept");
            assertEquals(Color.WHITE, color(image, 0, 0), "outside the box should be untouched");
        });
    }

    @Test
    void doesNotLeakThroughADiagonalGap() {
        FxTestSupport.runAndWait(() -> {
            // A diagonal line separates the top-right from the bottom-left;
            // the fill only spreads up, down, left and right.
            WritableImage image = filledImage(Color.WHITE);
            for (int i = 0; i < SIZE; i++) {
                image.getPixelWriter().setColor(i, i, Color.BLACK);
            }

            fill(image, SIZE - 1, 0, Color.RED);

            assertEquals(Color.RED, color(image, SIZE - 1, 0));
            assertEquals(Color.WHITE, color(image, 0, SIZE - 1), "the fill shouldn't cross the diagonal");
        });
    }

    @Test
    void fillingWithTheSameColorChangesNothing() {
        FxTestSupport.runAndWait(() -> {
            WritableImage image = filledImage(Color.RED);

            fill(image, 5, 5, Color.RED);

            assertEquals(Color.RED, color(image, 5, 5));
        });
    }

    @Test
    void fillsFromACornerPixel() {
        FxTestSupport.runAndWait(() -> {
            WritableImage image = filledImage(Color.WHITE);

            fill(image, 0, 0, Color.RED);

            assertEquals(Color.RED, color(image, SIZE - 1, SIZE - 1));
        });
    }

    @Test
    void fillsPixelsWithinTheToleranceOfTheClickedColor() {
        FxTestSupport.runAndWait(() -> {
            WritableImage image = filledImage(Color.WHITE);
            Color nearlyWhite = shadeAway(Color.WHITE, FloodFillProcessor.DEFAULT_TOLERANCE);
            image.getPixelWriter().setColor(7, 7, nearlyWhite);

            fill(image, 2, 2, Color.RED);

            assertEquals(Color.RED, color(image, 7, 7), "a pixel within the tolerance should be filled");
        });
    }

    @Test
    void stopsAtPixelsBeyondTheTolerance() {
        FxTestSupport.runAndWait(() -> {
            WritableImage image = filledImage(Color.WHITE);
            Color justTooDark = shadeAway(Color.WHITE, FloodFillProcessor.DEFAULT_TOLERANCE + 1);
            drawBox(image, 2, 2, 7, 7, justTooDark);

            fill(image, 4, 4, Color.RED);

            assertEquals(justTooDark, color(image, 2, 4), "a pixel beyond the tolerance should be kept");
            assertEquals(Color.WHITE, color(image, 0, 0), "the fill shouldn't pass through it");
        });
    }

    @Test
    void zeroToleranceFillsOnlyTheExactColor() {
        FxTestSupport.runAndWait(() -> {
            WritableImage image = filledImage(Color.WHITE);
            Color nearlyWhite = shadeAway(Color.WHITE, 1);
            image.getPixelWriter().setColor(7, 7, nearlyWhite);

            new FloodFillProcessor(image.getPixelReader(), image.getPixelWriter(), SIZE, SIZE, 0)
                    .fill(2, 2, Color.RED);

            assertEquals(nearlyWhite, color(image, 7, 7), "even a one-step difference should stop the fill");
            assertEquals(Color.RED, color(image, 0, 0));
        });
    }

    @Test
    void maximumToleranceFillsEveryColor() {
        FxTestSupport.runAndWait(() -> {
            WritableImage image = filledImage(Color.WHITE);
            drawBox(image, 2, 2, 7, 7, Color.BLACK);

            new FloodFillProcessor(image.getPixelReader(), image.getPixelWriter(), SIZE, SIZE,
                    FloodFillProcessor.MAX_TOLERANCE).fill(0, 0, Color.RED);

            assertEquals(Color.RED, color(image, 2, 4), "at full tolerance the black box is filled too");
            assertEquals(Color.RED, color(image, 4, 4), "and so is its inside");
        });
    }

    @Test
    void returnsTheBoundsOfTheFilledPixels() {
        FxTestSupport.runAndWait(() -> {
            WritableImage image = filledImage(Color.WHITE);
            drawBox(image, 2, 3, 7, 8, Color.BLACK);

            Rectangle2D bounds = new FloodFillProcessor(image.getPixelReader(), image.getPixelWriter(), SIZE, SIZE)
                    .fill(4, 5, Color.RED);

            // The inside of the box, between its borders.
            assertEquals(new Rectangle2D(3, 4, 4, 4), bounds);
        });
    }

    @Test
    void returnsNoBoundsWhenNothingWasFilled() {
        FxTestSupport.runAndWait(() -> {
            WritableImage image = filledImage(Color.RED);

            assertNull(new FloodFillProcessor(image.getPixelReader(), image.getPixelWriter(), SIZE, SIZE)
                    .fill(5, 5, Color.RED));
        });
    }

    @Test
    void fillsALargeImageCompletely() {
        FxTestSupport.runAndWait(() -> {
            // Big enough that queueing pixels more than once, as the fill
            // used to, would mean millions of extra queue entries.
            int width = 1600;
            int height = 1000;
            WritableImage image = filledImage(width, height, Color.WHITE);

            new FloodFillProcessor(image.getPixelReader(), image.getPixelWriter(), width, height)
                    .fill(width / 2.0, height / 2.0, Color.RED);

            int red = image.getPixelReader().getArgb(0, 0);
            assertEquals(Color.RED, color(image, 0, 0));
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (image.getPixelReader().getArgb(x, y) != red) {
                        fail("pixel " + x + "," + y + " wasn't filled");
                    }
                }
            }
        });
    }

    @Test
    void ignoresAStartPointOutsideTheImage() {
        FxTestSupport.runAndWait(() -> {
            WritableImage image = filledImage(Color.WHITE);

            fill(image, -1, 5, Color.RED);
            fill(image, 5, SIZE, Color.RED);

            assertEquals(Color.WHITE, color(image, 5, 5));
        });
    }

    private static void fill(WritableImage image, double x, double y, Color newColor) {
        new FloodFillProcessor(image.getPixelReader(), image.getPixelWriter(), SIZE, SIZE).fill(x, y, newColor);
    }

    private static WritableImage filledImage(Color color) {
        return filledImage(SIZE, SIZE, color);
    }

    private static WritableImage filledImage(int width, int height, Color color) {
        WritableImage image = new WritableImage(width, height);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.getPixelWriter().setColor(x, y, color);
            }
        }
        return image;
    }

    /**
     * Draws the outline of a box, inclusive of both corners.
     */
    private static void drawBox(WritableImage image, int x1, int y1, int x2, int y2, Color color) {
        PixelWriter writer = image.getPixelWriter();
        for (int x = x1; x <= x2; x++) {
            writer.setColor(x, y1, color);
            writer.setColor(x, y2, color);
        }
        for (int y = y1; y <= y2; y++) {
            writer.setColor(x1, y, color);
            writer.setColor(x2, y, color);
        }
    }

    /**
     * Returns the color whose red, green and blue channels are each the given
     * number of steps (0-255) darker than the given color's.
     */
    private static Color shadeAway(Color color, int steps) {
        return Color.rgb(
                (int) Math.round(color.getRed() * 255) - steps,
                (int) Math.round(color.getGreen() * 255) - steps,
                (int) Math.round(color.getBlue() * 255) - steps);
    }

    private static Color color(WritableImage image, int x, int y) {
        return image.getPixelReader().getColor(x, y);
    }
}
