package se.pbt.peint.support;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;

/**
 * Styles the application with an AtlantaFX theme that matches the operating
 * system's light or dark mode, and switches along when the system does.
 *
 * <p>The canvas itself stays white in both themes, like a sheet of paper;
 * only the controls around it change.</p>
 */
public final class SystemTheme {

    private SystemTheme() {
    }

    /**
     * Applies the theme for the system's current color scheme, and again
     * whenever the system's color scheme changes. Must be called on the
     * JavaFX Application Thread, before the first window is shown.
     */
    public static void follow() {
        Platform.Preferences preferences = Platform.getPreferences();
        apply(preferences.getColorScheme());
        preferences.colorSchemeProperty().addListener((observable, oldScheme, newScheme) -> apply(newScheme));
    }

    /**
     * Applies the theme for the given color scheme to every window.
     */
    static void apply(ColorScheme scheme) {
        Application.setUserAgentStylesheet(themeFor(scheme).getUserAgentStylesheet());
    }

    /**
     * Returns the theme to use for the given color scheme.
     */
    static Theme themeFor(ColorScheme scheme) {
        return scheme == ColorScheme.DARK ? new PrimerDark() : new PrimerLight();
    }
}
