package se.pbt.peint.support;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Reads and writes images to and from disk.
 *
 * <p>Wraps {@link ImageIO} so that file access is isolated from JavaFX and
 * from any threading concerns, making it simple to call from a background
 * thread (file I/O should never run on the JavaFX Application Thread) and
 * to test on its own.</p>
 */
public class ImageFileService {

    /**
     * Reads an image from the given file.
     *
     * @param file the file to read
     * @return the decoded image, or {@code null} if no registered reader
     *         understands the file's format
     * @throws IOException if the file cannot be read
     */
    public BufferedImage load(File file) throws IOException {
        return ImageIO.read(file);
    }

    /**
     * Writes an image to the given file in the specified format.
     *
     * <p>Formats without an alpha channel (JPEG, BMP) are given an opaque RGB
     * copy of the image, since ImageIO has no writer for ARGB images in those
     * formats and would otherwise silently write nothing.</p>
     *
     * @param file the file to write to
     * @param image the image to write
     * @param format the image format (e.g. {@code "png"}, {@code "jpg"})
     * @throws IOException if the image cannot be written, including when no
     *         registered writer supports the format
     */
    public void save(File file, BufferedImage image, String format) throws IOException {
        BufferedImage output = supportsAlpha(format) ? image : toRgb(image);
        if (!ImageIO.write(output, format, file)) {
            throw new IOException("No image writer available for format: " + format);
        }
    }

    /**
     * Extracts the file extension from a file name, so it can be used as an
     * image format for {@link #save(File, BufferedImage, String)}.
     *
     * @param fileName the file name to inspect
     * @return the lowercased extension without its leading dot, or
     *         {@code null} if the file name has none
     */
    public String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex > 0 && dotIndex < fileName.length() - 1) ? fileName.substring(dotIndex + 1).toLowerCase() : null;
    }

    private static boolean supportsAlpha(String format) {
        return !Set.of("jpg", "jpeg", "bmp").contains(format.toLowerCase());
    }

    /**
     * Copies the image onto an opaque white background, so transparent
     * areas come out white rather than black.
     */
    private static BufferedImage toRgb(BufferedImage image) {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }
}