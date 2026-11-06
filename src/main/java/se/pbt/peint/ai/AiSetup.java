package se.pbt.peint.ai;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Finds the quickest way to working AI, and takes it: an Ollama that
 * already runs, one that is installed and can be started, or one Peint
 * downloads itself, with the model downloaded if it's missing.
 *
 * <p>Ollama is only started or downloaded for the default address on this
 * computer. A user who has pointed Peint at another server, such as a
 * computer with a graphics card, is told it can't be reached instead of
 * quietly getting a slower local copy.</p>
 */
public final class AiSetup {

    /** Roughly how large the default model is, to say before downloading it. */
    public static final long DEFAULT_MODEL_SIZE = 3_300_000_000L;

    /** What it takes to get to working AI. */
    public sealed interface State {

        /** Ollama answers at the address and has the model: nothing to do. */
        record Ready(URI address) implements State {
        }

        /** Ollama answers at the address, but the model must be downloaded. */
        record ModelMissing(URI address) implements State {
        }

        /** An Ollama program is on this computer and can be started. */
        record CanStart(Path program) implements State {
        }

        /** Ollama must be downloaded, the given number of bytes, then started. */
        record NeedsDownload(long bytes) implements State {
        }

        /** A server at another address than the default doesn't answer. */
        record Unreachable(URI address) implements State {
        }

        /** No Ollama here, and Peint can't download it for this computer. */
        record Unsupported() implements State {
        }
    }

    /** How far setting up has come, to show the user. */
    public record Progress(String step, double fraction) {
    }

    /**
     * Starts an Ollama program in the background, for as long as Peint runs.
     */
    @FunctionalInterface
    public interface Starter {
        /**
         * @return the address where the started Ollama answers
         */
        URI start(Path program) throws IOException, InterruptedException;
    }

    private final AiSettings settings;
    private final URI defaultAddress;
    private final List<Path> installedPrograms;
    private final Optional<OllamaInstaller> installer;
    private final Starter starter;
    private URI startedAddress;

    /**
     * Sets up AI for this computer, with Ollama's usual address as the one
     * for which Ollama may be started or downloaded here.
     *
     * @param settings the address and model the user chose
     * @param installedPrograms where an installed Ollama program may be, in
     *                          order of preference; see {@link #installedPrograms()}
     * @param installer downloads Ollama for this computer, if Peint can
     * @param starter starts an Ollama program in the background
     */
    public AiSetup(AiSettings settings, List<Path> installedPrograms, Optional<OllamaInstaller> installer, Starter starter) {
        this(settings, AiSettings.DEFAULTS.address(), installedPrograms, installer, starter);
    }

    /**
     * @param defaultAddress the address for which Ollama may be started or
     *                       downloaded on this computer
     */
    AiSetup(AiSettings settings, URI defaultAddress, List<Path> installedPrograms, Optional<OllamaInstaller> installer,
            Starter starter) {
        this.settings = settings;
        this.defaultAddress = defaultAddress;
        this.installedPrograms = installedPrograms;
        this.installer = installer;
        this.starter = starter;
    }

    /**
     * Works out what it takes to get to working AI, without changing
     * anything. Quick: only asks local servers whether they answer.
     */
    public State check() throws IOException, InterruptedException {
        for (URI address : candidateAddresses()) {
            OllamaClient client = new OllamaClient(address);
            if (client.isAvailable()) {
                return client.hasModel(settings.model()) ? new State.Ready(address) : new State.ModelMissing(address);
            }
        }
        if (!usesDefaultAddress()) {
            return new State.Unreachable(settings.address());
        }
        if (installer.isPresent() && installer.get().isInstalled()) {
            return new State.CanStart(installer.get().executable());
        }
        for (Path program : installedPrograms) {
            if (Files.isRegularFile(program)) {
                return new State.CanStart(program);
            }
        }
        return installer.<State>map(it -> new State.NeedsDownload(it.downloadSize())).orElseGet(State.Unsupported::new);
    }

    /**
     * Does whatever is missing: downloads and starts Ollama, and downloads
     * the model. Blocks until done, so call it from a background thread.
     *
     * @param progress told how far setting up has come
     * @return the address where Ollama, with the model, now answers
     * @throws IOException if a step fails, or Ollama can't be set up here
     * @throws InterruptedException if the calling thread is interrupted,
     *                              which cancels setting up
     */
    public URI prepare(Consumer<Progress> progress) throws IOException, InterruptedException {
        State state = check();
        if (state instanceof State.NeedsDownload(long bytes)) {
            OllamaInstaller ollama = installer.orElseThrow();
            progress.accept(new Progress("Downloading Ollama", 0));
            ollama.install(received -> progress.accept(new Progress("Downloading Ollama", (double) received / bytes)));
            state = new State.CanStart(ollama.executable());
        }
        if (state instanceof State.CanStart(Path program)) {
            progress.accept(new Progress("Starting Ollama", -1));
            startedAddress = starter.start(program);
            OllamaClient started = new OllamaClient(startedAddress);
            state = started.hasModel(settings.model()) ? new State.Ready(startedAddress) : new State.ModelMissing(startedAddress);
        }
        if (state instanceof State.ModelMissing(URI address)) {
            progress.accept(new Progress("Downloading the model " + settings.model(), -1));
            new OllamaClient(address).pull(settings.model(),
                    pulled -> progress.accept(new Progress("Downloading the model " + settings.model(), pulled.fraction())));
            state = new State.Ready(address);
        }
        return switch (state) {
            case State.Ready(URI address) -> address;
            case State.Unreachable(URI address) -> throw new IOException("Ollama doesn't answer at " + address
                    + ". Start it there, or change the address in AI Settings.");
            case State.Unsupported() -> throw new IOException("Peint can't download Ollama for this computer. "
                    + "Install it from https://ollama.com, for example with your package manager, and try again.");
            default -> throw new IllegalStateException("Setting up AI stopped at " + state);
        };
    }

    /**
     * Where to look for a running Ollama: the user's address, and Peint's
     * own server if it has started one.
     */
    private List<URI> candidateAddresses() {
        if (startedAddress != null && usesDefaultAddress()) {
            return List.of(settings.address(), startedAddress);
        }
        return List.of(settings.address());
    }

    private boolean usesDefaultAddress() {
        return settings.address().equals(defaultAddress);
    }

    /**
     * Where Ollama's own installer puts its program on this computer:
     * {@code %LOCALAPPDATA%\Programs\Ollama} on Windows, the Ollama app on
     * macOS, and the usual install locations on Linux.
     */
    public static List<Path> installedPrograms() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            return localAppData == null ? List.of()
                    : List.of(Path.of(localAppData, "Programs", "Ollama", "ollama.exe"));
        }
        if (os.startsWith("mac")) {
            return List.of(Path.of("/Applications/Ollama.app/Contents/Resources/ollama"), Path.of("/usr/local/bin/ollama"));
        }
        return List.of(Path.of("/usr/local/bin/ollama"), Path.of("/usr/bin/ollama"));
    }
}
