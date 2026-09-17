import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;

/**
 * Host-only checks for the application-level extraction policy fixtures.
 * This intentionally does not parse an archive or claim parser certification.
 */
public final class ArchiveSecurityFixtureHarness {
    private static final String HEADER = "id\ttype\tvalue\texpected";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            fail("usage: ArchiveSecurityFixtureHarness <fixtures.tsv>");
        }
        int cases = 0;
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
                String observed = evaluate(fields[1], fields[2]);
                if (!fields[3].equals(observed)) {
                    fail("fixture " + fields[0] + " expected " + fields[3] + " but observed " + observed);
                }
                cases++;
            }
        }
        if (cases == 0) {
            fail("security fixture set is empty");
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
        if (value.startsWith("/") || value.matches("^[A-Za-z]:/.*")) {
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
        return numerator <= limit;
    }

    private static void fail(String message) {
        System.err.println("ERROR: " + message);
        System.exit(2);
    }
}
