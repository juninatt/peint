package se.pbt.peint.canvas;

/**
 * Fills the clicked area with the drawing color. Stays selected, so each
 * click fills another area, until another tool is chosen.
 */
public record FillTool() implements Tool {

    @Override
    public void clicked(CanvasManager canvas, double x, double y) {
        canvas.bucketFill(x, y, canvas.getDrawingColor());
    }
}
