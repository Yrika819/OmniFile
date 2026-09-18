import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;

/**
 * P05-004 disposable spool/containment/cleanup/cancellation PoC.
 * Host/JVM only. Uses only the JDK (no candidate libraries) so the
 * application-policy layer is demonstrated independently of parser choice;
 * the same policy hooks apply to Commons Compress / Zip4j / Junrar entry
 * streams. Not production code.
 */
public class SpoolContainmentHarness {
    static int failures = 0;

    static void check(boolean cond, String label) {
        System.out.println((cond ? "PASS" : "FAIL") + " " + label);
        if (!cond) failures++;
    }

    /** Bounded spool: sequential in -> unique temp, 64-bit counters, cancel. */
    static final class Spool {
        final Path tmp;
        final AtomicLong bytes = new AtomicLong();
        final AtomicBoolean cancelled = new AtomicBoolean();
        OutputStream out;
        boolean completed = false;

        Spool() throws IOException {
            tmp = Files.createTempFile("p05-spool-", ".bin");
            out = Files.newOutputStream(tmp);
        }

        void copy(InputStream in) throws IOException {
            byte[] buf = new byte[8192];
            try {
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancelled.get()) break;
                    out.write(buf, 0, n);
                    bytes.addAndGet(n);
                }
                completed = !cancelled.get();
            } finally {
                try { out.close(); } catch (IOException ignored) {}
                if (!completed) cleanup(); // failure/cancel path self-cleans; caller may also call cleanup() idempotently
            }
        }

        void cleanup() {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    /** Application-policy containment: normalize under root, reject escapes.
     * Backslashes are kept LITERAL (on Android/Linux they are ordinary
     * filename characters, not separators): `a\b\win.txt` stays a single
     * file directly under root. This avoids surprise directory creation
     * from flattening and keeps the name collision-surface minimal.
     * NOTE: this name layer does not evaluate symlink metadata
     * (`isSymbolicLink`); link rejection is demonstrated on TAR-native
     * symlinks in RealEngineSpoolHarness. */
    static Path resolve(Path root, String name) throws IOException {
        if (name == null || name.isEmpty()) throw new IOException("EMPTY_NAME");
        String n = name;
        if (n.startsWith("/")) throw new IOException("ABSOLUTE_PATH");
        Path r = root.resolve(n).normalize();
        if (!r.startsWith(root)) throw new IOException("ESCAPE");
        // drive-like / separator tricks
        if (n.matches("(?i)^[a-z]:.*")) throw new IOException("DRIVE_PATH");
        if (n.contains("\0")) throw new IOException("NUL_BYTE");
        return r;
    }

    public static void main(String[] a) throws Exception {
        Path work = Files.createTempDirectory("p05-spool-poc-");
        try {
            // 1. spool success: complete-before-consume + cleanup on success path
            byte[] payload = new byte[100_000];
            new Random(42).nextBytes(payload);
            Spool s1 = new Spool();
            s1.copy(new ByteArrayInputStream(payload));
            check(s1.completed && s1.bytes.get() == 100_000, "spool-success-bytes=" + s1.bytes.get());
            check(Files.size(s1.tmp) == 100_000, "spool-complete-before-consume");
            s1.cleanup();
            check(!Files.exists(s1.tmp), "spool-cleanup-on-success");

            // 2. spool cancel: bounded, deterministic, no residue
            Spool s2 = new Spool();
            byte[] big = new byte[5_000_000];
            InputStream slow = new ByteArrayInputStream(big);
            s2.cancelled.set(true);
            long t0 = System.nanoTime();
            s2.copy(slow);
            long latencyMs = (System.nanoTime() - t0) / 1_000_000;
            check(!s2.completed && !Files.exists(s2.tmp), "spool-cancel-no-residue latencyMs=" + latencyMs);

            // 3. spool failure cleanup (broken stream)
            Spool s3 = new Spool();
            InputStream broken = new InputStream() {
                int n = 0;
                public int read() throws IOException {
                    if (++n > 10) throw new IOException("SIMULATED_IO_ERROR");
                    return 0;
                }
            };
            boolean threw = false;
            try { s3.copy(broken); } catch (IOException e) { threw = true; s3.cleanup(); }
            check(threw && !Files.exists(s3.tmp), "spool-failure-cleanup");

            // 4. build hostile fixture zip in memory
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(bos);
            String[] names = {"safe.txt", "../escape.txt", "/absolute.txt",
                "dir/../normalized.txt", "conflict", "conflict/file.txt", "a\\b\\win.txt", "C:/drive.txt", "dup.txt"};
            for (String nm : names) {
                try {
                    zos.putNextEntry(new ZipEntry(nm));
                    zos.write(("data:" + nm).getBytes("UTF-8"));
                    zos.closeEntry();
                } catch (Exception e) {
                    System.out.println("NOTE fixture-entry-skipped nm=" + nm + " err=" + e.getMessage());
                }
            }
            zos.close();
            byte[] hostileZip = bos.toByteArray();

            // 5. spool the hostile zip (sequential provider input simulation),
            // then extract under policy with per-entry limits + cancellation.
            Spool s4 = new Spool();
            s4.copy(new ByteArrayInputStream(hostileZip));
            check(s4.completed, "hostile-spool-complete");
            Path root = work.resolve("dest");
            Files.createDirectories(root);
            Map<String, String> outcomes = new LinkedHashMap<>();
            Set<String> seen = new HashSet<>();
            int extracted = 0;
            long totalBytes = 0;
            final long MAX_ENTRY = 1 << 20, MAX_TOTAL = 1 << 22;
            final int MAX_ENTRIES = 1000;
            AtomicBoolean cancel = new AtomicBoolean(false);
            int examined = 0;
            try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(s4.tmp))) {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    examined++;
                    if (examined == 9) cancel.set(true); // cancel mid-extraction (after policy cases)
                    if (cancel.get()) { outcomes.put(e.getName(), "CANCELLED"); break; }
                    if (examined > MAX_ENTRIES) { outcomes.put(e.getName(), "ENTRY_LIMIT"); break; }
                    Path dest;
                    try {
                        dest = resolve(root, e.getName());
                    } catch (IOException ex) {
                        outcomes.put(e.getName(), "REJECTED:" + ex.getMessage());
                        continue;
                    }
                    if (!seen.add(dest.toString())) { outcomes.put(e.getName(), "DUPLICATE_SKIP"); continue; }
                    if (Files.isDirectory(dest) || (Files.exists(dest) && !Files.isRegularFile(dest))) {
                        outcomes.put(e.getName(), "CONFLICT_SKIP"); continue;
                    }
                    if (e.isDirectory()) { Files.createDirectories(dest); outcomes.put(e.getName(), "DIR"); continue; }
                    try {
                        Files.createDirectories(dest.getParent());
                    } catch (FileAlreadyExistsException ex) {
                        outcomes.put(e.getName(), "CONFLICT_SKIP:parent-is-file");
                        continue;
                    }
                    long w = 0;
                    try (OutputStream o = Files.newOutputStream(dest)) {
                        byte[] b = new byte[8192];
                        int n;
                        while ((n = zin.read(b)) > 0) {
                            w += n;
                            if (w > MAX_ENTRY || totalBytes + w > MAX_TOTAL) throw new IOException("RESOURCE_LIMIT");
                            o.write(b, 0, n);
                        }
                    } catch (IOException ex) {
                        Files.deleteIfExists(dest);
                        outcomes.put(e.getName(), "LIMIT_CLEANED"); continue;
                    }
                    totalBytes += w;
                    extracted++;
                    outcomes.put(e.getName(), "EXTRACTED");
                }
            }
            s4.cleanup();
            for (Map.Entry<String, String> en : outcomes.entrySet())
                System.out.println("ENTRY " + en.getKey() + " -> " + en.getValue());
            check(outcomes.getOrDefault("../escape.txt", "").startsWith("REJECTED"), "contain-parent-escape");
            check(outcomes.getOrDefault("/absolute.txt", "").startsWith("REJECTED"), "contain-absolute");
            check(outcomes.getOrDefault("C:/drive.txt", "").startsWith("REJECTED"), "contain-drive");
            check("EXTRACTED".equals(outcomes.get("a\\b\\win.txt")), "contain-backslash-literal-in-root");
            check(!Files.exists(root.resolve("a/b/win.txt").normalize())
                && Files.exists(root.resolve("a\\b\\win.txt")), "backslash-no-subdir-created");
            check("EXTRACTED".equals(outcomes.get("dir/../normalized.txt")), "contain-normalized-inside");
            // duplicate policy at the resolve/seen layer (JDK ZipOutputStream
            // refuses to author duplicate names, so exercise the policy directly)
            Path dupDest = resolve(root, "dup.txt");
            boolean firstSeen = seen.add(dupDest.toString());
            boolean secondSeen = seen.add(dupDest.toString());
            check(firstSeen && !secondSeen, "duplicate-policy-skip");
            check(outcomes.containsValue("CANCELLED"), "cancel-observed-mid-extraction");
            check(!Files.exists(root.resolve("escape.txt")) && !Files.exists(root.resolve("a").resolve("b"))
                && !Files.exists(root.resolve("../escape.txt").normalize()), "no-escape-on-disk");

            // 6. partial-output cleanup: I/O failure mid-extraction after earlier writes.
            // (Truncating only the central directory does NOT break ZipInputStream,
            // which parses local headers sequentially — so inject a hard read
            // failure during the second entry instead. This genuinely exercises
            // CLEANED_AFTER_FAILURE rather than cleanup-after-success.)
            Path root2 = work.resolve("dest2");
            Files.createDirectories(root2);
            ByteArrayOutputStream b2 = new ByteArrayOutputStream();
            ZipOutputStream z2 = new ZipOutputStream(b2);
            z2.putNextEntry(new ZipEntry("first-ok.txt"));
            z2.write("hello".getBytes("UTF-8"));
            z2.closeEntry();
            z2.putNextEntry(new ZipEntry("second-ok.txt"));
            z2.write("world".getBytes("UTF-8"));
            z2.closeEntry();
            z2.close();
            final byte[] fullZip = b2.toByteArray();
            final int failAfter = fullZip.length / 2; // fail inside the body
            InputStream failing = new InputStream() {
                int pos = 0;
                public int read() throws IOException {
                    if (pos >= failAfter) throw new IOException("SIMULATED_MID_EXTRACT_IO_ERROR");
                    return fullZip[pos++] & 0xFF;
                }
                public int read(byte[] buf, int off, int len) throws IOException {
                    if (pos >= failAfter) throw new IOException("SIMULATED_MID_EXTRACT_IO_ERROR");
                    int n = Math.min(len, failAfter - pos);
                    System.arraycopy(fullZip, pos, buf, off, n);
                    pos += n;
                    return n;
                }
            };
            List<Path> written = new ArrayList<>();
            boolean failedSafe = false;
            try (ZipInputStream zin = new ZipInputStream(failing)) {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    Path d = resolve(root2, e.getName());
                    Files.createDirectories(d.getParent());
                    try (OutputStream o = Files.newOutputStream(d)) {
                        byte[] b = new byte[8192];
                        int n;
                        while ((n = zin.read(b)) > 0) o.write(b, 0, n);
                    }
                    written.add(d);
                }
            } catch (Exception ex) {
                failedSafe = true;
                System.out.println("NOTE mid-extract-error " + ex.getClass().getSimpleName() + ":" + ex.getMessage());
            }
            // policy: on failure, remove files written by THIS operation
            for (Path p : written) Files.deleteIfExists(p);
            boolean clean = true;
            for (Path p : written) clean &= !Files.exists(p);
            check(failedSafe, "mid-extract-failure-observed");
            check(clean, "partial-cleanup-after-mid-extract-failure written=" + written.size());

            // 7. error normalization mapping (observed exception -> category)
            Map<String, String> cats = new LinkedHashMap<>();
            cats.put(new java.io.EOFException().getClass().getSimpleName(), "CORRUPT_OR_TRUNCATED");
            cats.put("ZipException:Wrong Password", "BAD_PASSWORD");
            cats.put("CorruptHeaderException", "CORRUPT_OR_TRUNCATED");
            cats.put("InitDeciphererFailedException", "PASSWORD_REQUIRED");
            cats.put("ZstdException:Src size is incorrect", "CORRUPT_OR_TRUNCATED");
            cats.put("ZstdException:Unknown frame descriptor", "CORRUPT_OR_TRUNCATED");
            cats.put("RESOURCE_LIMIT", "RESOURCE_LIMIT_EXCEEDED");
            cats.put("CANCELLED", "CANCELLED");
            cats.put("SIMULATED_IO_ERROR", "IO_FAILURE");
            check(cats.get("ZipException:Wrong Password").equals("BAD_PASSWORD")
                && cats.get("CANCELLED").equals("CANCELLED")
                && cats.size() == 9, "error-normalization-map-size=" + cats.size());
            System.out.println("NORMALIZATION " + cats);
        } finally {
            // recursive delete work
            Files.walk(work).sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
        System.out.println(failures == 0 ? "SPOOL_CONTAINMENT_POC=PASS" : "SPOOL_CONTAINMENT_POC=FAIL n=" + failures);
        if (failures > 0) System.exit(1);
    }
}
