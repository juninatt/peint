package se.pbt.peint;

import javafx.geometry.Orientation;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 * Manages the toolbar's behavior and position within the application.
 *
 * <p>This class allows toggling the toolbar's orientation between horizontal
 * (below the menu bar, at the top of the window) and vertical (at the left
 * of the window). The toolbar normally lives as a child of
 * {@code topContainer} alongside the menu bar, which itself always stays
 * at the top.</p>
 */
public class ToolbarManager {
    private final ToolBar toolBar;
    private final VBox topContainer;
    private final BorderPane rootPane;
    private boolean isToolbarAtTop = true;

    /**
     * Creates a new ToolbarManager to handle the specified toolbar and layout.
     *
     * @param toolBar the toolbar to manage
     * @param topContainer the container holding the toolbar (alongside the menu bar) at the top
     * @param rootPane the layout pane the toolbar can be moved to the left of
     */
    public ToolbarManager(ToolBar toolBar, VBox topContainer, BorderPane rootPane) {
        this.toolBar = toolBar;
        this.topContainer = topContainer;
        this.rootPane = rootPane;

        toolBar.setOrientation(Orientation.HORIZONTAL);
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
}