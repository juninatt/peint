package se.pbt.peint.canvas;

import javafx.scene.image.Image;

/**
 * One undoable change to the canvas: the rectangle it touched, as it looked
 * before and after the change.
 *
 * <p>Only the changed rectangle is kept, rather than the whole canvas, so a
 * short stroke costs a few kilobytes even on a large canvas.</p>
 *
 * @param x the left edge of the changed rectangle, in canvas pixels
 * @param y the top edge of the changed rectangle, in canvas pixels
 * @param before the rectangle's pixels before the change, drawn back to undo it
 * @param after the rectangle's pixels after the change, drawn back to redo it
 */
record Edit(int x, int y, Image before, Image after) {

    /**
     * Estimates the memory this edit takes up: four bytes (ARGB) per pixel,
     * for both the before and the after image.
     */
    long bytes() {
        return 2L * (long) before.getWidth() * (long) before.getHeight() * 4;
    }
}
