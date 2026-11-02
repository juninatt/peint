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

    /** The width, in pixels, the eraser starts out with. */
    public static final double DEFAULT_WIDTH = 12;

    private final Stroke stroke = new Stroke();
    private double width = DEFAULT_WIDTH;

    /**
     * @return the width, in pixels, of the eraser's strokes
     */
    public double getWidth() {
        return width;
    }

    /**
     * Sets the width, in pixels, of the eraser's strokes from the next stroke on.
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
        stroke.continueTo(canvas, CanvasManager.BACKGROUND, width, x, y);
    }

    @Override
    public void released(CanvasManager canvas, double x, double y) {
        stroke.finish(canvas);
    }
}
