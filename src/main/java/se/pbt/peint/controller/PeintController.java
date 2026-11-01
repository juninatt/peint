package se.pbt.peint.controller;

import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The main controller for the Peint application.
 *
 * <p>Wires the FXML view to {@link CanvasManager}, {@link ToolbarManager},
 * and {@link ImageFileService}, and handles the UI events those managers
 * don't own directly (file dialogs, background tasks, the color picker).</p>
 */
public class PeintController {

    private static final Logger LOGGER = Logger.getLogger(PeintController.class.getName());

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

        File chosenFile = fileChooser.showSaveDialog(canvas.getScene().getWindow());
        if (chosenFile == null) {
            return;
        }

        // No extension typed: take it from the filter the user picked, as
        // the native dialog doesn't add one on every platform.
        String typedExtension = imageFileService.getFileExtension(chosenFile.getName());
        String format = typedExtension != null
                ? typedExtension
                : defaultExtension(fileChooser.getSelectedExtensionFilter());
        File targetFile = typedExtension != null
                ? chosenFile
                : new File(chosenFile.getParentFile(), chosenFile.getName() + "." + format);

        saveImage(targetFile, format);
    }

    /**
     * Saves the canvas to the given file in the given format on a background
     * thread, reporting any failure with {@link #showError(String, String)}.
     */
    void saveImage(File targetFile, String format) {
        // Snapshotting the canvas must happen on the FX Application Thread;
        // only the disk write below is moved to a background thread.
        WritableImage writableImage = new WritableImage((int) canvas.getWidth(), (int) canvas.getHeight());
        canvas.snapshot(null, writableImage);
        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(writableImage, null);

        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() throws IOException {
                imageFileService.save(targetFile, bufferedImage, format);
                return null;
            }
        };
        saveTask.setOnFailed(event -> {
            LOGGER.log(Level.WARNING, "Failed to save image to " + targetFile, saveTask.getException());
            showError("Could not save " + targetFile.getName(), describe(saveTask.getException()));
        });

        BackgroundTasks.run(saveTask, "peint-image-save");
    }

    /**
     * Returns the first extension of the given filter (e.g. {@code "png"} for
     * {@code "*.png"}), or {@code "png"} if no filter is selected.
     */
    static String defaultExtension(FileChooser.ExtensionFilter filter) {
        if (filter == null || filter.getExtensions().isEmpty()) {
            return "png";
        }
        String pattern = filter.getExtensions().get(0);
        return pattern.substring(pattern.lastIndexOf('.') + 1).toLowerCase();
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

        loadImage(selectedFile);
    }

    /**
     * Loads the given image file onto the canvas from a background thread,
     * reporting any failure with {@link #showError(String, String)}.
     */
    void loadImage(File selectedFile) {
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
                LOGGER.warning("Not a readable image file: " + selectedFile);
                showError("Could not open " + selectedFile.getName(), "The file is not a supported image.");
            }
        });
        loadTask.setOnFailed(event -> {
            LOGGER.log(Level.WARNING, "Failed to load image from " + selectedFile, loadTask.getException());
            showError("Could not open " + selectedFile.getName(), describe(loadTask.getException()));
        });

        BackgroundTasks.run(loadTask, "peint-image-load");
    }

    /**
     * Tells the user that an operation failed, in an error dialog.
     *
     * <p>Failures used to be printed to the console only, so a failed save
     * looked exactly like a successful one.</p>
     *
     * @param header what failed, e.g. "Could not save drawing.png"
     * @param detail why it failed
     */
    void showError(String header, String detail) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Peint");
        alert.setHeaderText(header);
        alert.setContentText(detail);
        alert.showAndWait();
    }

    /**
     * Returns a failure's message, or its type when it has no message.
     */
    private static String describe(Throwable failure) {
        return failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
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