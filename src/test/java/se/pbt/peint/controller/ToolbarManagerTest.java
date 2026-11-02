package se.pbt.peint.controller;

import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
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
    void settingsRunAlongTheToolbar() {
        FxTestSupport.runAndWait(() -> {
            ToolBar toolBar = new ToolBar();
            VBox topContainer = new VBox(toolBar);
            Slider slider = new Slider();
            Label label = new Label("1 px");
            GridPane setting = new GridPane();
            setting.addRow(0, slider, label);
            ToolbarManager manager = new ToolbarManager(toolBar, topContainer, new BorderPane(),
                    new ColorPicker(), new Button(), setting);

            manager.togglePosition();
            assertEquals(Orientation.VERTICAL, slider.getOrientation());
            assertEquals(0, GridPane.getColumnIndex(label), "the label should move under the slider");
            assertEquals(1, GridPane.getRowIndex(label));

            manager.togglePosition();
            assertEquals(Orientation.HORIZONTAL, slider.getOrientation());
            assertEquals(1, GridPane.getColumnIndex(label), "the label should move back beside the slider");
            assertEquals(0, GridPane.getRowIndex(label));
            assertEquals(ToolbarManager.SLIDER_LENGTH, slider.getPrefWidth());
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