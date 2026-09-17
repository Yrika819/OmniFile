import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates a capability declaration table without invoking production code.
 * Unsupported or unproven native paths must remain absent, not optimistic.
 */
public final class ArchiveOptionalCapabilityHarness {
    private static final String HEADER = "candidate\tcapability\tdeclared\texpected";
    private static final Map<String, Boolean> EXPECTED_CAPABILITIES = Map.ofEntries(
            Map.entry(key("Commons Compress 1.28.0", "TAR_READ"), true),
            Map.entry(key("Commons Compress 1.28.0", "ZIP_READ"), true),
            Map.entry(key("Zip4j 2.11.6", "ENCRYPTED_ZIP_READ"), true),
            Map.entry(key("Junrar 8.1.x", "RAR_READ_EXTRACT"), true),
            Map.entry(key("Junrar 8.1.x", "RAR_CREATE"), false),
            Map.entry(key("zstd-jni Android AAR", "TAR_ZST_DECODE"), false),
            Map.entry(key("zstd-jni Android AAR", "RAR_READ_EXTRACT"), false),
            Map.entry(key("libarchive 3.8.9", "BROAD_ARCHIVE_READ"), false),
            Map.entry(key("libarchive 3.8.9", "TAR_READ"), false));

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            fail("usage: ArchiveOptionalCapabilityHarness <fixtures.tsv>");
        }
        int rows = 0;
        Set<String> seen = new LinkedHashSet<>();
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
                String candidate = fields[0].trim();
                String capability = fields[1].trim();
                String declaredText = fields[2].trim();
                String expectedText = fields[3].trim();
                if (candidate.isEmpty() || capability.isEmpty() || declaredText.isEmpty() || expectedText.isEmpty()) {
                    fail("invalid empty field in capability fixture: " + line);
                }
                if (!isBoolean(declaredText) || !isBoolean(expectedText)) {
                    fail("invalid boolean in capability fixture: " + line);
                }
                String key = key(candidate, capability);
                Boolean expectedTruth = EXPECTED_CAPABILITIES.get(key);
                if (expectedTruth == null) {
                    fail("unknown capability row: " + candidate + " / " + capability);
                }
                if (!seen.add(key)) {
                    fail("duplicate capability row: " + candidate + " / " + capability);
                }
                boolean declared = Boolean.parseBoolean(declaredText);
                boolean expected = Boolean.parseBoolean(expectedText);
                if (expected != expectedTruth) {
                    fail("expected truth mismatch: " + candidate + " / " + capability);
                }
                if (declared != expectedTruth) {
                    fail("untruthful capability row: " + line + " allowed=" + expectedTruth);
                }
                rows++;
            }
        }
        if (rows != EXPECTED_CAPABILITIES.size() || seen.size() != EXPECTED_CAPABILITIES.size()) {
            fail("expected exactly 9 unique rows; observed " + rows);
        }
        for (String key : EXPECTED_CAPABILITIES.keySet()) {
            if (!seen.contains(key)) {
                fail("missing expected capability row: " + key.replace('\u0000', '/'));
            }
        }
        System.out.println("OPTIONAL_CAPABILITY_SCOPE=DECLARATION_ONLY");
        System.out.println("OPTIONAL_CAPABILITY_ROWS=" + rows);
        System.out.println("OPTIONAL_CAPABILITIES=PASS");
    }

    private static String key(String candidate, String capability) {
        return candidate + "\u0000" + capability;
    }

    private static boolean isBoolean(String value) {
        return value.equals("true") || value.equals("false");
    }

    private static void fail(String message) {
        System.err.println("ERROR: " + message);
        System.exit(2);
    }
}
