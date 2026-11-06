package se.pbt.peint.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads Ollama's standalone build for this computer into a directory of
 * Peint's own, so the AI features work without installing anything by hand.
 *
 * <p>The version is pinned, and each download's SHA-256 checksum is part of
 * Peint, so only the exact files Peint was tested with are ever run: a file
 * changed on the way, or replaced at its source, is rejected. Updating
 * Ollama therefore takes a new Peint release.</p>
 *
 * <p>Linux is not covered: Ollama's Linux build is compressed with zstd,
 * which Java can't unpack, and on Linux Ollama is usually installed through
 * the package manager anyway.</p>
 */
public final class OllamaInstaller {

    /** The Ollama version Peint downloads. */
    public static final String VERSION = "0.40.15";

    private static final String RELEASES = "https://github.com/ollama/ollama/releases/download/v" + VERSION + "/";

    // Marks a complete installation; written last, after everything is in place.
    private static final String COMPLETE_MARKER = ".complete";

    /**
     * One of Ollama's standalone builds.
     *
     * @param url where to download it
     * @param size its size in bytes
     * @param sha256 its SHA-256 checksum, as published with the release
     * @param executable where the {@code ollama} program is, inside the archive
     * @param zip whether it's a zip archive, rather than a gzipped tar archive
     */
    public record Download(URI url, long size, String sha256, String executable, boolean zip) {
    }

    private final Path directory;
    private final Download download;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL) // GitHub serves release files from another host
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * @param directory where to install, e.g. Peint's data directory; each
     *                  version goes into a directory of its own inside it
     * @param download the build to install
     */
    public OllamaInstaller(Path directory, Download download) {
        this.directory = directory.resolve("ollama-" + VERSION);
        this.download = download;
    }

    /**
     * Returns Ollama's standalone build for the given operating system and
     * processor, or empty where Peint can't install Ollama by itself.
     */
    public static Optional<Download> forPlatform(String osName, String osArch) {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = osArch.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows") && (arch.equals("amd64") || arch.equals("x86_64"))) {
            return Optional.of(new Download(URI.create(RELEASES + "ollama-windows-amd64.zip"), 1_614_941_966L,
                    "9ca74a7ab66ec86870b1eecac7df160df08dc4e3a291ab5d2d59bcf9310221c6", "ollama.exe", true));
        }
        if (os.startsWith("windows") && (arch.equals("aarch64") || arch.equals("arm64"))) {
            return Optional.of(new Download(URI.create(RELEASES + "ollama-windows-arm64.zip"), 217_149_782L,
                    "35ce5195b57496dbc9ecbaa6593807e6ac26cbb53cf1f8bb57ac58daafa58574", "ollama.exe", true));
        }
        if (os.startsWith("mac")) {
            return Optional.of(new Download(URI.create(RELEASES + "ollama-darwin.tgz"), 185_971_671L,
                    "607c79d5d6315ef9d896db83adde736a4c0057196329843544e79b95afb260a8", "ollama", false));
        }
        return Optional.empty();
    }

    /**
     * @return Ollama's standalone build for this computer, or empty if Peint
     *         can't install Ollama here by itself
     */
    public static Optional<Download> forThisComputer() {
        return forPlatform(System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    /**
     * @return how many bytes installing will download
     */
    public long downloadSize() {
        return download.size();
    }

    /**
     * @return the {@code ollama} program, once installed
     */
    public Path executable() {
        return directory.resolve(download.executable());
    }

    /**
     * @return whether a complete installation is in place
     */
    public boolean isInstalled() {
        return Files.exists(directory.resolve(COMPLETE_MARKER)) && Files.exists(executable());
    }

    /**
     * Downloads, checks and unpacks Ollama. Blocks until done, so call it
     * from a background thread. Nothing is left behind if it fails or is
     * cancelled.
     *
     * @param progress told how many bytes have been downloaded so far
     * @throws IOException if the download fails, has the wrong size or
     *                     checksum, or can't be unpacked
     * @throws InterruptedException if the calling thread is interrupted,
     *                              which cancels the download
     */
    public void install(LongConsumer progress) throws IOException, InterruptedException {
        Files.createDirectories(directory.getParent());
        Path archive = directory.resolveSibling(directory.getFileName() + (download.zip() ? ".zip.part" : ".tgz.part"));
        Path unpacked = directory.resolveSibling(directory.getFileName() + ".unpacking");
        try {
            downloadAndCheck(archive, progress);
            deleteRecursively(unpacked);
            Files.createDirectories(unpacked);
            if (download.zip()) {
                unzip(archive, unpacked);
            } else {
                untar(archive, unpacked);
            }
            if (!Files.exists(unpacked.resolve(download.executable()))) {
                throw new IOException("The Ollama download had no " + download.executable());
            }
            Files.writeString(unpacked.resolve(COMPLETE_MARKER), VERSION);
            deleteRecursively(directory);
            Files.move(unpacked, directory, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(archive);
            deleteRecursively(unpacked);
        }
    }

    /**
     * Downloads the archive, computing its checksum on the way, and rejects
     * it unless both its size and checksum are the ones Peint expects.
     */
    private void downloadAndCheck(Path archive, LongConsumer progress) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(download.url()).GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Downloading Ollama failed: HTTP " + response.statusCode());
        }

        MessageDigest sha256 = sha256();
        long received = 0;
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(archive)) {
            byte[] buffer = new byte[1 << 16];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                if (Thread.interrupted()) {
                    throw new InterruptedException("Ollama download cancelled");
                }
                out.write(buffer, 0, read);
                sha256.update(buffer, 0, read);
                received += read;
                progress.accept(received);
            }
        }
        if (received != download.size()) {
            throw new IOException("The Ollama download was " + received + " bytes, not the expected " + download.size());
        }
        String checksum = HexFormat.of().formatHex(sha256.digest());
        if (!checksum.equalsIgnoreCase(download.sha256())) {
            throw new IOException("The Ollama download's checksum didn't match; it may have been damaged or changed");
        }
    }

    /**
     * Unpacks a zip archive, refusing any entry that would end up outside
     * the target directory ("zip slip").
     */
    static void unzip(Path archive, Path target) throws IOException, InterruptedException {
        Path root = target.toAbsolutePath().normalize();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (Thread.interrupted()) {
                    throw new InterruptedException("Unpacking Ollama cancelled");
                }
                Path destination = root.resolve(entry.getName()).normalize();
                if (!destination.startsWith(root)) {
                    throw new IOException("Refused an archive entry outside the target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Unpacks a gzipped tar archive with the system's own {@code tar}, which
     * macOS always has and which refuses entries outside the target directory.
     *
     * <p>The archive and target must be in the same directory. {@code tar}
     * runs there and is given only their names: GNU tar reads a path such as
     * {@code C:\...} as a file on a remote host called {@code C}, and waits
     * to connect to it.</p>
     */
    static void untar(Path archive, Path target) throws IOException, InterruptedException {
        Process tar = new ProcessBuilder("tar", "-xzf", archive.getFileName().toString(),
                "-C", target.getFileName().toString())
                .directory(archive.toAbsolutePath().getParent().toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(tar.getInputStream().readAllBytes());
        if (tar.waitFor() != 0) {
            throw new IOException("Unpacking Ollama failed: " + output.strip());
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path each : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(each);
            }
        }
    }
}
