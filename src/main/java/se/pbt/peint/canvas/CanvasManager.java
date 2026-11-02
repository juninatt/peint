package se.pbt.peint.canvas;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Rectangle2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
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

    /**
     * The color of the empty canvas, which erasing and clearing paint with.
     */
    public static final Color BACKGROUND = Color.WHITE;

    private final Canvas canvas;
    private final GraphicsContext gc;
    private final History history = new History();

    private Color drawingColor;

    // The mouse position the current stroke's next segment starts from.
    private double lastX;
    private double lastY;

    // The area the current stroke has painted in so far, or null before its
    // first segment.
    private Rectangle2D strokeBounds;

    // The canvas as it was when the current edit began; see beginEdit().
    private WritableImage editBefore;

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

        fillBackground(0, 0, canvas.getWidth(), canvas.getHeight());
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

            fillBackground(0, 0, newWidth, newHeight);
            gc.drawImage(snapshot, 0, 0);
        } else {
            canvas.setWidth(newWidth);
            canvas.setHeight(newHeight);
            fillBackground(0, 0, newWidth, newHeight);
        }
    }

    /**
     * Paints the given area with the {@link #BACKGROUND} color.
     *
     * <p>The canvas is opaque rather than transparent, so that erasing can
     * paint with the background color, and so the canvas looks the same as
     * the images it is saved to, which are always opaque.</p>
     */
    private void fillBackground(double x, double y, double width, double height) {
        gc.setFill(BACKGROUND);
        gc.fillRect(x, y, width, height);
    }

    /**
     * Enables freehand drawing on the canvas.
     *
     * <p>Pressing the mouse starts a new stroke, and each drag event draws one
     * segment from the previous mouse position to the current one. Drawing
     * only the new segment, rather than extending and re-stroking a single
     * path, keeps long strokes fast and stops their antialiased edges from
     * being drawn over and over, which made them thicker and darker.
     * Releasing the mouse records the stroke for undo, covering only the
     * rectangle it was drawn in.
     * Safe to call repeatedly, e.g. to restore drawing mode after another tool
     * (such as bucket fill) has temporarily taken over the canvas's mouse handlers.</p>
     */
    public void enableDrawingMode() {
        canvas.setOnMousePressed(event -> {
            beginEdit();
            lastX = event.getX();
            lastY = event.getY();
            strokeBounds = null;
        });

        canvas.setOnMouseDragged(event -> {
            gc.setStroke(drawingColor);
            gc.strokeLine(lastX, lastY, event.getX(), event.getY());
            strokeBounds = union(strokeBounds, segmentBounds(lastX, lastY, event.getX(), event.getY()));
            lastX = event.getX();
            lastY = event.getY();
        });

        canvas.setOnMouseReleased(event -> commitEdit(strokeBounds));
    }

    /**
     * Disables freehand drawing on the canvas by removing its mouse handlers.
     *
     * <p>Used while another tool (such as bucket fill) is temporarily armed and
     * waiting for a click, so that a press-and-drag doesn't also draw a stray line.</p>
     */
    public void disableDrawingMode() {
        canvas.setOnMousePressed(null);
        canvas.setOnMouseDragged(null);
        canvas.setOnMouseReleased(null);
    }

    /**
     * Undoes the last action on the canvas, by drawing the changed rectangle
     * back the way it was before.
     */
    public void undo() {
        Edit edit = history.undo();
        if (edit != null) {
            gc.drawImage(edit.before(), edit.x(), edit.y());
        }
    }

    /**
     * Redoes the last undone action on the canvas, by drawing the changed
     * rectangle back the way it was after.
     */
    public void redo() {
        Edit edit = history.redo();
        if (edit != null) {
            gc.drawImage(edit.after(), edit.x(), edit.y());
        }
    }

    /**
     * Clears all drawings from the canvas.
     */
    public void clearCanvas() {
        beginEdit();
        fillBackground(0, 0, canvas.getWidth(), canvas.getHeight());
        commitEdit(new Rectangle2D(0, 0, canvas.getWidth(), canvas.getHeight()));
    }

    /**
     * Loads an image onto the canvas, growing the canvas first if the image
     * is larger, so no part of the image is cut off.
     *
     * <p>The canvas is grown before the edit begins, so the edit's before
     * image covers the area the loaded image is drawn over in full.</p>
     */
    public void loadImage(BufferedImage image) {
        growCanvas(image.getWidth(), image.getHeight());
        beginEdit();
        gc.drawImage(SwingFXUtils.toFXImage(image, null), 0, 0);
        commitEdit(new Rectangle2D(0, 0, image.getWidth(), image.getHeight()));
    }

    /**
     * Fills an area on the canvas with a new color using {@link FloodFillProcessor}.
     *
     * <p>The snapshot taken to begin the edit doubles as the flood fill's
     * pixel source, and only the bounds of the filled pixels are recorded.</p>
     */
    public void bucketFill(double startX, double startY, Color newColor) {
        WritableImage before = beginEdit();

        FloodFillProcessor floodFillProcessor = new FloodFillProcessor(
                before.getPixelReader(), gc.getPixelWriter(), (int) before.getWidth(), (int) before.getHeight()
        );
        commitEdit(floodFillProcessor.fill(startX, startY, newColor));
    }

    /**
     * Starts an undoable change, by taking a snapshot of the canvas as it is
     * before the change. {@link #commitEdit(Rectangle2D)} then records the
     * part of it that changed.
     *
     * @return the snapshot of the canvas before the change
     */
    private WritableImage beginEdit() {
        editBefore = snapshotCanvas();
        return editBefore;
    }

    /**
     * Finishes the change started by {@link #beginEdit()}, recording the
     * given rectangle as it was before and is now.
     *
     * @param changed the rectangle the change touched, or {@code null} if
     *                nothing changed, in which case nothing is recorded
     */
    private void commitEdit(Rectangle2D changed) {
        WritableImage before = editBefore;
        editBefore = null;
        if (before == null || changed == null) {
            return;
        }

        // Whole pixels covering the changed rectangle, within the canvas.
        int x = Math.max(0, (int) Math.floor(changed.getMinX()));
        int y = Math.max(0, (int) Math.floor(changed.getMinY()));
        int maxX = Math.min((int) before.getWidth(), (int) Math.ceil(changed.getMaxX()));
        int maxY = Math.min((int) before.getHeight(), (int) Math.ceil(changed.getMaxY()));
        if (maxX <= x || maxY <= y) {
            return;
        }
        int width = maxX - x;
        int height = maxY - y;

        WritableImage beforeRegion = new WritableImage(before.getPixelReader(), x, y, width, height);
        history.record(new Edit(x, y, beforeRegion, snapshotRegion(x, y, width, height)));
    }

    /**
     * Returns the rectangle a stroke segment can paint in: the segment's
     * bounding box, widened by the line width and a pixel of antialiasing.
     */
    private Rectangle2D segmentBounds(double x1, double y1, double x2, double y2) {
        double margin = gc.getLineWidth() / 2 + 1;
        double minX = Math.min(x1, x2) - margin;
        double minY = Math.min(y1, y2) - margin;
        return new Rectangle2D(minX, minY, Math.abs(x2 - x1) + 2 * margin, Math.abs(y2 - y1) + 2 * margin);
    }

    private static Rectangle2D union(Rectangle2D a, Rectangle2D b) {
        if (a == null) {
            return b;
        }
        double minX = Math.min(a.getMinX(), b.getMinX());
        double minY = Math.min(a.getMinY(), b.getMinY());
        double maxX = Math.max(a.getMaxX(), b.getMaxX());
        double maxY = Math.max(a.getMaxY(), b.getMaxY());
        return new Rectangle2D(minX, minY, maxX - minX, maxY - minY);
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
     * Takes a snapshot of one rectangle of the canvas.
     */
    private WritableImage snapshotRegion(int x, int y, int width, int height) {
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setViewport(new Rectangle2D(x, y, width, height));
        WritableImage snapshot = new WritableImage(width, height);
        canvas.snapshot(parameters, snapshot);
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
     * @return the undo/redo history, for inspecting what edits record
     */
    History history() {
        return history;
    }

    /**
     * @return the color strokes are currently drawn in
     */
    public Color getDrawingColor() {
        return drawingColor;
    }
}