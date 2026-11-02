package se.pbt.peint.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.pbt.peint.FxTestSupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests against the real peint-view.fxml, wired to its controller as in the app.
 */
class PeintViewTest {

    private BorderPane root;
    private PeintController controller;
    private Scene scene;

    @BeforeAll
    static void initJfx() {
        FxTestSupport.initToolkit();
    }

    @BeforeEach
    void setUp() {
        FxTestSupport.runAndWait(() -> {
            FXMLLoader loader = new FXMLLoader(PeintController.class.getResource("/se/pbt/peint/peint-view.fxml"));
            try {
                root = loader.load();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            controller = loader.getController();
            scene = new Scene(root, 400, 300);

            // Lay the scene out up front, as showing the window does in the
            // app, so the canvas has its size before the test draws on it.
            root.applyCss();
            root.layout();
        });
    }

    @Test
    void menuItemsHaveKeyboardShortcuts() {
        Map<String, KeyCombination> accelerators = menuAccelerators();

        assertEquals(KeyCombination.valueOf("Shortcut+S"), accelerators.get("Save"));
        assertEquals(KeyCombination.valueOf("Shortcut+O"), accelerators.get("Open"));
        assertEquals(KeyCombination.valueOf("Shortcut+Z"), accelerators.get("Undo"));
        assertEquals(KeyCombination.valueOf("Shortcut+Y"), accelerators.get("Redo"));
        assertEquals(KeyCombination.valueOf("F"), accelerators.get("Fill"));
    }

    @Test
    void shortcutShiftZRedoes() {
        FxTestSupport.runAndWait(() -> {
            controller.canvasManager.bucketFill(10, 10, Color.RED);
            controller.canvasManager.undo();

            Runnable redo = scene.getAccelerators().get(PeintController.REDO_ALTERNATIVE);
            assertNotNull(redo, "Shortcut+Shift+Z should be registered on the scene");
            redo.run();

            WritableImage snapshot = new WritableImage(
                    (int) controller.canvas.getWidth(), (int) controller.canvas.getHeight());
            controller.canvas.snapshot(null, snapshot);
            assertEquals(Color.RED, snapshot.getPixelReader().getColor(10, 10), "the fill should be redone");
        });
    }

    /**
     * Maps each menu item's text to its accelerator.
     */
    private Map<String, KeyCombination> menuAccelerators() {
        MenuBar menuBar = (MenuBar) ((VBox) root.getTop()).getChildren().get(0);
        Map<String, KeyCombination> accelerators = new HashMap<>();
        for (Menu menu : menuBar.getMenus()) {
            for (MenuItem item : menu.getItems()) {
                accelerators.put(item.getText(), item.getAccelerator());
            }
        }
        return accelerators;
    }
}
