package se.pbt.peint.controller;

import javafx.geometry.Dimension2D;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.pbt.peint.FxTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class EraserCursorTest {

    @BeforeAll
    static void initJfx() {
        FxTestSupport.initToolkit();
    }

    @Test
    void theCircleIsDrawnAtItsTrueSizeInTheMiddleOfTheImage() {
        FxTestSupport.runAndWait(() -> {
            // A 14 px circle in a 32x32 image, as on Windows.
            WritableImage image = EraserCursor.circle(32, 32, 14);

            assertEquals(Color.BLACK, image.getPixelReader().getColor(16, 9), "outer edge of the circle, 7 px above the center");
            assertEquals(Color.WHITE, image.getPixelReader().getColor(16, 10), "the white ring just inside it");
            assertEquals(Color.TRANSPARENT, image.getPixelReader().getColor(16, 16), "the inside is see-through");
            assertEquals(Color.TRANSPARENT, image.getPixelReader().getColor(16, 7), "nothing outside the circle");
            assertEquals(Color.TRANSPARENT, image.getPixelReader().getColor(0, 0));
        });
    }

    @Test
    void anEraserThatFitsInACursorGetsACircleCursorCenteredOnTheMouse() {
        FxTestSupport.runAndWait(() -> {
            Cursor cursor = EraserCursor.forWidth(12);

            ImageCursor circle = assertInstanceOf(ImageCursor.class, cursor);
            Dimension2D best = ImageCursor.getBestSize(14, 14);
            assertEquals(best.getWidth(), circle.getImage().getWidth(), "the image should be the size the system uses");
            assertEquals(best.getWidth() / 2, circle.getHotspotX());
            assertEquals(best.getHeight() / 2, circle.getHotspotY());
        });
    }

    @Test
    void anEraserTooWideForACursorGetsACrosshair() {
        FxTestSupport.runAndWait(() -> {
            // Wider than any system's cursors.
            assertEquals(Cursor.CROSSHAIR, EraserCursor.forWidth(2000));
        });
    }
}
