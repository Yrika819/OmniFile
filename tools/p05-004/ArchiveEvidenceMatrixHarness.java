import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Disposable host/JVM validator for the P05-004 evidence manifest.
 *
 * This does not execute archive libraries. It only validates evidence-record
 * shape and computes a conservative candidate disposition from recorded facts.
 */
public final class ArchiveEvidenceMatrixHarness {
    private static final String HEADER = "candidate\tgate\tstatus\tnote";

    private static final List<String> REQUIRED_CANDIDATES = List.of(
            "Commons Compress 1.28.0",
            "Zip4j 2.11.6",
            "Junrar 8.1.x",
            "zstd-jni Android AAR",
            "libarchive 3.8.9");

    private static final Set<String> REQUIRED_GATES = Set.of(
            "functional-fixtures",
            "provider-access",
            "scale-100k",
            "license-provenance",
            "security-traversal",
            "security-symlink",
            "security-expansion",
            "security-duplicates",
            "security-truncated",
            "security-malformed",
            "security-cancellation",
            "security-nesting",
            "security-password",
            "security-multipart");

    private static final Set<String> NATIVE_CANDIDATES = Set.of(
            "zstd-jni Android AAR",
            "libarchive 3.8.9");

    private enum Status {
        PASS,
        CONDITIONAL,
        NOT_TESTED,
        UNKNOWN
    }

    private record Evidence(String candidate, String gate, Status status, String note) {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            fail("usage: ArchiveEvidenceMatrixHarness <manifest.tsv>");
        }

        List<Evidence> records = read(Path.of(args[0]));
        Map<String, List<Evidence>> byCandidate = new LinkedHashMap<>();
        for (Evidence evidence : records) {
            byCandidate.computeIfAbsent(evidence.candidate(), ignored -> new ArrayList<>()).add(evidence);
        }

        validateCoverage(byCandidate);

        boolean overallClosed = true;
        for (String candidate : REQUIRED_CANDIDATES) {
            String disposition = disposition(byCandidate.get(candidate));
            System.out.println(candidate + "=" + disposition);
            if (!"PASS".equals(disposition)) {
                overallClosed = false;
            }
        }
        System.out.println("OVERALL=" + (overallClosed ? "CLOSED" : "NOT_CLOSED"));
    }

    private static List<Evidence> read(Path manifest) throws IOException {
        List<Evidence> records = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            if (!HEADER.equals(line)) {
                fail("invalid header; expected: " + HEADER);
            }
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = line.split("\\t", -1);
                if (fields.length != 4) {
                    fail("line " + lineNumber + " must have four tab-separated fields");
                }
                String candidate = fields[0].trim();
                String gate = fields[1].trim();
                String statusText = fields[2].trim();
                String note = fields[3].trim();
                if (candidate.isEmpty() || gate.isEmpty() || note.isEmpty()) {
                    fail("line " + lineNumber + " has an empty required field");
                }
                if (!REQUIRED_CANDIDATES.contains(candidate)) {
                    fail("line " + lineNumber + " has unknown candidate: " + candidate);
                }
                try {
                    Status status = Status.valueOf(statusText);
                    String key = candidate + "\u0000" + gate;
                    if (!keys.add(key)) {
                        fail("duplicate candidate/gate: " + candidate + " / " + gate);
                    }
                    records.add(new Evidence(candidate, gate, status, note));
                } catch (IllegalArgumentException exception) {
                    fail("line " + lineNumber + " has invalid status: " + statusText);
                }
            }
        }
        return records;
    }

    private static void validateCoverage(Map<String, List<Evidence>> byCandidate) {
        for (String candidate : REQUIRED_CANDIDATES) {
            List<Evidence> records = byCandidate.get(candidate);
            if (records == null) {
                fail("missing candidate: " + candidate);
            }
            Set<String> gates = records.stream().map(Evidence::gate).collect(java.util.stream.Collectors.toSet());
            Set<String> missing = new LinkedHashSet<>(REQUIRED_GATES);
            missing.removeAll(gates);
            if (NATIVE_CANDIDATES.contains(candidate)) {
                missing.addAll(Set.of(
                        "native-provenance",
                        "native-abi",
                        "native-reproducible-build",
                        "native-android-compat",
                        "native-16k",
                        "native-crash",
                        "native-size",
                        "native-symbols",
                        "native-update-ownership",
                        "native-notices"));
                missing.removeAll(gates);
            }
            if (!missing.isEmpty()) {
                fail("missing gates for " + candidate + ": " + missing);
            }
        }
    }

    private static String disposition(List<Evidence> records) {
        Map<String, Status> statuses = new LinkedHashMap<>();
        for (Evidence evidence : records) {
            statuses.put(evidence.gate(), evidence.status());
        }
        if (statuses.get("functional-fixtures") != Status.PASS) {
            return "UNRESOLVED";
        }
        if (statuses.values().stream().anyMatch(status -> status != Status.PASS)) {
            return "CONDITIONAL";
        }
        return "PASS";
    }

    private static void fail(String message) {
        System.err.println("ERROR: " + message);
        System.exit(2);
    }
}
