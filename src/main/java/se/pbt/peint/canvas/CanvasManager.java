package se.pbt.peint;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import se.pbt.peint.canvas.FloodFillProcessor;
import se.pbt.peint.canvas.History;

import java.awt.image.BufferedImage;

/**
 * Manages the canvas, providing functionality for drawing, undo/redo, and flood fill operations.
 *
 * <p>This class encapsulates all logic related to canvas manipulation and state management.</p>
 */
public class CanvasManager {

    private final Canvas canvas;
    private final GraphicsContext gc;
    private final History history = new History();

    private Color drawingColor;

    /**
     * Creates a new CanvasManager to manage the specified canvas and its parent layout.
     *
     * @param canvas the canvas to manage
     * @param parent the parent layout containing the canvas
     */
    public CanvasManager(Canvas canvas, Region parent) {
        this.canvas = canvas;
        this.gc = canvas.getGraphicsContext2D();

        parent.widthProperty().addListener((observable, oldWidth, newWidth) ->
                resizeCanvas(newWidth.doubleValue(), canvas.getHeight()));
        parent.heightProperty().addListener((observable, oldHeight, newHeight) ->
                resizeCanvas(canvas.getWidth(), newHeight.doubleValue()));

        enableDrawingMode();
    }

    /**
     * Resizes the canvas to match the parent layout, preserving its current
     * drawing in the process.
     *
     * <p>Changing a {@link Canvas}'s width or height clears its pixel content, so
     * simply binding the size properties (as this class used to) erases the
     * drawing every time the window is resized. This method instead snapshots
     * the current content first, applies the new size, and redraws the
     * snapshot at the top-left corner, so existing drawings survive a resize
     * instead of being wiped out.</p>
     *
     * @param newWidth the new canvas width
     * @param newHeight the new canvas height
     */
    private void resizeCanvas(double newWidth, double newHeight) {
        if (newWidth <= 0 || newHeight <= 0) {
            return;
        }

        double previousWidth = canvas.getWidth();
        double previousHeight = canvas.getHeight();

        if (previousWidth > 0 && previousHeight > 0) {
            WritableImage snapshot = new WritableImage((int) previousWidth, (int) previousHeight);
            canvas.snapshot(null, snapshot);

            canvas.setWidth(newWidth);
            canvas.setHeight(newHeight);

            gc.drawImage(snapshot, 0, 0);
        } else {
            canvas.setWidth(newWidth);
            canvas.setHeight(newHeight);
        }
    }

    /**
     * Enables freehand drawing on the canvas.
     *
     * <p>Pressing the mouse starts a new drawing path, and dragging continues the path.
     * Safe to call repeatedly, e.g. to restore drawing mode after another tool
     * (such as bucket fill) has temporarily taken over the canvas's mouse handlers.</p>
     */
    public void enableDrawingMode() {
        canvas.setOnMousePressed(event -> {
            saveState();
            gc.setStroke(drawingColor);
            gc.beginPath();
            gc.moveTo(event.getX(), event.getY());
            gc.stroke();
        });

        canvas.setOnMouseDragged(event -> {
            gc.setStroke(drawingColor);
            gc.lineTo(event.getX(), event.getY());
            gc.stroke();
        });
    }

    /**
     * Disables freehand drawing on the canvas by removing its press/drag handlers.
     *
     * <p>Used while another tool (such as bucket fill) is temporarily armed and
     * waiting for a click, so that a press-and-drag doesn't also draw a stray line.</p>
     */
    public void disableDrawingMode() {
        canvas.setOnMousePressed(null);
        canvas.setOnMouseDragged(null);
    }

    /**
     * Undoes the last action on the canvas by moving back to the previous
     * state recorded in the {@link History}.
     */
    public void undo() {
        if (!history.canUndo()) {
            return;
        }

        BufferedImage currentState = SwingFXUtils.fromFXImage(canvas.snapshot(null, null), null);
        BufferedImage previousState = history.undo(currentState);
        gc.drawImage(SwingFXUtils.toFXImage(previousState, null), 0, 0);
    }

    /**
     * Redoes the last undone action on the canvas by reapplying the next
     * state recorded in the {@link History}.
     */
    public void redo() {
        if (!history.canRedo()) {
            return;
        }

        BufferedImage currentState = SwingFXUtils.fromFXImage(canvas.snapshot(null, null), null);
        BufferedImage nextState = history.redo(currentState);
        gc.drawImage(SwingFXUtils.toFXImage(nextState, null), 0, 0);
    }

    /**
     * Clears all drawings from the canvas.
     */
    public void clearCanvas() {
        saveState();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    /**
     * Loads an image onto the canvas.
     */
    public void loadImage(BufferedImage image) {
        saveState();
        gc.drawImage(SwingFXUtils.toFXImage(image, null), 0, 0);
    }

    /**
     * Fills an area on the canvas with a new color using {@link FloodFillProcessor}.
     *
     * <p>This method captures the current canvas state as an image to access pixel data,
     * initializes tools for reading and writing pixels, and delegates the flood fill
     * operation to a dedicated processor.</p>
     */
    public void bucketFill(double startX, double startY, Color newColor) {
        WritableImage writableImage = new WritableImage((int) canvas.getWidth(), (int) canvas.getHeight());
        canvas.snapshot(null, writableImage);

        PixelReader pixelReader = writableImage.getPixelReader();
        PixelWriter pixelWriter = gc.getPixelWriter();

        FloodFillProcessor floodFillProcessor = new FloodFillProcessor(
                pixelReader, pixelWriter, (int) canvas.getWidth(), (int) canvas.getHeight()
        );
        saveState();
        floodFillProcessor.fill(startX, startY, newColor);
    }

    /**
     * Records the current state of the canvas in the {@link History} so it
     * can later be undone.
     */
    private void saveState() {
        WritableImage snapshot = new WritableImage((int) canvas.getWidth(), (int) canvas.getHeight());
        canvas.snapshot(null, snapshot);

        history.record(SwingFXUtils.fromFXImage(snapshot, null));
    }

    /**
     * Sets the drawing color for the canvas.
     *
     * @param color the new drawing color
     */
    public void setDrawingColor(Color color) {
        this.drawingColor = color;
    }
}