package se.pbt.peint.support;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import javafx.application.Application;
import javafx.application.ColorScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.pbt.peint.FxTestSupport;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemThemeTest {

    @BeforeAll
    static void initJfx() {
        FxTestSupport.initToolkit();
    }

    @AfterEach
    void restoreDefaultTheme() {
        // The theme is global to the toolkit; don't leak it into other tests.
        FxTestSupport.runAndWait(() -> Application.setUserAgentStylesheet(null));
    }

    @Test
    void aDarkSystemGetsTheDarkTheme() {
        assertInstanceOf(PrimerDark.class, SystemTheme.themeFor(ColorScheme.DARK));
    }

    @Test
    void aLightSystemGetsTheLightTheme() {
        assertInstanceOf(PrimerLight.class, SystemTheme.themeFor(ColorScheme.LIGHT));
    }

    @Test
    void applyMakesTheThemeTheApplicationsStylesheet() {
        FxTestSupport.runAndWait(() -> {
            SystemTheme.apply(ColorScheme.DARK);

            assertEquals(new PrimerDark().getUserAgentStylesheet(), Application.getUserAgentStylesheet());
        });
    }

    @Test
    void bothThemesStylesheetsCanBeLoaded() throws IOException {
        for (Theme theme : new Theme[] {new PrimerLight(), new PrimerDark()}) {
            String stylesheet = theme.getUserAgentStylesheet();
            try (InputStream css = openStylesheet(stylesheet)) {
                assertTrue(css.read() >= 0, stylesheet + " should not be empty");
            }
        }
    }

    /**
     * Opens a stylesheet the way JavaFX resolves it: as a URL if it is one,
     * otherwise as a resource path inside AtlantaFX's module.
     */
    private static InputStream openStylesheet(String stylesheet) throws IOException {
        if (stylesheet.contains(":")) {
            return URI.create(stylesheet).toURL().openStream();
        }
        InputStream css = Theme.class.getResourceAsStream(stylesheet.startsWith("/") ? stylesheet : "/" + stylesheet);
        if (css == null) {
            throw new IOException("stylesheet not found: " + stylesheet);
        }
        return css;
    }
}
