import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Records the distinction between an inherited 4 KiB device observation and
 * actual 16 KiB native packaging evidence. It performs no native execution.
 */
public final class NativePackagingEvidenceHarness {
    private static final String HEADER = "candidate\tevidence\tobserved\tclassification\tnote";

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            fail("usage: NativePackagingEvidenceHarness <root> <evidence.tsv>");
        }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        long aarCount;
        long elfCount;
        try (var paths = Files.walk(root, 4)) {
            aarCount = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".aar"))
                    .count();
        }
        try (var paths = Files.walk(root, 4)) {
            elfCount = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("libzstd.so")
                            || path.getFileName().toString().equals("libarchive.so"))
                    .count();
        }
        boolean sawFourKiBOnly = false;
        int rows = 0;
        try (BufferedReader reader = Files.newBufferedReader(Path.of(args[1]), StandardCharsets.UTF_8)) {
            if (!HEADER.equals(reader.readLine())) {
                fail("invalid native evidence header");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length != 5 || fields[0].isBlank() || fields[1].isBlank()
                        || fields[2].isBlank() || fields[3].isBlank() || fields[4].isBlank()) {
                    fail("invalid native evidence row: " + line);
                }
                if (fields[1].equals("p0-page-size") && fields[2].equals("4096")
                        && fields[3].equals("NOT_16K_EVIDENCE")) {
                    sawFourKiBOnly = true;
                }
                rows++;
            }
        }
        if (rows == 0 || !sawFourKiBOnly) {
            fail("native evidence does not preserve the 4 KiB versus 16 KiB distinction");
        }
        System.out.println("NATIVE_LOCAL_AAR_COUNT=" + aarCount);
        System.out.println("NATIVE_LOCAL_ELF_COUNT=" + elfCount);
        System.out.println("NATIVE_PACKAGING=" + (aarCount == 0 && elfCount == 0 ? "NO_LOCAL_ARTIFACT" : "ARTIFACT_PRESENT_REQUIRES_INSPECTION"));
        System.out.println("NATIVE_16K=UNRESOLVED_4K_ONLY");
    }

    private static void fail(String message) {
        System.err.println("ERROR: " + message);
        System.exit(2);
    }
}
