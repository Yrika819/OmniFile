import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates a capability declaration table without invoking production code.
 * Unsupported or unproven native paths must remain absent, not optimistic.
 */
public final class ArchiveOptionalCapabilityHarness {
    private static final String HEADER = "candidate\tcapability\tdeclared\texpected";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            fail("usage: ArchiveOptionalCapabilityHarness <fixtures.tsv>");
        }
        int rows = 0;
        try (BufferedReader reader = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)) {
            if (!HEADER.equals(reader.readLine())) {
                fail("invalid capability fixture header");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length != 4) {
                    fail("capability fixture must have four fields: " + line);
                }
                boolean declared = Boolean.parseBoolean(fields[2]);
                boolean expected = Boolean.parseBoolean(fields[3]);
                boolean allowed = allowed(fields[0], fields[1]);
                if (declared != expected || declared != allowed) {
                    fail("untruthful capability row: " + line + " allowed=" + allowed);
                }
                rows++;
            }
        }
        if (rows == 0) {
            fail("capability fixture set is empty");
        }
        System.out.println("OPTIONAL_CAPABILITY_SCOPE=DECLARATION_ONLY");
        System.out.println("OPTIONAL_CAPABILITY_ROWS=" + rows);
        System.out.println("OPTIONAL_CAPABILITIES=PASS");
    }

    private static boolean allowed(String candidate, String capability) {
        return switch (candidate) {
            case "Commons Compress 1.28.0" -> capability.equals("TAR_READ") || capability.equals("ZIP_READ");
            case "Zip4j 2.11.6" -> capability.equals("ENCRYPTED_ZIP_READ");
            case "Junrar 8.1.x" -> capability.equals("RAR_READ_EXTRACT");
            case "zstd-jni Android AAR" -> false;
            case "libarchive 3.8.9" -> false;
            default -> throw new IllegalArgumentException("unknown candidate: " + candidate);
        };
    }

    private static void fail(String message) {
        System.err.println("ERROR: " + message);
        System.exit(2);
    }
}
