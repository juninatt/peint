# Peint - A Simple Paint App in JavaFX

## Description
Peint is a simple paint application built in JavaFX, inspired by classic Paint tools.

## Features
- **Freehand drawing**: Click and drag on the canvas to draw, in the color chosen in the color picker.
- **Fill**: Choose *Draw → Fill* (the cursor turns into a crosshair), then click an area to fill it with the current color. Press Escape to cancel.
- **Undo / Redo**: Step back and forward through up to 50 changes from the *Edit* menu (fewer on very large images, to keep memory use in check).
- **Clear**: Wipe the canvas from the *Edit* menu (can be undone).
- **Open / Save**: Open PNG, JPEG (including `.jfif` and CMYK), BMP and GIF images onto the canvas, and save the canvas as PNG, JPEG or BMP. Photos are turned upright by their EXIF orientation, and animated GIFs open as their first frame.
- **Large images**: The canvas grows to fit an opened image, and scrolls when it is larger than the window.
- **Movable toolbar**: Toggle the toolbar between the top and the left side of the window.


## Keyboard Shortcuts
On macOS, use Cmd instead of Ctrl.

| Shortcut                    | Action        |
|-----------------------------|---------------|
| Ctrl+S                      | Save          |
| Ctrl+O                      | Open          |
| Ctrl+Z                      | Undo          |
| Ctrl+Y or Ctrl+Shift+Z      | Redo          |
| F                           | Fill          |
| Escape                      | Cancel a fill |


## Technologies
- **JavaFX**: For the user interface and canvas.
- **FXML**: For defining the layout.
- **Ikonli**: For the FontAwesome icons in the menus and toolbar.
- **JUnit 5**: For the tests.


## Download
Ready-to-run builds for Windows, Linux and macOS (Apple Silicon) are attached to each
[release](https://github.com/juninatt/peint/releases). They include their own Java runtime,
so nothing else needs to be installed. Unpack the archive and run `bin/peint`
(`bin\peint.bat` on Windows).


## Requirements
To build from source: JDK 25 or later. Maven is not required; the included Maven Wrapper downloads it.


## Building from Source
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
