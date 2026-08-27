package se.pbt.peint.controller;

import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
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
import se.pbt.peint.canvas.CanvasManager;
import se.pbt.peint.support.BackgroundTasks;
import se.pbt.peint.support.ImageFileService;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * The main controller for the Peint application.
 *
 * <p>Wires the FXML view to {@link CanvasManager}, {@link ToolbarManager},
 * and {@link ImageFileService}, and handles the UI events those managers
 * don't own directly (file dialogs, background tasks, the color picker).</p>
 */
public class PeintController {

    @FXML
    Canvas canvas;

    @FXML
    BorderPane rootPane;

    @FXML
    Pane canvasContainer;

    @FXML
    VBox topContainer;

    @FXML
    ToolBar toolBar;

    @FXML
    ColorPicker colorPicker;

    @FXML
    Button toggleToolbarPositionButton;

    CanvasManager canvasManager;
    ToolbarManager toolbarManager;
    final ImageFileService imageFileService = new ImageFileService();

    /**
     * Initializes the controller and sets up managers for canvas and toolbar interactions.
     */
    public void initialize() {
        canvasManager = new CanvasManager(canvas, canvasContainer);
        toolbarManager = new ToolbarManager(toolBar, topContainer, rootPane, colorPicker, toggleToolbarPositionButton);

        colorPicker.setValue(Color.BLACK);
        colorPicker.setOnAction(event -> canvasManager.setDrawingColor(colorPicker.getValue()));
    }

    /**
     * Prompts for a destination file and saves the canvas to it as a PNG, JPEG, or BMP.
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

        // Snapshotting the canvas must happen on the FX Application Thread;
        // only the disk write below is moved to a background thread.
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
     * Prompts for an image file and loads it onto the canvas.
     */
    @FXML
    void onLoad() {
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
                canvasManager.loadImage(image);
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
     * Arms the bucket fill tool: disables freehand drawing and fills the
     * clicked area on the next canvas click, then restores the pencil.
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
     * Removes the fill tool's one-shot click handler and re-enables freehand
     * drawing, once a fill has completed.
     */
    void resetCanvasEvents() {
        canvas.setOnMouseClicked(null);
        canvasManager.enableDrawingMode();
    }
}