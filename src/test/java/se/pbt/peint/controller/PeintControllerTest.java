package se.pbt.peint.controller;

import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ToolBar;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.pbt.peint.FxTestSupport;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeintControllerTest {

    private static final long TIMEOUT_SECONDS = 10;

    private PeintController controller;
    private BorderPane rootPane;
    private ToolBar toolBar;

    // Headers passed to showError(), which would otherwise open a blocking dialog.
    private final List<String> reportedErrors = new CopyOnWriteArrayList<>();
    private final CountDownLatch errorReported = new CountDownLatch(1);

    @TempDir
    Path tempDir;

    @BeforeAll
    static void initJfx() {
        FxTestSupport.initToolkit();
    }

    @BeforeEach
    void setUp() {
        FxTestSupport.runAndWait(() -> {
            controller = new PeintController() {
                @Override
                void showError(String header, String detail) {
                    reportedErrors.add(header);
                    errorReported.countDown();
                }
            };
            controller.canvas = new Canvas(50, 50);
            controller.rootPane = new BorderPane();
            controller.canvasContainer = new Pane();
            controller.topContainer = new VBox();
            controller.toolBar = new ToolBar();
            controller.colorPicker = new ColorPicker();
            controller.toggleToolbarPositionButton = new Button();

            controller.rootPane.resize(200, 200);
            controller.topContainer.getChildren().add(controller.toolBar);

            // Laid out like peint-view.fxml, in a scene so key events reach
            // scene-level filters.
            controller.canvasContainer.getChildren().add(controller.canvas);
            controller.rootPane.setCenter(controller.canvasContainer);
            new Scene(controller.rootPane);

            rootPane = controller.rootPane;
            toolBar = controller.toolBar;

            controller.initialize();
        });
    }

    @Test
    void initializeSetsDefaultColorToBlack() {
        assertEquals(Color.BLACK, controller.colorPicker.getValue());
    }

    @Test
    void initializePassesTheInitialColorToTheCanvas() {
        assertEquals(Color.BLACK, controller.canvasManager.getDrawingColor());
    }

    @Test
    void choosingAColorInThePickerSetsTheDrawingColor() {
        FxTestSupport.runAndWait(() -> {
            controller.colorPicker.setValue(Color.RED);
            controller.colorPicker.fireEvent(new ActionEvent());

            assertEquals(Color.RED, controller.canvasManager.getDrawingColor());
        });
    }

    @Test
    void onToggleToolbarPositionMovesTheToolbarToTheLeft() {
        FxTestSupport.runAndWait(() -> controller.onToggleToolbarPosition());

        assertSame(toolBar, rootPane.getLeft());
    }

    @Test
    void onBucketFillArmsAClickHandlerOnTheCanvas() {
        FxTestSupport.runAndWait(() -> controller.onBucketFill());

        assertNotNull(controller.canvas.getOnMouseClicked());
    }

    @Test
    void onBucketFillShowsACrosshairCursor() {
        FxTestSupport.runAndWait(() -> controller.onBucketFill());

        assertEquals(Cursor.CROSSHAIR, controller.canvas.getCursor());
    }

    @Test
    void fillingRestoresTheCursorAndDrawingMode() {
        FxTestSupport.runAndWait(() -> {
            controller.onBucketFill();

            fireMouse(MouseEvent.MOUSE_CLICKED, 10, 10);

            assertEquals(Cursor.DEFAULT, controller.canvas.getCursor());
            assertNull(controller.canvas.getOnMouseClicked(), "the fill should only apply once");
            assertNotNull(controller.canvas.getOnMousePressed(), "drawing should be enabled again");
        });
    }

    @Test
    void escapeCancelsTheArmedFill() {
        FxTestSupport.runAndWait(() -> {
            controller.onBucketFill();

            pressKey(KeyCode.ESCAPE);

            assertEquals(Cursor.DEFAULT, controller.canvas.getCursor());
            assertNull(controller.canvas.getOnMouseClicked(), "a click after Escape shouldn't fill");
            assertNotNull(controller.canvas.getOnMousePressed(), "drawing should be enabled again");
        });
    }

    @Test
    void escapeDoesNothingOnceTheFillIsDone() {
        FxTestSupport.runAndWait(() -> {
            controller.onBucketFill();
            fireMouse(MouseEvent.MOUSE_CLICKED, 10, 10);
            controller.canvas.setCursor(Cursor.HAND);

            pressKey(KeyCode.ESCAPE);

            assertEquals(Cursor.HAND, controller.canvas.getCursor(), "the Escape filter should have been removed");
        });
    }

    @Test
    void otherKeysDoNotCancelTheArmedFill() {
        FxTestSupport.runAndWait(() -> {
            controller.onBucketFill();

            pressKey(KeyCode.A);

            assertEquals(Cursor.CROSSHAIR, controller.canvas.getCursor());
            assertNotNull(controller.canvas.getOnMouseClicked());
        });
    }

    @Test
    void defaultExtensionTakesTheFiltersFirstExtension() {
        FileChooser.ExtensionFilter jpeg = new FileChooser.ExtensionFilter("JPEG Files", "*.jpg", "*.jpeg");

        assertEquals("jpg", PeintController.defaultExtension(jpeg));
    }

    @Test
    void openFilterAcceptsEveryReadableFormat() {
        assertEquals(
                List.of("*.png", "*.jpg", "*.jpeg", "*.jfif", "*.bmp", "*.gif"),
                PeintController.openFilter().getExtensions());
    }

    @Test
    void loadImageOpensAGif() throws Exception {
        File file = tempDir.resolve("blue.gif").toFile();
        ImageIO.write(blueImage(10, 10), "gif", file);

        FxTestSupport.runAndWait(() -> controller.loadImage(file));

        awaitCondition(() -> Color.BLUE.equals(canvasPixel(5, 5)), "the GIF should be drawn onto the canvas");
        assertTrue(reportedErrors.isEmpty(), "opening a GIF shouldn't report an error");
    }

    @Test
    void loadImageOpensAJfifFile() throws Exception {
        // A .jfif file is a JPEG under another name; JPEG colors are approximate.
        File file = tempDir.resolve("blue.jfif").toFile();
        ImageIO.write(blueImage(10, 10), "jpg", file);

        FxTestSupport.runAndWait(() -> controller.loadImage(file));

        awaitCondition(() -> canvasPixel(5, 5).getBlue() > 0.9 && canvasPixel(5, 5).getRed() < 0.1,
                "the JFIF file should be drawn onto the canvas");
        assertTrue(reportedErrors.isEmpty(), "opening a JFIF file shouldn't report an error");
    }

    @Test
    void defaultExtensionFallsBackToPngWithoutAFilter() {
        assertEquals("png", PeintController.defaultExtension(null));
    }

    @Test
    void saveImageWritesTheCanvasToTheFile() throws Exception {
        File file = tempDir.resolve("drawing.png").toFile();

        FxTestSupport.runAndWait(() -> controller.saveImage(file, "png"));

        BufferedImage saved = awaitReadableImage(file);
        assertEquals(50, saved.getWidth());
        assertTrue(reportedErrors.isEmpty(), "a successful save shouldn't report an error");
    }

    @Test
    void saveImageReportsAFailedSave() throws InterruptedException {
        File file = tempDir.resolve("missing-folder").resolve("drawing.png").toFile();

        FxTestSupport.runAndWait(() -> controller.saveImage(file, "png"));

        assertErrorReported("Could not save drawing.png");
    }

    @Test
    void loadImageDrawsTheFileOntoTheCanvas() throws Exception {
        File file = tempDir.resolve("blue.png").toFile();
        ImageIO.write(blueImage(10, 10), "png", file);

        FxTestSupport.runAndWait(() -> controller.loadImage(file));

        awaitCondition(() -> Color.BLUE.equals(canvasPixel(5, 5)), "the image should be drawn onto the canvas");
        assertTrue(reportedErrors.isEmpty(), "a successful load shouldn't report an error");
    }

    @Test
    void loadImageReportsAFileThatIsNotAnImage() throws Exception {
        File file = tempDir.resolve("notes.txt").toFile();
        Files.writeString(file.toPath(), "not an image");

        FxTestSupport.runAndWait(() -> controller.loadImage(file));

        assertErrorReported("Could not open notes.txt");
    }

    @Test
    void loadImageReportsAMissingFile() throws InterruptedException {
        File file = tempDir.resolve("missing.png").toFile();

        FxTestSupport.runAndWait(() -> controller.loadImage(file));

        assertErrorReported("Could not open missing.png");
    }

    private static BufferedImage blueImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(java.awt.Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    private void fireMouse(EventType<MouseEvent> type, double x, double y) {
        Event.fireEvent(controller.canvas, new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, true, null));
    }

    private void pressKey(KeyCode code) {
        Event.fireEvent(controller.canvas,
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    private void assertErrorReported(String expectedHeader) throws InterruptedException {
        assertTrue(errorReported.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "an error should have been reported");
        assertEquals(List.of(expectedHeader), reportedErrors);
    }

    /**
     * Waits for the background save to finish writing a readable image.
     */
    private static BufferedImage awaitReadableImage(File file) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (file.exists()) {
                try {
                    BufferedImage image = ImageIO.read(file);
                    if (image != null) {
                        return image;
                    }
                } catch (IOException stillBeingWritten) {
                    // Retry until the write completes.
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("no readable image was written to " + file);
    }

    private static void awaitCondition(BooleanSupplier condition, String message) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError(message);
    }

    /**
     * Reads one pixel of the controller's canvas, on the FX Application Thread.
     */
    private Color canvasPixel(int x, int y) {
        AtomicReference<Color> color = new AtomicReference<>();
        FxTestSupport.runAndWait(() -> {
            WritableImage snapshot = new WritableImage((int) controller.canvas.getWidth(), (int) controller.canvas.getHeight());
            controller.canvas.snapshot(null, snapshot);
            color.set(snapshot.getPixelReader().getColor(x, y));
        });
        return color.get();
    }
}
