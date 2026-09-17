package dev.poc.filemanager.storagecap;

// POC-ONLY — NOT PRODUCTION AUTHORITY

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int REQUEST_TREE = 1001;
    private static final String PREF = "poc001";
    private static final String KEY_TREE = "treeUri";
    private static final long LARGE_SPARSE_SIZE = 4L * 1024L * 1024L * 1024L + 512L * 1024L * 1024L;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        String mode = getIntent().getStringExtra("mode");
        if (mode == null) return;
        switch (mode) {
            case "clear": clearResults(); finish(); break;
            case "environment": runAsync("environment", this::recordEnvironment); break;
            case "direct": runAsync("direct", this::runDirect); break;
            case "select_saf": chooseTree(); break;
            case "saf": runAsync("saf", this::runSaf); break;
            case "saf_probe": runAsync("saf_probe", this::probePersistedSaf); break;
            case "saf_release": runAsync("saf_release", this::releasePersistedSaf); break;
            case "pipe": runAsync("pipe", this::runSyntheticPipe); break;
            case "all_no_picker": runAsync("all_no_picker", () -> { recordEnvironment(); runDirect(); runSyntheticPipe(); }); break;
            default: record("CONTROL", "UNKNOWN_MODE", "FAIL", obj("mode", mode));
        }
    }

    private void buildUi() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 24, 24, 24);
        status = new TextView(this);
        status.setText("POC-001 — POC-ONLY — NOT PRODUCTION AUTHORITY\nSelect or run an isolated test.");
        box.addView(status);
        addButton(box, "Run environment + direct + synthetic pipe", () -> runAsync("all_no_picker", () -> { recordEnvironment(); runDirect(); runSyntheticPipe(); }));
        addButton(box, "Select SAF tree", this::chooseTree);
        addButton(box, "Run SAF tests", () -> runAsync("saf", this::runSaf));
        addButton(box, "Probe persisted SAF grant", () -> runAsync("saf_probe", this::probePersistedSaf));
        addButton(box, "Clear results", this::clearResults);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        setContentView(scroll);
    }

    private void addButton(LinearLayout box, String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(v -> action.run());
        box.addView(b);
    }

    private void runAsync(String name, ThrowingRunnable body) {
        status.setText("Running: " + name);
        new Thread(() -> {
            try {
                body.run();
                record("CONTROL", "RUN_DONE", "PASS", obj("mode", name));
                runOnUiThread(() -> status.setText("Done: " + name));
            } catch (Throwable t) {
                recordFailure("CONTROL", "RUN_FATAL", t, obj("mode", name));
                runOnUiThread(() -> status.setText("Failed: " + name + " — " + t));
            }
        }, "poc001-" + name).start();
    }

    private void chooseTree() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i, REQUEST_TREE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_TREE) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            record("SAF", "TREE_SELECTION", "FAIL", obj("reason", "cancelled_or_missing_uri"));
            status.setText("SAF tree selection cancelled");
            return;
        }
        Uri uri = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
            getSharedPreferences(PREF, MODE_PRIVATE).edit().putString(KEY_TREE, uri.toString()).apply();
            record("SAF", "TREE_SELECTION", "PASS", obj("uri", uri.toString(), "grantFlags", flags));
            status.setText("Selected: " + uri);
        } catch (Throwable t) {
            recordFailure("SAF", "TREE_SELECTION", t, obj("uri", uri.toString(), "grantFlags", flags));
        }
    }

    private void recordEnvironment() {
        JSONObject d = obj(
            "sdkInt", Build.VERSION.SDK_INT,
            "release", Build.VERSION.RELEASE,
            "codename", Build.VERSION.CODENAME,
            "model", Build.MODEL,
            "manufacturer", Build.MANUFACTURER,
            "fingerprint", Build.FINGERPRINT,
            "isExternalStorageManager", Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager(),
            "externalState", Environment.getExternalStorageState(),
            "externalDir", String.valueOf(getExternalFilesDir(null)),
            "publicDocuments", String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
        );
        record("ENV", "ANDROID_ENVIRONMENT", "PASS", d);
        JSONArray grants = new JSONArray();
        for (UriPermission p : getContentResolver().getPersistedUriPermissions()) {
            grants.put(obj("uri", p.getUri().toString(), "read", p.isReadPermission(), "write", p.isWritePermission(), "persistedTime", p.getPersistedTime()));
        }
        record("ENV", "PERSISTED_GRANTS", "PASS", obj("grants", grants));
    }

    private void runDirect() throws Exception {
        File root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "FileManagerPoc001Direct");
        deleteRecursive(root);
        require(root.mkdirs(), "mkdir root");
        record("DIRECT", "ROOT", "PASS", obj("path", root.getAbsolutePath(), "canRead", root.canRead(), "canWrite", root.canWrite()));

        List<String> names = Arrays.asList(
            "zero.bin",
            "small text.txt",
            "ユニコード.txt",
            "emoji-😀.txt",
            "quotes-'\".txt",
            "shell-$;[]{}.txt",
            "line\nbreak.txt",
            repeat("l", 220) + ".txt"
        );
        for (String name : names) {
            File f = new File(root, name);
            try {
                try (FileOutputStream out = new FileOutputStream(f)) {
                    if (!"zero.bin".equals(name)) out.write(("fixture:" + name).getBytes(StandardCharsets.UTF_8));
                }
                record("DIRECT", "CREATE_FIXTURE", "PASS", obj("name", name, "length", f.length(), "exists", f.exists()));
            } catch (Throwable t) {
                recordFailure("DIRECT", "CREATE_FIXTURE", t, obj("name", name, "path", f.getAbsolutePath()));
            }
        }

        File nested = new File(root, "nested/a/b/c");
        require(nested.mkdirs(), "nested mkdir");
        record("DIRECT", "MKDIR_NESTED", "PASS", obj("path", nested.getAbsolutePath()));

        File medium = new File(root, "medium.bin");
        createPatternFile(medium, 64L * 1024L * 1024L);
        record("DIRECT", "MEDIUM_FIXTURE", "PASS", obj("bytes", medium.length(), "sha256", sha256(medium)));

        long listStart = System.nanoTime();
        File[] listed = root.listFiles();
        record("DIRECT", "LIST", listed != null ? "PASS" : "FAIL", obj("count", listed == null ? -1 : listed.length, "elapsedMs", elapsedMs(listStart)));

        File small = new File(root, "small text.txt");
        record("DIRECT", "STAT", "PASS", obj("size", small.length(), "mtime", small.lastModified(), "isFile", small.isFile(), "canonical", small.getCanonicalPath()));

        long seqStart = System.nanoTime();
        long seqBytes = readSequential(medium);
        record("DIRECT", "SEQUENTIAL_READ", seqBytes == medium.length() ? "PASS" : "FAIL", obj("bytes", seqBytes, "elapsedMs", elapsedMs(seqStart), "mibPerSec", mibPerSec(seqBytes, seqStart)));

        try (RandomAccessFile raf = new RandomAccessFile(medium, "r")) {
            long pos = medium.length() / 2;
            long st = System.nanoTime();
            raf.seek(pos);
            int b = raf.read();
            record("DIRECT", "SEEK_RANDOM_READ", b >= 0 ? "PASS" : "FAIL", obj("offset", pos, "byte", b, "elapsedMicros", elapsedMicros(st)));
        }

        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(medium, ParcelFileDescriptor.MODE_READ_ONLY)) {
            JSONObject info = describeFd(pfd);
            boolean seek = tryLseek(pfd, medium.length() / 3, info);
            record("DIRECT", "FILE_DESCRIPTOR", seek ? "PASS" : "FAIL", info);
        }

        File rw = new File(root, "rw.bin");
        writeBytes(rw, "abc".getBytes(StandardCharsets.UTF_8), false);
        writeBytes(rw, "DEF".getBytes(StandardCharsets.UTF_8), true);
        record("DIRECT", "APPEND", rw.length() == 6 ? "PASS" : "FAIL", obj("length", rw.length(), "content", new String(Files.readAllBytes(rw.toPath()), StandardCharsets.UTF_8)));
        try (RandomAccessFile raf = new RandomAccessFile(rw, "rw")) { raf.setLength(2); }
        record("DIRECT", "TRUNCATE", rw.length() == 2 ? "PASS" : "FAIL", obj("length", rw.length()));

        File renameA = new File(root, "rename-a.bin");
        writeBytes(renameA, "rename".getBytes(StandardCharsets.UTF_8), false);
        Object keyBefore = fileKey(renameA);
        File renameB = new File(root, "rename-b.bin");
        long rnStart = System.nanoTime();
        boolean renamed = renameA.renameTo(renameB);
        Object keyAfter = renamed ? fileKey(renameB) : null;
        record("DIRECT", "SAME_PARENT_RENAME", renamed ? "PASS" : "FAIL", obj("elapsedMicros", elapsedMicros(rnStart), "identityKeyBefore", String.valueOf(keyBefore), "identityKeyAfter", String.valueOf(keyAfter), "pathChanged", !renameA.getAbsolutePath().equals(renameB.getAbsolutePath())));

        File dir2 = new File(root, "move-target"); require(dir2.mkdir(), "move target mkdir");
        File moved = new File(dir2, renameB.getName());
        long mvStart = System.nanoTime();
        boolean movedOk = renameB.renameTo(moved);
        record("DIRECT", "CROSS_DIRECTORY_MOVE", movedOk ? "PASS" : "FAIL", obj("elapsedMicros", elapsedMicros(mvStart), "identityKeyBefore", String.valueOf(keyAfter), "identityKeyAfter", movedOk ? String.valueOf(fileKey(moved)) : null));

        long beforeMtime = moved.lastModified();
        Thread.sleep(1200L);
        writeBytes(moved, "x".getBytes(StandardCharsets.UTF_8), true);
        record("DIRECT", "MODIFICATION_TIMESTAMP", moved.lastModified() >= beforeMtime ? "PASS" : "FAIL", obj("before", beforeMtime, "after", moved.lastModified()));

        AtomicBoolean cancel = new AtomicBoolean(false);
        Thread cancelThread = new Thread(() -> {
            try {
                long count = 0;
                byte[] buf = new byte[64 * 1024];
                try (FileInputStream in = new FileInputStream(medium)) {
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        count += n;
                        if (cancel.get()) break;
                        try { Thread.sleep(1L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    }
                }
                record("DIRECT", "CANCELLATION", cancel.get() && count < medium.length() ? "PASS" : "FAIL", obj("mechanism", "client_cooperative", "bytesBeforeStop", count, "total", medium.length()));
            } catch (Throwable t) { recordFailure("DIRECT", "CANCELLATION", t, null); }
        });
        cancelThread.start();
        Thread.sleep(15L);
        cancel.set(true);
        cancelThread.join(5000L);

        File sparse = new File(root, "sparse-gt4gb.bin");
        try {
            long st = System.nanoTime();
            try (RandomAccessFile raf = new RandomAccessFile(sparse, "rw")) { raf.setLength(LARGE_SPARSE_SIZE); }
            record("DIRECT", "GT4GB_SPARSE", sparse.length() == LARGE_SPARSE_SIZE ? "PASS" : "FAIL", obj("requested", LARGE_SPARSE_SIZE, "observed", sparse.length(), "elapsedMs", elapsedMs(st)));
        } catch (Throwable t) {
            recordFailure("DIRECT", "GT4GB_SPARSE", t, obj("requested", LARGE_SPARSE_SIZE));
        } finally {
            if (sparse.exists()) sparse.delete();
        }

        boolean deleted = moved.delete();
        record("DIRECT", "DELETE", deleted ? "PASS" : "FAIL", obj("existsAfter", moved.exists()));
        record("DIRECT", "DISCONNECT", "NOT_TESTED", obj("reason", "no removable-storage disconnect event exercised in this run"));
        record("DIRECT", "PERMISSION_REVOCATION", "NOT_TESTED", obj("reason", "MANAGE_EXTERNAL_STORAGE revocation was not exercised in this run"));
    }

    private void runSyntheticPipe() throws Exception {
        Uri pipe = DocumentsContract.buildDocumentUri("dev.poc.filemanager.storagecap.documents", "pipe");
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(pipe, "r")) {
            if (pfd == null) throw new IOException("null ParcelFileDescriptor");
            JSONObject info = describeFd(pfd);
            boolean seekable = tryLseek(pfd, 1L, info);
            byte[] first = new byte[64];
            int n;
            try (FileInputStream in = new FileInputStream(pfd.getFileDescriptor())) { n = in.read(first); }
            info.put("sequentialBytes", n);
            info.put("sequentialPreview", n > 0 ? new String(first, 0, n, StandardCharsets.UTF_8) : "");
            info.put("expectedSeekable", false);
            record("SYNTHETIC_PROVIDER", "PIPE_DESCRIPTOR", !seekable && n > 0 ? "PASS" : "FAIL", info);
        }
    }

    private void runSaf() throws Exception {
        String text = getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_TREE, null);
        if (text == null) {
            record("SAF", "ROOT", "NOT_TESTED", obj("reason", "no selected tree URI"));
            return;
        }
        Uri tree = Uri.parse(text);
        ContentResolver cr = getContentResolver();
        String treeDocumentId = DocumentsContract.getTreeDocumentId(tree);
        Uri treeDocument = DocumentsContract.buildDocumentUriUsingTree(tree, treeDocumentId);
        record("SAF", "ROOT", "PASS", obj("treeUri", tree.toString(), "documentUri", treeDocument.toString(), "documentId", treeDocumentId));

        Uri testDir = createDir(cr, treeDocument, "FileManagerPoc001Saf");
        if (testDir == null) throw new IOException("could not create SAF test directory");
        record("SAF", "MKDIR", "PASS", queryDocInstance(testDir));

        Uri zero = createFile(cr, testDir, "application/octet-stream", "zero.bin");
        Uri small = createFile(cr, testDir, "text/plain", "small text.txt");
        writeUri(cr, small, "wt", "hello SAF".getBytes(StandardCharsets.UTF_8));
        String[] oddNames = {"ユニコード.txt", "emoji-😀.txt", "quotes-'\".txt", "shell-$;[]{}.txt", "line\nbreak.txt", repeat("l", 220) + ".txt"};
        for (String n : oddNames) {
            try {
                Uri u = createFile(cr, testDir, "text/plain", n);
                record("SAF", "CREATE_FIXTURE", u != null ? "PASS" : "FAIL", obj("requestedName", n, "uri", String.valueOf(u), "metadata", u == null ? null : queryDocInstance(u)));
            } catch (Throwable t) { recordFailure("SAF", "CREATE_FIXTURE", t, obj("requestedName", n)); }
        }
        record("SAF", "ZERO_BYTE", queryLong(cr, zero, DocumentsContract.Document.COLUMN_SIZE, -1L) == 0L ? "PASS" : "FAIL", queryDocInstance(zero));

        long listStart = System.nanoTime();
        List<JSONObject> children = listChildren(cr, testDir);
        record("SAF", "LIST", "PASS", obj("count", children.size(), "elapsedMs", elapsedMs(listStart), "children", new JSONArray(children)));

        long seqStart = System.nanoTime();
        long read = readUri(cr, small);
        record("SAF", "SEQUENTIAL_READ", read > 0 ? "PASS" : "FAIL", obj("bytes", read, "elapsedMicros", elapsedMicros(seqStart)));

        try (ParcelFileDescriptor pfd = cr.openFileDescriptor(small, "r")) {
            if (pfd == null) throw new IOException("null pfd");
            JSONObject info = describeFd(pfd);
            boolean seek = tryLseek(pfd, 2L, info);
            record("SAF", "PARCEL_FILE_DESCRIPTOR", seek ? "PASS" : "CONDITIONAL", info);
        }
        try {
            android.content.res.AssetFileDescriptor afd = cr.openAssetFileDescriptor(small, "r");
            if (afd != null) {
                JSONObject d = obj("startOffset", afd.getStartOffset(), "declaredLength", afd.getDeclaredLength(), "length", afd.getLength());
                afd.close();
                record("SAF", "ASSET_FILE_DESCRIPTOR", "PASS", d);
            } else record("SAF", "ASSET_FILE_DESCRIPTOR", "NO", obj("reason", "null"));
        } catch (Throwable t) { recordFailure("SAF", "ASSET_FILE_DESCRIPTOR", t, null); }

        try (ParcelFileDescriptor pfd = cr.openFileDescriptor(small, "r")) {
            if (pfd == null) throw new IOException("null pfd");
            long st = System.nanoTime();
            FileChannel ch = new FileInputStream(pfd.getFileDescriptor()).getChannel();
            ch.position(4L);
            ByteBuffer one = ByteBuffer.allocate(1);
            int n = ch.read(one);
            record("SAF", "RANDOM_READ", n == 1 ? "PASS" : "FAIL", obj("offset", 4, "elapsedMicros", elapsedMicros(st), "bytes", n));
        } catch (Throwable t) { recordFailure("SAF", "RANDOM_READ", t, null); }

        long initialMtime = queryLong(cr, small, DocumentsContract.Document.COLUMN_LAST_MODIFIED, -1L);
        writeUri(cr, small, "wa", "++".getBytes(StandardCharsets.UTF_8));
        long appendSize = queryLong(cr, small, DocumentsContract.Document.COLUMN_SIZE, -1L);
        record("SAF", "APPEND", appendSize >= 11L ? "PASS" : "CONDITIONAL", obj("size", appendSize, "metadata", queryDocInstance(small)));
        writeUri(cr, small, "wt", "xy".getBytes(StandardCharsets.UTF_8));
        long truncSize = queryLong(cr, small, DocumentsContract.Document.COLUMN_SIZE, -1L);
        record("SAF", "TRUNCATE", truncSize == 2L ? "PASS" : "CONDITIONAL", obj("size", truncSize));
        long newMtime = queryLong(cr, small, DocumentsContract.Document.COLUMN_LAST_MODIFIED, -1L);
        record("SAF", "MODIFICATION_TIMESTAMP", newMtime >= initialMtime ? "PASS" : "CONDITIONAL", obj("before", initialMtime, "after", newMtime));

        String beforeDocId = safeDocId(small);
        Uri renamed = null;
        try {
            renamed = DocumentsContract.renameDocument(cr, small, "renamed.txt");
            record("SAF", "SAME_PARENT_RENAME", renamed != null ? "PASS" : "FAIL", obj(
                "oldUri", small.toString(), "newUri", String.valueOf(renamed),
                "oldDocumentId", beforeDocId, "newDocumentId", renamed == null ? null : safeDocId(renamed),
                "uriStable", renamed != null && renamed.equals(small), "documentIdStable", renamed != null && beforeDocId.equals(safeDocId(renamed))
            ));
        } catch (Throwable t) { recordFailure("SAF", "SAME_PARENT_RENAME", t, obj("oldUri", small.toString())); }

        Uri moveSource = renamed != null ? renamed : small;
        Uri dirA = createDir(cr, testDir, "dirA");
        Uri dirB = createDir(cr, testDir, "dirB");
        try {
            Uri moved = DocumentsContract.moveDocument(cr, moveSource, testDir, dirB);
            record("SAF", "CROSS_DIRECTORY_MOVE", moved != null ? "PASS" : "FAIL", obj(
                "sourceUri", moveSource.toString(), "movedUri", String.valueOf(moved),
                "sourceDocumentId", safeDocId(moveSource), "movedDocumentId", moved == null ? null : safeDocId(moved),
                "uriStable", moved != null && moved.equals(moveSource), "documentIdStable", moved != null && safeDocId(moveSource).equals(safeDocId(moved))
            ));
            if (moved != null) moveSource = moved;
        } catch (Throwable t) { recordFailure("SAF", "CROSS_DIRECTORY_MOVE", t, obj("source", moveSource.toString(), "targetParent", String.valueOf(dirB))); }

        Uri sparse = null;
        try {
            sparse = createFile(cr, testDir, "application/octet-stream", "sparse-gt4gb.bin");
            if (sparse == null) throw new IOException("create sparse returned null");
            long st = System.nanoTime();
            try (ParcelFileDescriptor pfd = cr.openFileDescriptor(sparse, "rw")) {
                if (pfd == null) throw new IOException("null pfd");
                Os.ftruncate(pfd.getFileDescriptor(), LARGE_SPARSE_SIZE);
            }
            long observed = queryLong(cr, sparse, DocumentsContract.Document.COLUMN_SIZE, -1L);
            record("SAF", "GT4GB_SPARSE", observed == LARGE_SPARSE_SIZE ? "PASS" : "CONDITIONAL", obj("requested", LARGE_SPARSE_SIZE, "observed", observed, "elapsedMs", elapsedMs(st)));
        } catch (Throwable t) {
            recordFailure("SAF", "GT4GB_SPARSE", t, obj("requested", LARGE_SPARSE_SIZE));
        } finally {
            if (sparse != null) { try { DocumentsContract.deleteDocument(cr, sparse); } catch (Throwable ignored) {} }
        }

        Uri cancelFile = createFile(cr, testDir, "application/octet-stream", "cancel.bin");
        if (cancelFile != null) {
            writeGeneratedUri(cr, cancelFile, 16L * 1024L * 1024L);
            CancellationSignal signal = new CancellationSignal();
            long[] count = {0};
            Thread reader = new Thread(() -> {
                try (ParcelFileDescriptor pfd = cr.openFileDescriptor(cancelFile, "r", signal);
                     FileInputStream in = pfd == null ? null : new FileInputStream(pfd.getFileDescriptor())) {
                    if (in == null) return;
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) >= 0) { count[0] += n; if (signal.isCanceled()) break; try { Thread.sleep(1L); } catch (InterruptedException e) { break; } }
                } catch (Throwable ignored) {}
            });
            reader.start();
            Thread.sleep(15L);
            signal.cancel();
            reader.join(5000L);
            record("SAF", "CANCELLATION", count[0] < 16L * 1024L * 1024L ? "PASS" : "CONDITIONAL", obj("mechanism", "CancellationSignal+cooperative", "bytesBeforeStop", count[0]));
        }

        try {
            boolean deleted = DocumentsContract.deleteDocument(cr, moveSource);
            record("SAF", "DELETE", deleted ? "PASS" : "FAIL", obj("uri", moveSource.toString()));
        } catch (Throwable t) { recordFailure("SAF", "DELETE", t, obj("uri", moveSource.toString())); }

        record("SAF", "DISCONNECT", "NOT_TESTED", obj("reason", "no removable/cloud DocumentsProvider was attached during this run"));
        record("SAF", "REVOCATION", "NOT_TESTED", obj("reason", "explicit persisted-grant revocation was not exercised; process-restart persistence was measured separately"));
    }

    private void releasePersistedSaf() {
        String text = getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_TREE, null);
        if (text == null) {
            record("SAF", "PERSISTED_GRANT_RELEASE", "NOT_TESTED", obj("reason", "no saved URI"));
            return;
        }
        Uri uri = Uri.parse(text);
        try {
            getContentResolver().releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            record("SAF", "PERSISTED_GRANT_RELEASE", "PASS", obj("uri", uri.toString()));
        } catch (Throwable t) {
            recordFailure("SAF", "PERSISTED_GRANT_RELEASE", t, obj("uri", uri.toString()));
        }
    }

    private void probePersistedSaf() {
        String text = getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_TREE, null);
        if (text == null) {
            record("SAF", "PERSISTED_GRANT_PROBE", "NOT_TESTED", obj("reason", "no saved URI"));
            return;
        }
        Uri uri = Uri.parse(text);
        try {
            String treeDocumentId = DocumentsContract.getTreeDocumentId(uri);
            Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, treeDocumentId);
            JSONObject m = queryDocInstance(documentUri);
            record("SAF", "PERSISTED_GRANT_PROBE", "PASS", obj("treeUri", uri.toString(), "documentUri", documentUri.toString(), "metadata", m));
        } catch (Throwable t) {
            recordFailure("SAF", "PERSISTED_GRANT_PROBE", t, obj("uri", uri.toString()));
        }
    }

    private static Uri createDir(ContentResolver cr, Uri parent, String name) throws Exception {
        Uri existing = findChildByName(cr, parent, name);
        if (existing != null) {
            try { deleteTree(cr, existing); } catch (Throwable ignored) {}
        }
        return DocumentsContract.createDocument(cr, parent, DocumentsContract.Document.MIME_TYPE_DIR, name);
    }

    private static Uri createFile(ContentResolver cr, Uri parent, String mime, String name) throws Exception {
        return DocumentsContract.createDocument(cr, parent, mime, name);
    }

    private static Uri findChildByName(ContentResolver cr, Uri parent, String name) throws Exception {
        String parentId = DocumentsContract.getDocumentId(parent);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentId);
        String[] proj = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME};
        try (Cursor c = cr.query(childrenUri, proj, null, null, null)) {
            if (c == null) return null;
            while (c.moveToNext()) {
                if (name.equals(c.getString(1))) return DocumentsContract.buildDocumentUriUsingTree(parent, c.getString(0));
            }
        }
        return null;
    }

    private static void deleteTree(ContentResolver cr, Uri doc) throws Exception {
        try { DocumentsContract.deleteDocument(cr, doc); } catch (Throwable ignored) {}
    }

    private static List<JSONObject> listChildren(ContentResolver cr, Uri parent) throws Exception {
        String parentId = DocumentsContract.getDocumentId(parent);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentId);
        String[] proj = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        ArrayList<JSONObject> result = new ArrayList<>();
        try (Cursor c = cr.query(childrenUri, proj, null, null, null)) {
            if (c == null) return result;
            while (c.moveToNext()) {
                result.add(obj("documentId", c.getString(0), "displayName", c.getString(1), "mime", c.getString(2), "flags", c.getLong(3), "size", c.isNull(4) ? JSONObject.NULL : c.getLong(4), "mtime", c.isNull(5) ? JSONObject.NULL : c.getLong(5)));
            }
        }
        return result;
    }

    private static JSONObject queryDoc(Uri uri) throws Exception {
        throw new UnsupportedOperationException("instance overload required");
    }

    private JSONObject queryDocInstance(Uri uri) throws Exception { return queryDoc(uri, getContentResolver()); }

    private static JSONObject queryDoc(Uri uri, ContentResolver cr) throws Exception {
        String[] proj = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        try (Cursor c = cr.query(uri, proj, null, null, null)) {
            if (c == null || !c.moveToFirst()) throw new IOException("query returned no row: " + uri);
            return obj("uri", uri.toString(), "documentId", c.getString(0), "displayName", c.getString(1), "mime", c.getString(2), "flags", c.getLong(3), "size", c.isNull(4) ? JSONObject.NULL : c.getLong(4), "mtime", c.isNull(5) ? JSONObject.NULL : c.getLong(5));
        }
    }

    private static long queryLong(ContentResolver cr, Uri uri, String column, long fallback) throws Exception {
        try (Cursor c = cr.query(uri, new String[]{column}, null, null, null)) {
            if (c == null || !c.moveToFirst() || c.isNull(0)) return fallback;
            return c.getLong(0);
        }
    }

    private static void writeUri(ContentResolver cr, Uri uri, String mode, byte[] bytes) throws Exception {
        try (ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, mode)) {
            if (pfd == null) throw new IOException("null pfd for " + uri + " mode=" + mode);
            try (FileOutputStream out = new FileOutputStream(pfd.getFileDescriptor())) { out.write(bytes); out.flush(); }
        }
    }

    private static void writeGeneratedUri(ContentResolver cr, Uri uri, long bytes) throws Exception {
        byte[] block = new byte[1024 * 1024];
        for (int i = 0; i < block.length; i++) block[i] = (byte)(i * 31);
        try (ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "wt")) {
            if (pfd == null) throw new IOException("null pfd");
            try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(pfd.getFileDescriptor()), 256 * 1024)) {
                long remain = bytes;
                while (remain > 0) { int n = (int)Math.min(block.length, remain); out.write(block, 0, n); remain -= n; }
            }
        }
    }

    private static long readUri(ContentResolver cr, Uri uri) throws Exception {
        try (ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "r")) {
            if (pfd == null) throw new IOException("null pfd");
            try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(pfd.getFileDescriptor()), 256 * 1024)) {
                byte[] buf = new byte[128 * 1024]; long n = 0; int r; while ((r = in.read(buf)) >= 0) n += r; return n;
            }
        }
    }

    private static JSONObject describeFd(ParcelFileDescriptor pfd) throws Exception {
        StructStat st = Os.fstat(pfd.getFileDescriptor());
        String type = OsConstants.S_ISREG(st.st_mode) ? "REGULAR" : OsConstants.S_ISDIR(st.st_mode) ? "DIRECTORY" : OsConstants.S_ISFIFO(st.st_mode) ? "FIFO" : OsConstants.S_ISCHR(st.st_mode) ? "CHAR" : "OTHER";
        return obj("fd", pfd.getFd(), "statMode", st.st_mode, "statSize", st.st_size, "fdType", type);
    }

    private static boolean tryLseek(ParcelFileDescriptor pfd, long offset, JSONObject out) {
        try {
            long result = Os.lseek(pfd.getFileDescriptor(), offset, OsConstants.SEEK_SET);
            try { out.put("seekable", true); out.put("seekResult", result); } catch (Exception ignored) {}
            return true;
        } catch (ErrnoException e) {
            try { out.put("seekable", false); out.put("seekErrno", e.errno); out.put("seekError", e.toString()); } catch (Exception ignored) {}
            return false;
        }
    }

    private static String safeDocId(Uri u) {
        try { return DocumentsContract.getDocumentId(u); } catch (Throwable t) {
            try { return DocumentsContract.getTreeDocumentId(u); } catch (Throwable ignored) { return "UNKNOWN"; }
        }
    }

    private static Object fileKey(File f) {
        try { return Files.readAttributes(f.toPath(), java.nio.file.attribute.BasicFileAttributes.class).fileKey(); }
        catch (Throwable t) { return "UNAVAILABLE:" + t.getClass().getSimpleName(); }
    }

    private static void createPatternFile(File f, long bytes) throws Exception {
        byte[] block = new byte[1024 * 1024];
        for (int i = 0; i < block.length; i++) block[i] = (byte)((i * 17 + 3) & 0xff);
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(f), 256 * 1024)) {
            long remain = bytes;
            while (remain > 0) { int n = (int)Math.min(block.length, remain); out.write(block, 0, n); remain -= n; }
        }
    }

    private static void writeBytes(File f, byte[] bytes, boolean append) throws Exception {
        try (FileOutputStream out = new FileOutputStream(f, append)) { out.write(bytes); out.flush(); }
    }

    private static long readSequential(File f) throws Exception {
        long n = 0; byte[] buf = new byte[256 * 1024];
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(f), buf.length)) { int r; while ((r = in.read(buf)) >= 0) n += r; }
        return n;
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[256 * 1024];
        try (FileInputStream in = new FileInputStream(f)) { int n; while ((n = in.read(buf)) >= 0) md.update(buf, 0, n); }
        StringBuilder sb = new StringBuilder(); for (byte b : md.digest()) sb.append(String.format(Locale.ROOT, "%02x", b)); return sb.toString();
    }

    private static void deleteRecursive(File f) throws IOException {
        if (!f.exists()) return;
        if (f.isDirectory()) { File[] kids = f.listFiles(); if (kids != null) for (File k : kids) deleteRecursive(k); }
        if (!f.delete()) throw new IOException("failed deleting " + f);
    }

    private static void require(boolean cond, String msg) throws IOException { if (!cond) throw new IOException(msg); }
    private static String repeat(String s, int n) { StringBuilder b = new StringBuilder(n * s.length()); for (int i = 0; i < n; i++) b.append(s); return b.toString(); }
    private static double elapsedMs(long start) { return (System.nanoTime() - start) / 1_000_000.0; }
    private static double elapsedMicros(long start) { return (System.nanoTime() - start) / 1_000.0; }
    private static double mibPerSec(long bytes, long start) { double sec = (System.nanoTime() - start) / 1_000_000_000.0; return sec <= 0 ? 0 : (bytes / 1048576.0) / sec; }

    private void clearResults() {
        File f = resultFile();
        if (f.exists()) f.delete();
        status.setText("Results cleared");
    }

    private File resultFile() { return new File(getFilesDir(), "results.jsonl"); }

    private synchronized void record(String category, String operation, String statusValue, JSONObject data) {
        try {
            JSONObject line = new JSONObject();
            line.put("ts", System.currentTimeMillis());
            line.put("category", category);
            line.put("operation", operation);
            line.put("status", statusValue);
            line.put("data", data == null ? JSONObject.NULL : data);
            byte[] bytes = (line.toString() + "\n").getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream out = new FileOutputStream(resultFile(), true)) { out.write(bytes); out.flush(); out.getFD().sync(); }
            android.util.Log.i("POC001", line.toString());
        } catch (Throwable t) {
            android.util.Log.e("POC001", "record failed", t);
        }
    }

    private void recordFailure(String category, String operation, Throwable t, JSONObject extra) {
        JSONObject d = extra == null ? new JSONObject() : extra;
        try { d.put("exception", t.getClass().getName()); d.put("message", String.valueOf(t.getMessage())); d.put("text", t.toString()); } catch (Exception ignored) {}
        record(category, operation, "FAIL", d);
    }

    private static JSONObject obj(Object... kv) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i + 1 < kv.length; i += 2) o.put(String.valueOf(kv[i]), kv[i + 1] == null ? JSONObject.NULL : kv[i + 1]);
        } catch (Exception e) { throw new RuntimeException(e); }
        return o;
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
}
