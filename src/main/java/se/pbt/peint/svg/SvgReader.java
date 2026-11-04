package se.pbt.peint.svg;

import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.StrokeLineCap;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the shapes of a simple SVG image: the subset a language model uses
 * when asked to draw with filled shapes.
 *
 * <p>Supported: {@code rect}, {@code circle}, {@code ellipse}, {@code line},
 * {@code polygon}, {@code polyline} and {@code path}, grouped in {@code g}
 * elements; fills and outlines as colors or linear and radial gradients;
 * {@code opacity}; and {@code translate} transforms. Presentation attributes
 * may also be given in a {@code style} attribute, and groups pass them on to
 * their children. Anything else, such as text, filters or other transforms,
 * is left out, so an unusual detail costs that detail rather than the whole
 * image.</p>
 *
 * <p>The image usually comes from a model, so the XML parser refuses
 * document type declarations and external entities: an image can't make
 * Peint read local files or fetch addresses (an XXE attack).</p>
 */
public final class SvgReader {

    // SVG's own default size, for an image with neither a viewBox nor a size.
    private static final double DEFAULT_WIDTH = 300;
    private static final double DEFAULT_HEIGHT = 150;

    // Elements whose content is never painted directly.
    private static final Set<String> NOT_PAINTED = Set.of(
            "defs", "clipPath", "mask", "symbol", "pattern", "marker", "title", "desc", "metadata",
            "style", "script", "text", "linearGradient", "radialGradient", "filter");

    private static final Pattern NUMBER = Pattern.compile("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");
    private static final Pattern TRANSLATE = Pattern.compile("translate\\(\\s*([^,)\\s]+)(?:[\\s,]+([^)\\s]+))?\\s*\\)");
    private static final Pattern URL = Pattern.compile("url\\(\\s*#([^)\\s]+)\\s*\\)");

    private final Map<String, Paint> gradients = new HashMap<>();
    private double width;
    private double height;

    private SvgReader() {
    }

    /**
     * Reads an SVG image.
     *
     * @param svg the SVG document, starting with {@code <svg}
     * @return the image's area and its shapes, in painting order
     * @throws SvgFormatException if the text is not well-formed XML, its root
     *                            element is not {@code svg}, or it declares a
     *                            document type
     */
    public static SvgImage read(String svg) throws SvgFormatException {
        return new SvgReader().readImage(svg);
    }

    private SvgImage readImage(String svg) throws SvgFormatException {
        Element root = parse(svg).getDocumentElement();
        if (!"svg".equals(name(root))) {
            throw new SvgFormatException("Not an SVG image: the root element is <" + name(root) + ">");
        }

        double minX = 0;
        double minY = 0;
        List<Double> viewBox = numbers(root.getAttribute("viewBox"));
        if (viewBox.size() == 4 && viewBox.get(2) > 0 && viewBox.get(3) > 0) {
            minX = viewBox.get(0);
            minY = viewBox.get(1);
            width = viewBox.get(2);
            height = viewBox.get(3);
        } else {
            width = positiveOr(length(root.getAttribute("width"), 0), DEFAULT_WIDTH);
            height = positiveOr(length(root.getAttribute("height"), 0), DEFAULT_HEIGHT);
        }

        collectGradients(root);
        List<SvgShape> shapes = new ArrayList<>();
        collectShapes(root, Inherited.DEFAULTS, shapes);
        return new SvgImage(minX, minY, width, height, shapes);
    }

    /**
     * Parses the XML with document type declarations, and so all entities,
     * refused.
     */
    private static Document parse(String svg) throws SvgFormatException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            // Report problems only as exceptions; the default handler also prints them on stderr.
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                }

                @Override
                public void error(SAXParseException e) throws SAXException {
                    throw e;
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXException {
                    throw e;
                }
            });
            return builder.parse(new InputSource(new StringReader(svg)));
        } catch (SAXException e) {
            throw new SvgFormatException("Not a readable SVG image: " + e.getMessage(), e);
        } catch (ParserConfigurationException | IOException e) {
            throw new SvgFormatException("Could not read the SVG image: " + e.getMessage(), e);
        }
    }

    // ---- Shapes ----------------------------------------------------------

    private void collectShapes(Element element, Inherited inherited, List<SvgShape> shapes) {
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element childElement) || NOT_PAINTED.contains(name(childElement))) {
                continue;
            }
            Inherited own = inherited.with(childElement, this);
            if (own.hidden()) {
                continue;
            }
            SvgShape shape = shape(childElement, own.appearance());
            if (shape != null) {
                shapes.add(shape);
            }
            collectShapes(childElement, own, shapes); // groups, and anything else with shapes inside
        }
    }

    private SvgShape shape(Element element, Appearance appearance) {
        return switch (name(element)) {
            case "rect" -> {
                double w = length(element.getAttribute("width"), width);
                double h = length(element.getAttribute("height"), height);
                double radius = Math.max(length(element.getAttribute("rx"), width), length(element.getAttribute("ry"), height));
                yield w > 0 && h > 0
                        ? new SvgShape.Rect(length(element.getAttribute("x"), width), length(element.getAttribute("y"), height),
                                w, h, radius, appearance)
                        : null;
            }
            case "circle" -> {
                double r = length(element.getAttribute("r"), Math.min(width, height));
                yield r > 0
                        ? new SvgShape.Ellipse(length(element.getAttribute("cx"), width), length(element.getAttribute("cy"), height),
                                r, r, appearance)
                        : null;
            }
            case "ellipse" -> {
                double rx = length(element.getAttribute("rx"), width);
                double ry = length(element.getAttribute("ry"), height);
                yield rx > 0 && ry > 0
                        ? new SvgShape.Ellipse(length(element.getAttribute("cx"), width), length(element.getAttribute("cy"), height),
                                rx, ry, appearance)
                        : null;
            }
            case "line" -> new SvgShape.Line(
                    length(element.getAttribute("x1"), width), length(element.getAttribute("y1"), height),
                    length(element.getAttribute("x2"), width), length(element.getAttribute("y2"), height), appearance);
            case "polygon", "polyline" -> {
                List<Double> points = numbers(element.getAttribute("points"));
                if (points.size() % 2 == 1) {
                    points = points.subList(0, points.size() - 1); // an unpaired coordinate is dropped
                }
                yield points.size() >= 4 ? new SvgShape.Polygon(points, name(element).equals("polygon"), appearance) : null;
            }
            case "path" -> {
                String data = element.getAttribute("d").strip();
                yield data.isEmpty() ? null : new SvgShape.Path(data, appearance);
            }
            default -> null;
        };
    }

    // ---- Paint -----------------------------------------------------------

    /**
     * Resolves a {@code fill} or {@code stroke} value.
     *
     * @return the paint; {@code null} for {@code none} or an unknown gradient;
     *         or {@code inherited} for a value that can't be read
     */
    Paint paint(String value, Paint inherited) {
        String text = value.strip();
        if (text.equals("none") || text.equals("transparent")) {
            return null;
        }
        Matcher url = URL.matcher(text);
        if (url.find()) {
            return gradients.get(url.group(1));
        }
        if (text.equals("currentColor")) {
            return Color.BLACK;
        }
        try {
            return Color.web(text);
        } catch (IllegalArgumentException unknownColor) {
            return inherited;
        }
    }

    private void collectGradients(Element element) {
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element childElement) {
                String id = childElement.getAttribute("id");
                if (!id.isEmpty() && name(childElement).equals("linearGradient")) {
                    gradients.put(id, linearGradient(childElement));
                } else if (!id.isEmpty() && name(childElement).equals("radialGradient")) {
                    gradients.put(id, radialGradient(childElement));
                }
                collectGradients(childElement);
            }
        }
    }

    private Paint linearGradient(Element gradient) {
        boolean proportional = !"userSpaceOnUse".equals(gradient.getAttribute("gradientUnits"));
        List<Stop> stops = stops(gradient);
        return new LinearGradient(
                coordinate(gradient.getAttribute("x1"), 0, proportional, width),
                coordinate(gradient.getAttribute("y1"), 0, proportional, height),
                coordinate(gradient.getAttribute("x2"), 1, proportional, width),
                coordinate(gradient.getAttribute("y2"), 0, proportional, height),
                proportional, CycleMethod.NO_CYCLE, stops);
    }

    private Paint radialGradient(Element gradient) {
        boolean proportional = !"userSpaceOnUse".equals(gradient.getAttribute("gradientUnits"));
        List<Stop> stops = stops(gradient);
        return new RadialGradient(0, 0,
                coordinate(gradient.getAttribute("cx"), 0.5, proportional, width),
                coordinate(gradient.getAttribute("cy"), 0.5, proportional, height),
                coordinate(gradient.getAttribute("r"), 0.5, proportional, Math.min(width, height)),
                proportional, CycleMethod.NO_CYCLE, stops);
    }

    private List<Stop> stops(Element gradient) {
        List<Stop> stops = new ArrayList<>();
        for (Node child = gradient.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element stop && name(stop).equals("stop")) {
                Map<String, String> style = style(stop);
                double offset = fraction(stop.getAttribute("offset"), 0);
                Paint color = paint(style.getOrDefault("stop-color", stop.getAttribute("stop-color")), Color.BLACK);
                double opacity = fraction(style.getOrDefault("stop-opacity", stop.getAttribute("stop-opacity")), 1);
                Color base = color instanceof Color c ? c : Color.BLACK;
                stops.add(new Stop(offset, base.deriveColor(0, 1, 1, opacity)));
            }
        }
        return stops.isEmpty() ? List.of(new Stop(0, Color.BLACK)) : stops;
    }

    /**
     * Reads a gradient coordinate: a fraction or percentage of the shape for
     * proportional gradients, an image coordinate otherwise.
     */
    private static double coordinate(String value, double fallback, boolean proportional, double size) {
        if (value.isBlank()) {
            return fallback;
        }
        return proportional ? fraction(value, fallback) : length(value, size);
    }

    // ---- Attributes ------------------------------------------------------

    /**
     * The presentation attributes a group passes on to its children, and
     * which a shape is painted with.
     */
    private record Inherited(Paint fill, Paint stroke, double strokeWidth, StrokeLineCap lineCap,
                             double opacity, double offsetX, double offsetY, boolean hidden) {

        // SVG's defaults: black fill, no outline, fully opaque.
        static final Inherited DEFAULTS = new Inherited(Color.BLACK, null, 1, StrokeLineCap.BUTT, 1, 0, 0, false);

        Inherited with(Element element, SvgReader reader) {
            Map<String, String> style = reader.style(element);
            Paint fill = has(style, "fill") ? reader.paint(style.get("fill"), this.fill) : this.fill;
            Paint stroke = has(style, "stroke") ? reader.paint(style.get("stroke"), this.stroke) : this.stroke;
            fill = withOpacity(fill, style.get("fill-opacity"));
            stroke = withOpacity(stroke, style.get("stroke-opacity"));
            double strokeWidth = has(style, "stroke-width") ? length(style.get("stroke-width"), 1) : this.strokeWidth;
            StrokeLineCap lineCap = has(style, "stroke-linecap") ? lineCap(style.get("stroke-linecap"), this.lineCap) : this.lineCap;
            double opacity = this.opacity * (has(style, "opacity") ? fraction(style.get("opacity"), 1) : 1);
            boolean hidden = "none".equals(style.get("display")) || "hidden".equals(style.get("visibility"));

            double offsetX = this.offsetX;
            double offsetY = this.offsetY;
            Matcher translate = TRANSLATE.matcher(element.getAttribute("transform"));
            while (translate.find()) {
                offsetX += number(translate.group(1), 0);
                offsetY += translate.group(2) == null ? 0 : number(translate.group(2), 0);
            }
            return new Inherited(fill, stroke, strokeWidth, lineCap, opacity, offsetX, offsetY, hidden);
        }

        Appearance appearance() {
            return new Appearance(fill, stroke, strokeWidth, lineCap, opacity, offsetX, offsetY);
        }

        private static boolean has(Map<String, String> style, String property) {
            return style.containsKey(property) && !style.get(property).isBlank();
        }

        private static Paint withOpacity(Paint paint, String opacity) {
            if (opacity == null || opacity.isBlank() || !(paint instanceof Color color)) {
                return paint;
            }
            return color.deriveColor(0, 1, 1, fraction(opacity, 1));
        }

        private static StrokeLineCap lineCap(String value, StrokeLineCap inherited) {
            return switch (value.strip()) {
                case "round" -> StrokeLineCap.ROUND;
                case "square" -> StrokeLineCap.SQUARE;
                case "butt" -> StrokeLineCap.BUTT;
                default -> inherited;
            };
        }
    }

    /**
     * Collects an element's presentation attributes, with those in its
     * {@code style} attribute taking precedence, as in SVG.
     */
    private Map<String, String> style(Element element) {
        Map<String, String> style = new HashMap<>();
        for (String property : List.of("fill", "stroke", "stroke-width", "stroke-linecap", "fill-opacity",
                "stroke-opacity", "opacity", "display", "visibility", "stop-color", "stop-opacity")) {
            if (element.hasAttribute(property)) {
                style.put(property, element.getAttribute(property).strip());
            }
        }
        for (String declaration : element.getAttribute("style").split(";")) {
            int colon = declaration.indexOf(':');
            if (colon > 0) {
                style.put(declaration.substring(0, colon).strip().toLowerCase(Locale.ROOT),
                        declaration.substring(colon + 1).strip());
            }
        }
        return style;
    }

    // ---- Numbers ---------------------------------------------------------

    /**
     * Reads a length such as {@code 120}, {@code 120px} or {@code 50%}, a
     * percentage being of the given size; 0 for a missing or unreadable value.
     */
    static double length(String value, double size) {
        String text = value == null ? "" : value.strip();
        if (text.endsWith("%")) {
            return number(text.substring(0, text.length() - 1), 0) / 100 * size;
        }
        return number(text, 0);
    }

    /**
     * Reads a fraction such as {@code 0.4} or {@code 40%}, kept within 0 and 1.
     */
    static double fraction(String value, double fallback) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            return fallback;
        }
        double fraction = text.endsWith("%") ? number(text.substring(0, text.length() - 1), fallback * 100) / 100
                : number(text, fallback);
        return Math.clamp(fraction, 0.0, 1.0);
    }

    /**
     * Reads the first number in the text, ignoring units such as {@code px}.
     */
    private static double number(String text, double fallback) {
        Matcher matcher = NUMBER.matcher(text == null ? "" : text);
        return matcher.find() ? Double.parseDouble(matcher.group()) : fallback;
    }

    /**
     * Reads every number in a list such as {@code "200,400 220,380"}.
     */
    private static List<Double> numbers(String text) {
        List<Double> numbers = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(text == null ? "" : text);
        while (matcher.find()) {
            numbers.add(Double.parseDouble(matcher.group()));
        }
        return numbers;
    }

    private static double positiveOr(double value, double fallback) {
        return value > 0 ? value : fallback;
    }

    private static String name(Element element) {
        return element.getLocalName() != null ? element.getLocalName() : element.getNodeName();
    }
}
