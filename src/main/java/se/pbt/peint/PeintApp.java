package se.pbt.peint;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import se.pbt.peint.support.SystemTheme;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The main entry point for the Peint application.
 *
 * <p>Peint is a simple, lightweight drawing application inspired by classic Paint tools.
 * It provides basic functionality for creating and editing images, designed to be straightforward
 * and easy to use.</p>
 */
public class PeintApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(PeintApp.class.getName());

    /**
     * Starts the application by loading the main interface and displaying the primary stage.
     *
     * @param stage the primary stage for the application
     */
    @Override
    public void start(Stage stage) {
        SystemTheme.follow();
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(PeintApp.class.getResource("peint-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 1200, 800);
            stage.setTitle("Peint - Your Drawing App");
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            showStartupError(e);
        }
    }

    /**
     * Reports a failure to load the application window, both on the console
     * and in a dialog, since a silently missing window otherwise gives the
     * user no indication anything went wrong.
     */
    private void showStartupError(IOException e) {
        LOGGER.log(Level.SEVERE, "Failed to load the application window", e);

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Peint failed to start");
        alert.setHeaderText("Could not load the application window");
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }

    /**
     * Launches the application.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        launch();
    }
}