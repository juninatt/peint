package se.pbt.peint.controller;

import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Manages the toolbar's position and appearance within the application.
 *
 * <p>Toggles the toolbar between horizontal (below the menu bar, at the top
 * of the window) and vertical (at the left of the window), and adjusts its
 * controls to fit whichever orientation is active.</p>
 */
public class ToolbarManager {
    private final ToolBar toolBar;
    private final VBox topContainer;
    private final BorderPane rootPane;
    private final ColorPicker colorPicker;
    private final Button positionToggleButton;
    private boolean isToolbarAtTop = true;

    /**
     * Creates a new ToolbarManager to handle the specified toolbar and layout.
     *
     * @param toolBar the toolbar to manage
     * @param topContainer the container holding the toolbar (alongside the menu bar) at the top
     * @param rootPane the layout pane the toolbar can be moved to the left of
     * @param colorPicker the color picker inside the toolbar
     * @param positionToggleButton the button that triggers {@link #togglePosition()}
     */
    public ToolbarManager(ToolBar toolBar, VBox topContainer, BorderPane rootPane,
                          ColorPicker colorPicker, Button positionToggleButton) {
        this.toolBar = toolBar;
        this.topContainer = topContainer;
        this.rootPane = rootPane;
        this.colorPicker = colorPicker;
        this.positionToggleButton = positionToggleButton;

        toolBar.setOrientation(Orientation.HORIZONTAL);
        toolBar.orientationProperty().addListener((observable, oldOrientation, newOrientation) ->
                adjustControlsForOrientation(newOrientation));
    }

    /**
     * Toggles the toolbar's position between below the menu bar and the left
     * of the window. The menu bar itself always stays at the top.
     */
    public void togglePosition() {
        if (isToolbarAtTop) {
            topContainer.getChildren().remove(toolBar);
            rootPane.setLeft(toolBar);
            toolBar.setOrientation(Orientation.VERTICAL);
        } else {
            rootPane.setLeft(null);
            topContainer.getChildren().add(toolBar);
            toolBar.setOrientation(Orientation.HORIZONTAL);
        }
        isToolbarAtTop = !isToolbarAtTop;
    }

    /**
     * Keeps the color picker and position-toggle button usable when the
     * toolbar is vertical, where {@code ToolBar} doesn't stretch children
     * to a shared width on its own.
     */
    private void adjustControlsForOrientation(Orientation orientation) {
        boolean vertical = orientation == Orientation.VERTICAL;

        // Hides the color picker's text label, leaving just the swatch + arrow.
        colorPicker.setStyle(vertical ? "-fx-color-label-visible: false;" : "");

        if (vertical) {
            positionToggleButton.prefWidthProperty().bind(colorPicker.widthProperty());
        } else {
            positionToggleButton.prefWidthProperty().unbind();
            positionToggleButton.setPrefWidth(Region.USE_COMPUTED_SIZE);
        }
    }
}