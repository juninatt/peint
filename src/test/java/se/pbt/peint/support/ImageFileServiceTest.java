package se.pbt.peint.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageFileServiceTest {

    private final ImageFileService service = new ImageFileService();

    @TempDir
    Path tempDir;

    @Test
    void savesAnArgbImageAsJpeg() throws IOException {
        File file = tempDir.resolve("image.jpg").toFile();

        service.save(file, argbImage(), "jpg");

        assertTrue(file.exists(), "an ARGB image should still be written as JPEG");
        assertNotNull(ImageIO.read(file));
    }

    @Test
    void savesAnArgbImageAsBmp() throws IOException {
        File file = tempDir.resolve("image.bmp").toFile();

        service.save(file, argbImage(), "bmp");

        assertNotNull(ImageIO.read(file));
    }

    @Test
    void transparentAreasBecomeWhiteInFormatsWithoutAlpha() throws IOException {
        File file = tempDir.resolve("image.bmp").toFile();

        service.save(file, argbImage(), "bmp");

        assertEquals(0xFFFFFF, ImageIO.read(file).getRGB(0, 0) & 0xFFFFFF);
    }

    @Test
    void savesAGifKeepingSolidColorsExactly() throws IOException {
        File file = tempDir.resolve("image.gif").toFile();

        service.save(file, ExifOrientationTest.halfRedHalfBlue(40, 20), "gif");

        BufferedImage saved = service.load(file);
        assertEquals(0xFFFF0000, saved.getRGB(5, 10), "a drawing's few colors fit GIF's palette exactly");
        assertEquals(0xFF0000FF, saved.getRGB(35, 10));
    }

    @Test
    void savesAGifOfAnImageWithMoreColorsThanItsPaletteHoldsApproximately() throws IOException {
        // 120 000 distinct colors, like a photo, for GIF's 256.
        BufferedImage gradient = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 300; y++) {
            for (int x = 0; x < 400; x++) {
                gradient.setRGB(x, y, 0xFF000000 | (x * 255 / 399) << 16 | (y * 255 / 299) << 8 | (x + y) * 255 / 698);
            }
        }
        File file = tempDir.resolve("gradient.gif").toFile();

        service.save(file, gradient, "gif");

        BufferedImage saved = service.load(file);
        long totalError = 0;
        for (int y = 0; y < 300; y++) {
            for (int x = 0; x < 400; x++) {
                totalError += channelDifference(gradient.getRGB(x, y), saved.getRGB(x, y));
            }
        }
        double averageError = totalError / (400.0 * 300);
        assertTrue(averageError < 16, "colors should be close, but were off by " + averageError + " on average");
    }

    /**
     * Returns the largest difference, 0-255, between two colors' red, green
     * and blue channels.
     */
    private static int channelDifference(int rgb, int otherRgb) {
        int difference = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            difference = Math.max(difference, Math.abs(((rgb >> shift) & 0xFF) - ((otherRgb >> shift) & 0xFF)));
        }
        return difference;
    }

    @Test
    void savingInAnUnsupportedFormatThrows() {
        File file = tempDir.resolve("image.txt").toFile();

        assertThrows(IOException.class, () -> service.save(file, argbImage(), "txt"));
    }

    @Test
    void loadTurnsAPhotoUprightByItsExifOrientation() throws IOException {
        // Stored 40x20 with red on the left; orientation 6 means "turn clockwise".
        File file = tempDir.resolve("portrait.jpg").toFile();
        Files.write(file.toPath(), ExifOrientationTest.jpegWithOrientation(6, ByteOrder.BIG_ENDIAN, false));

        BufferedImage loaded = service.load(file);

        assertEquals(20, loaded.getWidth());
        assertEquals(40, loaded.getHeight());
        assertTrue(isMostlyRed(loaded.getRGB(10, 5)), "the stored left side should now be at the top");
        assertTrue(isMostlyBlue(loaded.getRGB(10, 35)), "the stored right side should now be at the bottom");
    }

    @Test
    void loadLeavesAnImageWithoutOrientationAsStored() throws IOException {
        File file = tempDir.resolve("landscape.png").toFile();
        ImageIO.write(ExifOrientationTest.halfRedHalfBlue(40, 20), "png", file);

        BufferedImage loaded = service.load(file);

        assertEquals(40, loaded.getWidth());
        assertEquals(20, loaded.getHeight());
    }

    @Test
    void loadReadsACmykJpegInTheRightColors() throws IOException {
        // cmyk.jpg: 40x20, red on the left and blue on the right, stored as
        // CMYK the way print-oriented tools such as Photoshop save JPEGs.
        File file = tempDir.resolve("cmyk.jpg").toFile();
        try (InputStream in = ImageFileServiceTest.class.getResourceAsStream("cmyk.jpg")) {
            Files.copy(in, file.toPath());
        }

        BufferedImage loaded = service.load(file);

        assertNotNull(loaded, "a CMYK JPEG should be readable");
        assertTrue(isMostlyRed(loaded.getRGB(5, 10)), "left side should be red");
        assertTrue(isMostlyBlue(loaded.getRGB(35, 10)), "right side should be blue");
    }

    private static boolean isMostlyRed(int rgb) {
        return ((rgb >> 16) & 0xFF) > 200 && (rgb & 0xFF) < 60;
    }

    private static boolean isMostlyBlue(int rgb) {
        return (rgb & 0xFF) > 200 && ((rgb >> 16) & 0xFF) < 60;
    }

    private static BufferedImage argbImage() {
        return new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
    }
}
