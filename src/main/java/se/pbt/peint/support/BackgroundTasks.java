package se.pbt.peint.support;

import javafx.concurrent.Task;

/**
 * Runs JavaFX {@link Task}s on background threads.
 *
 * <p>Keeps slow work such as file I/O off the JavaFX Application Thread, so
 * the UI stays responsive. A {@link Task}'s own success/failure callbacks
 * still run back on the Application Thread as usual.</p>
 */
public final class BackgroundTasks {

    private BackgroundTasks() {
    }

    /**
     * Runs the given task on a new daemon thread.
     *
     * @param task the task to run
     * @param threadName the name to give the background thread
     */
    public static void run(Task<?> task, String threadName) {
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }
}