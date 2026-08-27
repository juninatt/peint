package se.pbt.peint.controller;

import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ToolBar;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import se.pbt.peint.ToolbarManager;
import se.pbt.peint.canvas.CanvasManager;
import se.pbt.peint.support.BackgroundTasks;
import se.pbt.peint.support.ImageFileService;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * The main controller for the Peint application.
 *
 * <p>This class handles the application's user interface events, such as saving, loading,
 * and editing the canvas, as well as managing the toolbar and color picker.</p>
 */
public class PeintController {

    @FXML
    Canvas canvas;

    @FXML
    BorderPane rootPane;

    @FXML
    VBox topContainer;

    @FXML
    ToolBar toolBar;

    @FXML
    ColorPicker colorPicker;

    CanvasManager canvasManager;
    ToolbarManager toolbarManager;
    final ImageFileService imageFileService = new ImageFileService();

    /**
     * Initializes the controller and sets up managers for canvas and toolbar interactions.
     */
    public void initialize() {
        canvasManager = new CanvasManager(canvas, rootPane);
        toolbarManager = new ToolbarManager(toolBar, topContainer, rootPane);

        colorPicker.setValue(Color.BLACK);
        colorPicker.setOnAction(event -> canvasManager.setDrawingColor(colorPicker.getValue()));
    }

    /**
     * Handles the "Save" action, allowing the user to save the canvas as an image.
     *
     * <p>Displays a file chooser with filters for common formats (PNG, JPEG, BMP),
     * ensuring compatibility and ease of selection for the user.</p>
     */
    @FXML
    void onSave() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Canvas As Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG Files", "*.png"),
                new FileChooser.ExtensionFilter("JPEG Files", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("BMP Files", "*.bmp")
        );

        File saveFile = fileChooser.showSaveDialog(canvas.getScene().getWindow());
        if (saveFile == null) {
            return;
        }

        String fileExtension = imageFileService.getFileExtension(saveFile.getName());
        if (fileExtension == null) {
            System.err.println("Unsupported file extension");
            return;
        }

        // Capture the canvas content while still on the FX Application Thread,
        // since Canvas can only be snapshotted there. Only the actual disk
        // write is moved to a background thread below.
        WritableImage writableImage = new WritableImage((int) canvas.getWidth(), (int) canvas.getHeight());
        canvas.snapshot(null, writableImage);
        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(writableImage, null);

        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() throws IOException {
                imageFileService.save(saveFile, bufferedImage, fileExtension);
                return null;
            }
        };
        saveTask.setOnFailed(event ->
                System.err.println("Failed to save image: " + saveTask.getException().getMessage()));

        BackgroundTasks.run(saveTask, "peint-image-save");
    }

    /**
     * Handles the "Load" action, allowing the user to load an image onto the canvas.
     */
    @FXML
    void onLoad() {
        // Open FileChooser for image selection
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Image File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.bmp")
        );

        File selectedFile = fileChooser.showOpenDialog(canvas.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }

        Task<BufferedImage> loadTask = new Task<>() {
            @Override
            protected BufferedImage call() throws IOException {
                return imageFileService.load(selectedFile);
            }
        };
        loadTask.setOnSucceeded(event -> {
            BufferedImage image = loadTask.getValue();
            if (image != null) {
                canvasManager.loadImage(image); // Draw the image on the canvas
            } else {
                System.err.println("Invalid image file: " + selectedFile.getName());
            }
        });
        loadTask.setOnFailed(event ->
                System.err.println("Failed to load image: " + loadTask.getException().getMessage()));

        BackgroundTasks.run(loadTask, "peint-image-load");
    }

    /**
     * Undoes the last action on the canvas.
     */
    @FXML
    void onUndo() {
        canvasManager.undo();
    }

    /**
     * Redoes the last undone action on the canvas.
     */
    @FXML
    void onRedo() {
        canvasManager.redo();
    }

    /**
     * Activates the bucket fill tool, allowing the user to fill an area of the canvas with a color.
     *
     * <p>Freehand drawing is temporarily disabled while the fill tool is armed, so that a
     * press-and-drag doesn't also draw a stray line. The tool fires once: after the next
     * click on the canvas, it fills the clicked area and then hands control back to the
     * pencil by re-enabling drawing mode and removing its own click handler.</p>
     */
    @FXML
    void onBucketFill() {
        canvasManager.disableDrawingMode();

        canvas.setOnMouseClicked(event -> {
            double x = event.getX();
            double y = event.getY();
            canvasManager.bucketFill(x, y, colorPicker.getValue());
            resetCanvasEvents();
        });
    }

    /**
     * Toggles the toolbar's position between the top and left of the window.
     */
    @FXML
    void onToggleToolbarPosition() {
        toolbarManager.togglePosition();
    }

    /**
     * Clears the canvas by removing all drawings.
     */
    @FXML
    void onClearCanvas() {
        canvasManager.clearCanvas();
    }

    /**
     * Restores the canvas to its default state after a one-shot tool (such as bucket
     * fill) has finished: removes that tool's click handler and re-enables freehand
     * drawing so the pencil works again.
     */
    void resetCanvasEvents() {
        canvas.setOnMouseClicked(null);
        canvasManager.enableDrawingMode();
    }
}