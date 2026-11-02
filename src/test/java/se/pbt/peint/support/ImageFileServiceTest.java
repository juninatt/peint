package se.pbt.peint.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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
