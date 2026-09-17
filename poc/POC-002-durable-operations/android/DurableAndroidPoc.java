// POC-ONLY — NOT PRODUCTION AUTHORITY
import android.os.Debug;
import android.os.Process;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public final class DurableAndroidPoc {
    private static final int BUF = 256 * 1024;
    private static final long CHECKPOINT = 8L * 1024L * 1024L;
    private static File root, source, partial, dest, stateFile, eventsFile;
    private static Properties st;
    private static long peakHeap, peakPssKb;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("usage: <root> setup <bytes> <COPY|MOVE> | run <fault>");
        root = new File(args[0]);
        source = new File(root, "source.bin");
        partial = new File(root, "destination.partial");
        dest = new File(root, "destination.bin");
        stateFile = new File(root, "state.properties");
        eventsFile = new File(root, "events.jsonl");
        String cmd = args[1];
        if ("setup".equals(cmd)) {
            if (args.length != 4) throw new IllegalArgumentException("setup requires bytes and type");
            setup(Long.parseLong(args[2]), args[3]);
            return;
        }
        if ("run".equals(cmd)) {
            if (args.length != 3) throw new IllegalArgumentException("run requires fault");
            run(args[2]);
            return;
        }
        if ("show".equals(cmd)) {
            load();
            for (String k : new TreeSet<>(st.stringPropertyNames())) System.out.println(k + "=" + st.getProperty(k));
            return;
        }
        throw new IllegalArgumentException("unknown command " + cmd);
    }

    private static void setup(long bytes, String type) throws Exception {
        deleteTree(root.toPath());
        if (!root.mkdirs() && !root.isDirectory()) throw new IOException("mkdir " + root);
        try (RandomAccessFile raf = new RandomAccessFile(source, "rw")) {
            raf.setLength(bytes);
            byte[] marker = "POC002-DURABLE-ANDROID".getBytes(StandardCharsets.UTF_8);
            raf.seek(0); raf.write(marker);
            if (bytes > marker.length * 2L) { raf.seek(bytes / 2); raf.write(marker); }
            if (bytes > marker.length) { raf.seek(Math.max(0, bytes - marker.length)); raf.write(marker); }
            raf.getFD().sync();
        }
        st = new Properties();
        st.setProperty("opId", UUID.randomUUID().toString());
        st.setProperty("type", type);
        st.setProperty("source", source.getAbsolutePath());
        st.setProperty("partial", partial.getAbsolutePath());
        st.setProperty("destination", dest.getAbsolutePath());
        st.setProperty("phase", "PLAN");
        st.setProperty("totalBytes", Long.toString(bytes));
        st.setProperty("completedBytes", "0");
        st.setProperty("sourceLength", Long.toString(source.length()));
        st.setProperty("sourceMtime", Long.toString(source.lastModified()));
        st.setProperty("verification", "NONE");
        st.setProperty("retryCount", "0");
        st.setProperty("lastError", "");
        st.setProperty("createdAt", Long.toString(System.currentTimeMillis()));
        st.setProperty("updatedAt", Long.toString(System.currentTimeMillis()));
        st.setProperty("sourceDeleted", "false");
        st.setProperty("atomicFinalize", "UNKNOWN");
        save();
        event("SETUP", map("bytes", bytes, "type", type, "sourceMtime", source.lastModified()));
    }

    private static void run(String fault) throws Exception {
        load();
        long total = l("totalBytes");
        if (!source.exists()) { block("SOURCE_MISSING"); return; }
        if (source.length() != l("sourceLength") || source.lastModified() != l("sourceMtime")) {
            block("SOURCE_MUTATED");
            return;
        }

        if (!partial.exists() && !"COMPLETE".equals(st.getProperty("phase"))) {
            if (!partial.createNewFile()) throw new IOException("create partial");
            st.setProperty("phase", "CREATE_PARTIAL"); save();
            event("CREATE_PARTIAL", map("path", partial.getAbsolutePath()));
        }

        long actual = partial.exists() ? partial.length() : 0;
        long durable = l("completedBytes");
        if (actual != durable && !"COMPLETE".equals(st.getProperty("phase"))) {
            st.setProperty("completedBytes", Long.toString(actual));
            st.setProperty("phase", actual >= total ? "VERIFY" : "TRANSFER");
            save();
            event("RECOVER_RECONCILE", map("durableBytes", durable, "actualPartialBytes", actual));
        }

        String phase = st.getProperty("phase");
        if ("COMPLETE".equals(phase)) { event("ALREADY_COMPLETE", map()); return; }
        if ("BLOCKED".equals(phase) || "CANCELLED".equals(phase)) {
            st.setProperty("retryCount", Long.toString(l("retryCount") + 1));
            st.setProperty("phase", actual >= total ? "VERIFY" : "TRANSFER");
            st.setProperty("lastError", ""); save();
            event("RETRY", map("from", phase));
        }

        if (l("completedBytes") < total) transfer(fault, total);
        else if (!"VERIFIED".equals(st.getProperty("phase"))) {
            st.setProperty("phase", "VERIFY"); save();
        }
        if ("CANCELLED".equals(st.getProperty("phase")) || "BLOCKED".equals(st.getProperty("phase"))) return;

        if ("kill_after_transfer".equals(fault)) killNow("AFTER_TRANSFER_BEFORE_VERIFY");

        if (!"VERIFIED".equals(st.getProperty("phase"))) verify();
        if ("kill_after_verify".equals(fault)) killNow("AFTER_VERIFY_BEFORE_FINALIZE");

        if ("conflict_final".equals(fault)) {
            try (FileOutputStream o = new FileOutputStream(dest)) { o.write("CONFLICT".getBytes(StandardCharsets.UTF_8)); o.getFD().sync(); }
        }
        finalizeDestination();
        if ("MOVE".equals(st.getProperty("type"))) {
            if (!"COMPLETE".equals(st.getProperty("phase"))) throw new IllegalStateException("delete before COMPLETE");
            boolean deleted = source.delete();
            st.setProperty("sourceDeleted", Boolean.toString(deleted)); save();
            event("MOVE_SOURCE_DELETE", map("afterPhase", "COMPLETE", "deleted", deleted));
        }
    }

    private static void transfer(String fault, long total) throws Exception {
        st.setProperty("phase", "TRANSFER"); save();
        long completed = l("completedBytes");
        long started = System.nanoTime();
        long nextCheckpoint = ((completed / CHECKPOINT) + 1) * CHECKPOINT;
        long killAt = killThreshold(fault, total);
        try (FileInputStream in = new FileInputStream(source);
             RandomAccessFile out = new RandomAccessFile(partial, "rw")) {
            in.getChannel().position(completed);
            out.seek(completed);
            byte[] buf = new byte[BUF];
            while (completed < total) {
                int want = (int)Math.min(buf.length, total - completed);
                int n = in.read(buf, 0, want);
                if (n < 0) throw new EOFException("source ended at " + completed);
                out.write(buf, 0, n);
                completed += n;
                sampleMemory();
                if (killAt > 0 && completed >= killAt) {
                    out.getFD().sync();
                    event("INJECT_PROCESS_KILL", map("fault", fault, "actualBytes", completed, "durableBytes", l("completedBytes")));
                    Process.killProcess(Process.myPid());
                    System.exit(137);
                }
                if ("cancel_mid".equals(fault) && completed >= total / 2) {
                    out.getFD().sync();
                    st.setProperty("completedBytes", Long.toString(completed));
                    st.setProperty("phase", "CANCELLED"); save();
                    event("CANCELLED", map("completedBytes", completed, "partialBytes", partial.length()));
                    return;
                }
                if ("enospc_mid".equals(fault) && completed >= total / 2) {
                    out.getFD().sync();
                    st.setProperty("completedBytes", Long.toString(completed));
                    st.setProperty("phase", "BLOCKED");
                    st.setProperty("lastError", "INJECTED_ENOSPC"); save();
                    event("FAULT_ENOSPC", map("kind", "injected", "completedBytes", completed));
                    return;
                }
                if (completed >= nextCheckpoint || completed == total) {
                    out.getFD().sync();
                    st.setProperty("completedBytes", Long.toString(completed)); save();
                    event("CHECKPOINT", map("completedBytes", completed, "peakHeapBytes", peakHeap, "peakPssKb", peakPssKb));
                    nextCheckpoint += CHECKPOINT;
                }
            }
            out.getFD().sync();
        }
        st.setProperty("completedBytes", Long.toString(total));
        st.setProperty("phase", "VERIFY");
        st.setProperty("transferMs", Double.toString((System.nanoTime() - started) / 1_000_000.0));
        st.setProperty("peakHeapBytes", Long.toString(peakHeap));
        st.setProperty("peakPssKb", Long.toString(peakPssKb));
        save();
        event("TRANSFER_DONE", map("bytes", total, "transferMs", st.getProperty("transferMs"), "peakHeapBytes", peakHeap, "peakPssKb", peakPssKb));
    }

    private static void verify() throws Exception {
        st.setProperty("phase", "VERIFY"); save();
        long t = System.nanoTime();
        String src = sha256(source);
        String dst = sha256(partial);
        boolean same = src.equals(dst) && source.length() == partial.length();
        st.setProperty("sourceSha256", src);
        st.setProperty("partialSha256", dst);
        st.setProperty("verification", same ? "SHA256_MATCH" : "FAILED");
        st.setProperty("verifyMs", Double.toString((System.nanoTime() - t) / 1_000_000.0));
        if (!same) { block("VERIFY_MISMATCH"); return; }
        st.setProperty("phase", "VERIFIED"); save();
        event("VERIFY_DONE", map("verifyMs", st.getProperty("verifyMs"), "sha256", src));
    }

    private static void finalizeDestination() throws Exception {
        if (!"VERIFIED".equals(st.getProperty("phase"))) return;
        if (dest.exists()) { block("DESTINATION_CONFLICT"); return; }
        st.setProperty("phase", "FINALIZE"); save();
        boolean atomic = true;
        try { Files.move(partial.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { atomic = false; Files.move(partial.toPath(), dest.toPath()); }
        st.setProperty("atomicFinalize", Boolean.toString(atomic));
        st.setProperty("phase", "COMPLETE");
        st.setProperty("completedBytes", st.getProperty("totalBytes"));
        st.setProperty("completedAt", Long.toString(System.currentTimeMillis())); save();
        event("COMPLETE", map("atomicFinalize", atomic, "destinationBytes", dest.length(), "sourceExistsAtComplete", source.exists()));
    }

    private static long killThreshold(String fault, long total) {
        if ("kill_early".equals(fault)) return Math.max(BUF, total / 20);
        if ("kill_mid".equals(fault)) return total / 2;
        if ("kill_near".equals(fault)) return Math.max(BUF, total * 95 / 100);
        return -1;
    }

    private static void killNow(String where) throws Exception {
        event("INJECT_PROCESS_KILL", map("fault", where, "phase", st.getProperty("phase"), "completedBytes", l("completedBytes")));
        Process.killProcess(Process.myPid());
        System.exit(137);
    }

    private static void block(String error) throws Exception {
        if (st == null) load();
        st.setProperty("phase", "BLOCKED"); st.setProperty("lastError", error); save();
        event("BLOCKED", map("error", error, "completedBytes", l("completedBytes"), "partialBytes", partial.exists() ? partial.length() : -1));
    }

    private static void sampleMemory() {
        Runtime r = Runtime.getRuntime();
        peakHeap = Math.max(peakHeap, r.totalMemory() - r.freeMemory());
        peakPssKb = Math.max(peakPssKb, Debug.getPss());
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] b = new byte[BUF];
        try (InputStream in = new BufferedInputStream(new FileInputStream(f), BUF)) {
            int n; while ((n = in.read(b)) >= 0) md.update(b, 0, n);
        }
        StringBuilder s = new StringBuilder(); for (byte x : md.digest()) s.append(String.format(Locale.ROOT, "%02x", x)); return s.toString();
    }

    private static void load() throws Exception {
        st = new Properties(); try (FileInputStream in = new FileInputStream(stateFile)) { st.load(in); }
    }
    private static long l(String k) { return Long.parseLong(st.getProperty(k, "0")); }

    private static void save() throws Exception {
        st.setProperty("updatedAt", Long.toString(System.currentTimeMillis()));
        File tmp = new File(root, "state.properties.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) { st.store(out, "POC-ONLY — NOT PRODUCTION AUTHORITY"); out.getFD().sync(); }
        try { Files.move(tmp.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(tmp.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING); }
    }

    private static synchronized void event(String name, Map<String,Object> data) throws Exception {
        StringBuilder s = new StringBuilder();
        s.append('{').append("\"ts\":").append(System.currentTimeMillis()).append(",\"event\":").append(q(name)).append(",\"data\":{");
        boolean first = true; for (Map.Entry<String,Object> e : data.entrySet()) { if (!first) s.append(','); first=false; s.append(q(e.getKey())).append(':').append(j(e.getValue())); }
        s.append("}}\n");
        try (FileOutputStream out = new FileOutputStream(eventsFile, true)) { out.write(s.toString().getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
        System.out.print(s);
    }
    private static Map<String,Object> map(Object... kv) { LinkedHashMap<String,Object> m = new LinkedHashMap<>(); for (int i=0;i+1<kv.length;i+=2) m.put(String.valueOf(kv[i]), kv[i+1]); return m; }
    private static String j(Object v) { if (v == null) return "null"; if (v instanceof Number || v instanceof Boolean) return String.valueOf(v); return q(String.valueOf(v)); }
    private static String q(String s) { return "\"" + s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\""; }

    private static void deleteTree(Path p) throws Exception {
        if (!Files.exists(p)) return;
        try (java.util.stream.Stream<Path> s = Files.walk(p)) {
            s.sorted(Comparator.reverseOrder()).forEach(x -> { try { Files.deleteIfExists(x); } catch (IOException e) { throw new UncheckedIOException(e); } });
        }
    }
}
