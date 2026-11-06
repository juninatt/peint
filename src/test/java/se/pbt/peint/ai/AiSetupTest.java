package se.pbt.peint.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSetupTest {

    private static final String MODEL = "qwen3.5:4b";
    private static final String WITH_MODEL = "{\"models\":[{\"name\":\"qwen3.5:4b\"}]}";
    private static final String WITHOUT_MODEL = "{\"models\":[]}";

    @TempDir
    Path dataDirectory;

    // Ollama's usual address on this computer, where nothing answers until a test says so.
    private URI defaultAddress;

    private final List<AutoCloseable> servers = new ArrayList<>();
    private final List<Path> started = new ArrayList<>();
    private FakeOllama startedOllama;

    @BeforeEach
    void pickAnAddressWhereNothingAnswers() throws IOException {
        defaultAddress = URI.create("http://127.0.0.1:" + freePort());
    }

    @AfterEach
    void stopServers() throws Exception {
        for (AutoCloseable server : servers) {
            server.close();
        }
    }

    @Test
    void readyWhenOllamaAnswersWithTheModel() throws Exception {
        FakeOllama ollama = runningOllama(WITH_MODEL);

        AiSetup setup = setup(ollama.address(), List.of(), Optional.empty());

        assertEquals(new AiSetup.State.Ready(ollama.address()), setup.check());
        assertEquals(ollama.address(), setup.prepare(progress -> { }), "nothing should need doing");
        assertTrue(started.isEmpty());
    }

    @Test
    void theModelIsMissingWhenOllamaAnswersWithoutIt() throws Exception {
        FakeOllama ollama = runningOllama(WITHOUT_MODEL);

        assertEquals(new AiSetup.State.ModelMissing(ollama.address()),
                setup(ollama.address(), List.of(), Optional.empty()).check());
    }

    @Test
    void anotherAddressThatDoesntAnswerIsUnreachable() throws Exception {
        URI gpuBox = URI.create("http://127.0.0.1:" + freePort());
        Path installed = installedProgram();
        AiSetup setup = setup(gpuBox, List.of(installed), Optional.of(installer()));

        assertEquals(new AiSetup.State.Unreachable(gpuBox), setup.check(),
                "a chosen server shouldn't be swapped for a local Ollama");
        IOException error = assertThrows(IOException.class, () -> setup.prepare(progress -> { }));
        assertTrue(error.getMessage().contains("change the address in AI Settings"), error.getMessage());
        assertTrue(started.isEmpty());
    }

    @Test
    void peintsOwnDownloadedOllamaIsStartedFirst() throws Exception {
        OllamaInstaller installer = installer();
        installer.install(bytes -> { });

        AiSetup setup = setup(defaultAddress, List.of(installedProgram()), Optional.of(installer));

        assertEquals(new AiSetup.State.CanStart(installer.executable()), setup.check());
    }

    @Test
    void anInstalledOllamaCanBeStarted() throws Exception {
        Path installed = installedProgram();

        assertEquals(new AiSetup.State.CanStart(installed),
                setup(defaultAddress, List.of(dataDirectory.resolve("missing.exe"), installed), Optional.of(installer())).check());
    }

    @Test
    void ollamaMustBeDownloadedWhenNothingIsInstalled() throws Exception {
        OllamaInstaller installer = installer();

        assertEquals(new AiSetup.State.NeedsDownload(installer.downloadSize()),
                setup(defaultAddress, List.of(dataDirectory.resolve("missing.exe")), Optional.of(installer)).check());
    }

    @Test
    void unsupportedWhenOllamaCanNeitherBeFoundNorDownloaded() throws Exception {
        AiSetup setup = setup(defaultAddress, List.of(), Optional.empty());

        assertInstanceOf(AiSetup.State.Unsupported.class, setup.check());
        IOException error = assertThrows(IOException.class, () -> setup.prepare(progress -> { }));
        assertTrue(error.getMessage().contains("https://ollama.com"), error.getMessage());
    }

    @Test
    void prepareDownloadsStartsAndFetchesTheModelInTurn() throws Exception {
        OllamaInstaller installer = installer();
        AiSetup setup = setup(defaultAddress, List.of(), Optional.of(installer));
        List<AiSetup.Progress> progress = new ArrayList<>();

        URI address = setup.prepare(progress::add);

        assertEquals(startedOllama.address(), address);
        assertEquals(List.of(installer.executable()), started, "the downloaded Ollama should be started");
        assertTrue(installer.isInstalled());
        List<String> steps = progress.stream().map(AiSetup.Progress::step).distinct().toList();
        assertEquals(List.of("Downloading Ollama", "Starting Ollama", "Downloading the model " + MODEL), steps);
        assertEquals("{\"model\":\"qwen3.5:4b\",\"stream\":true}", startedOllama.requests().getLast().body());
        assertEquals(new AiSetup.State.Ready(startedOllama.address()), setup.check(),
                "once started, Peint's own Ollama should be found again");
    }

    @Test
    void startStartsOllamaButLeavesAMissingModelForTheUserToDecide() throws Exception {
        Path installed = installedProgram();
        AiSetup setup = setup(defaultAddress, List.of(installed), Optional.empty());

        AiSetup.State state = setup.start();

        assertEquals(List.of(installed), started);
        assertEquals(new AiSetup.State.ModelMissing(startedOllama.address()), state);
        assertTrue(startedOllama.requests().stream().noneMatch(request -> request.path().equals("/api/pull")),
                "nothing large should be downloaded without asking");
    }

    @Test
    void startDoesNothingWhenOllamaMustBeDownloaded() throws Exception {
        OllamaInstaller installer = installer();
        AiSetup setup = setup(defaultAddress, List.of(), Optional.of(installer));

        assertEquals(new AiSetup.State.NeedsDownload(installer.downloadSize()), setup.start());
        assertTrue(started.isEmpty());
    }

    @Test
    void prepareSkipsTheModelWhenTheStartedOllamaHasIt() throws Exception {
        AiSetup setup = setup(defaultAddress, List.of(installedProgram()), Optional.empty(), WITH_MODEL);

        setup.prepare(progress -> { });

        assertTrue(startedOllama.requests().stream().noneMatch(request -> request.path().equals("/api/pull")));
    }

    private AiSetup setup(URI address, List<Path> installedPrograms, Optional<OllamaInstaller> installer) {
        return setup(address, installedPrograms, installer, WITHOUT_MODEL);
    }

    /**
     * Sets up AI with a starter that, instead of running a program, starts
     * a fake Ollama with the given models, then reports what it was asked to start.
     */
    private AiSetup setup(URI address, List<Path> installedPrograms, Optional<OllamaInstaller> installer, String startedModels) {
        AiSetup.Starter starter = program -> {
            started.add(program);
            startedOllama = new FakeOllama()
                    .answerInTurn("/api/tags", 200, startedModels, WITH_MODEL)
                    .answerStreamed("/api/pull", 0, "{\"status\":\"pulling manifest\"}", "{\"status\":\"success\"}");
            servers.add(startedOllama);
            return startedOllama.address();
        };
        return new AiSetup(new AiSettings(address, MODEL), defaultAddress, installedPrograms, installer, starter);
    }

    private FakeOllama runningOllama(String models) throws IOException {
        FakeOllama ollama = new FakeOllama().answer("/api/tags", 200, models);
        servers.add(ollama);
        return ollama;
    }

    private Path installedProgram() throws IOException {
        Path program = dataDirectory.resolve("Programs").resolve("Ollama").resolve("ollama.exe");
        Files.createDirectories(program.getParent());
        Files.writeString(program, "an installed Ollama");
        return program;
    }

    /**
     * An installer that downloads a small fake Ollama from a fake download site.
     */
    private OllamaInstaller installer() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("ollama.exe"));
            zip.write("Peint's own Ollama".getBytes());
            zip.closeEntry();
        }
        byte[] archive = bytes.toByteArray();
        FakeFileServer site = new FakeFileServer();
        servers.add(site);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive));
        return new OllamaInstaller(dataDirectory.resolve("Peint"), new OllamaInstaller.Download(
                site.serve("/ollama.zip", archive), archive.length, sha256, "ollama.exe", true));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
