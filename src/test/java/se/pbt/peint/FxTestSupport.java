package se.pbt.peint;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;

import java.util.concurrent.CountDownLatch;

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
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}