package se.pbt.peint.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSettingsTest {

    private Preferences preferences;

    @BeforeEach
    void useEmptyPreferences() {
        // A node of its own, so the tests never touch the user's real settings.
        preferences = Preferences.userRoot().node("se/pbt/peint/test-" + UUID.randomUUID());
    }

    @AfterEach
    void removePreferences() throws BackingStoreException {
        preferences.removeNode();
    }

    @Test
    void loadGivesTheDefaultsWhenNothingIsStored() {
        assertEquals(AiSettings.DEFAULTS, AiSettings.load(preferences));
        assertEquals(URI.create("http://localhost:11434"), AiSettings.DEFAULTS.address());
        assertEquals("qwen3.5:4b", AiSettings.DEFAULTS.model());
    }

    @Test
    void savedSettingsAreLoadedAgain() {
        AiSettings settings = new AiSettings(URI.create("http://gpu-box:11434"), "gemma3:12b");

        settings.save(preferences);

        assertEquals(settings, AiSettings.load(preferences));
    }

    @Test
    void anInvalidStoredAddressFallsBackToTheDefault() {
        preferences.put("ollamaAddress", "not a url");
        preferences.put("ollamaModel", "llava");

        AiSettings loaded = AiSettings.load(preferences);

        assertEquals(AiSettings.DEFAULTS.address(), loaded.address());
        assertEquals("llava", loaded.model(), "the valid model should still be used");
    }

    @Test
    void aBlankStoredModelFallsBackToTheDefault() {
        preferences.put("ollamaModel", "   ");

        assertEquals(AiSettings.DEFAULT_MODEL, AiSettings.load(preferences).model());
    }

    @Test
    void parseAddressAcceptsHttpAndHttpsUrlsWithAHost() {
        assertEquals(URI.create("http://localhost:11434"), AiSettings.parseAddress(" http://localhost:11434 ").orElseThrow());
        assertTrue(AiSettings.parseAddress("https://ollama.example.com").isPresent());
    }

    @Test
    void parseAddressRejectsAnythingElse() {
        for (String text : new String[] {"", "localhost:11434", "ftp://host", "http://", "http://bad host", "not a url"}) {
            assertTrue(AiSettings.parseAddress(text).isEmpty(), text + " should be rejected");
        }
    }
}
