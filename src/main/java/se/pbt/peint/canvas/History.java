package se.pbt.peint.canvas;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A bounded undo/redo history of canvas states.
 *
 * <p>Recording a new state clears the redo history, since redoing something
 * that predates a new action no longer makes sense. The undo history is
 * capped at {@value #MAX_HISTORY_SIZE} entries so a long session doesn't
 * hold an unbounded number of full-canvas snapshots in memory.</p>
 */
public class History {

    /**
     * The maximum number of states kept on the undo history before the
     * oldest one is discarded.
     */
    private static final int MAX_HISTORY_SIZE = 50;

    private final Deque<BufferedImage> undoStack = new ArrayDeque<>();
    private final Deque<BufferedImage> redoStack = new ArrayDeque<>();

    /**
     * Records a new state, making it available to undo, and clears the
     * redo history since it no longer applies once a new action is taken.
     *
     * @param state the state to record
     */
    public void record(BufferedImage state) {
        undoStack.push(state);
        if (undoStack.size() > MAX_HISTORY_SIZE) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    /**
     * Moves back to the previous state, if any, pushing the current one
     * onto the redo history so it can be reapplied later.
     *
     * @param currentState the state being moved away from
     * @return the previous state, or {@code null} if there is nothing to undo
     */
    public BufferedImage undo(BufferedImage currentState) {
        if (undoStack.isEmpty()) return null;
        redoStack.push(currentState);
        return undoStack.pop();
    }

    /**
     * Reapplies the most recently undone state, if any, pushing the
     * current one back onto the undo history.
     *
     * @param currentState the state being moved away from
     * @return the next state, or {@code null} if there is nothing to redo
     */
    public BufferedImage redo(BufferedImage currentState) {
        if (redoStack.isEmpty()) return null;
        undoStack.push(currentState);
        return redoStack.pop();
    }

    /**
     * @return {@code true} if {@link #undo(BufferedImage)} has a state to move to
     */
    public boolean canUndo() { return !undoStack.isEmpty(); }

    /**
     * @return {@code true} if {@link #redo(BufferedImage)} has a state to move to
     */
    public boolean canRedo() { return !redoStack.isEmpty(); }
}