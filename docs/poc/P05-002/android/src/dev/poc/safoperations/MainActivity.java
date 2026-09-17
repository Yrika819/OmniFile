package dev.poc.safoperations;

/* POC-ONLY — NOT PRODUCTION AUTHORITY. */

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String LABEL = "POC-ONLY — NOT PRODUCTION AUTHORITY";
    private static final String LOCAL_TO_SAF = "LOCAL_TO_SAF";
    private static final String SAF_TO_LOCAL = "SAF_TO_LOCAL";
    private static final String SAF_TO_SAF = "SAF_TO_SAF";
    private static final int TREE_SOURCE = 41;
    private static final int TREE_DESTINATION = 42;
    private static final int BUFFER_SIZE = 32 * 1024;
    private static final int MAX_UI_LOG_BYTES = 128 * 1024;
    // Keep one Log.d payload below Android's single-entry logger limit.
    private static final int MAX_LOGCAT_EVENT_BYTES = 3500;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Object logLock = new Object();
    private final Object uiLock = new Object();
    private Uri sourceTree;
    private Uri destinationTree;
    private Uri safSource;
    private File eventsFile;
    private File currentFile;
    private TextView output;
    private volatile boolean uiRefreshPending;
    private volatile boolean uiRefreshAgain;
    private volatile boolean transferActive;
    private volatile boolean interruptRequested;
    private volatile boolean cancelRequested;
    private volatile boolean conflictArmed;
    private int operationNumber;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        eventsFile = new File(getFilesDir(), "p05-saf-events.jsonl");
        currentFile = new File(getFilesDir(), "p05-saf-current.json");
        sourceTree = uriFromPrefs("sourceTree");
        destinationTree = uriFromPrefs("destinationTree");
        safSource = uriFromPrefs("safSource");
        buildUi();
        record("APP_START", baseEvent());
        recordPersistedGrants();
        reconcileCurrent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        recordPersistedGrants();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("P05 SAF durable-recovery harness\n" + LABEL);
        title.setTextSize(18);
        root.addView(title);

        addButton(root, "Select source tree", new Runnable() {
            @Override public void run() { chooseTree(TREE_SOURCE); }
        });
        addButton(root, "Select destination tree", new Runnable() {
            @Override public void run() { chooseTree(TREE_DESTINATION); }
        });
        addButton(root, "Create local fixture", new Runnable() {
            @Override public void run() { createLocalFixture(); }
        });
        addButton(root, "Create SAF fixture", new Runnable() {
            @Override public void run() { createSafFixture(); }
        });
        addButton(root, "Local→SAF copy", new Runnable() {
            @Override public void run() { startTransfer(LOCAL_TO_SAF, false, false); }
        });
        addButton(root, "SAF→Local copy", new Runnable() {
            @Override public void run() { startTransfer(SAF_TO_LOCAL, false, false); }
        });
        addButton(root, "SAF→SAF copy", new Runnable() {
            @Override public void run() { startTransfer(SAF_TO_SAF, false, false); }
        });
        addButton(root, "Run SAF→SAF move-shaped", new Runnable() {
            @Override public void run() { startTransfer(SAF_TO_SAF, true, false); }
        });
        addButton(root, "Interrupt current", new Runnable() {
            @Override public void run() { interruptRequested = true; record("INTERRUPT_REQUESTED", baseEvent()); }
        });
        addButton(root, "Cancel current", new Runnable() {
            @Override public void run() { cancelRequested = true; record("CANCEL_REQUESTED", baseEvent()); }
        });
        addButton(root, "Mutate SAF source", new Runnable() {
            @Override public void run() { mutateSafSource(); }
        });
        addButton(root, "Arm destination conflict", new Runnable() {
            @Override public void run() {
                conflictArmed = true;
                JSONObject event = baseEvent();
                put(event, "classification", "CONFLICT_DESTINATION");
                record("CONFLICT_ARMED", event);
            }
        });
        addButton(root, "SAF→SAF conflict copy", new Runnable() {
            @Override public void run() { startTransfer(SAF_TO_SAF, false, true); }
        });
        addButton(root, "Restart / reconcile", new Runnable() {
            @Override public void run() { reconcileCurrent(); }
        });

        output = new TextView(this);
        output.setTextIsSelectable(true);
        output.setText(readTextForUi(eventsFile));
        root.addView(output);
        setContentView(scroll);
    }

    private void addButton(LinearLayout root, String label, final Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { action.run(); }
        });
        root.addView(button);
    }

    private void chooseTree(int requestCode) {
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(flags);
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            record("TREE_SELECTION_CANCELLED", baseEvent());
            return;
        }
        Uri tree = data.getData();
        int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(tree, takeFlags);
        } catch (SecurityException error) {
            JSONObject event = baseEvent();
            put(event, "uri", tree.toString());
            put(event, "error", error.toString());
            record("PERSIST_GRANT_FAILED", event);
            return;
        }
        if (requestCode == TREE_SOURCE) {
            sourceTree = tree;
            saveUriPref("sourceTree", tree);
        } else if (requestCode == TREE_DESTINATION) {
            destinationTree = tree;
            saveUriPref("destinationTree", tree);
        }
        JSONObject event = identity(tree);
        put(event, "selection", requestCode == TREE_SOURCE ? "SOURCE_TREE" : "DESTINATION_TREE");
        put(event, "persistablePermissionRequested", true);
        record("TREE_SELECTED", event);
        recordPersistedGrants();
    }

    private void recordPersistedGrants() {
        JSONObject event = baseEvent();
        List<String> uris = new ArrayList<>();
        for (UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
            uris.add(permission.getUri().toString());
        }
        put(event, "persistedGrantCount", uris.size());
        put(event, "persistedGrantUris", uris.toString());
        record("PERSISTED_GRANTS_REQUERY", event);
    }

    private void createLocalFixture() {
        File directory = new File(getFilesDir(), "fixtures");
        File fixture = new File(directory, "p05-local-source.bin");
        try {
            directory.mkdirs();
            writeFile(fixture, fixtureBytes("local"));
            JSONObject event = baseEvent();
            put(event, "sourceUri", "file://local-fixtures/p05-local-source.bin");
            put(event, "sourceProviderId", "local");
            put(event, "sourceIdentity", localIdentity(fixture));
            record("LOCAL_FIXTURE_CREATED", event);
        } catch (Exception error) {
            recordError("LOCAL_FIXTURE_FAILED", error);
        }
    }

    private void createSafFixture() {
        if (sourceTree == null) {
            record("SAF_FIXTURE_BLOCKED", reasonEvent("source tree is not selected"));
            return;
        }
        try {
            Uri sourceParent = parentDocumentUri(sourceTree);
            Uri uri = DocumentsContract.createDocument(getContentResolver(), sourceParent,
                    "application/octet-stream", "p05-saf-source.bin");
            if (uri == null) throw new IOException("provider returned null fixture URI");
            writeUri(uri, fixtureBytes("saf"));
            safSource = uri;
            saveUriPref("safSource", uri);
            JSONObject event = identity(uri);
            put(event, "sourceUri", uri.toString());
            put(event, "sourceProviderId", providerId(uri));
            record("SAF_FIXTURE_CREATED", event);
        } catch (Exception error) {
            recordError("SAF_FIXTURE_FAILED", error);
        }
    }

    private void mutateSafSource() {
        if (safSource == null) {
            record("MUTATION_BLOCKED", reasonEvent("SAF fixture is not selected"));
            return;
        }
        try {
            writeUri(safSource, fixtureBytes("mutated"));
            JSONObject event = identity(safSource);
            put(event, "mutation", "overwrite-with-different-deterministic-bytes");
            put(event, "classification", "MUTATION");
            record("MUTATION", event);
        } catch (Exception error) {
            recordError("MUTATION_FAILED", error);
        }
    }

    private void startTransfer(final String direction, final boolean move, final boolean conflict) {
        if (transferActive) {
            record("TRANSFER_REJECTED", reasonEvent("another transfer is active"));
            return;
        }
        transferActive = true;
        interruptRequested = false;
        cancelRequested = false;
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    transfer(direction, move, conflict || conflictArmed);
                } finally {
                    conflictArmed = false;
                    transferActive = false;
                }
            }
        });
    }

    private void transfer(String direction, boolean move, boolean conflict) {
        File localFixture = new File(new File(getFilesDir(), "fixtures"), "p05-local-source.bin");
        File localOutputDirectory = new File(getFilesDir(), "outputs");
        SourceInfo source;
        try {
            if (LOCAL_TO_SAF.equals(direction)) {
                source = sourceInfo(localFixture, "local");
            } else {
                if (safSource == null) throw new IOException("SAF fixture is not selected");
                source = sourceInfo(safSource, providerId(safSource));
            }
        } catch (Exception error) {
            recordError("TRANSFER_BLOCKED", error);
            return;
        }

        int number;
        synchronized (logLock) { number = ++operationNumber; }
        String operationId = "p05-op-" + number;
        String finalName = conflict ? "p05-conflict-final.bin" : operationId + ".final.bin";
        String partialName = operationId + ".partial.bin";
        JSONObject record = baseEvent();
        put(record, "operationId", operationId);
        put(record, "operationKind", move ? "move" : "copy");
        put(record, "direction", direction);
        put(record, "phase", "PLAN");
        put(record, "status", "RUNNING");
        put(record, "sourceUri", source.uri);
        put(record, "sourceProviderId", source.providerId);
        put(record, "sourceVersion", source.version);
        put(record, "sourceLength", source.length);
        put(record, "sourceSha256", source.sha256);
        put(record, "destinationProviderId", LOCAL_TO_SAF.equals(direction) || SAF_TO_SAF.equals(direction)
                ? providerId(destinationTree) : "local");
        put(record, "destinationTreeUri", destinationTree == null ? null : destinationTree.toString());
        put(record, "partialName", partialName);
        put(record, "finalName", finalName);
        put(record, "checkpointBytes", 0L);
        put(record, "verification", "NOT_STARTED");
        put(record, "finalizationAcknowledged", false);
        put(record, "deleteSource", false);
        put(record, "deleteSourceEligibility", move ? "EXPLICIT_PARENT_STEP_ONLY" : "NOT_APPLICABLE");
        persistAndRecord("PLAN", record);

        Uri partialUri = null;
        File partialFile = null;
        try {
            if ((LOCAL_TO_SAF.equals(direction) || SAF_TO_SAF.equals(direction)) && destinationTree == null) {
                throw new IOException("destination tree is not selected");
            }
            if (conflict && (LOCAL_TO_SAF.equals(direction) || SAF_TO_SAF.equals(direction))) {
                Uri destinationParent = parentDocumentUri(destinationTree);
                Uri existing = findChild(destinationTree, finalName);
                if (existing == null) {
                    existing = DocumentsContract.createDocument(getContentResolver(), destinationParent,
                            "application/octet-stream", finalName);
                    if (existing == null) throw new IOException("provider returned null conflict URI");
                    writeUri(existing, fixtureBytes("wrong-final"));
                }
                put(record, "destinationUri", existing.toString());
                put(record, "phase", "CONFLICT");
                put(record, "status", "CONFLICT_DESTINATION");
                put(record, "classification", "CONFLICT_DESTINATION");
                persistAndRecord("CONFLICT_DESTINATION", record);
                return;
            }
            if (LOCAL_TO_SAF.equals(direction) || SAF_TO_SAF.equals(direction)) {
                Uri destinationParent = parentDocumentUri(destinationTree);
                partialUri = DocumentsContract.createDocument(getContentResolver(), destinationParent,
                        "application/octet-stream", partialName);
                if (partialUri == null) throw new IOException("provider returned null partial URI");
                put(record, "partialUri", partialUri.toString());
                put(record, "destinationUri", partialUri.toString());
            } else {
                localOutputDirectory.mkdirs();
                partialFile = new File(localOutputDirectory, partialName);
                put(record, "partialLocalPath", "outputs/" + partialName);
                put(record, "destinationUri", "file://outputs/" + partialName);
            }
            put(record, "phase", "CREATE_PARTIAL");
            persistAndRecord("CREATE_PARTIAL", record);

            copyWithCheckpoints(direction, source, partialUri, partialFile, record);
            String status = record.optString("status", "RUNNING");
            if (!"RUNNING".equals(status)) return;

            put(record, "phase", "VERIFY");
            Measure measured = partialUri == null ? measure(partialFile) : measure(partialUri);
            boolean matches = measured.length == source.length && source.sha256.equals(measured.sha256);
            put(record, "verification", matches ? "PASS" : "FAIL");
            if (!matches) {
                put(record, "status", "CONFLICT_DESTINATION");
                put(record, "classification", "CONFLICT_DESTINATION");
                persistAndRecord("VERIFY_FAILED", record);
                return;
            }
            persistAndRecord("VERIFY", record);

            put(record, "phase", "FINALIZE");
            persistAndRecord("FINALIZE_PENDING", record);
            Uri finalUri = partialUri;
            if (partialUri != null) {
                finalUri = DocumentsContract.renameDocument(getContentResolver(), partialUri, finalName);
                if (finalUri == null) throw new IOException("provider returned null final URI");
                put(record, "finalUri", finalUri.toString());
                put(record, "destinationUri", finalUri.toString());
                put(record, "finalIdentity", identity(finalUri));
            } else {
                File finalFile = new File(localOutputDirectory, finalName);
                if (partialFile == null || !partialFile.renameTo(finalFile)) {
                    throw new IOException("local partial rename failed");
                }
                put(record, "finalLocalPath", "outputs/" + finalName);
                put(record, "destinationUri", "file://outputs/" + finalName);
            }
            put(record, "finalizationAcknowledged", true);
            persistAndRecord("FINALIZE", record);
            put(record, "phase", "COMPLETE");
            put(record, "status", "COMPLETE");
            put(record, "classification", "FINAL_DESTINATION_OBSERVED");
            persistAndRecord("COMPLETE", record);
        } catch (Exception error) {
            put(record, "status", "FAILED");
            put(record, "error", error.toString());
            persistAndRecord("TRANSFER_FAILED", record);
        }
    }

    private void copyWithCheckpoints(String direction, SourceInfo source, Uri partialUri,
                                     File partialFile, JSONObject record) throws Exception {
        InputStream input = openSource(direction, source);
        OutputStream outputStream = partialUri == null
                ? new FileOutputStream(partialFile, false)
                : getContentResolver().openOutputStream(partialUri, "w");
        if (outputStream == null) throw new IOException("provider returned null output stream");
        try (InputStream in = input; OutputStream out = outputStream) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long written = 0;
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
                written += count;
                if (written % BUFFER_SIZE == 0 || written == source.length) {
                    out.flush();
                    put(record, "phase", "CHECKPOINT");
                    put(record, "checkpointBytes", written);
                    persistAndRecord("CHECKPOINT", record);
                }
                if (cancelRequested) {
                    put(record, "phase", "CANCELLED");
                    put(record, "status", "CANCELLED");
                    put(record, "classification", "CANCELLED");
                    persistAndRecord("CANCELLED", record);
                    return;
                }
                if (interruptRequested) {
                    put(record, "phase", "INTERRUPTED");
                    put(record, "status", "INTERRUPTED");
                    put(record, "classification", "INTERRUPTED");
                    persistAndRecord("INTERRUPTED", record);
                    return;
                }
            }
        }
        put(record, "phase", "TRANSFER");
        persistAndRecord("TRANSFER", record);
    }

    private void reconcileCurrent() {
        final JSONObject record = readCurrent();
        if (record == null) {
            record("RECONCILE", reasonEvent("no durable operation record"));
            return;
        }
        String status = record.optString("status", "");
        if ("COMPLETE".equals(status) || "CANCELLED".equals(status)
                || "CONFLICT_DESTINATION".equals(status) || "FAILED".equals(status)) {
            put(record, "classification", "TERMINAL_RECORD");
            record("RECONCILE", record);
            return;
        }
        try {
            String direction = record.optString("direction", "");
            SourceInfo source = sourceInfoForRecord(record, direction);
            String expectedHash = record.optString("sourceSha256", "");
            long expectedLength = record.optLong("sourceLength", -1);
            if (source == null) {
                put(record, "classification", "BLOCKED_PERMISSION");
                put(record, "reconcileAction", "WAIT_FOR_AUTHORIZATION");
                persistAndRecord("RECONCILE", record);
                return;
            }
            if (!expectedHash.equals(source.sha256) || expectedLength != source.length) {
                put(record, "classification", "CONFLICT_SOURCE_CHANGED");
                put(record, "reconcileAction", "REQUIRE_USER_REVIEW");
                persistAndRecord("RECONCILE", record);
                return;
            }
            Uri finalUri = uriFromString(record.optString("finalUri", null));
            if (finalUri == null && (LOCAL_TO_SAF.equals(direction) || SAF_TO_SAF.equals(direction))
                    && destinationTree != null) {
                finalUri = findChild(destinationTree, record.optString("finalName", ""));
            }
            File finalFile = localFinalFile(record);
            Measure finalMeasure = finalUri == null ? measureIfPresent(finalFile) : measureIfPresent(finalUri);
            if (finalMeasure != null) {
                boolean match = finalMeasure.length == expectedLength && expectedHash.equals(finalMeasure.sha256);
                put(record, "reconciledFinalIdentity", finalUri == null ? "local" : identity(finalUri));
                if (!match) {
                    put(record, "classification", "CONFLICT_DESTINATION");
                    put(record, "reconcileAction", "REQUIRE_USER_REVIEW");
                } else if (!record.optBoolean("finalizationAcknowledged", false)) {
                    put(record, "classification", "FINALIZATION_AMBIGUOUS");
                    put(record, "reconcileAction", "REQUIRE_USER_REVIEW");
                } else {
                    put(record, "classification", "FINAL_DESTINATION_OBSERVED");
                    put(record, "reconcileAction", "move".equals(record.optString("operationKind"))
                            ? "REQUIRE_EXPLICIT_SOURCE_DELETE" : "MARK_COMPLETE");
                }
                persistAndRecord("RECONCILE", record);
                return;
            }
            Uri partialUri = uriFromString(record.optString("partialUri", null));
            File partialFile = localPartialFile(record);
            Measure partialMeasure = partialUri == null ? measureIfPresent(partialFile) : measureIfPresent(partialUri);
            if (partialMeasure != null && prefix(source, partialUri, partialFile, partialMeasure.length)) {
                put(record, "classification", "RESUME_FROM_PARTIAL");
                put(record, "reconcileAction", "TRANSFER_FROM_OFFSET");
                put(record, "resumeOffset", partialMeasure.length);
            } else {
                put(record, "classification", "RESTART_REQUIRED");
                put(record, "reconcileAction", "RECREATE_PARTIAL");
                put(record, "resumeOffset", 0L);
            }
            persistAndRecord("RECONCILE", record);
        } catch (SecurityException error) {
            put(record, "classification", "BLOCKED_PERMISSION");
            put(record, "reconcileAction", "WAIT_FOR_AUTHORIZATION");
            put(record, "error", error.toString());
            persistAndRecord("RECONCILE", record);
        } catch (Exception error) {
            put(record, "classification", "BLOCKED_PROVIDER");
            put(record, "reconcileAction", "WAIT_FOR_PROVIDER");
            put(record, "error", error.toString());
            persistAndRecord("RECONCILE", record);
        }
    }

    private SourceInfo sourceInfoForRecord(JSONObject record, String direction) throws Exception {
        if (LOCAL_TO_SAF.equals(direction)) {
            return sourceInfo(new File(new File(getFilesDir(), "fixtures"), "p05-local-source.bin"), "local");
        }
        Uri uri = uriFromString(record.optString("sourceUri", null));
        if (uri == null || !hasPersistedGrant(uri)) return null;
        return sourceInfo(uri, providerId(uri));
    }

    private SourceInfo sourceInfo(File file, String id) throws Exception {
        if (!file.isFile()) throw new IOException("missing local fixture");
        Measure measured = measure(file);
        return new SourceInfo("file://local-fixtures/p05-local-source.bin", id,
                id + ":" + file.lastModified() + ":" + measured.length,
                measured.length, measured.sha256, file, null);
    }

    private SourceInfo sourceInfo(Uri uri, String id) throws Exception {
        Measure measured = measure(uri);
        JSONObject metadata = identity(uri);
        String documentId = metadata.optString("documentId", uri.toString());
        long modified = metadata.optLong("lastModified", -1);
        return new SourceInfo(uri.toString(), id, documentId + ":" + modified + ":" + measured.length,
                measured.length, measured.sha256, null, uri);
    }

    private InputStream openSource(String direction, SourceInfo source) throws Exception {
        if (LOCAL_TO_SAF.equals(direction)) return new FileInputStream(source.file);
        InputStream input = getContentResolver().openInputStream(source.uriObject);
        if (input == null) throw new IOException("provider returned null input stream");
        return input;
    }

    private boolean prefix(SourceInfo source, Uri partialUri, File partialFile, long partialLength) throws Exception {
        InputStream sourceIn = source.file != null ? new FileInputStream(source.file)
                : getContentResolver().openInputStream(source.uriObject);
        if (sourceIn == null) return false;
        InputStream partialIn = partialUri == null ? new FileInputStream(partialFile)
                : getContentResolver().openInputStream(partialUri);
        if (partialIn == null) return false;
        try (InputStream a = sourceIn; InputStream b = partialIn) {
            byte[] left = new byte[BUFFER_SIZE];
            byte[] right = new byte[BUFFER_SIZE];
            long remaining = partialLength;
            while (remaining > 0) {
                int wanted = (int) Math.min(left.length, remaining);
                int leftCount = readAtMost(a, left, wanted);
                int rightCount = readAtMost(b, right, wanted);
                if (leftCount != rightCount) return false;
                for (int i = 0; i < leftCount; i++) if (left[i] != right[i]) return false;
                remaining -= leftCount;
                if (leftCount == 0) return false;
            }
            return true;
        }
    }

    private int readAtMost(InputStream input, byte[] buffer, int wanted) throws IOException {
        int total = 0;
        while (total < wanted) {
            int count = input.read(buffer, total, wanted - total);
            if (count == -1) break;
            total += count;
        }
        return total;
    }

    private Measure measureIfPresent(File file) throws Exception {
        return file != null && file.isFile() ? measure(file) : null;
    }

    private Measure measureIfPresent(Uri uri) throws Exception {
        return uri != null && exists(uri) ? measure(uri) : null;
    }

    private Measure measure(File file) throws Exception {
        return measure(new FileInputStream(file));
    }

    private Measure measure(Uri uri) throws Exception {
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) throw new IOException("provider returned null input stream");
        return measure(input);
    }

    private Measure measure(InputStream input) throws Exception {
        try (InputStream in = input) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            long length = 0;
            int count;
            while ((count = in.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
                length += count;
            }
            return new Measure(length, hex(digest.digest()));
        }
    }

    private boolean exists(Uri uri) throws Exception {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null)) {
            return cursor != null && cursor.moveToFirst();
        }
    }

    private Uri findChild(Uri tree, String displayName) throws Exception {
        Uri parent = parentDocumentUri(tree);
        String parentId = DocumentsContract.getDocumentId(parent);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME};
        try (Cursor cursor = getContentResolver().query(children, columns, null, null, null)) {
            if (cursor == null) return null;
            int idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            while (cursor.moveToNext()) {
                if (displayName.equals(cursor.getString(nameColumn))) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(idColumn));
                }
            }
            return null;
        }
    }

    private Uri parentDocumentUri(Uri treeUri) throws Exception {
        String treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId);
    }

    private JSONObject identity(Uri uri) {
        JSONObject result = baseEvent();
        put(result, "uri", uri == null ? null : uri.toString());
        put(result, "providerId", providerId(uri));
        if (uri == null) return result;
        try { put(result, "documentId", DocumentsContract.getDocumentId(uri)); } catch (Exception ignored) { }
        try { put(result, "treeDocumentId", DocumentsContract.getTreeDocumentId(uri)); } catch (Exception ignored) { }
        String[] columns = {DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_FLAGS};
        try (Cursor cursor = getContentResolver().query(uri, columns, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                put(result, "displayName", cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)));
                put(result, "mimeType", cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)));
                put(result, "size", cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)));
                put(result, "lastModified", cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)));
                put(result, "flags", cursor.getInt(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)));
            }
        } catch (Exception error) {
            put(result, "identityQueryError", error.toString());
        }
        return result;
    }

    private JSONObject localIdentity(File file) throws Exception {
        Measure measured = measure(file);
        JSONObject result = baseEvent();
        put(result, "path", "fixtures/p05-local-source.bin");
        put(result, "providerId", "local");
        put(result, "size", measured.length);
        put(result, "lastModified", file.lastModified());
        put(result, "sha256", measured.sha256);
        return result;
    }

    private boolean hasPersistedGrant(Uri uri) {
        for (UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
            if (!(permission.isReadPermission() || permission.isWritePermission())) continue;
            if (permission.getUri().equals(uri)) return true;
            try {
                if (DocumentsContract.isTreeUri(permission.getUri())
                        && permission.getUri().getAuthority().equals(uri.getAuthority())
                        && DocumentsContract.getTreeDocumentId(permission.getUri())
                        .equals(DocumentsContract.getTreeDocumentId(uri))) return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    private String providerId(Uri uri) {
        return uri == null || uri.getAuthority() == null ? "local" : uri.getAuthority();
    }

    private Uri uriFromPrefs(String key) {
        String value = getSharedPreferences("p05", Context.MODE_PRIVATE).getString(key, null);
        return uriFromString(value);
    }

    private Uri uriFromString(String value) {
        return value == null || value.length() == 0 ? null : Uri.parse(value);
    }

    private void saveUriPref(String key, Uri value) {
        getSharedPreferences("p05", Context.MODE_PRIVATE).edit().putString(key, value.toString()).apply();
    }

    private File localPartialFile(JSONObject record) {
        String path = record.optString("partialLocalPath", null);
        return path == null ? null : new File(getFilesDir(), path);
    }

    private File localFinalFile(JSONObject record) {
        String path = record.optString("finalLocalPath", null);
        if (path != null) return new File(getFilesDir(), path);
        String name = record.optString("finalName", null);
        return name == null ? null : new File(new File(getFilesDir(), "outputs"), name);
    }

    private void writeUri(Uri uri, byte[] bytes) throws Exception {
        OutputStream outputStream = getContentResolver().openOutputStream(uri, "w");
        if (outputStream == null) throw new IOException("provider returned null output stream");
        try (OutputStream out = outputStream) { out.write(bytes); out.flush(); }
    }

    private void writeFile(File file, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
    }

    private byte[] fixtureBytes(String seed) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] marker = ("P05-SAF-FIXTURE:" + seed + "\n").getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < 262144; i++) output.write(marker, 0, marker.length);
        return output.toByteArray();
    }

    private JSONObject readCurrent() {
        try { return new JSONObject(readText(currentFile)); }
        catch (Exception ignored) { return null; }
    }

    private void persistAndRecord(String eventType, JSONObject record) {
        writeTextAtomic(currentFile, record.toString());
        record(eventType, record);
    }

    private void recordError(String eventType, Exception error) {
        JSONObject event = baseEvent();
        put(event, "error", error.toString());
        record(eventType, event);
    }

    private JSONObject reasonEvent(String reason) {
        JSONObject event = baseEvent();
        put(event, "reason", reason);
        return event;
    }

    private JSONObject baseEvent() {
        JSONObject event = new JSONObject();
        put(event, "label", LABEL);
        put(event, "timestampMs", System.currentTimeMillis());
        put(event, "deleteSource", false);
        return event;
    }

    private void record(String eventType, JSONObject event) {
        JSONObject line = baseEvent();
        put(line, "eventType", eventType);
        Iterator<String> keys = event.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            put(line, key, event.opt(key));
        }
        String text = line.toString() + "\n";
        synchronized (logLock) {
            try (FileOutputStream outputFile = new FileOutputStream(eventsFile, true)) {
                outputFile.write(text.getBytes(StandardCharsets.UTF_8));
                outputFile.flush();
                outputFile.getFD().sync();
            } catch (IOException ignored) { }
        }
        String logcatText = truncateUtf8(line.toString(), MAX_LOGCAT_EVENT_BYTES);
        Log.d("P05-SAF", logcatText);
        if (output != null) {
            synchronized (uiLock) {
                if (uiRefreshPending) {
                    uiRefreshAgain = true;
                    return;
                }
                uiRefreshPending = true;
            }
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    output.setText(readTextForUi(eventsFile));
                    boolean refreshAgain;
                    synchronized (uiLock) {
                        refreshAgain = uiRefreshAgain;
                        uiRefreshAgain = false;
                        if (!refreshAgain) uiRefreshPending = false;
                    }
                    if (refreshAgain) runOnUiThread(this);
                }
            });
        }
    }

    private void writeTextAtomic(File target, String text) {
        File temporary = new File(target.getPath() + ".tmp");
        try (FileOutputStream outputFile = new FileOutputStream(temporary, false)) {
            outputFile.write(text.getBytes(StandardCharsets.UTF_8));
            outputFile.flush();
            outputFile.getFD().sync();
            if (target.exists() && !target.delete()) throw new IOException("current record replacement failed");
            if (!temporary.renameTo(target)) throw new IOException("current record rename failed");
        } catch (IOException ignored) { }
    }

    private String readText(File file) {
        if (!file.isFile()) return "";
        try (FileInputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) != -1) outputBytes.write(buffer, 0, count);
            return outputBytes.toString("UTF-8");
        } catch (Exception ignored) {
            return "";
        }
    }

    private String readTextForUi(File file) {
        return readTextTail(file, MAX_UI_LOG_BYTES);
    }

    private String readTextTail(File file, int maxBytes) {
        if (!file.isFile()) return "";
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long length = input.length();
            long start = Math.max(0L, length - maxBytes);
            input.seek(start);
            byte[] bytes = new byte[(int) (length - start)];
            input.readFully(bytes);
            String text = new String(bytes, StandardCharsets.UTF_8);
            return start == 0L ? text : "[UI tail; durable JSONL remains complete]\n" + text;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String truncateUtf8(String value, int maxBytes) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length <= maxBytes) return value;
        int suffixBytes = "…".getBytes(StandardCharsets.UTF_8).length;
        int budget = Math.max(0, maxBytes - suffixBytes);
        int end = value.length();
        while (end > 0 && value.substring(0, end).getBytes(StandardCharsets.UTF_8).length > budget) {
            end--;
        }
        return value.substring(0, end) + "…";
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    private static void put(JSONObject object, String key, Object value) {
        try { object.put(key, value == null ? JSONObject.NULL : value); }
        catch (JSONException error) { throw new IllegalStateException(error); }
    }

    private static final class SourceInfo {
        final String uri;
        final String providerId;
        final String version;
        final long length;
        final String sha256;
        final File file;
        final Uri uriObject;

        SourceInfo(String uri, String providerId, String version, long length, String sha256,
                   File file, Uri uriObject) {
            this.uri = uri;
            this.providerId = providerId;
            this.version = version;
            this.length = length;
            this.sha256 = sha256;
            this.file = file;
            this.uriObject = uriObject;
        }
    }

    private static final class Measure {
        final long length;
        final String sha256;

        Measure(long length, String sha256) {
            this.length = length;
            this.sha256 = sha256;
        }
    }
}
