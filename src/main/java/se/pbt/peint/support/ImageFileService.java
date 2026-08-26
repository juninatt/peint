package se.pbt.peint.support;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

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
     * @param file the file to write to
     * @param image the image to write
     * @param format the image format (e.g. {@code "png"}, {@code "jpg"})
     * @throws IOException if the image cannot be written
     */
    public void save(File file, BufferedImage image, String format) throws IOException {
        ImageIO.write(image, format, file);
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
}