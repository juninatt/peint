package se.pbt.peint.canvas;

import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.pbt.peint.FxTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static Color color(WritableImage image, int x, int y) {
        return image.getPixelReader().getColor(x, y);
    }
}
