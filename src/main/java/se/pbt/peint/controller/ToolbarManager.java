package se.pbt.peint.controller;

import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Slider;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
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

    /** The length of a setting's slider, along the toolbar. */
    static final double SLIDER_LENGTH = 100;

    private final ToolBar toolBar;
    private final VBox topContainer;
    private final BorderPane rootPane;
    private final ColorPicker colorPicker;
    private final Button positionToggleButton;
    private final GridPane[] settings;
    private boolean isToolbarAtTop = true;

    /**
     * Creates a new ToolbarManager to handle the specified toolbar and layout.
     *
     * @param toolBar the toolbar to manage
     * @param topContainer the container holding the toolbar (alongside the menu bar) at the top
     * @param rootPane the layout pane the toolbar can be moved to the left of
     * @param colorPicker the color picker inside the toolbar
     * @param positionToggleButton the button that triggers {@link #togglePosition()}
     * @param settings groups of a slider and its value label, laid out along
     *                 the toolbar
     */
    public ToolbarManager(ToolBar toolBar, VBox topContainer, BorderPane rootPane,
                          ColorPicker colorPicker, Button positionToggleButton, GridPane... settings) {
        this.toolBar = toolBar;
        this.topContainer = topContainer;
        this.rootPane = rootPane;
        this.colorPicker = colorPicker;
        this.positionToggleButton = positionToggleButton;
        this.settings = settings;

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
     * Keeps the color picker, the settings and the position-toggle button
     * usable when the toolbar is vertical, where {@code ToolBar} doesn't
     * stretch children to a shared width on its own.
     *
     * <p>Each setting's slider and label are turned to run along the toolbar,
     * so a vertical toolbar stays as narrow as its buttons instead of
     * widening to fit a horizontal slider and its label side by side.</p>
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

        for (GridPane setting : settings) {
            // Laid out in a row along a horizontal toolbar, in a column down a vertical one.
            for (int i = 0; i < setting.getChildren().size(); i++) {
                Node child = setting.getChildren().get(i);
                GridPane.setColumnIndex(child, vertical ? 0 : i);
                GridPane.setRowIndex(child, vertical ? i : 0);
                GridPane.setHalignment(child, HPos.CENTER);
                if (child instanceof Slider slider) {
                    slider.setOrientation(orientation);
                    slider.setPrefWidth(vertical ? Region.USE_COMPUTED_SIZE : SLIDER_LENGTH);
                    slider.setPrefHeight(vertical ? SLIDER_LENGTH : Region.USE_COMPUTED_SIZE);
                }
            }
        }
    }
}