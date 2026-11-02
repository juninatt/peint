package se.pbt.peint.controller;

import javafx.event.ActionEvent;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ToolBar;
import javafx.scene.image.WritableImage;
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
    void defaultExtensionTakesTheFiltersFirstExtension() {
        FileChooser.ExtensionFilter jpeg = new FileChooser.ExtensionFilter("JPEG Files", "*.jpg", "*.jpeg");

        assertEquals("jpg", PeintController.defaultExtension(jpeg));
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
        BufferedImage blue = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = blue.createGraphics();
        g.setColor(java.awt.Color.BLUE);
        g.fillRect(0, 0, 10, 10);
        g.dispose();
        ImageIO.write(blue, "png", file);

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
