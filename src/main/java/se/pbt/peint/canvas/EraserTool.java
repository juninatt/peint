package se.pbt.peint.canvas;

/**
 * Erases with freehand strokes, painting the canvas's
 * {@link CanvasManager#BACKGROUND background} color.
 *
 * <p>Works like {@link BrushTool}, but ignores the drawing color, and is
 * wider: strokes are antialiased, so a stroke only partly covers the pixels
 * along its edges, and a thin eraser would leave a faint trace of the line it
 * went over.</p>
 */
public final class EraserTool implements Tool {

    /** The width, in pixels, of the eraser's strokes. */
    static final double WIDTH = 12;

    private final Stroke stroke = new Stroke();

    @Override
    public void pressed(CanvasManager canvas, double x, double y) {
        stroke.start(canvas, x, y);
    }

    @Override
    public void dragged(CanvasManager canvas, double x, double y) {
        stroke.continueTo(canvas, CanvasManager.BACKGROUND, WIDTH, x, y);
    }

    @Override
    public void released(CanvasManager canvas, double x, double y) {
        stroke.finish(canvas);
    }
}
