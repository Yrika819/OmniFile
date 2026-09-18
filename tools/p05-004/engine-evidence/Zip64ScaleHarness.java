import net.lingala.zip4j.ZipFile;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipMethod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/** Disposable real ZIP parser-scale probe. This is not production code. */
public final class Zip64ScaleHarness {
    public static void main(String[] args) throws Exception {
        int entries = Integer.parseInt(args[0]);
        Path path = Path.of(args[1]);
        Instant start = Instant.now();
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(path))) {
            out.setUseZip64(Zip64Mode.Always);
            for (int i = 0; i < entries; i++) {
                ZipArchiveEntry entry = new ZipArchiveEntry("entry-" + i + ".txt");
                entry.setMethod(ZipMethod.STORED.getCode());
                entry.setSize(0);
                entry.setCrc(0);
                out.putArchiveEntry(entry);
                out.closeArchiveEntry();
            }
        }
        long buildMs = Duration.between(start, Instant.now()).toMillis();
        start = Instant.now();
        int commons = 0;
        try (org.apache.commons.compress.archivers.zip.ZipFile zip =
                     new org.apache.commons.compress.archivers.zip.ZipFile.Builder().setPath(path).get()) {
            var all = zip.getEntries();
            while (all.hasMoreElements()) { all.nextElement(); commons++; }
        }
        long commonsMs = Duration.between(start, Instant.now()).toMillis();
        start = Instant.now();
        int zip4j;
        try (ZipFile zip = new ZipFile(path.toFile())) { zip4j = zip.getFileHeaders().size(); }
        long zip4jMs = Duration.between(start, Instant.now()).toMillis();
        if (commons != entries || zip4j != entries) throw new AssertionError("entry count mismatch");
        System.out.println("ZIP64_SCALE=" + entries + " build_ms=" + buildMs
                + " commons_list_ms=" + commonsMs + " zip4j_open_ms=" + zip4jMs
                + " commons_count=" + commons + " zip4j_count=" + zip4j
                + " bytes=" + Files.size(path));
    }
}
