module se.pbt.peint {
    // Core JavaFX modules
    requires javafx.controls;
    requires javafx.fxml;

    // Third-party libraries for enhanced UI and styling
    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;

    // Java and Swing integration
    requires java.desktop;
    requires javafx.swing;

    // Module visibility
    opens se.pbt.peint to javafx.fxml;
    exports se.pbt.peint;
    exports se.pbt.peint.canvas;
    opens se.pbt.peint.canvas to javafx.fxml;
    exports se.pbt.peint.controller;
    opens se.pbt.peint.controller to javafx.fxml;
}
