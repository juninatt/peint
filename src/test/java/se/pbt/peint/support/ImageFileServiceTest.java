package se.pbt.peint.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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

    private static BufferedImage argbImage() {
        return new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
    }
}
