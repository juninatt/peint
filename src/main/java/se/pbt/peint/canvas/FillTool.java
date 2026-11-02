package se.pbt.peint.canvas;

/**
 * Fills the clicked area with the drawing color.
 *
 * @param onFilled run after each fill, e.g. to switch back to the brush
 */
public record FillTool(Runnable onFilled) implements Tool {

    @Override
    public void clicked(CanvasManager canvas, double x, double y) {
        canvas.bucketFill(x, y, canvas.getDrawingColor());
        onFilled.run();
    }
}
