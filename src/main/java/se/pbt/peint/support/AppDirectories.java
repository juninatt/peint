package se.pbt.peint.support;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Where Peint keeps its own files, such as the Ollama it downloads, in each
 * operating system's usual place for an application's data.
 */
public final class AppDirectories {

    private AppDirectories() {
    }

    /**
     * @return Peint's data directory on this computer, not necessarily created yet
     */
    public static Path data() {
        return data(System.getProperty("os.name"), Path.of(System.getProperty("user.home")), System.getenv());
    }

    /**
     * Peint's data directory for the given operating system:
     * {@code %LOCALAPPDATA%\Peint} on Windows,
     * {@code ~/Library/Application Support/Peint} on macOS, and
     * {@code $XDG_DATA_HOME/peint} or {@code ~/.local/share/peint} elsewhere.
     */
    static Path data(String osName, Path home, Map<String, String> environment) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            String localAppData = environment.get("LOCALAPPDATA");
            Path base = localAppData != null && !localAppData.isBlank()
                    ? Path.of(localAppData)
                    : home.resolve("AppData").resolve("Local");
            return base.resolve("Peint");
        }
        if (os.startsWith("mac")) {
            return home.resolve("Library").resolve("Application Support").resolve("Peint");
        }
        String xdgDataHome = environment.get("XDG_DATA_HOME");
        Path base = xdgDataHome != null && !xdgDataHome.isBlank()
                ? Path.of(xdgDataHome)
                : home.resolve(".local").resolve("share");
        return base.resolve("peint");
    }
}
