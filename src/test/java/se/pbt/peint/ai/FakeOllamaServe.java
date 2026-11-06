package se.pbt.peint.ai;

/**
 * Stands in for the {@code ollama serve} program in tests of
 * {@link OllamaServer}: run in a process of its own, it listens where
 * {@code OLLAMA_HOST} says, as Ollama does, until it is stopped.
 *
 * <p>With the argument {@code silent}, it runs without ever answering, like
 * an Ollama that hangs while starting.</p>
 */
public final class FakeOllamaServe {

    private FakeOllamaServe() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("fake ollama starting");
        if (args.length > 0 && args[0].equals("silent")) {
            Thread.sleep(Long.MAX_VALUE);
        }
        String host = System.getenv("OLLAMA_HOST"); // e.g. 127.0.0.1:11435
        int port = Integer.parseInt(host.substring(host.lastIndexOf(':') + 1));
        new FakeOllama(port);
        System.out.println("fake ollama listening on " + port);
        Thread.sleep(Long.MAX_VALUE);
    }
}
