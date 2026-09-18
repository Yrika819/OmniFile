import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdInputStream;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile.Builder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Disposable host/JVM parser probe. This is not production code. */
public final class ArchiveRealEngineHarness {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "/tmp/p05-004-real-engine" : args[0]);
        Files.createDirectories(root);
        Path zip = root.resolve("hostile.zip");
        Path tar = root.resolve("hostile.tar");
        Path tarGz = root.resolve("hostile.tar.gz");
        Path encrypted = root.resolve("encrypted.zip");
        createZip(zip);
        createTar(tar);
        gzip(tar, tarGz);
        createEncryptedZip(root, encrypted);
        runZipParsers(zip);
        runTarParsers(tar, tarGz);
        runZip4jEncrypted(encrypted);
        runZstd(root);
        System.out.println("REAL_ENGINE_HARNESS=PASS");
    }

    private static void createZip(Path path) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String name : new String[]{"safe.txt", "../escape.txt", "/absolute.txt", "dir/../normalized.txt"}) {
                out.putNextEntry(new ZipEntry(name));
                out.write(("payload:" + name).getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }

    private static void createTar(Path path) throws IOException {
        try (TarArchiveOutputStream out = new TarArchiveOutputStream(Files.newOutputStream(path))) {
            out.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (String name : new String[]{"safe.txt", "../escape.txt", "/absolute.txt"}) {
                byte[] payload = ("payload:" + name).getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry entry = new TarArchiveEntry(name);
                entry.setSize(payload.length);
                out.putArchiveEntry(entry);
                out.write(payload);
                out.closeArchiveEntry();
            }
            out.finish();
        }
    }

    private static void gzip(Path input, Path output) throws IOException {
        try (InputStream in = Files.newInputStream(input);
             GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(output))) {
            in.transferTo(out);
        }
    }

    private static void createEncryptedZip(Path root, Path output) throws IOException {
        Path payload = root.resolve("secret.txt");
        Files.writeString(payload, "secret-payload", StandardCharsets.UTF_8);
        ZipParameters params = new ZipParameters();
        params.setEncryptFiles(true);
        params.setEncryptionMethod(EncryptionMethod.AES);
        params.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        try (ZipFile zip = new ZipFile(output.toFile(), "correct".toCharArray())) {
            zip.addFile(payload.toFile(), params);
        }
    }

    private static void runZipParsers(Path zip) throws Exception {
        int streamCount = 0;
        try (ZipArchiveInputStream archive = new ZipArchiveInputStream(Files.newInputStream(zip))) {
            ZipArchiveEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                streamCount++;
                archive.transferTo(OutputStream.nullOutputStream());
                System.out.println("COMMONS_ZIP_ENTRY=" + entry.getName());
            }
        }
        int randomCount = 0;
        try (org.apache.commons.compress.archivers.zip.ZipFile archive = new Builder().setPath(zip).get()) {
            var entries = archive.getEntries();
            while (entries.hasMoreElements()) { entries.nextElement(); randomCount++; }
        }
        int zip4jCount;
        try (ZipFile archive = new ZipFile(zip.toFile())) { zip4jCount = archive.getFileHeaders().size(); }
        require(streamCount == 4 && randomCount == 4 && zip4jCount == 4, "zip counts");
        byte[] bytes = Files.readAllBytes(zip);
        requireFailure("COMMONS_ZIP_TRUNCATED", () -> {
            try (ZipArchiveInputStream archive = new ZipArchiveInputStream(
                    new ByteArrayInputStream(Arrays.copyOf(bytes, bytes.length - 12)))) {
                while (archive.getNextEntry() != null) archive.transferTo(OutputStream.nullOutputStream());
            }
        });
        requireFailure("ZIP4J_ZIP_TRUNCATED", () -> {
            Path truncated = zip.resolveSibling("truncated.zip");
            Files.write(truncated, Arrays.copyOf(bytes, bytes.length - 12));
            try (ZipFile archive = new ZipFile(truncated.toFile())) { archive.getFileHeaders(); }
        });
    }

    private static void runTarParsers(Path tar, Path tarGz) throws Exception {
        int tarCount = countTar(Files.newInputStream(tar));
        int tarGzCount = countTar(new org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream(
                Files.newInputStream(tarGz)));
        require(tarCount == 3 && tarGzCount == 3, "tar counts");
        System.out.println("COMMONS_TAR_ENTRIES=" + tarCount);
        System.out.println("COMMONS_TAR_GZ_ENTRIES=" + tarGzCount);
    }

    private static int countTar(InputStream input) throws IOException {
        int count = 0;
        try (TarArchiveInputStream in = new TarArchiveInputStream(input)) {
            TarArchiveEntry entry;
            while ((entry = in.getNextTarEntry()) != null) {
                count++;
                in.transferTo(OutputStream.nullOutputStream());
            }
        }
        return count;
    }

    private static void runZip4jEncrypted(Path encrypted) throws Exception {
        try (ZipFile zip = new ZipFile(encrypted.toFile(), "correct".toCharArray())) {
            require(zip.getFileHeaders().size() == 1, "encrypted count");
            require(new String(zip.getInputStream(zip.getFileHeader("secret.txt")).readAllBytes(), StandardCharsets.UTF_8)
                    .equals("secret-payload"), "encrypted payload");
        }
        requireFailure("ZIP4J_WRONG_PASSWORD", () -> {
            try (ZipFile zip = new ZipFile(encrypted.toFile(), "wrong".toCharArray())) {
                zip.extractAll(encrypted.getParent().resolve("wrong-output").toString());
            }
        });
        System.out.println("ZIP4J_AES=PASS_WRONG_PASSWORD_CONTROLLED");
    }

    private static void runZstd(Path root) throws Exception {
        byte[] source = "zstd-payload".getBytes(StandardCharsets.UTF_8);
        byte[] frame = Zstd.compress(source);
        Path zst = root.resolve("payload.zst");
        Files.write(zst, frame);
        try (ZstdInputStream in = new ZstdInputStream(Files.newInputStream(zst))) {
            require(Arrays.equals(source, in.readAllBytes()), "zstd payload");
        }
        requireFailure("ZSTD_TRUNCATED", () -> {
            Path truncated = root.resolve("truncated.zst");
            Files.write(truncated, Arrays.copyOf(frame, Math.max(1, frame.length - 1)));
            try (ZstdInputStream in = new ZstdInputStream(Files.newInputStream(truncated))) { in.readAllBytes(); }
        });
        System.out.println("ZSTD_DECODE=PASS_TRUNCATED_CONTROLLED");
    }

    private static void requireFailure(String label, Throwing action) throws Exception {
        try {
            action.run();
            throw new AssertionError(label + " unexpectedly accepted");
        } catch (AssertionError e) {
            throw e;
        } catch (Throwable e) {
            System.out.println(label + "=" + e.getClass().getName() + ":" + e.getMessage());
        }
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    @FunctionalInterface
    private interface Throwing { void run() throws Exception; }
}
