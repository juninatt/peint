package se.pbt.peint.canvas;

/**
 * Draws freehand strokes in the drawing color.
 *
 * <p>Pressing the mouse starts a new {@link Stroke}, each drag continues it,
 * and releasing the mouse records it for undo.</p>
 */
public final class BrushTool implements Tool {

    /** The width, in pixels, of the brush's strokes. */
    static final double WIDTH = 1;

    private final Stroke stroke = new Stroke();

    @Override
    public void pressed(CanvasManager canvas, double x, double y) {
        stroke.start(canvas, x, y);
    }

    @Override
    public void dragged(CanvasManager canvas, double x, double y) {
        stroke.continueTo(canvas, canvas.getDrawingColor(), WIDTH, x, y);
    }

    @Override
    public void released(CanvasManager canvas, double x, double y) {
        stroke.finish(canvas);
    }
}
