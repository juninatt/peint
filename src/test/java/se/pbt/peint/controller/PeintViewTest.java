package se.pbt.peint.controller;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.pbt.peint.FxTestSupport;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void canvasFillsTheVisibleAreaWithoutScrollBars() {
        FxTestSupport.runAndWait(() -> {
            ScrollPane scrollPane = (ScrollPane) root.getCenter();
            Bounds viewport = scrollPane.getViewportBounds();

            assertEquals(viewport.getWidth(), controller.canvas.getWidth(), 1, "canvas should fill the visible width");
            assertEquals(viewport.getHeight(), controller.canvas.getHeight(), 1, "canvas should fill the visible height");
            assertFalse(isScrollable(scrollPane), "a canvas that fits shouldn't scroll");
        });
    }

    @Test
    void anImageLargerThanTheWindowCanBeScrolledTo() {
        FxTestSupport.runAndWait(() -> {
            ScrollPane scrollPane = (ScrollPane) root.getCenter();
            controller.canvasManager.loadImage(blueImage(1000, 800));
            root.layout();

            assertEquals(1000, controller.canvasContainer.getWidth(), "the container should grow with the canvas");
            assertEquals(800, controller.canvasContainer.getHeight());
            assertTrue(scrollPane.getViewportBounds().getWidth() < 1000, "the image should be wider than the window");
            assertTrue(isScrollable(scrollPane), "the rest of the image should be reachable by scrolling");
        });
    }

    @Test
    void shrinkingTheWindowKeepsTheCanvasAndScrolls() {
        FxTestSupport.runAndWait(() -> {
            ScrollPane scrollPane = (ScrollPane) root.getCenter();
            double fullWidth = controller.canvas.getWidth();

            root.resize(200, 150);
            root.layout();

            assertEquals(fullWidth, controller.canvas.getWidth(), "the canvas shouldn't shrink");
            assertTrue(isScrollable(scrollPane), "the part outside the smaller window should be reachable by scrolling");
        });
    }

    /**
     * Whether the scroll pane's content is larger than its visible area, so
     * that part of it can only be seen by scrolling.
     */
    private static boolean isScrollable(ScrollPane scrollPane) {
        Bounds viewport = scrollPane.getViewportBounds();
        Bounds content = scrollPane.getContent().getLayoutBounds();
        return content.getWidth() > viewport.getWidth() + 1 || content.getHeight() > viewport.getHeight() + 1;
    }

    private static BufferedImage blueImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(java.awt.Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
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
