package se.pbt.peint.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaInstallerTest {

    @TempDir
    Path dataDirectory;

    private FakeFileServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new FakeFileServer();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void choosesTheBuildForEachSupportedPlatform() {
        assertEquals("ollama-windows-amd64.zip", fileName(OllamaInstaller.forPlatform("Windows 11", "amd64")));
        assertEquals("ollama-windows-arm64.zip", fileName(OllamaInstaller.forPlatform("Windows 11", "aarch64")));
        assertEquals("ollama-darwin.tgz", fileName(OllamaInstaller.forPlatform("Mac OS X", "aarch64")));
        assertEquals("ollama-darwin.tgz", fileName(OllamaInstaller.forPlatform("Mac OS X", "x86_64")));
        assertTrue(OllamaInstaller.forPlatform("Linux", "amd64").isEmpty(), "Linux installs Ollama through its package manager");
    }

    @Test
    void everyBuildIsPinnedToTheTestedVersionWithAChecksum() {
        for (String[] platform : new String[][] {{"Windows 11", "amd64"}, {"Windows 11", "aarch64"}, {"Mac OS X", "aarch64"}}) {
            OllamaInstaller.Download download = OllamaInstaller.forPlatform(platform[0], platform[1]).orElseThrow();
            assertTrue(download.url().toString().startsWith("https://github.com/ollama/ollama/releases/download/v"
                    + OllamaInstaller.VERSION + "/"), download.url().toString());
            assertTrue(download.sha256().matches("[0-9a-f]{64}"), download.sha256());
            assertTrue(download.size() > 100_000_000, "a real Ollama build is hundreds of megabytes");
        }
    }

    @Test
    void installsADownloadedZip() throws Exception {
        byte[] zip = zip(Map.of("ollama.exe", "the program", "lib/ollama/ggml.dll", "a library"));
        OllamaInstaller installer = installer(zip, sha256(zip), "ollama.exe", true);
        List<Long> progress = new ArrayList<>();

        assertFalse(installer.isInstalled());
        installer.install(progress::add);

        assertTrue(installer.isInstalled());
        assertEquals("the program", Files.readString(installer.executable()));
        assertEquals("a library", Files.readString(installer.executable().resolveSibling("lib/ollama/ggml.dll")));
        assertEquals((long) zip.length, progress.getLast(), "progress should end at the full size");
        assertOnlyTheInstallationIsLeft();
    }

    @Test
    void installsADownloadedTarGz() throws Exception {
        byte[] tgz = tarGz("ollama", "the program");
        OllamaInstaller installer = installer(tgz, sha256(tgz), "ollama", false);

        installer.install(bytes -> { });

        assertTrue(installer.isInstalled());
        assertEquals("the program", Files.readString(installer.executable()).strip());
    }

    @Test
    void rejectsADownloadWithTheWrongChecksum() throws Exception {
        byte[] zip = zip(Map.of("ollama.exe", "a changed program"));
        OllamaInstaller installer = installer(zip, sha256("the real program".getBytes()), "ollama.exe", true);

        IOException error = assertThrows(IOException.class, () -> installer.install(bytes -> { }));

        assertTrue(error.getMessage().contains("checksum"), error.getMessage());
        assertFalse(installer.isInstalled());
        assertOnlyTheInstallationIsLeft();
    }

    @Test
    void rejectsAnArchiveEntryOutsideTheTargetDirectory() throws Exception {
        byte[] zip = zip(Map.of("ollama.exe", "the program", "../../escaped.txt", "zip slip"));
        OllamaInstaller installer = installer(zip, sha256(zip), "ollama.exe", true);

        IOException error = assertThrows(IOException.class, () -> installer.install(bytes -> { }));

        assertTrue(error.getMessage().contains("outside the target directory"), error.getMessage());
        assertFalse(Files.exists(dataDirectory.resolve("escaped.txt")));
        assertFalse(Files.exists(dataDirectory.getParent().resolve("escaped.txt")));
        assertFalse(installer.isInstalled());
    }

    @Test
    void rejectsAnArchiveWithoutTheProgram() throws Exception {
        byte[] zip = zip(Map.of("README.txt", "no program here"));
        OllamaInstaller installer = installer(zip, sha256(zip), "ollama.exe", true);

        assertThrows(IOException.class, () -> installer.install(bytes -> { }));
        assertFalse(installer.isInstalled());
    }

    @Test
    void reportsAFailedDownload() {
        OllamaInstaller installer = new OllamaInstaller(dataDirectory, new OllamaInstaller.Download(
                server.serve("/elsewhere", new byte[0]).resolve("/missing.zip"), 10, "00", "ollama.exe", true));

        IOException error = assertThrows(IOException.class, () -> installer.install(bytes -> { }));

        assertTrue(error.getMessage().contains("HTTP 404"), error.getMessage());
    }

    @Test
    void interruptingTheInstallingThreadCancelsTheDownload() throws Exception {
        byte[] zip = zip(Map.of("ollama.exe", "x".repeat(400_000))); // about 100 chunks of 4 KB
        URI url = server.serve("/slow.zip", zip, 50);
        OllamaInstaller installer = new OllamaInstaller(dataDirectory,
                new OllamaInstaller.Download(url, zip.length, sha256(zip), "ollama.exe", true));
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                installer.install(bytes -> started.countDown());
            } catch (Throwable t) {
                outcome.set(t);
            }
        });

        thread.start();
        started.await();
        thread.interrupt();
        thread.join(5_000);

        assertFalse(thread.isAlive(), "the download should stop soon after the interruption");
        assertInstanceOf(InterruptedException.class, outcome.get());
        assertFalse(installer.isInstalled());
        assertOnlyTheInstallationIsLeft();
    }

    private OllamaInstaller installer(byte[] archive, String sha256, String executable, boolean zip) {
        URI url = server.serve(zip ? "/ollama.zip" : "/ollama.tgz", archive);
        return new OllamaInstaller(dataDirectory, new OllamaInstaller.Download(url, archive.length, sha256, executable, zip));
    }

    /**
     * Checks that no partial download or half-unpacked directory is left
     * next to the installation.
     */
    private void assertOnlyTheInstallationIsLeft() throws IOException {
        try (var entries = Files.list(dataDirectory)) {
            List<String> names = entries.map(path -> path.getFileName().toString()).toList();
            assertTrue(names.stream().allMatch(name -> name.equals("ollama-" + OllamaInstaller.VERSION)), names.toString());
        }
    }

    private static String fileName(Optional<OllamaInstaller.Download> download) {
        String url = download.orElseThrow().url().toString();
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private static byte[] zip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /**
     * Makes a gzipped tar archive with one file, using the system's tar, as
     * Ollama's macOS build is.
     */
    private byte[] tarGz(String name, String content) throws IOException, InterruptedException {
        Path source = Files.createTempDirectory(dataDirectory.getParent(), "tar-source");
        Files.writeString(source.resolve(name), content);
        Path archive = source.resolveSibling(source.getFileName() + ".tgz");
        // Relative paths, as in OllamaInstaller.untar(): GNU tar reads "C:\..." as a remote host.
        Process tar = new ProcessBuilder("tar", "-czf", archive.getFileName().toString(),
                "-C", source.getFileName().toString(), name)
                .directory(source.getParent().toFile())
                .redirectErrorStream(true).start();
        tar.getInputStream().readAllBytes();
        assertEquals(0, tar.waitFor(), "tar should be available to the tests");
        return Files.readAllBytes(archive);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
