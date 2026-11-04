package se.pbt.peint.controller;

import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import se.pbt.peint.ai.AiSettings;
import se.pbt.peint.ai.Description;
import se.pbt.peint.ai.DrawingDescriber;
import se.pbt.peint.ai.OllamaClient;
import se.pbt.peint.ai.OllamaException;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

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
    GridPane widthControls;

    @FXML
    Slider widthSlider;

    @FXML
    Label widthLabel;

    @FXML
    GridPane toleranceControls;

    @FXML
    Slider toleranceSlider;

    @FXML
    Label toleranceLabel;

    @FXML
    MenuItem describeItem;

    CanvasManager canvasManager;
    ToolbarManager toolbarManager;
    final ImageFileService imageFileService = new ImageFileService();

    // One instance of each tool, so settings such as a width are kept while
    // another tool is selected.
    final BrushTool brushTool = new BrushTool();
    final EraserTool eraserTool = new EraserTool();
    final FillTool fillTool = new FillTool();

    // Where the AI settings are kept between runs.
    Preferences preferences = Preferences.userRoot().node("se/pbt/peint");

    // The local Ollama server the AI menu uses, and the model it asks.
    AiSettings aiSettings = AiSettings.DEFAULTS;
    OllamaClient ollama = new OllamaClient(aiSettings.address());
    String model = aiSettings.model();

    // The most recent description of the drawing, or null before the first.
    Description lastDescription;

    // Where the width slider's value goes: the selected tool's width setter,
    // or nothing while the selected tool has no width.
    private DoubleConsumer widthTarget = width -> { };

    /**
     * Initializes the controller and sets up managers for canvas and toolbar interactions.
     */
    public void initialize() {
        canvasManager = new CanvasManager(canvas, canvasContainer);
        applyAiSettings(AiSettings.load(preferences));
        toolbarManager = new ToolbarManager(toolBar, topContainer, rootPane, colorPicker, toggleToolbarPositionButton,
                widthControls, toleranceControls);

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
     * Prompts for a destination file and saves the canvas to it as a PNG, JPEG, BMP or GIF.
     */
    @FXML
    void onSave() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Canvas As Image");
        fileChooser.getExtensionFilters().addAll(saveFilters());

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
     * Returns the file filters for the Save dialog, one per format the canvas
     * can be saved in. The first is the one selected by default.
     *
     * <p>GIF holds at most 256 colors, so drawings keep their colors exactly
     * but photos come out with slightly shifted colors; the filter says so.</p>
     */
    static List<FileChooser.ExtensionFilter> saveFilters() {
        return List.of(
                new FileChooser.ExtensionFilter("PNG Files", "*.png"),
                new FileChooser.ExtensionFilter("JPEG Files", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("BMP Files", "*.bmp"),
                new FileChooser.ExtensionFilter("GIF Files (256 colors)", "*.gif")
        );
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
     * Checks in the background whether Ollama runs, and disables
     * <em>Describe Drawing</em> if it doesn't. Called each time the AI menu
     * opens, so starting Ollama while Peint runs makes the item available.
     */
    @FXML
    void onAiMenuShowing() {
        Task<Boolean> check = new Task<>() {
            @Override
            protected Boolean call() {
                return ollama.isAvailable();
            }
        };
        check.setOnSucceeded(event -> describeItem.setDisable(!check.getValue()));
        BackgroundTasks.run(check, "peint-ollama-check");
    }

    /**
     * Asks the model for a title and a description of the drawing, showing
     * progress, which can be cancelled, while it thinks, and the answer once
     * it has one.
     */
    @FXML
    void onDescribe() {
        // Snapshotting the canvas must happen on the FX Application Thread;
        // only asking the model is moved to a background thread.
        WritableImage snapshot = new WritableImage((int) canvas.getWidth(), (int) canvas.getHeight());
        canvas.snapshot(null, snapshot);
        BufferedImage drawing = SwingFXUtils.fromFXImage(snapshot, null);

        DrawingDescriber describer = new DrawingDescriber(ollama, model);
        Task<Description> describeTask = new Task<>() {
            @Override
            protected Description call() throws IOException, InterruptedException {
                return describer.describe(drawing);
            }
        };
        Runnable closeProgress = showProgress("Looking at your drawing\u2026",
                "A model on your own computer is describing the drawing. This can take a minute.",
                () -> describeTask.cancel(true));

        describeTask.setOnSucceeded(event -> {
            closeProgress.run();
            lastDescription = describeTask.getValue();
            showDescription(lastDescription);
        });
        describeTask.setOnFailed(event -> {
            closeProgress.run();
            LOGGER.log(Level.WARNING, "Failed to describe the drawing", describeTask.getException());
            showError("Could not describe the drawing", explainOllamaFailure(describeTask.getException()));
        });
        describeTask.setOnCancelled(event -> closeProgress.run());

        BackgroundTasks.run(describeTask, "peint-describe");
    }

    /**
     * Lets the user choose the Ollama server and model, and keeps the choice
     * for later runs.
     */
    @FXML
    void onAiSettings() {
        askForAiSettings(aiSettings).ifPresent(settings -> {
            applyAiSettings(settings);
            settings.save(preferences);
        });
    }

    private void applyAiSettings(AiSettings settings) {
        aiSettings = settings;
        ollama = new OllamaClient(settings.address());
        model = settings.model();
    }

    /**
     * Asks for an Ollama address and model, starting from the given ones.
     * OK is only enabled while the address is a valid URL and a model is named.
     *
     * @return the new settings, or empty if the user cancelled
     */
    Optional<AiSettings> askForAiSettings(AiSettings current) {
        TextField address = new TextField(current.address().toString());
        TextField modelName = new TextField(current.model());
        address.setPrefColumnCount(24);

        GridPane fields = new GridPane();
        fields.setHgap(8);
        fields.setVgap(8);
        fields.addRow(0, new Label("Ollama address"), address);
        fields.addRow(1, new Label("Model"), modelName);
        fields.add(new Label("The model must be installed (ollama pull <model>) and able to see images."), 0, 2, 2, 1);

        Dialog<AiSettings> dialog = new Dialog<>();
        dialog.setTitle("Peint");
        dialog.setHeaderText("AI Settings");
        dialog.getDialogPane().setContent(fields);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.initOwner(canvas.getScene().getWindow());

        Node ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(Bindings.createBooleanBinding(
                () -> AiSettings.parseAddress(address.getText()).isEmpty() || modelName.getText().isBlank(),
                address.textProperty(), modelName.textProperty()));
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new AiSettings(AiSettings.parseAddress(address.getText()).orElseThrow(), modelName.getText().strip())
                : null);
        return dialog.showAndWait();
    }

    /**
     * Explains why talking to Ollama failed in terms the user can act on.
     * Ollama's own messages, such as an unknown model, are shown as they are.
     */
    String explainOllamaFailure(Throwable failure) {
        if (failure instanceof OllamaException) {
            return failure.getMessage() + ".";
        }
        if (failure instanceof IOException && !ollama.isAvailable()) {
            return "Peint could not reach Ollama at " + ollama.address()
                    + ". Make sure Ollama is installed and running, then try again.";
        }
        return describe(failure);
    }

    /**
     * Shows that something is in progress, with a button to cancel it.
     *
     * @param header what is happening
     * @param detail what to expect
     * @param cancel run when the user cancels
     * @return closes the progress dialog
     */
    Runnable showProgress(String header, String detail, Runnable cancel) {
        Alert progress = new Alert(Alert.AlertType.NONE, detail, ButtonType.CANCEL);
        progress.setTitle("Peint");
        progress.setHeaderText(header);
        progress.setGraphic(new ProgressIndicator());
        progress.initOwner(canvas.getScene().getWindow());
        progress.resultProperty().addListener((observable, oldResult, newResult) -> {
            if (newResult == ButtonType.CANCEL) {
                cancel.run();
            }
        });
        progress.show();
        return () -> {
            // Closing doesn't count as cancelling: the result is set first.
            progress.setResult(ButtonType.OK);
            progress.close();
        };
    }

    /**
     * Shows the model's title and description, offering to copy the
     * description as alt text.
     */
    void showDescription(Description description) {
        ButtonType copy = new ButtonType("Copy Alt Text", ButtonBar.ButtonData.OK_DONE);
        TextArea text = new TextArea(description.text());
        text.setEditable(false);
        text.setWrapText(true);
        text.setPrefRowCount(4);

        Alert alert = new Alert(Alert.AlertType.INFORMATION, null, copy, ButtonType.CLOSE);
        alert.setTitle("Peint");
        alert.setHeaderText(description.title());
        alert.getDialogPane().setContent(text);
        alert.initOwner(canvas.getScene().getWindow());
        alert.showAndWait()
                .filter(copy::equals)
                .ifPresent(button -> Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.PLAIN_TEXT, description.text())));
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
            case EraserTool eraser -> showWidth(eraser.getWidth(), width -> {
                eraser.setWidth(width);
                canvas.setCursor(cursorFor(eraser)); // the cursor shows the eraser's size
            });
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
            case EraserTool eraser -> EraserCursor.forWidth(eraser.getWidth());
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
