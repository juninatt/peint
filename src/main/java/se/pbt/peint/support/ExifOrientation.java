package se.pbt.peint.support;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Reads the EXIF orientation of a JPEG file and applies it to the decoded image.
 *
 * <p>Cameras and phones usually store photos the way the sensor captured
 * them and record in the EXIF metadata how they should be turned for display.
 * {@link javax.imageio.ImageIO} ignores that metadata, so a photo taken in
 * portrait mode would otherwise open lying on its side.</p>
 *
 * <p>Only the single orientation tag is read, by walking the JPEG's segments
 * by hand. Libraries such as metadata-extractor would do this too, but they
 * are not proper Java modules, which the {@code javafx:jlink} release build
 * requires.</p>
 *
 * <p>Orientation values follow the EXIF specification: 1 is upright, 2-4
 * are mirrored or turned upside down, and 5-8 are turned a quarter, which
 * swaps the image's width and height.</p>
 */
public final class ExifOrientation {

    /** The orientation of an image that needs no turning. */
    public static final int NORMAL = 1;

    private static final int MARKER_SOI = 0xD8;
    private static final int MARKER_APP1 = 0xE1;
    private static final int MARKER_SOS = 0xDA;
    private static final int MARKER_EOI = 0xD9;
    private static final int TAG_ORIENTATION = 0x0112;
    private static final byte[] EXIF_HEADER = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);

    private ExifOrientation() {
    }

    /**
     * Reads the EXIF orientation of the given file.
     *
     * @param file the file to inspect
     * @return the orientation (1-8), or {@link #NORMAL} if the file is not a
     *         JPEG, has no orientation, or its metadata cannot be read
     */
    public static int read(File file) {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            return read(new DataInputStream(in));
        } catch (IOException | RuntimeException unreadable) {
            // Missing or malformed metadata just means the image is shown as stored.
            return NORMAL;
        }
    }

    /**
     * Walks the JPEG segments up to the image data, looking for the EXIF
     * (APP1) segment.
     */
    private static int read(DataInputStream in) throws IOException {
        if (in.readUnsignedByte() != 0xFF || in.readUnsignedByte() != MARKER_SOI) {
            return NORMAL;
        }
        while (true) {
            if (in.readUnsignedByte() != 0xFF) {
                return NORMAL;
            }
            int marker = in.readUnsignedByte();
            while (marker == 0xFF) {
                marker = in.readUnsignedByte(); // fill bytes before a marker
            }
            if (marker == MARKER_SOS || marker == MARKER_EOI) {
                return NORMAL; // the image data starts; metadata comes before it
            }

            int length = in.readUnsignedShort() - 2;
            if (length < 0) {
                return NORMAL;
            }
            if (marker == MARKER_APP1) {
                byte[] segment = new byte[length];
                in.readFully(segment);
                if (startsWith(segment, EXIF_HEADER)) {
                    return readTiffOrientation(segment, EXIF_HEADER.length);
                }
            } else if (in.skipBytes(length) < length) {
                throw new EOFException();
            }
        }
    }

    /**
     * Reads the orientation tag from the first image directory (IFD0) of the
     * TIFF structure that EXIF data is stored in.
     */
    private static int readTiffOrientation(byte[] segment, int tiffStart) {
        ByteBuffer tiff = ByteBuffer.wrap(segment, tiffStart, segment.length - tiffStart).slice();
        short byteOrder = tiff.getShort(0);
        if (byteOrder == 0x4949) {         // "II": little-endian
            tiff.order(ByteOrder.LITTLE_ENDIAN);
        } else if (byteOrder == 0x4D4D) {  // "MM": big-endian
            tiff.order(ByteOrder.BIG_ENDIAN);
        } else {
            return NORMAL;
        }
        if (tiff.getShort(2) != 42) {
            return NORMAL;
        }

        int directory = tiff.getInt(4);
        int entryCount = Short.toUnsignedInt(tiff.getShort(directory));
        for (int i = 0; i < entryCount; i++) {
            int entry = directory + 2 + i * 12;
            if (Short.toUnsignedInt(tiff.getShort(entry)) == TAG_ORIENTATION) {
                int orientation = Short.toUnsignedInt(tiff.getShort(entry + 8));
                return orientation >= 1 && orientation <= 8 ? orientation : NORMAL;
            }
        }
        return NORMAL;
    }

    /**
     * Returns the image turned and/or mirrored so it displays upright for
     * the given EXIF orientation.
     *
     * @param image the image as stored in the file
     * @param orientation the EXIF orientation (1-8)
     * @return the upright image, or the given image itself for {@link #NORMAL}
     *         or an unknown orientation
     */
    public static BufferedImage apply(BufferedImage image, int orientation) {
        if (orientation < 2 || orientation > 8) {
            return image;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        boolean swapsSides = orientation >= 5;
        int newWidth = swapsSides ? height : width;
        int newHeight = swapsSides ? width : height;

        int[] source = image.getRGB(0, 0, width, height, null, 0, width);
        int[] target = new int[source.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int targetX;
                int targetY;
                switch (orientation) {
                    case 2 -> { targetX = width - 1 - x; targetY = y; }               // mirrored
                    case 3 -> { targetX = width - 1 - x; targetY = height - 1 - y; }  // upside down
                    case 4 -> { targetX = x; targetY = height - 1 - y; }              // mirrored, upside down
                    case 5 -> { targetX = y; targetY = x; }                           // mirrored, turned
                    case 6 -> { targetX = height - 1 - y; targetY = x; }              // turned clockwise
                    case 7 -> { targetX = height - 1 - y; targetY = width - 1 - x; }  // mirrored, turned
                    default -> { targetX = y; targetY = width - 1 - x; }              // 8: turned counterclockwise
                }
                target[targetY * newWidth + targetX] = source[y * width + x];
            }
        }

        BufferedImage upright = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        upright.setRGB(0, 0, newWidth, newHeight, target, 0, newWidth);
        return upright;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
