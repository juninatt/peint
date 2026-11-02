package se.pbt.peint.canvas;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A bounded undo/redo history of canvas {@link Edit}s.
 *
 * <p>Recording a new edit clears the redo history, since redoing something
 * that predates a new action no longer makes sense.</p>
 *
 * <p>The history is bounded both by a number of edits and by the memory they
 * take up together, undo and redo edits alike. An edit only holds the
 * rectangle it changed, so the step limit is what usually applies; the
 * memory limit takes over for edits covering a large canvas, such as filling
 * or clearing an opened photo. When either limit is exceeded, the oldest undo
 * edits are discarded first. The most recent edit is always kept, even if it
 * alone exceeds the memory limit, so the last action can still be undone.</p>
 */
public class History {

    /**
     * The default maximum number of edits kept on the undo history.
     */
    static final int MAX_EDITS = 50;

    /**
     * The default maximum memory, in bytes, all recorded edits may take up
     * together.
     */
    static final long MAX_BYTES = 256L * 1024 * 1024;

    private final int maxEdits;
    private final long maxBytes;

    private final Deque<Edit> undoStack = new ArrayDeque<>();
    private final Deque<Edit> redoStack = new ArrayDeque<>();
    private long totalBytes;

    /**
     * Creates a history with the default limits.
     */
    public History() {
        this(MAX_EDITS, MAX_BYTES);
    }

    /**
     * Creates a history with the given limits.
     *
     * @param maxEdits the maximum number of edits kept on the undo history
     * @param maxBytes the maximum memory, in bytes, all edits may take up together
     */
    History(int maxEdits, long maxBytes) {
        this.maxEdits = maxEdits;
        this.maxBytes = maxBytes;
    }

    /**
     * Records a new edit, making it available to undo, and clears the redo
     * history since it no longer applies once a new action is taken.
     *
     * @param edit the edit to record
     */
    void record(Edit edit) {
        while (!redoStack.isEmpty()) {
            totalBytes -= redoStack.pop().bytes();
        }
        undoStack.push(edit);
        totalBytes += edit.bytes();
        trim();
    }

    /**
     * Takes the most recent edit off the undo history and makes it available
     * to redo.
     *
     * @return the edit to revert, by drawing its {@link Edit#before()} image
     *         back in place, or {@code null} if there is nothing to undo
     */
    Edit undo() {
        if (undoStack.isEmpty()) return null;
        Edit edit = undoStack.pop();
        redoStack.push(edit);
        return edit;
    }

    /**
     * Takes the most recently undone edit off the redo history and makes it
     * available to undo again.
     *
     * @return the edit to reapply, by drawing its {@link Edit#after()} image
     *         back in place, or {@code null} if there is nothing to redo
     */
    Edit redo() {
        if (redoStack.isEmpty()) return null;
        Edit edit = redoStack.pop();
        undoStack.push(edit);
        trim();
        return edit;
    }

    /**
     * @return {@code true} if {@link #undo()} has an edit to revert
     */
    public boolean canUndo() { return !undoStack.isEmpty(); }

    /**
     * @return {@code true} if {@link #redo()} has an edit to reapply
     */
    public boolean canRedo() { return !redoStack.isEmpty(); }

    /**
     * @return the number of edits currently available to undo
     */
    int undoCount() { return undoStack.size(); }

    /**
     * @return the memory, in bytes, all recorded edits take up together
     */
    long totalBytes() { return totalBytes; }

    /**
     * Discards the oldest undo edits until both limits are met again,
     * always keeping at least one edit overall.
     */
    private void trim() {
        while (undoStack.size() > maxEdits
                || (totalBytes > maxBytes && undoStack.size() + redoStack.size() > 1 && !undoStack.isEmpty())) {
            totalBytes -= undoStack.removeLast().bytes();
        }
    }
}
