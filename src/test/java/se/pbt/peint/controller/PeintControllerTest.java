package se.pbt.peint.controller;

import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.pbt.peint.FxTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PeintControllerTest {

    private PeintController controller;
    private BorderPane rootPane;
    private ToolBar toolBar;

    @BeforeAll
    static void initJfx() {
        FxTestSupport.initToolkit();
    }

    @BeforeEach
    void setUp() {
        FxTestSupport.runAndWait(() -> {
            controller = new PeintController();
            controller.canvas = new Canvas();
            controller.rootPane = new BorderPane();
            controller.canvasContainer = new Pane();
            controller.topContainer = new VBox();
            controller.toolBar = new ToolBar();
            controller.colorPicker = new ColorPicker();
            controller.toggleToolbarPositionButton = new Button();

            controller.rootPane.resize(200, 200);
            controller.topContainer.getChildren().add(controller.toolBar);

            rootPane = controller.rootPane;
            toolBar = controller.toolBar;

            controller.initialize();
        });
    }

    @Test
    void initializeSetsDefaultColorToBlack() {
        assertEquals(Color.BLACK, controller.colorPicker.getValue());
    }

    @Test
    void onToggleToolbarPositionMovesTheToolbarToTheLeft() {
        FxTestSupport.runAndWait(() -> controller.onToggleToolbarPosition());

        assertSame(toolBar, rootPane.getLeft());
    }

    @Test
    void onBucketFillArmsAClickHandlerOnTheCanvas() {
        FxTestSupport.runAndWait(() -> controller.onBucketFill());

        assertNotNull(controller.canvas.getOnMouseClicked());
    }

    @Test
    void defaultExtensionTakesTheFiltersFirstExtension() {
        FileChooser.ExtensionFilter jpeg = new FileChooser.ExtensionFilter("JPEG Files", "*.jpg", "*.jpeg");

        assertEquals("jpg", PeintController.defaultExtension(jpeg));
    }

    @Test
    void defaultExtensionFallsBackToPngWithoutAFilter() {
        assertEquals("png", PeintController.defaultExtension(null));
    }
}
