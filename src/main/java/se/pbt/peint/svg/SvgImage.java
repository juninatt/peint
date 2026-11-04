package se.pbt.peint.svg;

import java.util.List;

/**
 * The shapes of an SVG image, in painting order, and the area of the
 * image's coordinates they are drawn in (its {@code viewBox}).
 *
 * @param minX the left edge of the image's area
 * @param minY the top edge of the image's area
 * @param width the width of the image's area
 * @param height the height of the image's area
 * @param shapes the shapes, back to front
 */
public record SvgImage(double minX, double minY, double width, double height, List<SvgShape> shapes) {

    public SvgImage {
        shapes = List.copyOf(shapes);
    }
}
