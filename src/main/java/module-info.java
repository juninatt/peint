module se.pbt.peint {
    // Core JavaFX modules
    requires javafx.controls;
    requires javafx.fxml;

    // Talking to a local Ollama server
    requires java.net.http;
    requires tools.jackson.databind;

    // Themes
    requires atlantafx.base;

    // Icons
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;

    // Java and Swing integration
    requires java.desktop;
    requires javafx.swing;
    requires java.logging;
    requires java.prefs;
    requires java.xml;

    // Module visibility
    opens se.pbt.peint to javafx.fxml;
    exports se.pbt.peint;
    exports se.pbt.peint.canvas;
    opens se.pbt.peint.canvas to javafx.fxml;
    exports se.pbt.peint.controller;
    opens se.pbt.peint.controller to javafx.fxml;
}
