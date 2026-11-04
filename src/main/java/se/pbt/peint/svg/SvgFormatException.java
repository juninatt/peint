package se.pbt.peint.svg;

import java.io.IOException;

/**
 * The text is not an SVG image Peint can read.
 */
public class SvgFormatException extends IOException {

    public SvgFormatException(String message) {
        super(message);
    }

    public SvgFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
