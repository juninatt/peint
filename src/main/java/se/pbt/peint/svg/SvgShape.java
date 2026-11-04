package se.pbt.peint.svg;

import java.util.List;

/**
 * One shape of an SVG image, in the image's own coordinates.
 *
 * <p>Sealed, so code that draws shapes can switch over every kind of shape
 * and be checked by the compiler when a kind is added.</p>
 */
public sealed interface SvgShape {

    /** @return how the shape is filled and outlined */
    Appearance appearance();

    /** {@code <rect>}, with optionally rounded corners. */
    record Rect(double x, double y, double width, double height, double cornerRadius,
                Appearance appearance) implements SvgShape {
    }

    /** {@code <circle>} and {@code <ellipse>}: an ellipse with a center and two radii. */
    record Ellipse(double centerX, double centerY, double radiusX, double radiusY,
                   Appearance appearance) implements SvgShape {
    }

    /** {@code <line>}: a straight line, which only has an outline. */
    record Line(double x1, double y1, double x2, double y2, Appearance appearance) implements SvgShape {
    }

    /**
     * {@code <polygon>} and {@code <polyline>}: points joined by straight
     * lines, closed back to the first point for a polygon.
     *
     * @param points x and y coordinates, alternating
     */
    record Polygon(List<Double> points, boolean closed, Appearance appearance) implements SvgShape {
    }

    /**
     * {@code <path>}: an outline described by SVG path data, such as
     * {@code M0 320 Q 150 360 300 320 Z}.
     */
    record Path(String data, Appearance appearance) implements SvgShape {
    }
}
