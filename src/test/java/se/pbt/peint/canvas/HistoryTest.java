package se.pbt.peint.canvas;

import javafx.scene.image.Image;
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

    private static final int MAX_HISTORY_SIZE = History.MAX_STATES;

    private final History history = new History();

    @BeforeAll
    static void initJfx() {
        FxTestSupport.initToolkit();
    }

    @Test
    @DisplayName("canUndo is false when nothing has been recorded")
    void canUndoIsFalseWithNoRecordedState() {
        assertFalse(history.canUndo());
    }

    @Test
    @DisplayName("canRedo is false when nothing has been recorded")
    void canRedoIsFalseWithNoRecordedState() {
        assertFalse(history.canRedo());
    }

    @Test
    @DisplayName("recording a state makes it available to undo")
    void recordMakesTheStateAvailableToUndo() {
        history.record(newState());

        assertTrue(history.canUndo());
    }

    @Test
    @DisplayName("undo returns the most recently recorded state")
    void undoReturnsTheMostRecentlyRecordedState() {
        history.record(newState());
        Image second = newState();
        history.record(second);

        assertSame(second, history.undo(newState()));
    }

    @Test
    @DisplayName("undo with nothing recorded returns null")
    void undoWithNothingRecordedReturnsNull() {
        assertNull(history.undo(newState()));
    }

    @Test
    @DisplayName("undo with nothing recorded does not make anything redoable")
    void undoWithNothingRecordedDoesNotAffectRedo() {
        history.undo(newState());

        assertFalse(history.canRedo(), "an undo that returns null shouldn't push anything onto redo");
    }

    @Test
    @DisplayName("undo makes the current state available to redo")
    void undoMakesTheCurrentStateAvailableToRedo() {
        history.record(newState());

        history.undo(newState());

        assertTrue(history.canRedo());
    }

    @Test
    @DisplayName("redo returns the state that was just undone")
    void redoReturnsTheStateThatWasUndone() {
        history.record(newState());
        Image current = newState();

        history.undo(current);

        assertSame(current, history.redo(newState()));
    }

    @Test
    @DisplayName("redo with nothing undone returns null")
    void redoWithNothingUndoneReturnsNull() {
        history.record(newState());

        assertNull(history.redo(newState()));
    }

    @Test
    @DisplayName("redo makes the current state available to undo again")
    void redoMakesTheCurrentStateAvailableToUndoAgain() {
        history.record(newState());
        Image undone = history.undo(newState());

        history.redo(undone);

        assertTrue(history.canUndo());
    }

    @Test
    @DisplayName("recording a new state clears the redo history")
    void recordingANewStateClearsRedoHistory() {
        history.record(newState());
        history.undo(newState()); // something is now redoable

        history.record(newState()); // a fresh action should invalidate that redo branch

        assertFalse(history.canRedo());
    }

    @Test
    @DisplayName("undo followed by redo returns to the same states")
    void undoRedoRoundTripReturnsToTheSameStates() {
        Image original = newState();
        Image afterDrawing = newState();
        history.record(original);

        Image undone = history.undo(afterDrawing);
        Image redone = history.redo(undone);

        assertSame(original, undone);
        assertSame(afterDrawing, redone);
    }

    @Test
    @DisplayName("the oldest state is dropped once the history exceeds its size limit")
    void undoHistoryDropsTheOldestEntryBeyondTheLimit() {
        Image oldestState = newState();
        history.record(oldestState);

        // One more than the cap: the (MAX_HISTORY_SIZE + 1)th push evicts oldestState.
        for (int i = 0; i < MAX_HISTORY_SIZE; i++) {
            history.record(newState());
        }

        for (int i = 0; i < MAX_HISTORY_SIZE; i++) {
            Image undone = history.undo(newState());
            assertNotSame(oldestState, undone, "the oldest state should have been evicted once the history filled up");
        }

        assertFalse(history.canUndo(), "exactly MAX_HISTORY_SIZE states should remain after eviction");
    }

    @Test
    @DisplayName("the oldest states are dropped once the history exceeds its memory limit")
    void undoHistoryDropsTheOldestEntriesBeyondTheMemoryLimit() {
        // Each 10x10 state takes 400 bytes; room for three of them.
        History limited = new History(MAX_HISTORY_SIZE, 1200);

        for (int i = 0; i < 5; i++) {
            limited.record(newState(10, 10));
        }

        assertEquals(3, limited.undoCount());
        assertEquals(1200, limited.totalBytes());
    }

    @Test
    @DisplayName("the most recent state is kept even if it alone exceeds the memory limit")
    void mostRecentStateIsKeptEvenIfLargerThanTheMemoryLimit() {
        History limited = new History(MAX_HISTORY_SIZE, 1200);
        limited.record(newState(10, 10));
        Image huge = newState(100, 100); // 40 000 bytes

        limited.record(huge);

        assertEquals(1, limited.undoCount(), "older states should make room for it");
        assertSame(huge, limited.undo(newState()), "the last action should still be undoable");
    }

    @Test
    @DisplayName("redo states count towards the memory limit")
    void redoStatesCountTowardsTheMemoryLimit() {
        History limited = new History(MAX_HISTORY_SIZE, 1200);
        limited.record(newState(10, 10));
        limited.record(newState(10, 10));
        limited.record(newState(10, 10)); // 1200 bytes: exactly at the limit

        limited.undo(newState(10, 10)); // moves one state to redo, adds the current one

        assertTrue(limited.totalBytes() <= 1200, "undoing shouldn't push the history past its limit");
        assertTrue(limited.canRedo(), "the state just undone from should stay redoable");
    }

    @Test
    @DisplayName("undo and redo keep the memory count in step")
    void undoAndRedoKeepTheMemoryCountInStep() {
        History limited = new History(MAX_HISTORY_SIZE, 1_000_000);
        limited.record(newState(10, 10));
        limited.record(newState(20, 20));

        limited.undo(newState(30, 30));
        limited.redo(newState(20, 20));

        // Undo stack: the 10x10 state and the 20x20 current state passed to redo.
        assertEquals(400 + 1600, limited.totalBytes());
    }

    @Test
    @DisplayName("recording a new state releases the memory of the redo history")
    void recordingReleasesTheRedoHistoryMemory() {
        History limited = new History(MAX_HISTORY_SIZE, 1_000_000);
        limited.record(newState(10, 10));
        limited.undo(newState(20, 20)); // undo: empty, redo: 20x20

        limited.record(newState(10, 10));

        assertEquals(400, limited.totalBytes());
    }

    private static Image newState() {
        return newState(1, 1);
    }

    /**
     * Creates a state of the given size; it takes up width * height * 4 bytes.
     */
    private static Image newState(int width, int height) {
        return new WritableImage(width, height);
    }
}