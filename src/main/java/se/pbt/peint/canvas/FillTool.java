package se.pbt.peint.canvas;

/**
 * Fills the clicked area with the drawing color. Stays selected, so each
 * click fills another area, until another tool is chosen.
 */
public final class FillTool implements Tool {

    private int tolerance = FloodFillProcessor.DEFAULT_TOLERANCE;

    /**
     * @return how far, per color channel (0-255), a pixel may differ from the
     *         clicked one and still be filled
     */
    public int getTolerance() {
        return tolerance;
    }

    /**
     * Sets how far, per color channel (0-255), a pixel may differ from the
     * clicked one and still be filled, from the next fill on.
     */
    public void setTolerance(int tolerance) {
        this.tolerance = tolerance;
    }

    @Override
    public void clicked(CanvasManager canvas, double x, double y) {
        canvas.bucketFill(x, y, canvas.getDrawingColor(), tolerance);
    }
}
