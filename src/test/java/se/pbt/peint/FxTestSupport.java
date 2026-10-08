package se.pbt.peint;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class FxTestSupport {
    private static volatile boolean initialized = false;

    private FxTestSupport() {
    }

    public static synchronized void initToolkit() {
        if (!initialized) {
            new JFXPanel();
            initialized = true;
        }
    }

    public static void runAndWait(Runnable action) {
        initToolkit();
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Rethrow on the calling (test) thread; otherwise a failed assertion
        // inside action would only reach the FX thread's uncaught-exception
        // handler, and the test would still pass.
        Throwable t = failure.get();
        if (t instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (t instanceof Error error) {
            throw error;
        }
        if (t != null) {
            throw new RuntimeException(t);
        }
    }
}