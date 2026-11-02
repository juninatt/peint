package se.pbt.peint.controller;

import javafx.geometry.Dimension2D;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/**
 * Creates the eraser's cursor: a circle as wide as the eraser, so it shows
 * exactly what a stroke will erase.
 *
 * <p>The circle is drawn as a black ring with a white ring inside it, so it
 * stays visible on both dark and light parts of the drawing.</p>
 */
final class EraserCursor {

    private EraserCursor() {
    }

    /**
     * Returns a cursor showing the area an eraser of the given width covers.
     *
     * <p>Operating systems only support certain cursor sizes (Windows: just
     * 32x32 pixels) and scale any other cursor image to one of them, which
     * would draw the circle larger or smaller than the eraser. So the image
     * is made the size the system asks for, with the circle drawn at its true
     * size in the middle. When the eraser is too wide to fit, a crosshair is
     * used instead.</p>
     *
     * @param width the eraser's width, in pixels
     */
    static Cursor forWidth(double width) {
        int diameter = (int) Math.ceil(width) + 2; // room for the outline around the circle
        Dimension2D best = ImageCursor.getBestSize(diameter, diameter);
        int imageWidth = (int) best.getWidth();
        int imageHeight = (int) best.getHeight();
        if (imageWidth < diameter || imageHeight < diameter) {
            return Cursor.CROSSHAIR;
        }
        return new ImageCursor(circle(imageWidth, imageHeight, diameter), imageWidth / 2.0, imageHeight / 2.0);
    }

    /**
     * Draws a ring of the given diameter in the middle of an image of the
     * given size: black on the outside, white just inside it, transparent
     * elsewhere.
     */
    static WritableImage circle(int imageWidth, int imageHeight, int diameter) {
        WritableImage image = new WritableImage(imageWidth, imageHeight);
        PixelWriter writer = image.getPixelWriter();
        double centerX = imageWidth / 2.0;
        double centerY = imageHeight / 2.0;
        double outer = diameter / 2.0;
        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                double distance = Math.hypot(x + 0.5 - centerX, y + 0.5 - centerY);
                if (distance <= outer && distance > outer - 1) {
                    writer.setArgb(x, y, 0xFF000000);
                } else if (distance <= outer - 1 && distance > outer - 2) {
                    writer.setArgb(x, y, 0xFFFFFFFF);
                }
            }
        }
        return image;
    }
}
