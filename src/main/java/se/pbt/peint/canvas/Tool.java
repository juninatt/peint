package se.pbt.peint.canvas;

/**
 * A drawing tool: what the mouse does on the canvas.
 *
 * <p>{@link CanvasManager} forwards the canvas's mouse events to whichever
 * tool is selected. Each method does nothing by default, so a tool only
 * implements the events it uses. The interface is sealed, so code that
 * handles tools (such as picking a cursor for each) can switch over every
 * one of them and be checked by the compiler when a tool is added.</p>
 */
public sealed interface Tool permits BrushTool, FillTool {

    /**
     * Called when the mouse is pressed on the canvas.
     */
    default void pressed(CanvasManager canvas, double x, double y) {
    }

    /**
     * Called when the mouse is dragged across the canvas.
     */
    default void dragged(CanvasManager canvas, double x, double y) {
    }

    /**
     * Called when the mouse is released after being pressed on the canvas.
     */
    default void released(CanvasManager canvas, double x, double y) {
    }

    /**
     * Called when the canvas is clicked.
     */
    default void clicked(CanvasManager canvas, double x, double y) {
    }
}
