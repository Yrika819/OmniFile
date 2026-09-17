import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Host-only checks for the application-level extraction policy fixtures.
 * This intentionally does not parse an archive or claim parser certification.
 */
public final class ArchiveSecurityFixtureHarness {
    private static final String HEADER = "id\ttype\tvalue\texpected";
    private static final List<String> REQUIRED_CASE_IDS = List.of(
            "safe-relative",
            "absolute-posix",
            "dotdot-parent",
            "mixed-separators",
            "drive-like",
            "drive-relative",
            "symlink-entry",
            "expanded-byte-limit",
            "duplicate-destination",
            "truncated-entry",
            "cancelled-extraction",
            "nested-depth-limit",
            "wrong-password",
            "missing-multipart");
    private static final Map<String, String> REQUIRED_CASE_TYPES = Map.ofEntries(
            Map.entry("safe-relative", "PATH"),
            Map.entry("absolute-posix", "PATH"),
            Map.entry("dotdot-parent", "PATH"),
            Map.entry("mixed-separators", "PATH"),
            Map.entry("drive-like", "PATH"),
            Map.entry("drive-relative", "PATH"),
            Map.entry("symlink-entry", "SYMLINK"),
            Map.entry("expanded-byte-limit", "EXPANSION"),
            Map.entry("duplicate-destination", "DUPLICATE"),
            Map.entry("truncated-entry", "TRUNCATED"),
            Map.entry("cancelled-extraction", "CANCELLATION"),
            Map.entry("nested-depth-limit", "NESTING"),
            Map.entry("wrong-password", "PASSWORD"),
            Map.entry("missing-multipart", "MULTIPART"));

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            fail("usage: ArchiveSecurityFixtureHarness <fixtures.tsv>");
        }
        int cases = 0;
        Set<String> seen = new LinkedHashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)) {
            if (!HEADER.equals(reader.readLine())) {
                fail("invalid security fixture header");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length != 4) {
                    fail("security fixture must have four fields: " + line);
                }
                String id = fields[0].trim();
                String type = fields[1].trim();
                String value = fields[2].trim();
                String expected = fields[3].trim();
                if (id.isEmpty() || type.isEmpty() || value.isEmpty() || expected.isEmpty()) {
                    fail("invalid empty field in security fixture: " + line);
                }
                if (!REQUIRED_CASE_TYPES.containsKey(id)) {
                    fail("unknown case id: " + id);
                }
                if (!seen.add(id)) {
                    fail("duplicate case id: " + id);
                }
                if (!REQUIRED_CASE_TYPES.get(id).equals(type)) {
                    fail("invalid case type: " + type);
                }
                if (!Set.of("ALLOW", "REJECT", "CANCELLED").contains(expected)
                        || (type.equals("CANCELLATION") && !expected.equals("CANCELLED"))
                        || (!type.equals("CANCELLATION") && expected.equals("CANCELLED"))) {
                    fail("invalid expected outcome: " + expected + " for " + id);
                }
                final String observed;
                try {
                    observed = evaluate(type, value);
                } catch (IllegalArgumentException exception) {
                    fail("invalid case " + id + ": " + exception.getMessage());
                    return;
                }
                if (!expected.equals(observed)) {
                    fail("fixture " + id + " expected " + expected + " but observed " + observed);
                }
                cases++;
            }
        }
        for (String id : REQUIRED_CASE_IDS) {
            if (!seen.contains(id)) {
                fail("missing required case: " + id);
            }
        }
        if (cases != REQUIRED_CASE_IDS.size()) {
            fail("security fixture count mismatch: " + cases);
        }
        System.out.println("SECURITY_SCOPE=APPLICATION_POLICY_ONLY");
        System.out.println("SECURITY_CASES=" + cases);
        System.out.println("SECURITY_FIXTURES=PASS");
    }

    private static String evaluate(String type, String value) {
        return switch (type) {
            case "PATH" -> safeRelativePath(value) ? "ALLOW" : "REJECT";
            case "SYMLINK", "DUPLICATE", "TRUNCATED", "PASSWORD", "MULTIPART" -> "REJECT";
            case "EXPANSION", "NESTING" -> withinLimit(value) ? "ALLOW" : "REJECT";
            case "CANCELLATION" -> "CANCELLED";
            default -> throw new IllegalArgumentException("unknown security fixture type: " + type);
        };
    }

    private static boolean safeRelativePath(String raw) {
        String value = raw.replace('\\', '/');
        if (value.startsWith("/") || value.matches("^[A-Za-z]:.*")) {
            return false;
        }
        ArrayDeque<String> components = new ArrayDeque<>();
        for (String component : value.split("/", -1)) {
            if (component.isEmpty() || ".".equals(component)) {
                continue;
            }
            if ("..".equals(component)) {
                if (components.isEmpty()) {
                    return false;
                }
                components.removeLast();
            } else {
                components.addLast(component);
            }
        }
        return !components.isEmpty();
    }

    private static boolean withinLimit(String value) {
        String[] values = value.split("/", -1);
        if (values.length != 2) {
            throw new IllegalArgumentException("expected numerator/limit: " + value);
        }
        long numerator = Long.parseLong(values[0]);
        long limit = Long.parseLong(values[1]);
        if (numerator < 0 || limit < 0) {
            throw new IllegalArgumentException("numerator and limit must be non-negative");
        }
        return numerator <= limit;
    }

    private static void fail(String message) {
        System.err.println("ERROR: " + message);
        System.exit(2);
    }
}
