package se.pbt.peint.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescriptionTest {

    @Test
    void fileNameIsTheTitleInLowercaseWithHyphens() {
        assertEquals("sunny-house-adventure", fileName("Sunny House Adventure"));
    }

    @Test
    void fileNameDropsAccentsAndApostrophes() {
        assertEquals("anges-basta-hus", fileName("Ånge's Bästa Hus"));
    }

    @Test
    void fileNameTurnsOtherCharactersIntoSingleSeparators() {
        assertEquals("a-boat-at-sea", fileName("  A boat... at sea?! "));
    }

    @Test
    void fileNameFallsBackToDrawingWhenNothingIsUsable() {
        assertEquals("drawing", fileName("☀️ ✨"));
        assertEquals("drawing", fileName(""));
    }

    @Test
    void aLongFileNameIsCutAtAWordBoundary() {
        String name = fileName("The Very Long Title Of A Drawing Of A House With A Garden");

        assertTrue(name.length() <= Description.MAX_FILE_NAME_LENGTH, name);
        assertFalse(name.endsWith("-"), name);
        assertEquals("the-very-long-title-of-a-drawing-of-a", name);
    }

    private static String fileName(String title) {
        return new Description(title, "text").fileName();
    }
}
