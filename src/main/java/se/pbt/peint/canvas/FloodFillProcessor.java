package se.pbt.peint.canvas;

import javafx.geometry.Rectangle2D;
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

    /**
     * The default for how far, per color channel (0-255), a pixel may differ
     * from the clicked pixel and still be filled.
     *
     * <p>Strokes are antialiased, so their edges fade through lighter shades
     * into the background. With an exact match those shades were left
     * unfilled, as a pale outline inside every filled shape. This tolerance
     * fills the lighter part of that fade and stops at the darker core of the
     * stroke. The trade-off is that areas of very similar colors are filled
     * as one.</p>
     */
    public static final int DEFAULT_TOLERANCE = 100;

    /** The largest tolerance, at which every pixel is close enough. */
    public static final int MAX_TOLERANCE = 255;

    private final PixelReader pixelReader;
    private final PixelWriter pixelWriter;
    private final int canvasWidth;
    private final int canvasHeight;
    private final int tolerance;

    /**
     * Initializes the processor with canvas dimensions and pixel data
     * handlers, using the {@link #DEFAULT_TOLERANCE}.
     */
    public FloodFillProcessor(PixelReader pixelReader, PixelWriter pixelWriter, int canvasWidth, int canvasHeight) {
        this(pixelReader, pixelWriter, canvasWidth, canvasHeight, DEFAULT_TOLERANCE);
    }

    /**
     * Initializes the processor with canvas dimensions, pixel data handlers,
     * and how far (0-255 per color channel) a pixel may differ from the
     * clicked one and still be filled.
     */
    public FloodFillProcessor(PixelReader pixelReader, PixelWriter pixelWriter, int canvasWidth, int canvasHeight,
                              int tolerance) {
        this.pixelReader = pixelReader;
        this.pixelWriter = pixelWriter;
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.tolerance = tolerance;
    }

    /**
     * Fills a contiguous area starting at the given coordinates with a new color.
     *
     * <p>The algorithm replaces all connected pixels within the tolerance of
     * the initial color with the specified new color.</p>
     *
     * <p>Each pixel is marked as visited when it is queued rather than when it
     * is filled, so no pixel enters the queue more than once. Queued pixels
     * are stored as their index ({@code y * width + x}) in a plain
     * {@code int} array, so even a fill covering the whole canvas allocates
     * just one array of {@code width * height} ints.</p>
     *
     * @return the bounds of the pixels that were filled, or {@code null} if
     *         none were, e.g. because the area already had the new color
     */
    public Rectangle2D fill(double startX, double startY, Color newColor) {
        int x0 = (int) startX;
        int y0 = (int) startY;
        if (!isInside(x0, y0)) {
            return null;
        }

        int targetArgb = pixelReader.getArgb(x0, y0);
        int newArgb = toArgb(newColor);
        if (newArgb == targetArgb) {
            return null;
        }

        int minX = x0;
        int minY = y0;
        int maxX = x0;
        int maxY = y0;

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
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);

            tail = enqueueIfMatching(x + 1, y, targetArgb, queue, tail, queued); // Right
            tail = enqueueIfMatching(x - 1, y, targetArgb, queue, tail, queued); // Left
            tail = enqueueIfMatching(x, y + 1, targetArgb, queue, tail, queued); // Down
            tail = enqueueIfMatching(x, y - 1, targetArgb, queue, tail, queued); // Up
        }

        return new Rectangle2D(minX, minY, maxX - minX + 1, maxY - minY + 1);
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
        if (queued[index] || !isCloseTo(pixelReader.getArgb(x, y), targetArgb)) {
            return tail;
        }
        queued[index] = true;
        queue[tail] = index;
        return tail + 1;
    }

    /**
     * Checks whether two ARGB colors differ by at most the tolerance in every
     * channel, alpha included.
     */
    private boolean isCloseTo(int argb, int targetArgb) {
        for (int shift = 0; shift <= 24; shift += 8) {
            int channel = (argb >>> shift) & 0xFF;
            int targetChannel = (targetArgb >>> shift) & 0xFF;
            if (Math.abs(channel - targetChannel) > tolerance) {
                return false;
            }
        }
        return true;
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
