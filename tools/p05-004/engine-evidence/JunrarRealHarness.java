import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Disposable positive RAR4/RAR5 and corrupt-header probe. */
public final class JunrarRealHarness {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Path rar4 = root.resolve("rar4.rar");
        Path rar5 = root.resolve("rar5.rar");
        Path corrupt = root.resolve("corrupt-header.rar");
        probeFile("RAR4_FILE", rar4);
        probeFile("RAR5_FILE", rar5);
        probeStream("RAR4_STREAM", rar4);
        probeStream("RAR5_STREAM", rar5);
        try (Archive archive = new Archive(corrupt.toFile())) {
            System.out.println("RAR_CORRUPT=ACCEPTED entries=" + archive.getFileHeaders().size()
                    + " broken=" + archive.hasBrokenHeaders()
                    + " failures=" + archive.getHeaderFailures().size());
        } catch (Throwable error) {
            System.out.println("RAR_CORRUPT=" + error.getClass().getName() + ":" + error.getMessage());
        }
    }

    private static void probeFile(String label, Path path) throws Exception {
        int entries = 0;
        try (Archive archive = new Archive(path.toFile())) {
            for (FileHeader header : archive) {
                entries++;
                if (!header.isDirectory()) archive.extractFile(header, OutputStream.nullOutputStream());
            }
            System.out.println(label + " entries=" + entries + " format=" + archive.getFormat()
                    + " encrypted=" + archive.isEncrypted());
        }
    }

    private static void probeStream(String label, Path path) throws Exception {
        int entries = 0;
        try (InputStream input = Files.newInputStream(path); Archive archive = new Archive(input)) {
            for (FileHeader ignored : archive) entries++;
        }
        System.out.println(label + " entries=" + entries);
    }
}
