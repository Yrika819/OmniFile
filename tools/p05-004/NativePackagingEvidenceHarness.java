import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Records the distinction between an inherited 4 KiB device observation and
 * actual 16 KiB native packaging evidence. It performs no native execution.
 */
public final class NativePackagingEvidenceHarness {
    private static final String HEADER = "candidate\tevidence\tobserved\tclassification\tnote";
    private static final Map<String, String> EXPECTED_EVIDENCE = Map.ofEntries(
            Map.entry(key("zstd-jni Android AAR", "local-aar"), "ABSENT\tNOT_TESTED"),
            Map.entry(key("zstd-jni Android AAR", "local-elf"), "ABSENT\tNOT_TESTED"),
            Map.entry(key("zstd-jni Android AAR", "p0-page-size"), "4096\tNOT_16K_EVIDENCE"),
            Map.entry(key("libarchive 3.8.9", "local-source-build"), "ABSENT\tNOT_TESTED"),
            Map.entry(key("libarchive 3.8.9", "local-elf"), "ABSENT\tNOT_TESTED"),
            Map.entry(key("libarchive 3.8.9", "p0-page-size"), "ABSENT\tNOT_TESTED"));

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            fail("usage: NativePackagingEvidenceHarness <root> <evidence.tsv>");
        }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        long aarCount;
        long elfCount;
        try (var paths = Files.walk(root)) {
            aarCount = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".aar"))
                    .count();
        }
        try (var paths = Files.walk(root)) {
            elfCount = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("libzstd.so")
                            || path.getFileName().toString().equals("libarchive.so"))
                    .count();
        }
        boolean sawFourKiBOnly = false;
        int rows = 0;
        Set<String> seen = new LinkedHashSet<>();
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
                String candidate = fields[0].trim();
                String evidence = fields[1].trim();
                String observed = fields[2].trim();
                String classification = fields[3].trim();
                String key = key(candidate, evidence);
                String expected = EXPECTED_EVIDENCE.get(key);
                if (expected == null) {
                    fail("unknown native evidence row: " + candidate + " / " + evidence);
                }
                if (!seen.add(key)) {
                    fail("duplicate native evidence row: " + candidate + " / " + evidence);
                }
                if (!expected.equals(observed + "\t" + classification)) {
                    fail("invalid native evidence row: " + line);
                }
                if (key.equals(key("zstd-jni Android AAR", "p0-page-size"))) {
                    sawFourKiBOnly = true;
                }
                rows++;
            }
        }
        if (rows != EXPECTED_EVIDENCE.size() || seen.size() != EXPECTED_EVIDENCE.size()) {
            fail("expected exactly 6 unique rows; observed " + rows);
        }
        for (String key : EXPECTED_EVIDENCE.keySet()) {
            if (!seen.contains(key)) {
                fail("missing expected native evidence row: " + key.replace('\u0000', '/'));
            }
        }
        if (!sawFourKiBOnly) {
            fail("native evidence does not preserve the 4 KiB versus 16 KiB distinction");
        }
        System.out.println("NATIVE_LOCAL_AAR_COUNT=" + aarCount);
        System.out.println("NATIVE_LOCAL_ELF_COUNT=" + elfCount);
        System.out.println("NATIVE_PACKAGING=" + (aarCount == 0 && elfCount == 0 ? "NO_LOCAL_ARTIFACT" : "ARTIFACT_PRESENT_REQUIRES_INSPECTION"));
        System.out.println("NATIVE_16K=UNRESOLVED_4K_ONLY");
    }

    private static String key(String candidate, String evidence) {
        return candidate + "\u0000" + evidence;
    }

    private static void fail(String message) {
        System.err.println("ERROR: " + message);
        System.exit(2);
    }
}
