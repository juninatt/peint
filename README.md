# Peint - A Simple Paint App in JavaFX

## Description
Peint is a simple paint application built in JavaFX, inspired by classic Paint tools.

## Features
- **Freehand drawing**: Click and drag on the canvas to draw, in the color chosen in the color picker.
- **Fill**: Choose *Draw → Fill*, then click an area to fill it with the current color.
- **Undo / Redo**: Step back and forward through up to 50 changes from the *Edit* menu.
- **Clear**: Wipe the canvas from the *Edit* menu (can be undone).
- **Open / Save**: Open PNG, JPEG and BMP images onto the canvas, and save the canvas in any of those formats.
- **Movable toolbar**: Toggle the toolbar between the top and the left side of the window.


## Technologies
- **JavaFX**: For the user interface and canvas.
- **FXML**: For defining the layout.
- **Ikonli**: For the FontAwesome icons in the menus and toolbar.
- **JUnit 5**: For the tests.


## Requirements
- JDK 17 or later. Maven is not required; the included Maven Wrapper downloads it.


## Installation and Running
1. Clone this repository.
2. Run the application:
   ```
   ./mvnw javafx:run
   ```
   On Windows (cmd or PowerShell), use `.\mvnw.cmd javafx:run` instead.

To run the tests:
```
./mvnw test
```


---
**Built with JavaFX to explore the basics of canvas and GUI development.**
