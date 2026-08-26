package se.pbt.peint.canvas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("History")
class HistoryTest {

    // Mirrors History's own private MAX_HISTORY_SIZE. Keep in sync if that
    // constant changes.
    private static final int MAX_HISTORY_SIZE = 50;

    private final History history = new History();

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
        BufferedImage second = newState();
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
        BufferedImage current = newState();

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
        BufferedImage undone = history.undo(newState());

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
        BufferedImage original = newState();
        BufferedImage afterDrawing = newState();
        history.record(original);

        BufferedImage undone = history.undo(afterDrawing);
        BufferedImage redone = history.redo(undone);

        assertSame(original, undone);
        assertSame(afterDrawing, redone);
    }

    @Test
    @DisplayName("the oldest state is dropped once the history exceeds its size limit")
    void undoHistoryDropsTheOldestEntryBeyondTheLimit() {
        BufferedImage oldestState = newState();
        history.record(oldestState);

        // One more than the cap: the (MAX_HISTORY_SIZE + 1)th push evicts oldestState.
        for (int i = 0; i < MAX_HISTORY_SIZE; i++) {
            history.record(newState());
        }

        for (int i = 0; i < MAX_HISTORY_SIZE; i++) {
            BufferedImage undone = history.undo(newState());
            assertNotSame(oldestState, undone, "the oldest state should have been evicted once the history filled up");
        }

        assertFalse(history.canUndo(), "exactly MAX_HISTORY_SIZE states should remain after eviction");
    }

    private static BufferedImage newState() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    }
}