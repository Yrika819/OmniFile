// POC-ONLY — NOT PRODUCTION AUTHORITY
import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class ArchivePoc {
    private static final long MIB = 1024L * 1024L;
    private static Path fixtures;
    private static Path work;
    private static BufferedWriter out;

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("usage: ArchivePoc <fixtures> <work> <results.jsonl>");
        fixtures = Paths.get(args[0]).toAbsolutePath().normalize();
        work = Paths.get(args[1]).toAbsolutePath().normalize();
        Path result = Paths.get(args[2]).toAbsolutePath().normalize();
        deleteTree(work);
        Files.createDirectories(work);
        Files.createDirectories(result.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(result, StandardCharsets.UTF_8)) {
            out = w;
            rec("ENV", "HARNESS", "PASS", "java", System.getProperty("java.version"), "os", System.getProperty("os.name"), "arch", System.getProperty("os.arch"), "fixtures", fixtures.toString());
            commonsZip();
            zip4j();
            commonsTar();
            commons7z();
            junrar();
            rec("CONTROL", "RUN_DONE", "PASS");
        }
    }

    private static void commonsZip() {
        String engine = "COMMONS_COMPRESS_ZIP";
        for (String f : new String[]{"normal.zip", "zip64-forced.zip", "many-10k.zip", "many-100k.zip"}) {
            measure(engine, "LIST_" + f, () -> {
                long before = usedHeapAfterGc();
                long t = System.nanoTime();
                int count = 0;
                try (org.apache.commons.compress.archivers.zip.ZipFile z = new org.apache.commons.compress.archivers.zip.ZipFile(file(f))) {
                    Enumeration<ZipArchiveEntry> en = z.getEntries();
                    while (en.hasMoreElements()) { en.nextElement(); count++; }
                }
                long ms = elapsedMs(t);
                long after = usedHeapAfterGc();
                return data("count", count, "elapsedMs", ms, "heapDeltaBytes", after - before);
            });
        }
        measure(engine, "VIRTUAL_ENTRY_OPEN", () -> {
            try (org.apache.commons.compress.archivers.zip.ZipFile z = new org.apache.commons.compress.archivers.zip.ZipFile(file("normal.zip"))) {
                ZipArchiveEntry e = z.getEntry("nested/ユニコード😀.txt");
                long t = System.nanoTime();
                long n = consume(z.getInputStream(e), Long.MAX_VALUE);
                return data("entry", e.getName(), "bytes", n, "elapsedMicros", elapsedMicros(t));
            }
        });
        for (String f : new String[]{"zip-aes256.zip", "zip-traditional.zip"}) {
            measure(engine, "ENCRYPTED_READ_" + f, () -> {
                try (org.apache.commons.compress.archivers.zip.ZipFile z = new org.apache.commons.compress.archivers.zip.ZipFile(file(f))) {
                    ZipArchiveEntry e = z.getEntries().nextElement();
                    boolean canRead = z.canReadEntryData(e);
                    try {
                        consume(z.getInputStream(e), Long.MAX_VALUE);
                        return data("canReadEntryData", canRead, "readSucceeded", true);
                    } catch (Throwable t) {
                        return data("canReadEntryData", canRead, "readSucceeded", false, "error", shortError(t));
                    }
                }
            });
        }
        measure(engine, "TRAVERSAL_GUARD", () -> {
            int rejected = 0, safe = 0;
            try (org.apache.commons.compress.archivers.zip.ZipFile z = new org.apache.commons.compress.archivers.zip.ZipFile(file("traversal.zip"))) {
                Enumeration<ZipArchiveEntry> en = z.getEntries();
                while (en.hasMoreElements()) {
                    ZipArchiveEntry e = en.nextElement();
                    if (safeTarget(work.resolve("commons-traversal"), e.getName()) == null) rejected++; else safe++;
                }
            }
            if (rejected != 2) throw new AssertionError("expected 2 traversal rejections, got " + rejected);
            return data("rejected", rejected, "safe", safe);
        });
        measure(engine, "SYMLINK_GUARD", () -> {
            int symlinks = 0, rejected = 0;
            try (org.apache.commons.compress.archivers.zip.ZipFile z = new org.apache.commons.compress.archivers.zip.ZipFile(file("symlink-link-write.zip"))) {
                Enumeration<ZipArchiveEntry> en = z.getEntries();
                while (en.hasMoreElements()) {
                    ZipArchiveEntry e = en.nextElement();
                    if (e.isUnixSymlink()) { symlinks++; rejected++; continue; }
                    if (safeTarget(work.resolve("commons-symlink"), e.getName()) == null) rejected++;
                }
            }
            if (symlinks < 1) throw new AssertionError("symlink not detected");
            return data("symlinksDetected", symlinks, "rejectedEntries", rejected);
        });
        measure(engine, "DECOMPRESSION_LIMIT", () -> {
            try (org.apache.commons.compress.archivers.zip.ZipFile z = new org.apache.commons.compress.archivers.zip.ZipFile(file("bomb-safe.zip"))) {
                ZipArchiveEntry e = z.getEntries().nextElement();
                boolean limited = false;
                long read = 0;
                try { read = consume(z.getInputStream(e), MIB); } catch (LimitExceeded x) { limited = true; read = x.bytes; }
                if (!limited) throw new AssertionError("limit did not trigger");
                return data("limitBytes", MIB, "bytesSeen", read, "limited", true, "declaredSize", e.getSize());
            }
        });
        expectFailure(engine, "MALFORMED_TRUNCATED", () -> new org.apache.commons.compress.archivers.zip.ZipFile(file("malformed-truncated.zip")).close());
    }

    private static void zip4j() {
        String engine = "ZIP4J";
        for (String f : new String[]{"normal.zip", "zip64-forced.zip", "many-10k.zip", "many-100k.zip"}) {
            measure(engine, "LIST_" + f, () -> {
                long before = usedHeapAfterGc();
                long t = System.nanoTime();
                net.lingala.zip4j.ZipFile z = new net.lingala.zip4j.ZipFile(file(f));
                int count = z.getFileHeaders().size();
                long ms = elapsedMs(t);
                long after = usedHeapAfterGc();
                z.close();
                return data("count", count, "elapsedMs", ms, "heapDeltaBytes", after - before);
            });
        }
        for (String f : new String[]{"zip-aes256.zip", "zip-traditional.zip"}) {
            measure(engine, "ENCRYPTED_READ_" + f, () -> {
                try (net.lingala.zip4j.ZipFile z = new net.lingala.zip4j.ZipFile(file(f), "POCpass".toCharArray())) {
                    net.lingala.zip4j.model.FileHeader h = z.getFileHeaders().get(0);
                    long n = consume(z.getInputStream(h), Long.MAX_VALUE);
                    return data("encrypted", z.isEncrypted(), "bytes", n, "entry", h.getFileName());
                }
            });
        }
        measure(engine, "SPLIT_CREATE_READ", () -> {
            Path d = work.resolve("split-input"); Files.createDirectories(d);
            ArrayList<File> parts = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Path p = d.resolve("part" + i + ".bin");
                byte[] b = new byte[96 * 1024];
                new Random(1234 + i).nextBytes(b); Files.write(p, b); parts.add(p.toFile());
            }
            Path split = work.resolve("split-test.zip");
            ZipParameters zp = new ZipParameters(); zp.setCompressionMethod(CompressionMethod.STORE);
            try (net.lingala.zip4j.ZipFile z = new net.lingala.zip4j.ZipFile(split.toFile())) {
                z.createSplitZipFile(parts, zp, true, 65536L);
            }
            try (net.lingala.zip4j.ZipFile z = new net.lingala.zip4j.ZipFile(split.toFile())) {
                int segments = z.getSplitZipFiles().size();
                int entries = z.getFileHeaders().size();
                long n = consume(z.getInputStream(z.getFileHeaders().get(0)), Long.MAX_VALUE);
                if (!z.isSplitArchive()) throw new AssertionError("not recognized as split");
                return data("split", true, "segments", segments, "entries", entries, "firstEntryBytes", n);
            }
        });
        measure(engine, "BUILTIN_TRAVERSAL_EXTRACTION", () -> {
            Path base = work.resolve("zip4j-traversal"); Path dest = base.resolve("out"); Files.createDirectories(dest);
            boolean threw = false; String err = "";
            try (net.lingala.zip4j.ZipFile z = new net.lingala.zip4j.ZipFile(file("traversal.zip"))) { z.extractAll(dest.toString()); }
            catch (Throwable t) { threw = true; err = shortError(t); }
            boolean escaped1 = Files.exists(base.resolve("escape.txt"));
            boolean escaped2 = Files.exists(base.resolve("escape2.txt"));
            if (escaped1 || escaped2) throw new AssertionError("path traversal escaped destination");
            return data("threw", threw, "escaped", false, "error", err);
        });
        measure(engine, "BUILTIN_SYMLINK_LINK_WRITE", () -> {
            Path base = work.resolve("zip4j-symlink"); Path dest = base.resolve("out"); Files.createDirectories(dest);
            boolean threw = false; String err = "";
            try (net.lingala.zip4j.ZipFile z = new net.lingala.zip4j.ZipFile(file("symlink-link-write.zip"))) { z.extractAll(dest.toString()); }
            catch (Throwable t) { threw = true; err = shortError(t); }
            boolean symlink = Files.isSymbolicLink(dest.resolve("link"));
            boolean escaped = Files.exists(work.resolve("outside-target/pwn.txt")) || Files.exists(base.resolve("outside-target/pwn.txt"));
            if (escaped) throw new AssertionError("symlink link-then-write escaped destination");
            return data("threw", threw, "symlinkCreated", symlink, "escaped", false, "error", err);
        });
        measure(engine, "DECOMPRESSION_LIMIT", () -> {
            try (net.lingala.zip4j.ZipFile z = new net.lingala.zip4j.ZipFile(file("bomb-safe.zip"))) {
                net.lingala.zip4j.model.FileHeader h = z.getFileHeaders().get(0);
                boolean limited = false; long read = 0;
                try { read = consume(z.getInputStream(h), MIB); } catch (LimitExceeded x) { limited = true; read = x.bytes; }
                if (!limited) throw new AssertionError("limit did not trigger");
                return data("limitBytes", MIB, "bytesSeen", read, "limited", true, "declaredSize", h.getUncompressedSize());
            }
        });
        expectFailure(engine, "MALFORMED_TRUNCATED", () -> new net.lingala.zip4j.ZipFile(file("malformed-truncated.zip")).getFileHeaders());
    }

    private static void commonsTar() {
        String engine = "COMMONS_COMPRESS_TAR";
        String[] files = {"normal.tar", "normal.tar.gz", "normal.tar.bz2", "normal.tar.xz", "normal.tar.zst"};
        for (String f : files) {
            if (!file(f).exists()) { rec(engine, "LIST_" + f, "NOT_TESTED", "reason", "fixture unavailable"); continue; }
            measure(engine, "LIST_" + f, () -> {
                long t = System.nanoTime(); int count = 0;
                try (TarArchiveInputStream in = tarInput(file(f))) { while (in.getNextEntry() != null) count++; }
                return data("count", count, "elapsedMs", elapsedMs(t));
            });
        }
        measure(engine, "TRAVERSAL_GUARD", () -> {
            int rejected = 0;
            try (TarArchiveInputStream in = tarInput(file("traversal.tar"))) {
                TarArchiveEntry e; while ((e = in.getNextEntry()) != null) if (safeTarget(work.resolve("tar-traversal"), e.getName()) == null) rejected++;
            }
            if (rejected < 1) throw new AssertionError("traversal not rejected");
            return data("rejected", rejected);
        });
        measure(engine, "SYMLINK_GUARD", () -> {
            int links = 0;
            try (TarArchiveInputStream in = tarInput(file("symlink-link-write.tar"))) {
                TarArchiveEntry e; while ((e = in.getNextEntry()) != null) if (e.isSymbolicLink() || e.isLink()) links++;
            }
            if (links < 1) throw new AssertionError("symlink not detected");
            return data("linksRejected", links);
        });
        expectFailure(engine, "MALFORMED_TRUNCATED", () -> {
            try (TarArchiveInputStream in = tarInput(file("malformed-truncated.tar"))) { while (in.getNextEntry() != null) consume(in, Long.MAX_VALUE); }
        });
    }

    private static void commons7z() {
        String engine = "COMMONS_COMPRESS_7Z";
        for (String f : new String[]{"normal.7z", "solid.7z", "many-10k.7z"}) {
            measure(engine, "LIST_" + f, () -> {
                long before = usedHeapAfterGc(); long t = System.nanoTime(); int count = 0;
                try (SevenZFile z = new SevenZFile(file(f))) { for (SevenZArchiveEntry ignored : z.getEntries()) count++; }
                long ms = elapsedMs(t); long after = usedHeapAfterGc();
                return data("count", count, "elapsedMs", ms, "heapDeltaBytes", after - before);
            });
        }
        expectFailure(engine, "ENCRYPTED_WITHOUT_PASSWORD", () -> {
            try (SevenZFile z = new SevenZFile(file("encrypted.7z"))) { for (SevenZArchiveEntry ignored : z.getEntries()) {} }
        });
        measure(engine, "ENCRYPTED_WITH_PASSWORD", () -> {
            try (SevenZFile z = new SevenZFile(file("encrypted.7z"), "POCpass".toCharArray())) {
                int c = 0; for (SevenZArchiveEntry ignored : z.getEntries()) c++; return data("entries", c);
            }
        });
        for (String f : new String[]{"normal.7z", "solid.7z"}) {
            measure(engine, "ENTRY_OPEN_" + f, () -> {
                try (SevenZFile z = new SevenZFile(file(f))) {
                    SevenZArchiveEntry target = null; for (SevenZArchiveEntry e : z.getEntries()) if (!e.isDirectory()) target = e;
                    long t = System.nanoTime(); long n = consume(z.getInputStream(target), Long.MAX_VALUE);
                    return data("entry", target.getName(), "bytes", n, "elapsedMs", elapsedMs(t));
                }
            });
        }
        measure(engine, "EXTRACT_64M", () -> {
            try (SevenZFile z = new SevenZFile(file("large-64m.7z"))) {
                SevenZArchiveEntry e = null; for (SevenZArchiveEntry x : z.getEntries()) if (!x.isDirectory()) e = x;
                long t = System.nanoTime(); long n = consume(z.getInputStream(e), Long.MAX_VALUE); double sec = (System.nanoTime()-t)/1e9;
                return data("bytes", n, "elapsedMs", sec*1000.0, "mibPerSec", (n/MIB)/sec, "note", "highly-compressible sparse-zero fixture");
            }
        });
        if (file("malformed-truncated.7z").exists()) expectFailure(engine, "MALFORMED_TRUNCATED", () -> new SevenZFile(file("malformed-truncated.7z")).close());
        else rec(engine, "MALFORMED_TRUNCATED", "NOT_TESTED", "reason", "fixture unavailable");
    }

    private static void junrar() {
        String engine = "JUNRAR";
        for (String f : new String[]{"rar4.rar", "rar5.rar", "rar4-solid.rar", "rar5-solid.rar", "stored.part1.rar", "test-documents.part1.rar"}) {
            measure(engine, "LIST_EXTRACT_" + f, () -> {
                long t = System.nanoTime();
                try (Archive a = new Archive(file(f))) {
                    List<FileHeader> hs = a.getFileHeaders(); long n = 0; String first = "";
                    for (FileHeader h : hs) if (!h.isDirectory()) { first = h.getFileName(); n = consume(a.getInputStream(h), Long.MAX_VALUE); break; }
                    return data("format", String.valueOf(a.getFormat()), "entries", hs.size(), "firstEntry", first, "firstEntryBytes", n, "elapsedMs", elapsedMs(t));
                }
            });
        }
        for (String f : new String[]{"rar4-password-junrar.rar", "rar5-password-junrar.rar", "rar5-encrypted-junrar.rar"}) {
            measure(engine, "PASSWORD_" + f, () -> {
                try (Archive a = new Archive(file(f), "junrar")) {
                    List<FileHeader> hs = a.getFileHeaders(); long n = 0; for (FileHeader h : hs) if (!h.isDirectory()) { n = consume(a.getInputStream(h), Long.MAX_VALUE); break; }
                    return data("entries", hs.size(), "passwordProtected", a.isPasswordProtected(), "encryptedHeaders", a.isEncrypted(), "firstEntryBytes", n);
                }
            });
        }
        for (String f : new String[]{"rar-mkdir-escape.rar", "rar-sibling-prefix-traversal.rar"}) {
            measure(engine, "TRAVERSAL_GUARD_" + f, () -> {
                int rejected = 0; int entries = 0;
                try (Archive a = new Archive(file(f))) {
                    for (FileHeader h : a.getFileHeaders()) { entries++; if (safeTarget(work.resolve("rar-safe"), h.getFileName()) == null) rejected++; }
                }
                return data("entries", entries, "rejectedByGuard", rejected);
            });
        }
        measure(engine, "MALFORMED_CORRUPT_HEADER", () -> {
            boolean threw = false; boolean broken = false; int failures = 0; String err = "";
            try (Archive a = new Archive(file("rar-malformed-corrupt-header.rar"))) { broken = a.hasBrokenHeaders(); failures = a.getHeaderFailures().size(); }
            catch (Throwable t) { threw = true; err = shortError(t); }
            if (!threw && !broken && failures == 0) throw new AssertionError("malformed archive accepted without signal");
            return data("threw", threw, "hasBrokenHeaders", broken, "headerFailures", failures, "error", err);
        });
        rec(engine, "RAR_CREATION", "NO", "reason", "Junrar exposes read/extract APIs only; RAR creation not claimed");
    }

    private static TarArchiveInputStream tarInput(File f) throws IOException {
        InputStream raw = new BufferedInputStream(new FileInputStream(f));
        String n = f.getName();
        if (n.endsWith(".tar.gz")) raw = new GzipCompressorInputStream(raw);
        else if (n.endsWith(".tar.bz2")) raw = new BZip2CompressorInputStream(raw);
        else if (n.endsWith(".tar.xz")) raw = new XZCompressorInputStream(raw);
        else if (n.endsWith(".tar.zst")) raw = new ZstdCompressorInputStream(raw);
        return new TarArchiveInputStream(raw);
    }

    private static File file(String name) { return fixtures.resolve(name).toFile(); }

    private static Path safeTarget(Path root, String rawName) {
        if (rawName == null || rawName.indexOf('\0') >= 0) return null;
        String n = rawName.replace('\\', '/');
        if (n.startsWith("/") || n.matches("^[A-Za-z]:/.*") || n.startsWith("//")) return null;
        Path r = root.toAbsolutePath().normalize();
        Path t = r.resolve(n).normalize();
        return t.startsWith(r) ? t : null;
    }

    private static long consume(InputStream in, long limit) throws IOException, LimitExceeded {
        try (InputStream x = in) {
            byte[] b = new byte[128 * 1024]; long n = 0; int r;
            while ((r = x.read(b)) >= 0) { n += r; if (n > limit) throw new LimitExceeded(n); }
            return n;
        }
    }

    private static long usedHeapAfterGc() throws InterruptedException {
        System.gc(); Thread.sleep(60L); Runtime r = Runtime.getRuntime(); return r.totalMemory() - r.freeMemory();
    }

    private static void measure(String engine, String test, CheckedSupplier<Map<String,Object>> body) {
        try { recMap(engine, test, "PASS", body.get()); }
        catch (Throwable t) { rec(engine, test, "FAIL", "error", shortError(t)); }
    }

    private static void expectFailure(String engine, String test, CheckedRunnable body) {
        try { body.run(); rec(engine, test, "FAIL", "reason", "malformed/encrypted input unexpectedly accepted without failure"); }
        catch (Throwable t) { rec(engine, test, "PASS", "expectedFailure", shortError(t)); }
    }

    private static Map<String,Object> data(Object... kv) {
        LinkedHashMap<String,Object> m = new LinkedHashMap<>(); for (int i=0;i+1<kv.length;i+=2) m.put(String.valueOf(kv[i]), kv[i+1]); return m;
    }

    private static synchronized void rec(String engine, String test, String status, Object... kv) {
        recMap(engine, test, status, data(kv));
    }

    private static synchronized void recMap(String engine, String test, String status, Map<String,Object> data) {
        try {
            StringBuilder s = new StringBuilder();
            s.append('{').append("\"ts\":").append(System.currentTimeMillis())
             .append(",\"engine\":").append(json(engine)).append(",\"test\":").append(json(test))
             .append(",\"status\":").append(json(status)).append(",\"data\":{");
            boolean first = true; for (Map.Entry<String,Object> e : data.entrySet()) { if (!first) s.append(','); first=false; s.append(json(e.getKey())).append(':').append(jsonValue(e.getValue())); }
            s.append("}}\n"); out.write(s.toString()); out.flush(); System.out.print(s);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static String jsonValue(Object v) {
        if (v == null) return "null"; if (v instanceof Number || v instanceof Boolean) return String.valueOf(v); return json(String.valueOf(v));
    }
    private static String json(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""; }
    private static String shortError(Throwable t) { String m = t.getMessage(); return t.getClass().getName() + (m == null ? "" : ": " + m); }
    private static long elapsedMs(long start) { return (System.nanoTime()-start)/1_000_000L; }
    private static long elapsedMicros(long start) { return (System.nanoTime()-start)/1_000L; }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (var s = Files.walk(p)) { s.sorted(Comparator.reverseOrder()).forEach(x -> { try { Files.deleteIfExists(x); } catch (IOException e) { throw new UncheckedIOException(e); } }); }
    }

    @FunctionalInterface private interface CheckedRunnable { void run() throws Exception; }
    @FunctionalInterface private interface CheckedSupplier<T> { T get() throws Exception; }
    private static final class LimitExceeded extends Exception { final long bytes; LimitExceeded(long b) { super("limit exceeded at " + b); bytes=b; } }
}
