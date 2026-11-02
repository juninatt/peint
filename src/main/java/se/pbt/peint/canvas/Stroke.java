package se.pbt.peint.canvas;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;

/**
 * A freehand stroke in progress, shared by the tools that draw one
 * ({@link BrushTool} and {@link EraserTool}).
 *
 * <p>Each drag draws one segment from the previous mouse position to the
 * current one. Drawing only the new segment, rather than extending and
 * re-stroking a single path, keeps long strokes fast and stops their
 * antialiased edges from being drawn over and over, which made them thicker
 * and darker. The stroke also tracks the rectangle it has painted in, so
 * only that rectangle is recorded for undo.</p>
 */
final class Stroke {

    // The mouse position the next segment starts from.
    private double lastX;
    private double lastY;

    // The area painted in so far, or null before the first segment.
    private Rectangle2D bounds;

    /**
     * Starts a new stroke at the given point, beginning an undoable edit.
     */
    void start(CanvasManager canvas, double x, double y) {
        canvas.beginEdit();
        lastX = x;
        lastY = y;
        bounds = null;
    }

    /**
     * Draws the segment from the previous point to the given one.
     *
     * <p>Segments have round ends, so that wide strokes join up smoothly
     * where they bend instead of showing notches between segments.</p>
     */
    void continueTo(CanvasManager canvas, Color color, double width, double x, double y) {
        GraphicsContext gc = canvas.graphics();
        gc.setStroke(color);
        gc.setLineWidth(width);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.strokeLine(lastX, lastY, x, y);
        bounds = union(bounds, segmentBounds(gc, lastX, lastY, x, y));
        lastX = x;
        lastY = y;
    }

    /**
     * Ends the stroke, recording the rectangle it was drawn in for undo.
     */
    void finish(CanvasManager canvas) {
        canvas.commitEdit(bounds);
    }

    /**
     * Returns the rectangle a stroke segment can paint in: the segment's
     * bounding box, widened by the line width and a pixel of antialiasing.
     */
    private static Rectangle2D segmentBounds(GraphicsContext gc, double x1, double y1, double x2, double y2) {
        double margin = gc.getLineWidth() / 2 + 1;
        double minX = Math.min(x1, x2) - margin;
        double minY = Math.min(y1, y2) - margin;
        return new Rectangle2D(minX, minY, Math.abs(x2 - x1) + 2 * margin, Math.abs(y2 - y1) + 2 * margin);
    }

    private static Rectangle2D union(Rectangle2D a, Rectangle2D b) {
        if (a == null) {
            return b;
        }
        double minX = Math.min(a.getMinX(), b.getMinX());
        double minY = Math.min(a.getMinY(), b.getMinY());
        double maxX = Math.max(a.getMaxX(), b.getMaxX());
        double maxY = Math.max(a.getMaxY(), b.getMaxY());
        return new Rectangle2D(minX, minY, maxX - minX, maxY - minY);
    }
}
