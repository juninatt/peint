package se.pbt.peint.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExifOrientationTest {

    @TempDir
    Path tempDir;

    @Test
    void readsTheOrientationFromBigEndianExif() throws IOException {
        File file = write("big.jpg", jpegWithOrientation(6, ByteOrder.BIG_ENDIAN, false));

        assertEquals(6, ExifOrientation.read(file));
    }

    @Test
    void readsTheOrientationFromLittleEndianExif() throws IOException {
        File file = write("little.jpg", jpegWithOrientation(8, ByteOrder.LITTLE_ENDIAN, false));

        assertEquals(8, ExifOrientation.read(file));
    }

    @Test
    void readsTheOrientationWhenExifComesAfterOtherSegments() throws IOException {
        File file = write("after-jfif.jpg", jpegWithOrientation(3, ByteOrder.BIG_ENDIAN, true));

        assertEquals(3, ExifOrientation.read(file));
    }

    @Test
    void aJpegWithoutExifIsNormal() throws IOException {
        File file = write("plain.jpg", encode(halfRedHalfBlue(40, 20), "jpg"));

        assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(file));
    }

    @Test
    void aPngIsNormal() throws IOException {
        File file = write("image.png", encode(halfRedHalfBlue(40, 20), "png"));

        assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(file));
    }

    @Test
    void aTruncatedFileIsNormal() throws IOException {
        byte[] jpeg = jpegWithOrientation(6, ByteOrder.BIG_ENDIAN, false);
        File file = write("truncated.jpg", Arrays.copyOf(jpeg, 12));

        assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(file));
    }

    @Test
    void aMissingFileIsNormal() {
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(tempDir.resolve("missing.jpg").toFile()));
    }

    @Test
    void normalOrientationReturnsTheImageUnchanged() {
        BufferedImage image = labelledImage();

        assertSame(image, ExifOrientation.apply(image, ExifOrientation.NORMAL));
    }

    @Test
    void appliesEveryOrientation() {
        // A 3x2 image whose pixels are numbered 0-5, row by row:
        //   0 1 2
        //   3 4 5
        // Each expected result is the upright image, row by row.
        assertTurnedInto(2, 3, 2, 1, 0, 5, 4, 3);       // mirrored
        assertTurnedInto(3, 3, 5, 4, 3, 2, 1, 0);       // upside down
        assertTurnedInto(4, 3, 3, 4, 5, 0, 1, 2);       // mirrored, upside down
        assertTurnedInto(5, 2, 0, 3, 1, 4, 2, 5);       // mirrored, turned
        assertTurnedInto(6, 2, 3, 0, 4, 1, 5, 2);       // turned clockwise
        assertTurnedInto(7, 2, 5, 2, 4, 1, 3, 0);       // mirrored, turned
        assertTurnedInto(8, 2, 2, 5, 1, 4, 0, 3);       // turned counterclockwise
    }

    private static void assertTurnedInto(int orientation, int expectedWidth, int... expectedPixels) {
        BufferedImage upright = ExifOrientation.apply(labelledImage(), orientation);

        assertEquals(expectedWidth, upright.getWidth(), "width for orientation " + orientation);
        assertEquals(6 / expectedWidth, upright.getHeight(), "height for orientation " + orientation);
        int[] labels = new int[6];
        for (int y = 0; y < upright.getHeight(); y++) {
            for (int x = 0; x < upright.getWidth(); x++) {
                labels[y * upright.getWidth() + x] = upright.getRGB(x, y) & 0xFF;
            }
        }
        assertEquals(Arrays.toString(expectedPixels), Arrays.toString(labels), "pixels for orientation " + orientation);
    }

    /**
     * A 3x2 image whose pixels carry their own index (0-5) in the blue channel.
     */
    private static BufferedImage labelledImage() {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 6; i++) {
            image.setRGB(i % 3, i / 3, 0xFF000000 | i);
        }
        return image;
    }

    private File write(String name, byte[] bytes) throws IOException {
        Path path = tempDir.resolve(name);
        Files.write(path, bytes);
        return path.toFile();
    }

    /**
     * Encodes a 40x20 image, red on the left and blue on the right, as a JPEG
     * carrying the given EXIF orientation.
     *
     * @param afterJfif whether to place the EXIF segment after ImageIO's own
     *                  JFIF segment rather than straight after the file header
     */
    static byte[] jpegWithOrientation(int orientation, ByteOrder order, boolean afterJfif) throws IOException {
        byte[] jpeg = encode(halfRedHalfBlue(40, 20), "jpg");
        byte[] app1 = exifSegment(orientation, order);

        // Right after the start-of-image marker, or after the APP0 segment that follows it.
        int insertAt = 2;
        if (afterJfif) {
            insertAt += 2 + (((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF));
        }

        byte[] result = new byte[jpeg.length + app1.length];
        System.arraycopy(jpeg, 0, result, 0, insertAt);
        System.arraycopy(app1, 0, result, insertAt, app1.length);
        System.arraycopy(jpeg, insertAt, result, insertAt + app1.length, jpeg.length - insertAt);
        return result;
    }

    /**
     * Builds an APP1 segment holding a minimal EXIF TIFF structure with a
     * single orientation entry.
     */
    private static byte[] exifSegment(int orientation, ByteOrder order) {
        ByteBuffer tiff = ByteBuffer.allocate(26).order(order);
        byte orderMark = order == ByteOrder.BIG_ENDIAN ? (byte) 'M' : (byte) 'I';
        tiff.put(orderMark).put(orderMark);
        tiff.putShort((short) 42);
        tiff.putInt(8);                   // offset of the first directory
        tiff.putShort((short) 1);         // one entry
        tiff.putShort((short) 0x0112);    // orientation tag
        tiff.putShort((short) 3);         // type: unsigned short
        tiff.putInt(1);                   // one value
        tiff.putShort((short) orientation);
        tiff.putShort((short) 0);         // padding of the four-byte value field
        tiff.putInt(0);                   // no next directory

        byte[] header = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        int length = 2 + header.length + tiff.capacity();
        ByteBuffer segment = ByteBuffer.allocate(2 + length);
        segment.put((byte) 0xFF).put((byte) 0xE1).putShort((short) length);
        segment.put(header).put(tiff.array());
        return segment.array();
    }

    static BufferedImage halfRedHalfBlue(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(Color.BLUE);
        g.fillRect(width / 2, 0, width - width / 2, height);
        g.dispose();
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }
}
