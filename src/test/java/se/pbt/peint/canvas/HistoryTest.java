package se.pbt.peint.canvas;

import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.pbt.peint.FxTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("History")
class HistoryTest {

    private static final int MAX_HISTORY_SIZE = History.MAX_EDITS;

    private final History history = new History();

    @BeforeAll
    static void initJfx() {
        FxTestSupport.initToolkit();
    }

    @Test
    @DisplayName("canUndo is false when nothing has been recorded")
    void canUndoIsFalseWithNoRecordedEdit() {
        assertFalse(history.canUndo());
    }

    @Test
    @DisplayName("canRedo is false when nothing has been recorded")
    void canRedoIsFalseWithNoRecordedEdit() {
        assertFalse(history.canRedo());
    }

    @Test
    @DisplayName("recording an edit makes it available to undo")
    void recordMakesTheEditAvailableToUndo() {
        history.record(newEdit());

        assertTrue(history.canUndo());
    }

    @Test
    @DisplayName("undo returns the most recently recorded edit")
    void undoReturnsTheMostRecentlyRecordedEdit() {
        history.record(newEdit());
        Edit second = newEdit();
        history.record(second);

        assertSame(second, history.undo());
    }

    @Test
    @DisplayName("undo with nothing recorded returns null")
    void undoWithNothingRecordedReturnsNull() {
        assertNull(history.undo());
    }

    @Test
    @DisplayName("undo with nothing recorded does not make anything redoable")
    void undoWithNothingRecordedDoesNotAffectRedo() {
        history.undo();

        assertFalse(history.canRedo(), "an undo that returns null shouldn't push anything onto redo");
    }

    @Test
    @DisplayName("undo makes the edit available to redo")
    void undoMakesTheEditAvailableToRedo() {
        history.record(newEdit());

        history.undo();

        assertTrue(history.canRedo());
    }

    @Test
    @DisplayName("redo returns the edit that was just undone")
    void redoReturnsTheEditThatWasUndone() {
        Edit edit = newEdit();
        history.record(edit);

        history.undo();

        assertSame(edit, history.redo());
    }

    @Test
    @DisplayName("redo with nothing undone returns null")
    void redoWithNothingUndoneReturnsNull() {
        history.record(newEdit());

        assertNull(history.redo());
    }

    @Test
    @DisplayName("redo makes the edit available to undo again")
    void redoMakesTheEditAvailableToUndoAgain() {
        history.record(newEdit());
        history.undo();

        history.redo();

        assertTrue(history.canUndo());
    }

    @Test
    @DisplayName("recording a new edit clears the redo history")
    void recordingANewEditClearsRedoHistory() {
        history.record(newEdit());
        history.undo(); // something is now redoable

        history.record(newEdit()); // a fresh action should invalidate that redo branch

        assertFalse(history.canRedo());
    }

    @Test
    @DisplayName("undo and redo walk back and forth through edits in order")
    void undoAndRedoWalkThroughEditsInOrder() {
        Edit first = newEdit();
        Edit second = newEdit();
        history.record(first);
        history.record(second);

        assertSame(second, history.undo());
        assertSame(first, history.undo());
        assertSame(first, history.redo());
        assertSame(second, history.redo());
    }

    @Test
    @DisplayName("the oldest edit is dropped once the history exceeds its size limit")
    void undoHistoryDropsTheOldestEntryBeyondTheLimit() {
        Edit oldestEdit = newEdit();
        history.record(oldestEdit);

        // One more than the cap: the (MAX_HISTORY_SIZE + 1)th push evicts oldestEdit.
        for (int i = 0; i < MAX_HISTORY_SIZE; i++) {
            history.record(newEdit());
        }

        for (int i = 0; i < MAX_HISTORY_SIZE; i++) {
            Edit undone = history.undo();
            assertNotSame(oldestEdit, undone, "the oldest edit should have been evicted once the history filled up");
        }

        assertFalse(history.canUndo(), "exactly MAX_HISTORY_SIZE edits should remain after eviction");
    }

    @Test
    @DisplayName("the oldest edits are dropped once the history exceeds its memory limit")
    void undoHistoryDropsTheOldestEntriesBeyondTheMemoryLimit() {
        // Each 10x10 edit takes 800 bytes (before and after); room for three of them.
        History limited = new History(MAX_HISTORY_SIZE, 2400);

        for (int i = 0; i < 5; i++) {
            limited.record(newEdit(10, 10));
        }

        assertEquals(3, limited.undoCount());
        assertEquals(2400, limited.totalBytes());
    }

    @Test
    @DisplayName("the most recent edit is kept even if it alone exceeds the memory limit")
    void mostRecentEditIsKeptEvenIfLargerThanTheMemoryLimit() {
        History limited = new History(MAX_HISTORY_SIZE, 2400);
        limited.record(newEdit(10, 10));
        Edit huge = newEdit(100, 100); // 80 000 bytes

        limited.record(huge);

        assertEquals(1, limited.undoCount(), "older edits should make room for it");
        assertSame(huge, limited.undo(), "the last action should still be undoable");
    }

    @Test
    @DisplayName("undone edits still count towards the memory limit")
    void redoEditsCountTowardsTheMemoryLimit() {
        History limited = new History(MAX_HISTORY_SIZE, 1_000_000);
        limited.record(newEdit(10, 10));
        limited.record(newEdit(20, 20));

        limited.undo();

        assertEquals(800 + 3200, limited.totalBytes(), "an undone edit is kept for redo");
    }

    @Test
    @DisplayName("recording a new edit releases the memory of the redo history")
    void recordingReleasesTheRedoHistoryMemory() {
        History limited = new History(MAX_HISTORY_SIZE, 1_000_000);
        limited.record(newEdit(20, 20));
        limited.undo();

        limited.record(newEdit(10, 10));

        assertEquals(800, limited.totalBytes());
    }

    @Test
    @DisplayName("a small edit takes up only the memory of the rectangle it changed")
    void anEditTakesUpOnlyItsRectangle() {
        Edit stroke = newEdit(80, 20);

        assertEquals(2L * 80 * 20 * 4, stroke.bytes());
    }

    private static Edit newEdit() {
        return newEdit(1, 1);
    }

    /**
     * Creates an edit of the given size; it takes up width * height * 8 bytes.
     */
    private static Edit newEdit(int width, int height) {
        return new Edit(0, 0, new WritableImage(width, height), new WritableImage(width, height));
    }
}
