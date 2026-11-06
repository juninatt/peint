package se.pbt.peint.support;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppDirectoriesTest {

    private static final Path HOME = Path.of("home", "anna");

    @Test
    void windowsUsesLocalAppData() {
        assertEquals(Path.of("C:", "Users", "anna", "AppData", "Local", "Peint"),
                AppDirectories.data("Windows 11", HOME,
                        Map.of("LOCALAPPDATA", Path.of("C:", "Users", "anna", "AppData", "Local").toString())));
    }

    @Test
    void windowsFallsBackToTheHomeDirectoryWithoutLocalAppData() {
        assertEquals(HOME.resolve("AppData").resolve("Local").resolve("Peint"),
                AppDirectories.data("Windows 11", HOME, Map.of()));
    }

    @Test
    void macOsUsesApplicationSupport() {
        assertEquals(HOME.resolve("Library").resolve("Application Support").resolve("Peint"),
                AppDirectories.data("Mac OS X", HOME, Map.of()));
    }

    @Test
    void linuxUsesTheXdgDataDirectory() {
        assertEquals(Path.of("data", "home").resolve("peint"),
                AppDirectories.data("Linux", HOME, Map.of("XDG_DATA_HOME", Path.of("data", "home").toString())));
        assertEquals(HOME.resolve(".local").resolve("share").resolve("peint"),
                AppDirectories.data("Linux", HOME, Map.of()));
    }
}
