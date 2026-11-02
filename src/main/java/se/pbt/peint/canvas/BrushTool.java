package se.pbt.peint.canvas;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;

/**
 * Draws freehand strokes in the drawing color.
 *
 * <p>Pressing the mouse starts a new stroke, and each drag event draws one
 * segment from the previous mouse position to the current one. Drawing only
 * the new segment, rather than extending and re-stroking a single path, keeps
 * long strokes fast and stops their antialiased edges from being drawn over
 * and over, which made them thicker and darker. Releasing the mouse records
 * the stroke for undo, covering only the rectangle it was drawn in.</p>
 */
public final class BrushTool implements Tool {

    // The mouse position the current stroke's next segment starts from.
    private double lastX;
    private double lastY;

    // The area the current stroke has painted in so far, or null before its
    // first segment.
    private Rectangle2D strokeBounds;

    @Override
    public void pressed(CanvasManager canvas, double x, double y) {
        canvas.beginEdit();
        lastX = x;
        lastY = y;
        strokeBounds = null;
    }

    @Override
    public void dragged(CanvasManager canvas, double x, double y) {
        GraphicsContext gc = canvas.graphics();
        gc.setStroke(canvas.getDrawingColor());
        gc.strokeLine(lastX, lastY, x, y);
        strokeBounds = union(strokeBounds, segmentBounds(gc, lastX, lastY, x, y));
        lastX = x;
        lastY = y;
    }

    @Override
    public void released(CanvasManager canvas, double x, double y) {
        canvas.commitEdit(strokeBounds);
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
