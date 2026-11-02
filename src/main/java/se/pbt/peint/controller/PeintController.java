package se.pbt.peint.controller;

import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import se.pbt.peint.canvas.BrushTool;
import se.pbt.peint.canvas.CanvasManager;
import se.pbt.peint.canvas.EraserTool;
import se.pbt.peint.canvas.FillTool;
import se.pbt.peint.canvas.FloodFillProcessor;
import se.pbt.peint.canvas.Tool;
import se.pbt.peint.support.BackgroundTasks;
import se.pbt.peint.support.ImageFileService;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.function.DoubleConsumer;
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

    static final KeyCombination SELECT_BRUSH = KeyCombination.valueOf("Esc");

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

    @FXML
    ToggleGroup toolGroup;

    @FXML
    ToggleButton brushButton;

    @FXML
    ToggleButton eraserButton;

    @FXML
    ToggleButton fillButton;

    @FXML
    HBox widthControls;

    @FXML
    Slider widthSlider;

    @FXML
    Label widthLabel;

    @FXML
    HBox toleranceControls;

    @FXML
    Slider toleranceSlider;

    @FXML
    Label toleranceLabel;

    CanvasManager canvasManager;
    ToolbarManager toolbarManager;
    final ImageFileService imageFileService = new ImageFileService();

    // One instance of each tool, so settings such as a width are kept while
    // another tool is selected.
    final BrushTool brushTool = new BrushTool();
    final EraserTool eraserTool = new EraserTool();
    final FillTool fillTool = new FillTool();

    // Where the width slider's value goes: the selected tool's width setter,
    // or nothing while the selected tool has no width.
    private DoubleConsumer widthTarget = width -> { };

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

        widthSlider.valueProperty().addListener((observable, oldWidth, newWidth) -> {
            double width = Math.round(newWidth.doubleValue());
            widthTarget.accept(width);
            widthLabel.setText((int) width + " px");
        });
        // Shown in percent; the fill tool works in steps of 0-255 per channel.
        toleranceSlider.setValue(toPercent(fillTool.getTolerance()));
        toleranceLabel.setText(Math.round(toleranceSlider.getValue()) + " %");
        toleranceSlider.valueProperty().addListener((observable, oldPercent, newPercent) -> {
            fillTool.setTolerance(fromPercent(newPercent.doubleValue()));
            toleranceLabel.setText(Math.round(newPercent.doubleValue()) + " %");
        });

        selectTool(brushTool, brushButton);

        // Clicking the selected tool's button would otherwise leave no tool
        // selected; keep it selected instead.
        toolGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null) {
                oldToggle.setSelected(true);
            }
        });

        // The scene doesn't exist yet when loaded from FXML, so the scene-wide
        // shortcuts are added once the canvas is placed in one.
        if (canvas.getScene() != null) {
            addSceneShortcuts(canvas.getScene());
        }
        canvas.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                addSceneShortcuts(newScene);
            }
        });
    }

    /**
     * Adds the shortcuts no menu item carries: the menu's Redo item can only
     * have one accelerator (Shortcut+Y), and Escape has no menu item.
     */
    private void addSceneShortcuts(Scene scene) {
        scene.getAccelerators().put(REDO_ALTERNATIVE, this::onRedo);
        scene.getAccelerators().put(SELECT_BRUSH, this::onBrush);
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
     * Selects the brush, for drawing freehand strokes.
     */
    @FXML
    void onBrush() {
        selectTool(brushTool, brushButton);
    }

    /**
     * Selects the eraser, which erases with freehand strokes.
     */
    @FXML
    void onEraser() {
        selectTool(eraserTool, eraserButton);
    }

    /**
     * Selects the fill tool, which fills each clicked area until another
     * tool is chosen.
     */
    @FXML
    void onBucketFill() {
        selectTool(fillTool, fillButton);
    }

    /**
     * Selects a tool on the canvas, and shows it in the toolbar and in the
     * canvas's cursor, whether it was chosen from the toolbar, the menu or
     * a shortcut.
     */
    private void selectTool(Tool tool, ToggleButton button) {
        canvasManager.setTool(tool);
        button.setSelected(true);
        canvas.setCursor(cursorFor(tool));
        showSettingsFor(tool);
    }

    /**
     * Shows the toolbar settings that apply to the given tool, and hides the
     * rest: a width for the brush and the eraser, a tolerance for the fill.
     */
    private void showSettingsFor(Tool tool) {
        switch (tool) {
            case BrushTool brush -> showWidth(brush.getWidth(), brush::setWidth);
            case EraserTool eraser -> showWidth(eraser.getWidth(), eraser::setWidth);
            case FillTool fill -> {
                widthTarget = width -> { };
                setShown(widthControls, false);
            }
        }
        setShown(toleranceControls, tool instanceof FillTool);
    }

    /**
     * Shows or hides a toolbar control, letting hidden ones give up their space.
     */
    private static void setShown(Node control, boolean shown) {
        control.setVisible(shown);
        control.setManaged(shown);
    }

    static double toPercent(int tolerance) {
        return tolerance * 100.0 / FloodFillProcessor.MAX_TOLERANCE;
    }

    static int fromPercent(double percent) {
        return (int) Math.round(percent * FloodFillProcessor.MAX_TOLERANCE / 100);
    }

    private void showWidth(double width, DoubleConsumer setter) {
        // Detached first, so moving the slider to this tool's width doesn't
        // write it into the previously selected tool.
        widthTarget = value -> { };
        widthSlider.setValue(width);
        widthLabel.setText((int) width + " px");
        widthTarget = setter;
        setShown(widthControls, true);
    }

    /**
     * Returns the cursor shown over the canvas while the given tool is
     * selected. Tool is sealed, so this switch covers every tool, and a new
     * tool won't compile until it is given a cursor here.
     */
    static Cursor cursorFor(Tool tool) {
        return switch (tool) {
            case BrushTool brush -> Cursor.DEFAULT;
            case EraserTool eraser -> Cursor.DEFAULT;
            case FillTool fill -> Cursor.CROSSHAIR;
        };
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
}
