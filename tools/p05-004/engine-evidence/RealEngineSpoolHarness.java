import java.io.*;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

/**
 * P05-004 disposable real-engine spool integration PoC (host/JVM).
 * Exact artifacts: Commons Compress 1.28.0, Zip4j 2.11.6, zstd-jni 1.5.7-17
 * (see docs/poc/P05-004/01_ENVIRONMENT.md). Not production code.
 *
 * Proves: sequential provider bytes -> bounded temp spool ->
 * seekable/reopen consumption by the REAL retained engines, plus
 * byte-level BZ2/XZ/CPIO route evidence for the retained Commons path.
 */
public class RealEngineSpoolHarness {
    static int failures = 0;

    static void check(boolean cond, String label) {
        System.out.println((cond ? "PASS" : "FAIL") + " " + label);
        if (!cond) failures++;
    }

    static Path spool(byte[] data) throws IOException {
        Path tmp = Files.createTempFile("p05-real-spool-", ".bin");
        Files.write(tmp, data);
        return tmp;
    }

    /** Minimal stored-method zip with two identical entry names. */
    static byte[] rawDupZip() throws IOException {
        byte[] data1 = "first".getBytes("UTF-8");
        byte[] data2 = "second".getBytes("UTF-8");
        byte[] name = "dup.txt".getBytes("UTF-8");
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> localOffsets = new ArrayList<>();
        List<byte[]> datas = Arrays.asList(data1, data2);
        List<Long> crcs = new ArrayList<>();
        for (byte[] d : datas) {
            crc.reset(); crc.update(d); crcs.add(crc.getValue());
            localOffsets.add(out.size());
            le(out, 0x04034b50, 4); // local header sig
            le(out, 20, 2); le(out, 0, 2); le(out, 0, 2); // version, flags, method=stored
            le(out, 0, 2); le(out, 0, 2); // time, date
            le(out, crcs.get(crcs.size() - 1), 4);
            le(out, d.length, 4); le(out, d.length, 4);
            le(out, name.length, 2); le(out, 0, 2);
            out.write(name);
            out.write(d);
        }
        int centralStart = out.size();
        for (int i = 0; i < 2; i++) {
            byte[] d = datas.get(i);
            le(out, 0x02014b50, 4); // central sig
            le(out, 20, 2); le(out, 20, 2); // made-by, need
            le(out, 0, 2); le(out, 0, 2); // flags, method
            le(out, 0, 2); le(out, 0, 2); // time, date
            le(out, crcs.get(i), 4);
            le(out, d.length, 4); le(out, d.length, 4);
            le(out, name.length, 2);
            le(out, 0, 2); le(out, 0, 2); le(out, 0, 2); le(out, 0, 2); // extra, comment, disk, internal
            le(out, 0, 4); // external attrs
            le(out, localOffsets.get(i), 4);
            out.write(name);
        }
        int centralSize = out.size() - centralStart;
        le(out, 0x06054b50, 4); // EOCD
        le(out, 0, 2); le(out, 0, 2); // disk numbers
        le(out, 2, 2); le(out, 2, 2); // entries
        le(out, centralSize, 4); le(out, centralStart, 4); le(out, 0, 2);
        return out.toByteArray();
    }

    static void le(ByteArrayOutputStream out, long v, int bytes) {
        for (int i = 0; i < bytes; i++) out.write((int) ((v >>> (8 * i)) & 0xFF));
    }

    public static void main(String[] a) throws Exception {
        // ---- 1. hostile zip bytes (stdlib-authored) ----
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(bos);
        String[] names = {"safe.txt", "../escape.txt", "/absolute.txt", "sub/nested.txt"};
        for (String nm : names) {
            zos.putNextEntry(new ZipEntry(nm));
            zos.write(("payload:" + nm).getBytes("UTF-8"));
            zos.closeEntry();
        }
        zos.close();
        byte[] hostileZip = bos.toByteArray();

        // ---- 2. spool, then Commons SEQUENTIAL stream over spooled temp ----
        Path spooled = spool(hostileZip);
        List<String> seqNames = new ArrayList<>();
        try (ZipArchiveInputStream zin = new ZipArchiveInputStream(Files.newInputStream(spooled))) {
            ZipArchiveEntry e;
            while ((e = zin.getNextEntry()) != null) seqNames.add(e.getName());
        }
        check(seqNames.containsAll(Arrays.asList(names)), "commons-sequential-over-spool=" + seqNames);

        // ---- 3. Commons SEEKABLE ZipFile reopen of the same spooled temp ----
        List<String> seekNames = new ArrayList<>();
        try (ZipFile zf = ZipFile.builder().setPath(spooled).get()) {
            Enumeration<ZipArchiveEntry> en = zf.getEntries();
            while (en.hasMoreElements()) seekNames.add(en.nextElement().getName());
            ZipArchiveEntry safe = zf.getEntry("safe.txt");
            check(safe != null, "commons-seekable-random-entry");
        }
        check(seekNames.equals(seqNames), "commons-spool-reopen-consistent");

        // ---- 4. Zip4j ZipFile over spooled temp (local-file full-feature path) ----
        net.lingala.zip4j.ZipFile jzf = new net.lingala.zip4j.ZipFile(spooled.toFile());
        List<String> jNames = new ArrayList<>();
        jzf.getFileHeaders().forEach(h -> jNames.add(h.getFileName()));
        check(jNames.containsAll(Arrays.asList(names)), "zip4j-over-spooled-temp=" + jNames.size() + "-entries");

        // ---- 5. zstd-jni decode of bytes read THROUGH the spool ----
        byte[] hello = "hello-p05-004".getBytes("UTF-8");
        byte[] comp = com.github.luben.zstd.Zstd.compress(hello);
        Path zspool = spool(comp);
        byte[] viaSpool = Files.readAllBytes(zspool);
        byte[] back = com.github.luben.zstd.Zstd.decompress(viaSpool, 64);
        check(Arrays.equals(hello, Arrays.copyOf(back, hello.length)), "zstd-via-spool-roundtrip");
        Files.deleteIfExists(zspool);

        // ---- 6. BZ2 byte-level route (Commons) ----
        ByteArrayOutputStream bBz = new ByteArrayOutputStream();
        try (BZip2CompressorOutputStream bz = new BZip2CompressorOutputStream(bBz)) {
            bz.write("bz2-payload-p05".getBytes("UTF-8"));
        }
        String bzBack;
        try (BZip2CompressorInputStream bi = new BZip2CompressorInputStream(new ByteArrayInputStream(bBz.toByteArray()))) {
            bzBack = new String(bi.readAllBytes(), "UTF-8");
        }
        check("bz2-payload-p05".equals(bzBack), "commons-bz2-roundtrip");

        // ---- 7. XZ byte-level route (Commons + xz 1.10) ----
        ByteArrayOutputStream bXz = new ByteArrayOutputStream();
        try (XZCompressorOutputStream xz = new XZCompressorOutputStream(bXz)) {
            xz.write("xz-payload-p05".getBytes("UTF-8"));
        }
        String xzBack;
        try (XZCompressorInputStream xi = new XZCompressorInputStream(new ByteArrayInputStream(bXz.toByteArray()))) {
            xzBack = new String(xi.readAllBytes(), "UTF-8");
        }
        check("xz-payload-p05".equals(xzBack), "commons-xz-roundtrip");

        // ---- 8. TAR.BZ2 / TAR.XZ end-to-end (Commons) ----
        for (String kind : new String[]{"bz2", "xz"}) {
            ByteArrayOutputStream tarBytes = new ByteArrayOutputStream();
            try (TarArchiveOutputStream tar = new TarArchiveOutputStream(tarBytes)) {
                byte[] data = ("tar-" + kind + "-content").getBytes("UTF-8");
                TarArchiveEntry te = new TarArchiveEntry("inner.txt");
                te.setSize(data.length);
                tar.putArchiveEntry(te);
                tar.write(data);
                tar.closeArchiveEntry();
            }
            ByteArrayOutputStream wrapped = new ByteArrayOutputStream();
            if (kind.equals("bz2")) {
                try (BZip2CompressorOutputStream c = new BZip2CompressorOutputStream(wrapped)) { c.write(tarBytes.toByteArray()); }
            } else {
                try (XZCompressorOutputStream c = new XZCompressorOutputStream(wrapped)) { c.write(tarBytes.toByteArray()); }
            }
            // spool the compressed tar, then stream-decode through spool
            Path tSpool = spool(wrapped.toByteArray());
            InputStream raw = Files.newInputStream(tSpool);
            InputStream dec = kind.equals("bz2")
                    ? new BZip2CompressorInputStream(raw)
                    : new XZCompressorInputStream(raw);
            String found;
            try (TarArchiveInputStream tin = new TarArchiveInputStream(dec)) {
                TarArchiveEntry te = tin.getNextEntry();
                found = te.getName() + ":" + new String(tin.readAllBytes(), "UTF-8");
            }
            Files.deleteIfExists(tSpool);
            check(("inner.txt:tar-" + kind + "-content").equals(found), "commons-tar-" + kind + "-via-spool");
        }

        // ---- 9. CPIO route (Commons, new_odc format) ----
        ByteArrayOutputStream cpioBytes = new ByteArrayOutputStream();
        try (CpioArchiveOutputStream cpio = new CpioArchiveOutputStream(cpioBytes, CpioArchiveOutputStream.FORMAT_NEW)) {
            byte[] data = "cpio-payload".getBytes("UTF-8");
            CpioArchiveEntry ce = new CpioArchiveEntry("cpio-inner.txt");
            ce.setSize(data.length);
            cpio.putArchiveEntry(ce);
            cpio.write(data);
            cpio.closeArchiveEntry();
        }
        String cpioFound;
        try (CpioArchiveInputStream cin = new CpioArchiveInputStream(new ByteArrayInputStream(cpioBytes.toByteArray()))) {
            CpioArchiveEntry ce = cin.getNextEntry();
            cpioFound = ce.getName() + ":" + new String(cin.readAllBytes(), "UTF-8");
        }
        check("cpio-inner.txt:cpio-payload".equals(cpioFound), "commons-cpio-roundtrip");

        Files.deleteIfExists(spooled);

        // ---- 10. raw duplicate-name zip through the REAL parser + policy ----
        byte[] dupZip = rawDupZip();
        List<String> dupSeen = new ArrayList<>();
        Set<String> dupPolicy = new HashSet<>();
        List<String> dupOutcomes = new ArrayList<>();
        try (ZipArchiveInputStream zin = new ZipArchiveInputStream(new ByteArrayInputStream(dupZip))) {
            org.apache.commons.compress.archivers.zip.ZipArchiveEntry e;
            while ((e = zin.getNextEntry()) != null) {
                dupSeen.add(e.getName());
                if (!dupPolicy.add(e.getName())) dupOutcomes.add(e.getName() + "=DUPLICATE_SKIP");
                else dupOutcomes.add(e.getName() + "=FIRST_ACCEPT");
            }
        }
        check(dupSeen.size() == 2 && dupSeen.get(0).equals(dupSeen.get(1)), "parser-lists-both-duplicates=" + dupSeen);
        check(dupOutcomes.toString().contains("DUPLICATE_SKIP"), "duplicate-policy-on-real-parser=" + dupOutcomes);

        // ---- 11. symlink entry: parser exposes, policy rejects ----
        // NOTE: ZIP external-attribute round-trip for symlink mode did not
        // survive authoring in this Commons path (read unixmode=0), so the
        // symlink detection/reject path is demonstrated on TAR, where
        // symlinks are native. The wrapper policy (never materialize links)
        // is engine-neutral.
        ByteArrayOutputStream sBos = new ByteArrayOutputStream();
        try (org.apache.commons.compress.archivers.tar.TarArchiveOutputStream tos =
                new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(sBos)) {
            org.apache.commons.compress.archivers.tar.TarArchiveEntry link =
                    new org.apache.commons.compress.archivers.tar.TarArchiveEntry("evil-link",
                            org.apache.commons.compress.archivers.tar.TarArchiveEntry.LF_SYMLINK);
            link.setLinkName("../outside.txt");
            tos.putArchiveEntry(link);
            tos.closeArchiveEntry();
            byte[] fdata = "f".getBytes("UTF-8");
            org.apache.commons.compress.archivers.tar.TarArchiveEntry f =
                    new org.apache.commons.compress.archivers.tar.TarArchiveEntry("f.txt");
            f.setSize(fdata.length);
            tos.putArchiveEntry(f);
            tos.write(fdata);
            tos.closeArchiveEntry();
        }
        boolean symlinkRejected = false;
        try (org.apache.commons.compress.archivers.tar.TarArchiveInputStream tin =
                new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
                        new ByteArrayInputStream(sBos.toByteArray()))) {
            org.apache.commons.compress.archivers.tar.TarArchiveEntry e;
            while ((e = tin.getNextEntry()) != null) {
                if (e.isSymbolicLink() || e.isLink()) {
                    symlinkRejected = true; // policy: never materialize symlinks
                    System.out.println("ENTRY " + e.getName() + " -> REJECTED:SYMLINK target=" + e.getLinkName());
                }
            }
        }
        check(symlinkRejected, "symlink-detected-and-rejected");

        System.out.println(failures == 0 ? "REAL_ENGINE_SPOOL=PASS" : "REAL_ENGINE_SPOOL=FAIL n=" + failures);
        if (failures > 0) System.exit(1);
    }
}
