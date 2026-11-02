package se.pbt.peint.canvas;

/**
 * Draws freehand strokes in the drawing color.
 *
 * <p>Pressing the mouse starts a new {@link Stroke}, each drag continues it,
 * and releasing the mouse records it for undo.</p>
 */
public final class BrushTool implements Tool {

    /** The width, in pixels, the brush starts out with. */
    public static final double DEFAULT_WIDTH = 1;

    private final Stroke stroke = new Stroke();
    private double width = DEFAULT_WIDTH;

    /**
     * @return the width, in pixels, of the brush's strokes
     */
    public double getWidth() {
        return width;
    }

    /**
     * Sets the width, in pixels, of the brush's strokes from the next stroke on.
     */
    public void setWidth(double width) {
        this.width = width;
    }

    @Override
    public void pressed(CanvasManager canvas, double x, double y) {
        stroke.start(canvas, x, y);
    }

    @Override
    public void dragged(CanvasManager canvas, double x, double y) {
        stroke.continueTo(canvas, canvas.getDrawingColor(), width, x, y);
    }

    @Override
    public void released(CanvasManager canvas, double x, double y) {
        stroke.finish(canvas);
    }
}
