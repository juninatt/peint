package se.pbt.peint.canvas;

import javafx.scene.image.Image;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A bounded undo/redo history of canvas states.
 *
 * <p>Recording a new state clears the redo history, since redoing something
 * that predates a new action no longer makes sense.</p>
 *
 * <p>Every state is a full snapshot of the canvas, so the history is bounded
 * both by a number of states and by the memory they take up together, undo
 * and redo states alike. The step limit is what applies for an ordinary
 * window-sized canvas; the memory limit takes over for large canvases, such
 * as an opened photo, where a single state can take tens of megabytes. When
 * either limit is exceeded, the oldest undo states are discarded first. The
 * most recent state is always kept, even if it alone exceeds the memory
 * limit, so the last action can still be undone.</p>
 */
public class History {

    /**
     * The default maximum number of states kept on the undo history.
     */
    static final int MAX_STATES = 50;

    /**
     * The default maximum memory, in bytes, all recorded states may take up
     * together.
     */
    static final long MAX_BYTES = 256L * 1024 * 1024;

    private final int maxStates;
    private final long maxBytes;

    private final Deque<Image> undoStack = new ArrayDeque<>();
    private final Deque<Image> redoStack = new ArrayDeque<>();
    private long totalBytes;

    /**
     * Creates a history with the default limits.
     */
    public History() {
        this(MAX_STATES, MAX_BYTES);
    }

    /**
     * Creates a history with the given limits.
     *
     * @param maxStates the maximum number of states kept on the undo history
     * @param maxBytes the maximum memory, in bytes, all states may take up together
     */
    History(int maxStates, long maxBytes) {
        this.maxStates = maxStates;
        this.maxBytes = maxBytes;
    }

    /**
     * Records a new state, making it available to undo, and clears the
     * redo history since it no longer applies once a new action is taken.
     *
     * @param state the state to record
     */
    public void record(Image state) {
        while (!redoStack.isEmpty()) {
            totalBytes -= sizeOf(redoStack.pop());
        }
        pushUndo(state);
        trim();
    }

    /**
     * Moves back to the previous state, if any, pushing the current one
     * onto the redo history so it can be reapplied later.
     *
     * @param currentState the state being moved away from
     * @return the previous state, or {@code null} if there is nothing to undo
     */
    public Image undo(Image currentState) {
        if (undoStack.isEmpty()) return null;
        Image previousState = popUndo();
        redoStack.push(currentState);
        totalBytes += sizeOf(currentState);
        trim();
        return previousState;
    }

    /**
     * Reapplies the most recently undone state, if any, pushing the
     * current one back onto the undo history.
     *
     * @param currentState the state being moved away from
     * @return the next state, or {@code null} if there is nothing to redo
     */
    public Image redo(Image currentState) {
        if (redoStack.isEmpty()) return null;
        Image nextState = redoStack.pop();
        totalBytes -= sizeOf(nextState);
        pushUndo(currentState);
        trim();
        return nextState;
    }

    /**
     * @return {@code true} if {@link #undo(Image)} has a state to move to
     */
    public boolean canUndo() { return !undoStack.isEmpty(); }

    /**
     * @return {@code true} if {@link #redo(Image)} has a state to move to
     */
    public boolean canRedo() { return !redoStack.isEmpty(); }

    /**
     * @return the number of states currently available to undo
     */
    int undoCount() { return undoStack.size(); }

    /**
     * @return the memory, in bytes, all recorded states take up together
     */
    long totalBytes() { return totalBytes; }

    private void pushUndo(Image state) {
        undoStack.push(state);
        totalBytes += sizeOf(state);
    }

    private Image popUndo() {
        Image state = undoStack.pop();
        totalBytes -= sizeOf(state);
        return state;
    }

    /**
     * Discards the oldest undo states until both limits are met again,
     * always keeping at least one state overall.
     */
    private void trim() {
        while (undoStack.size() > maxStates
                || (totalBytes > maxBytes && undoStack.size() + redoStack.size() > 1 && !undoStack.isEmpty())) {
            totalBytes -= sizeOf(undoStack.removeLast());
        }
    }

    /**
     * Estimates the memory a state takes up: four bytes (ARGB) per pixel.
     */
    private static long sizeOf(Image state) {
        return (long) state.getWidth() * (long) state.getHeight() * 4;
    }
}
