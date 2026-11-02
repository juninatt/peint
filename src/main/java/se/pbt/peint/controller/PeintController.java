package se.pbt.peint.controller;

import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ToolBar;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import se.pbt.peint.canvas.BrushTool;
import se.pbt.peint.canvas.CanvasManager;
import se.pbt.peint.canvas.FillTool;
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

    static final KeyCombination REDO_ALTERNATIVE = KeyCombination.valueOf("Shortcut+Shift+Z");

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

    // Cancels an armed bucket fill; added to the scene only while armed.
    private final EventHandler<KeyEvent> cancelFillOnEscape = event -> {
        if (event.getCode() == KeyCode.ESCAPE) {
            restoreBrush();
            event.consume();
        }
    };

    /**
     * Initializes the controller and sets up managers for canvas and toolbar interactions.
     */
    public void initialize() {
        canvasManager = new CanvasManager(canvas, canvasContainer);
        toolbarManager = new ToolbarManager(toolBar, topContainer, rootPane, colorPicker, toggleToolbarPositionButton);

        // setValue() doesn't fire the picker's action, so the initial color
        // is passed on to the canvas explicitly.
        colorPicker.setValue(Color.BLACK);
        canvasManager.setDrawingColor(colorPicker.getValue());
        colorPicker.setOnAction(event -> canvasManager.setDrawingColor(colorPicker.getValue()));

        // The menu's Redo item can only carry one accelerator (Shortcut+Y),
        // so the other common one is added to the scene once there is one.
        canvas.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getAccelerators().put(REDO_ALTERNATIVE, this::onRedo);
            }
        });
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
        fileChooser.getExtensionFilters().add(openFilter());

        File selectedFile = fileChooser.showOpenDialog(canvas.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }

        loadImage(selectedFile);
    }

    /**
     * Returns the file filter for the Open dialog, covering every format
     * {@link ImageFileService#load(File)} can read.
     *
     * <p>{@code .jfif} is a JPEG under another name, which some browsers use
     * when saving images. GIFs open as their first frame only.</p>
     */
    static FileChooser.ExtensionFilter openFilter() {
        return new FileChooser.ExtensionFilter("Image Files",
                "*.png", "*.jpg", "*.jpeg", "*.jfif", "*.bmp", "*.gif");
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
     * Arms the bucket fill tool: fills the clicked area on the next canvas
     * click, then switches back to the brush.
     *
     * <p>While armed, the canvas shows a crosshair cursor, and Escape
     * cancels the fill. Escape is caught by a filter on the whole scene, so
     * it works whichever control has focus once the menu closes.</p>
     */
    @FXML
    void onBucketFill() {
        canvasManager.setTool(new FillTool(this::restoreBrush));
        canvas.setCursor(Cursor.CROSSHAIR);

        Scene scene = canvas.getScene();
        if (scene != null) {
            // Removed first, so arming the fill twice doesn't add it twice.
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, cancelFillOnEscape);
            scene.addEventFilter(KeyEvent.KEY_PRESSED, cancelFillOnEscape);
        }
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
     * Switches back to the brush, removing the fill tool's cursor and Escape
     * filter, once a fill has completed or been cancelled.
     */
    void restoreBrush() {
        canvasManager.setTool(new BrushTool());
        canvas.setCursor(Cursor.DEFAULT);
        Scene scene = canvas.getScene();
        if (scene != null) {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, cancelFillOnEscape);
        }
    }
}