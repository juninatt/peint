package se.pbt.peint.svg;

import javafx.scene.paint.Paint;
import javafx.scene.shape.StrokeLineCap;

/**
 * How an SVG shape is painted: its fill and outline, how see-through it is,
 * and where it is moved to by {@code translate} transforms.
 *
 * @param fill the fill, a color or gradient, or {@code null} for none
 * @param stroke the outline's paint, or {@code null} for none
 * @param strokeWidth the outline's width, in the image's coordinates
 * @param lineCap how the ends of open outlines are drawn
 * @param opacity how opaque the whole shape is, from 0 (invisible) to 1
 * @param offsetX how far {@code translate} transforms move the shape to the right
 * @param offsetY how far {@code translate} transforms move the shape down
 */
public record Appearance(Paint fill, Paint stroke, double strokeWidth, StrokeLineCap lineCap,
                         double opacity, double offsetX, double offsetY) {
}
