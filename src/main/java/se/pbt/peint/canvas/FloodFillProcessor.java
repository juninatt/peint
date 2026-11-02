package se.pbt.peint.canvas;

import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.paint.Color;

/**
 * Performs flood fill operations on a canvas.
 *
 * <p>This class processes pixel data from a canvas using a flood fill
 * algorithm to replace a contiguous area of color with a new color.</p>
 */
public class FloodFillProcessor {
    private final PixelReader pixelReader;
    private final PixelWriter pixelWriter;
    private final int canvasWidth;
    private final int canvasHeight;

    /**
     * Initializes the processor with canvas dimensions and pixel data handlers.
     */
    public FloodFillProcessor(PixelReader pixelReader, PixelWriter pixelWriter, int canvasWidth, int canvasHeight) {
        this.pixelReader = pixelReader;
        this.pixelWriter = pixelWriter;
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
    }

    /**
     * Fills a contiguous area starting at the given coordinates with a new color.
     *
     * <p>The algorithm replaces all connected pixels of the same initial color
     * with the specified new color.</p>
     *
     * <p>Each pixel is marked as visited when it is queued rather than when it
     * is filled, so no pixel enters the queue more than once. Queued pixels
     * are stored as their index ({@code y * width + x}) in a plain
     * {@code int} array, so even a fill covering the whole canvas allocates
     * just one array of {@code width * height} ints.</p>
     */
    public void fill(double startX, double startY, Color newColor) {
        int x0 = (int) startX;
        int y0 = (int) startY;
        if (!isInside(x0, y0)) {
            return;
        }

        int targetArgb = pixelReader.getArgb(x0, y0);
        int newArgb = toArgb(newColor);
        if (newArgb == targetArgb) {
            return;
        }

        boolean[] queued = new boolean[canvasWidth * canvasHeight];
        int[] queue = new int[canvasWidth * canvasHeight];
        int head = 0;
        int tail = 0;

        int startIndex = y0 * canvasWidth + x0;
        queued[startIndex] = true;
        queue[tail++] = startIndex;

        while (head < tail) {
            int index = queue[head++];
            int x = index % canvasWidth;
            int y = index / canvasWidth;

            pixelWriter.setArgb(x, y, newArgb);

            tail = enqueueIfMatching(x + 1, y, targetArgb, queue, tail, queued); // Right
            tail = enqueueIfMatching(x - 1, y, targetArgb, queue, tail, queued); // Left
            tail = enqueueIfMatching(x, y + 1, targetArgb, queue, tail, queued); // Down
            tail = enqueueIfMatching(x, y - 1, targetArgb, queue, tail, queued); // Up
        }
    }

    /**
     * Adds a pixel to the queue if it is on the canvas, not already queued,
     * and part of the area being filled.
     *
     * @return the queue's new tail
     */
    private int enqueueIfMatching(int x, int y, int targetArgb, int[] queue, int tail, boolean[] queued) {
        if (!isInside(x, y)) {
            return tail;
        }
        int index = y * canvasWidth + x;
        if (queued[index] || pixelReader.getArgb(x, y) != targetArgb) {
            return tail;
        }
        queued[index] = true;
        queue[tail] = index;
        return tail + 1;
    }

    private boolean isInside(int x, int y) {
        return x >= 0 && x < canvasWidth && y >= 0 && y < canvasHeight;
    }

    /**
     * Converts a color to the packed 32-bit ARGB format used by
     * {@link PixelReader#getArgb} and {@link PixelWriter#setArgb}.
     */
    private static int toArgb(Color color) {
        return (int) Math.round(color.getOpacity() * 255) << 24
                | (int) Math.round(color.getRed() * 255) << 16
                | (int) Math.round(color.getGreen() * 255) << 8
                | (int) Math.round(color.getBlue() * 255);
    }
}
