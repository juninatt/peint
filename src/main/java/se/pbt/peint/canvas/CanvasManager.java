package se.pbt.peint.canvas;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.awt.image.BufferedImage;

/**
 * Manages the canvas, providing functionality for drawing, undo/redo, and flood fill operations.
 *
 * <p>This class encapsulates all logic related to canvas manipulation and state management.</p>
 */
public final class CanvasManager {

    private final Canvas canvas;
    private final GraphicsContext gc;
    private final History history = new History();

    private Color drawingColor;

    // The mouse position the current stroke's next segment starts from.
    private double lastX;
    private double lastY;

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
                growCanvas(newWidth.doubleValue(), parent.getHeight()));
        parent.heightProperty().addListener((observable, oldHeight, newHeight) ->
                growCanvas(parent.getWidth(), newHeight.doubleValue()));

        // The canvas can be larger than its parent after the window shrinks,
        // so clip it to the parent rather than let it paint over the toolbar.
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(parent.widthProperty());
        clip.heightProperty().bind(parent.heightProperty());
        parent.setClip(clip);

        enableDrawingMode();
    }

    /**
     * Grows the canvas to at least the given size, e.g. to cover the parent
     * layout, preserving its current drawing in the process. The canvas
     * never shrinks.
     *
     * <p>Changing a {@link Canvas}'s width or height clears its pixel content, so
     * simply binding the size properties (as this class used to) erases the
     * drawing every time the window is resized. This method instead snapshots
     * the current content first, applies the new size, and redraws the
     * snapshot at the top-left corner, so existing drawings survive a resize
     * instead of being wiped out.</p>
     *
     * <p>Shrinking the canvas along with the window would permanently cut off
     * everything outside the smaller size, so the canvas keeps its largest
     * size and the parent's clip hides whatever no longer fits.</p>
     *
     * @param minWidth the width the canvas should at least have
     * @param minHeight the height the canvas should at least have
     */
    private void growCanvas(double minWidth, double minHeight) {
        double newWidth = Math.max(canvas.getWidth(), minWidth);
        double newHeight = Math.max(canvas.getHeight(), minHeight);
        if (newWidth <= 0 || newHeight <= 0
                || (newWidth == canvas.getWidth() && newHeight == canvas.getHeight())) {
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
     * <p>Pressing the mouse starts a new stroke, and each drag event draws one
     * segment from the previous mouse position to the current one. Drawing
     * only the new segment, rather than extending and re-stroking a single
     * path, keeps long strokes fast and stops their antialiased edges from
     * being drawn over and over, which made them thicker and darker.
     * Safe to call repeatedly, e.g. to restore drawing mode after another tool
     * (such as bucket fill) has temporarily taken over the canvas's mouse handlers.</p>
     */
    public void enableDrawingMode() {
        canvas.setOnMousePressed(event -> {
            saveState();
            lastX = event.getX();
            lastY = event.getY();
        });

        canvas.setOnMouseDragged(event -> {
            gc.setStroke(drawingColor);
            gc.strokeLine(lastX, lastY, event.getX(), event.getY());
            lastX = event.getX();
            lastY = event.getY();
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

        gc.drawImage(history.undo(snapshotCanvas()), 0, 0);
    }

    /**
     * Redoes the last undone action on the canvas by reapplying the next
     * state recorded in the {@link History}.
     */
    public void redo() {
        if (!history.canRedo()) {
            return;
        }

        gc.drawImage(history.redo(snapshotCanvas()), 0, 0);
    }

    /**
     * Clears all drawings from the canvas.
     */
    public void clearCanvas() {
        saveState();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    /**
     * Loads an image onto the canvas, growing the canvas first if the image
     * is larger, so no part of the image is cut off.
     *
     * <p>The canvas is grown before its state is recorded for undo, so the
     * recorded state covers the whole grown canvas. Recorded the other way
     * around, undoing would restore a state smaller than the canvas and
     * leave the part of the image outside it in place.</p>
     */
    public void loadImage(BufferedImage image) {
        growCanvas(image.getWidth(), image.getHeight());
        saveState();
        gc.drawImage(SwingFXUtils.toFXImage(image, null), 0, 0);
    }

    /**
     * Fills an area on the canvas with a new color using {@link FloodFillProcessor}.
     *
     * <p>Takes a single snapshot of the canvas, used both as the flood fill's
     * pixel source and as the state recorded for undo.</p>
     */
    public void bucketFill(double startX, double startY, Color newColor) {
        WritableImage snapshot = snapshotCanvas();

        PixelReader pixelReader = snapshot.getPixelReader();
        PixelWriter pixelWriter = gc.getPixelWriter();

        history.record(snapshot);

        FloodFillProcessor floodFillProcessor = new FloodFillProcessor(
                pixelReader, pixelWriter, (int) canvas.getWidth(), (int) canvas.getHeight()
        );
        floodFillProcessor.fill(startX, startY, newColor);
    }

    /**
     * Records the current state of the canvas in the {@link History} so it
     * can later be undone.
     */
    private void saveState() {
        history.record(snapshotCanvas());
    }

    /**
     * Takes a snapshot of the canvas at its current size.
     */
    private WritableImage snapshotCanvas() {
        WritableImage snapshot = new WritableImage((int) canvas.getWidth(), (int) canvas.getHeight());
        canvas.snapshot(null, snapshot);
        return snapshot;
    }

    /**
     * Sets the drawing color for the canvas.
     *
     * @param color the new drawing color
     */
    public void setDrawingColor(Color color) {
        this.drawingColor = color;
    }

    /**
     * @return the color strokes are currently drawn in
     */
    public Color getDrawingColor() {
        return drawingColor;
    }
}