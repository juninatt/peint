package se.pbt.peint.controller;

import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.pbt.peint.FxTestSupport;
import se.pbt.peint.canvas.BrushTool;
import se.pbt.peint.canvas.EraserTool;
import se.pbt.peint.canvas.FillTool;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
            controller.toolGroup = new ToggleGroup();
            controller.brushButton = new ToggleButton();
            controller.eraserButton = new ToggleButton();
            controller.fillButton = new ToggleButton();
            controller.brushButton.setToggleGroup(controller.toolGroup);
            controller.eraserButton.setToggleGroup(controller.toolGroup);
            controller.fillButton.setToggleGroup(controller.toolGroup);
            controller.brushButton.setSelected(true);
            controller.widthSlider = new Slider(1, 50, 1);
            controller.widthLabel = new Label();
            controller.widthControls = new GridPane();
            controller.widthControls.addRow(0, controller.widthSlider, controller.widthLabel);
            controller.toleranceSlider = new Slider(0, 100, 0);
            controller.toleranceLabel = new Label();
            controller.toleranceControls = new GridPane();
            controller.toleranceControls.addRow(0, controller.toleranceSlider, controller.toleranceLabel);

            controller.rootPane.resize(200, 200);
            controller.topContainer.getChildren().add(controller.toolBar);

            // Laid out like peint-view.fxml, in a scene so key events reach
            // the scene's shortcuts.
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
    void theBrushIsSelectedAtFirst() {
        assertInstanceOf(BrushTool.class, controller.canvasManager.getTool());
        assertTrue(controller.brushButton.isSelected());
    }

    @Test
    void onBucketFillSelectsTheFillTool() {
        FxTestSupport.runAndWait(() -> controller.onBucketFill());

        assertInstanceOf(FillTool.class, controller.canvasManager.getTool());
        assertTrue(controller.fillButton.isSelected(), "the toolbar should show the fill tool as selected");
    }

    @Test
    void onBucketFillShowsACrosshairCursor() {
        FxTestSupport.runAndWait(() -> controller.onBucketFill());

        assertEquals(Cursor.CROSSHAIR, controller.canvas.getCursor());
    }

    @Test
    void theFillToolStaysSelectedAfterAFill() {
        FxTestSupport.runAndWait(() -> {
            controller.onBucketFill();

            fireMouse(MouseEvent.MOUSE_CLICKED, 10, 10);

            assertInstanceOf(FillTool.class, controller.canvasManager.getTool(), "the fill should stay selected");
            assertEquals(Cursor.CROSSHAIR, controller.canvas.getCursor());
        });
    }

    @Test
    void onBrushSelectsTheBrush() {
        FxTestSupport.runAndWait(() -> {
            controller.onBucketFill();

            controller.onBrush();

            assertInstanceOf(BrushTool.class, controller.canvasManager.getTool());
            assertTrue(controller.brushButton.isSelected());
            assertEquals(Cursor.DEFAULT, controller.canvas.getCursor());
        });
    }

    @Test
    void onEraserSelectsTheEraser() {
        FxTestSupport.runAndWait(() -> controller.onEraser());

        assertInstanceOf(EraserTool.class, controller.canvasManager.getTool());
        assertTrue(controller.eraserButton.isSelected());
    }

    @Test
    void escapeSelectsTheBrush() {
        FxTestSupport.runAndWait(() -> {
            controller.onBucketFill();

            pressKey(KeyCode.ESCAPE);

            assertInstanceOf(BrushTool.class, controller.canvasManager.getTool());
            assertEquals(Cursor.DEFAULT, controller.canvas.getCursor());
        });
    }

    @Test
    void clickingTheSelectedToolButtonKeepsItSelected() {
        FxTestSupport.runAndWait(() -> {
            controller.brushButton.setSelected(false); // what clicking a selected ToggleButton does

            assertTrue(controller.brushButton.isSelected(), "some tool should always be selected");
        });
    }

    @Test
    void theWidthControlsShowTheBrushWidthAtFirst() {
        assertTrue(controller.widthControls.isVisible());
        assertEquals(BrushTool.DEFAULT_WIDTH, controller.widthSlider.getValue());
        assertEquals("1 px", controller.widthLabel.getText());
    }

    @Test
    void movingTheWidthSliderSetsTheSelectedToolsWidth() {
        FxTestSupport.runAndWait(() -> {
            controller.widthSlider.setValue(8);

            assertEquals(8, controller.brushTool.getWidth());
            assertEquals("8 px", controller.widthLabel.getText());
        });
    }

    @Test
    void eachToolKeepsItsOwnWidth() {
        FxTestSupport.runAndWait(() -> {
            controller.widthSlider.setValue(4);

            controller.onEraser();
            assertEquals(EraserTool.DEFAULT_WIDTH, controller.widthSlider.getValue(), "the eraser should show its own width");
            controller.widthSlider.setValue(30);

            controller.onBrush();
            assertEquals(4, controller.widthSlider.getValue(), "the brush should keep the width it was given");
            assertEquals(4, controller.brushTool.getWidth());
            assertEquals(30, controller.eraserTool.getWidth());
        });
    }

    @Test
    void theWidthSliderSnapsToWholePixels() {
        FxTestSupport.runAndWait(() -> {
            controller.widthSlider.setValue(6.4);

            assertEquals(6, controller.brushTool.getWidth());
        });
    }

    @Test
    void theWidthControlsAreHiddenForTheFillTool() {
        FxTestSupport.runAndWait(() -> {
            controller.onBucketFill();

            assertFalse(controller.widthControls.isVisible());
            assertFalse(controller.widthControls.isManaged(), "hidden controls shouldn't take up toolbar space");
        });
    }

    @Test
    void theToleranceControlsAreShownOnlyForTheFillTool() {
        FxTestSupport.runAndWait(() -> {
            assertFalse(controller.toleranceControls.isVisible(), "hidden while the brush is selected");

            controller.onBucketFill();
            assertTrue(controller.toleranceControls.isVisible());

            controller.onEraser();
            assertFalse(controller.toleranceControls.isVisible());
        });
    }

    @Test
    void theToleranceSliderStartsAtTheDefaultTolerance() {
        assertEquals(39, Math.round(controller.toleranceSlider.getValue()), "100 of 255 is about 39 %");
        assertEquals("39 %", controller.toleranceLabel.getText());
    }

    @Test
    void movingTheToleranceSliderSetsTheFillTolerance() {
        FxTestSupport.runAndWait(() -> {
            controller.toleranceSlider.setValue(0);
            assertEquals(0, controller.fillTool.getTolerance());

            controller.toleranceSlider.setValue(100);
            assertEquals(255, controller.fillTool.getTolerance());
            assertEquals("100 %", controller.toleranceLabel.getText());
        });
    }

    @Test
    void tolerancePercentConvertsBothWays() {
        for (int tolerance = 0; tolerance <= 255; tolerance++) {
            assertEquals(tolerance, PeintController.fromPercent(PeintController.toPercent(tolerance)));
        }
    }

    @Test
    void everyToolHasACursor() {
        assertEquals(Cursor.DEFAULT, PeintController.cursorFor(new BrushTool()));
        assertEquals(Cursor.DEFAULT, PeintController.cursorFor(new EraserTool()));
        assertEquals(Cursor.CROSSHAIR, PeintController.cursorFor(new FillTool()));
    }

    @Test
    void otherKeysDoNotCancelTheArmedFill() {
        FxTestSupport.runAndWait(() -> {
            controller.onBucketFill();

            pressKey(KeyCode.A);

            assertEquals(Cursor.CROSSHAIR, controller.canvas.getCursor());
            assertInstanceOf(FillTool.class, controller.canvasManager.getTool());
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
