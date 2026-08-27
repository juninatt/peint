package se.pbt.peint.controller;

import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.pbt.peint.FxTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ToolbarManagerTest {

    @BeforeAll
    static void initJfx() {
        FxTestSupport.initToolkit();
    }

    @Test
    void constructorSetsHorizontalOrientation() {
        FxTestSupport.runAndWait(() -> {
            ToolBar toolBar = new ToolBar();
            VBox topContainer = new VBox(toolBar);
            BorderPane rootPane = new BorderPane();
            new ToolbarManager(toolBar, topContainer, rootPane, new ColorPicker(), new Button());

            assertEquals(Orientation.HORIZONTAL, toolBar.getOrientation());
        });
    }

    @Test
    void togglePositionMovesToolbarToLeftAndBack() {
        FxTestSupport.runAndWait(() -> {
            ToolBar toolBar = new ToolBar();
            VBox topContainer = new VBox(toolBar);
            BorderPane rootPane = new BorderPane();
            ToolbarManager manager = new ToolbarManager(toolBar, topContainer, rootPane, new ColorPicker(), new Button());

            manager.togglePosition();
            assertSame(toolBar, rootPane.getLeft());
            assertEquals(Orientation.VERTICAL, toolBar.getOrientation());

            manager.togglePosition();
            assertNull(rootPane.getLeft());
            assertEquals(Orientation.HORIZONTAL, toolBar.getOrientation());
        });
    }
}