package com.omnifile.storage;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

/** Deterministic in-process DocumentsProvider used only by instrumentation tests. */
public final class TestDocumentsProvider extends DocumentsProvider {
    public static final String AUTHORITY = "com.omnifile.test.documents";
    public static final android.net.Uri ROOT_URI = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root");

    public static final String FAILURE_SECURITY = "security";
    public static final String FAILURE_NOT_FOUND = "not-found";
    public static final String FAILURE_UNSUPPORTED = "unsupported";
    public static final String FAILURE_IO = "io";
    public static final String FAILURE_NULL = "null";

    private static final String[] DOCUMENT_COLUMNS = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
    };
    private static final String[] ROOT_COLUMNS = {
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
    };
    private static final Map<String, Node> NODES = new LinkedHashMap<>();
    // VS07: documents opened as real regular files (seekable) instead of pipes.
    private static final Map<String, java.io.File> REGULAR_FILE_BACKING = new LinkedHashMap<>();
    private static int renameCalls;
    private static int deleteCalls;
    private static int createCalls;
    private static int readOpenCalls;
    private static int writeOpenCalls;
    private static int nextCreatedId;
    private static String configuredRenameSource;
    private static String configuredRenameId;
    private static String configuredRenameName;
    private static String configuredRenameFailure;
    private static String configuredRenameFailureSource;
    private static String configuredDeleteFailure;
    private static String configuredCreateFailure;
    private static String configuredReadFailure;
    private static String configuredWriteFailure;
    private static boolean ambiguousRename;
    private static boolean ambiguousDelete;
    private static int readFailureAfterBytes = -1;
    private static int writeFailureAfterBytes = -1;
    private static final AtomicInteger pendingIo = new AtomicInteger(0);
    private static volatile boolean providerUnavailable;

    static {
        reset();
    }

    public static void reset() {
        int directoryFlags = DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;
        int fileFlags = DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                | DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                | DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
        NODES.clear();
        NODES.put("root", new Node("root", null, "Controlled SAF", DocumentsContract.Document.MIME_TYPE_DIR,
                null, 0L, directoryFlags, null, false));
        NODES.put("root/folder", new Node("root/folder", "root", "Folder A", DocumentsContract.Document.MIME_TYPE_DIR,
                null, 1000L, directoryFlags, null, false));
        NODES.put("root/empty", new Node("root/empty", "root", "empty", DocumentsContract.Document.MIME_TYPE_DIR,
                null, 2000L, directoryFlags, null, false));
        NODES.put("root/alpha", new Node("root/alpha", "root", "alpha.txt", "text/plain",
                null, 3000L, fileFlags, "alpha".getBytes(), false));
        NODES.put("root/folder/nested", new Node("root/folder/nested", "root/folder", "nested.txt", "text/plain",
                null, 4000L, fileFlags, "nested".getBytes(), false));
        renameCalls = 0;
        deleteCalls = 0;
        createCalls = 0;
        readOpenCalls = 0;
        writeOpenCalls = 0;
        nextCreatedId = 1;
        configuredRenameSource = null;
        configuredRenameId = null;
        configuredRenameName = null;
        configuredRenameFailure = null;
        configuredRenameFailureSource = null;
        configuredDeleteFailure = null;
        configuredCreateFailure = null;
        configuredReadFailure = null;
        configuredWriteFailure = null;
        ambiguousRename = false;
        ambiguousDelete = false;
        readFailureAfterBytes = -1;
        writeFailureAfterBytes = -1;
        providerUnavailable = false;
        pendingIo.set(0);
        REGULAR_FILE_BACKING.clear();
    }

    /** Adds a readable file document (pipe-backed sequential content by default). */
    public static void addDocument(String id, String parentId, String displayName, String mimeType, byte[] content) {
        int fileFlags = DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                | DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                | DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
        NODES.put(id, new Node(id, parentId, displayName, mimeType, null, 6000L, fileFlags,
                content == null ? null : content.clone(), false));
    }

    /** VS07: when set, read-mode openDocument returns a real regular-file descriptor. */
    public static void setRegularFileBacking(String documentId, java.io.File file) {
        if (file == null) {
            REGULAR_FILE_BACKING.remove(documentId);
        } else {
            REGULAR_FILE_BACKING.put(documentId, file);
        }
    }

    public static void enableRootMutationFlags() {
        Node root = NODES.get("root");
        root.flags |= DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                | DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
    }

    public static void setCreateSupported(String documentId, boolean supported) {
        setFlag(documentId, DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE, supported);
    }

    public static void setRenameSupported(String documentId, boolean supported) {
        setFlag(documentId, DocumentsContract.Document.FLAG_SUPPORTS_RENAME, supported);
    }

    public static void setDeleteSupported(String documentId, boolean supported) {
        setFlag(documentId, DocumentsContract.Document.FLAG_SUPPORTS_DELETE, supported);
    }

    public static void setWriteSupported(String documentId, boolean supported) {
        setFlag(documentId, DocumentsContract.Document.FLAG_SUPPORTS_WRITE, supported);
    }

    public static void setUnknownSize(String documentId, boolean unknown) {
        node(documentId).unknownSize = unknown;
    }

    public static void setContent(String documentId, byte[] content) {
        node(documentId).content = content.clone();
    }

    public static byte[] content(String documentId) {
        return node(documentId).content == null ? null : node(documentId).content.clone();
    }

    public static boolean exists(String documentId) {
        return NODES.containsKey(documentId);
    }

    public static void configureRename(String sourceId, String returnedId, String returnedName) {
        configuredRenameSource = sourceId;
        configuredRenameId = returnedId;
        configuredRenameName = returnedName;
    }

    public static void configureRenameFailure(String documentId) {
        configureRenameFailure(documentId, FAILURE_IO);
    }

    public static void configureRenameFailure(String documentId, String failure) {
        configuredRenameFailureSource = documentId;
        configuredRenameFailure = failure;
    }

    public static void configureAmbiguousRename(String documentId, String returnedId, String returnedName) {
        configuredRenameSource = documentId;
        configuredRenameId = returnedId;
        configuredRenameName = returnedName;
        ambiguousRename = true;
    }

    public static void configureDeleteFailure(String documentId) {
        configureDeleteFailure(documentId, FAILURE_IO);
    }

    public static void configureDeleteFailure(String documentId, String failure) {
        configuredDeleteFailure = documentId + "\u0000" + failure;
    }

    public static void configureAmbiguousDelete(String documentId) {
        ambiguousDelete = true;
        configuredDeleteFailure = documentId;
    }

    public static void configureCreateFailure(String failure) {
        configuredCreateFailure = failure;
    }

    public static void configureReadFailure(String documentId, String failure) {
        configuredReadFailure = documentId + "\u0000" + failure;
    }

    public static void configureWriteFailure(String documentId, String failure) {
        configuredWriteFailure = documentId + "\u0000" + failure;
    }

    public static void configureReadFailureAfterBytes(int bytes) {
        readFailureAfterBytes = bytes;
    }

    public static void configureWriteFailureAfterBytes(int bytes) {
        writeFailureAfterBytes = bytes;
    }

    public static void setProviderUnavailable(boolean unavailable) {
        providerUnavailable = unavailable;
    }

    /**
     * Blocks until every pipe-backed read and write started so far has finished
     * committing, or until the timeout expires.
     *
     * This counts in-flight operations rather than holding a single completion
     * handle. A single handle is wrong as soon as two operations overlap: opening
     * a second pipe replaced the handle, so a caller could observe an already
     * completed latch while an earlier writer was still committing, and the
     * transfer would verify against stale content. That surfaced as an
     * intermittent StorageError.IoFailure, which OperationManager correctly maps
     * to RETRYABLE_FAILURE.
     */
    public static boolean awaitPendingIo() {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (pendingIo.get() > 0) {
            if (System.nanoTime() >= deadlineNanos) return false;
            try {
                Thread.sleep(1);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    public static int renameCalls() {
        return renameCalls;
    }

    public static int deleteCalls() {
        return deleteCalls;
    }

    public static int createCalls() {
        return createCalls;
    }

    public static int readOpenCalls() {
        return readOpenCalls;
    }

    public static int writeOpenCalls() {
        return writeOpenCalls;
    }

    private static void setFlag(String documentId, int flag, boolean enabled) {
        Node node = node(documentId);
        if (enabled) node.flags |= flag;
        else node.flags &= ~flag;
    }

    private static Node node(String documentId) {
        Node node = NODES.get(documentId);
        if (node == null) throw new IllegalArgumentException("Unknown document " + documentId);
        return node;
    }

    /**
     * Waits for background pipe I/O to settle so that a read of document state
     * is never served from a pre-commit snapshot.
     *
     * openWritePipe commits node.content on a background thread once the writer
     * closes its descriptor, which is how a real DocumentsProvider behaves.
     * The product then immediately re-reads the document to verify finalization,
     * and a size derived from node.content would be missing if the commit had
     * not landed yet. That surfaced as a cross-version intermittent
     * StorageError.IoFailure in the SAF transfer runtime tests, which
     * OperationManager correctly maps to RETRYABLE_FAILURE.
     *
     * Every entry point that observes document state waits here, so the fixture
     * behaves like a provider that has committed by the time it is asked. The
     * write thread itself never re-enters the provider, so this cannot deadlock,
     * and the bound keeps a stuck writer from hanging a test.
     */
    private static void awaitSettled() {
        if (pendingIo.get() <= 0) return;
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (pendingIo.get() > 0) {
            if (System.nanoTime() >= deadlineNanos) return;
            try {
                Thread.sleep(1);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void beginIo() {
        pendingIo.incrementAndGet();
    }

    private static void endIo() {
        pendingIo.decrementAndGet();
    }

    private static void throwFailure(String failure, String documentId, String operation) throws FileNotFoundException {
        if (failure == null) return;
        if (FAILURE_SECURITY.equals(failure)) throw new SecurityException("controlled permission failure");
        if (FAILURE_NOT_FOUND.equals(failure)) throw new FileNotFoundException(documentId);
        if (FAILURE_UNSUPPORTED.equals(failure)) throw new UnsupportedOperationException("controlled unsupported operation");
        if (FAILURE_IO.equals(failure)) throw new IllegalStateException("controlled " + operation + " failure");
    }

    private static String configuredFailure(String value) {
        if (value == null) return null;
        int separator = value.indexOf('\u0000');
        return separator < 0 ? value : value.substring(separator + 1);
    }

    private static boolean appliesTo(String value, String documentId) {
        return value != null && (value.equals(documentId) || value.startsWith(documentId + "\u0000"));
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        if (providerUnavailable) throw new IllegalStateException("controlled provider unavailable");
        MatrixCursor cursor = new MatrixCursor(ROOT_COLUMNS);
        cursor.addRow(new Object[]{"root", "root", "Controlled SAF", "Synthetic test tree", "*/*", DocumentsContract.Root.FLAG_LOCAL_ONLY, 0});
        return cursor;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) {
        awaitSettled();
        if (providerUnavailable) throw new IllegalStateException("controlled provider unavailable");
        documentId = normalizeDocumentId(documentId);
        MatrixCursor cursor = new MatrixCursor(DOCUMENT_COLUMNS);
        addNode(cursor, NODES.get(documentId));
        return cursor;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) {
        if (providerUnavailable) throw new IllegalStateException("controlled provider unavailable");
        parentDocumentId = normalizeDocumentId(parentDocumentId);
        MatrixCursor cursor = new MatrixCursor(DOCUMENT_COLUMNS);
        List<Node> children = new ArrayList<>();
        for (Node node : NODES.values()) {
            if (parentDocumentId.equals(node.parentId)) children.add(node);
        }
        children.sort(Comparator.comparing(node -> node.name));
        for (Node node : children) addNode(cursor, node);
        return cursor;
    }

    private static String normalizeDocumentId(String value) {
        if (value == null || !value.startsWith("content://")) return value;
        try {
            return android.net.Uri.decode(DocumentsContract.getDocumentId(android.net.Uri.parse(value)));
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String childDocumentId) {
        if (providerUnavailable) throw new IllegalStateException("controlled provider unavailable");
        String normalizedParent = normalizeDocumentId(parentDocumentId);
        String normalizedChild = normalizeDocumentId(childDocumentId);
        // Containment is an identity-boundary question, not an existence
        // query. An in-tree but deleted document must pass this check so that
        // queryDocument() can return an empty cursor and the adapter can report
        // truthful NotFound instead of framework-level permission denial.
        return normalizedParent.equals(normalizedChild)
                || normalizedChild.startsWith(normalizedParent + "/");
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName) throws FileNotFoundException {
        awaitSettled();
        if (providerUnavailable) throw new IllegalStateException("controlled provider unavailable");
        parentDocumentId = normalizeDocumentId(parentDocumentId);
        createCalls++;
        if (FAILURE_NULL.equals(configuredCreateFailure)) return null;
        throwFailure(configuredCreateFailure, parentDocumentId, "create");
        Node parent = NODES.get(parentDocumentId);
        if (parent == null) throw new FileNotFoundException(parentDocumentId);
        if (parent.mimeType == null || !DocumentsContract.Document.MIME_TYPE_DIR.equals(parent.mimeType)) {
            throw new UnsupportedOperationException("parent is not a directory");
        }
        if ((parent.flags & DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) == 0) {
            throw new UnsupportedOperationException("create is not supported");
        }
        for (Node child : NODES.values()) {
            if (parentDocumentId.equals(child.parentId) && displayName.equals(child.name)) {
                throw new IllegalStateException("destination conflict");
            }
        }
        String id = parentDocumentId + "/created-" + nextCreatedId++;
        int flags = DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                | DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                | DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
        NODES.put(id, new Node(id, parentDocumentId, displayName, mimeType, null, 5000L, flags, new byte[0], false));
        return id;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        if (mode == null || !mode.contains("w")) awaitSettled();
        if (providerUnavailable) throw new IllegalStateException("controlled provider unavailable");
        documentId = normalizeDocumentId(documentId);
        Node node = NODES.get(documentId);
        if (node == null) throw new FileNotFoundException(documentId);
        boolean write = mode.contains("w");
        String configured = write ? configuredWriteFailure : configuredReadFailure;
        if (appliesTo(configured, documentId)) {
            String failure = configuredFailure(configured);
            if (FAILURE_NULL.equals(failure)) return null;
            throwFailure(failure, documentId, write ? "write" : "read");
        }
        if (write && (node.flags & DocumentsContract.Document.FLAG_SUPPORTS_WRITE) == 0) {
            throw new UnsupportedOperationException("write is not supported");
        }
        if (!write && node.content == null && !REGULAR_FILE_BACKING.containsKey(documentId)) {
            throw new UnsupportedOperationException("not a file");
        }
        if (!write) {
            readOpenCalls++;
            java.io.File backing = REGULAR_FILE_BACKING.get(documentId);
            if (backing != null) {
                try {
                    return ParcelFileDescriptor.open(backing, ParcelFileDescriptor.MODE_READ_ONLY);
                } catch (IOException error) {
                    throw fileNotFound(error);
                }
            }
            return openReadPipe(node);
        }
        writeOpenCalls++;
        return openWritePipe(node);
    }

    private static ParcelFileDescriptor openReadPipe(Node node) throws FileNotFoundException {
        final ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createPipe();
        } catch (IOException error) {
            throw fileNotFound(error);
        }
        beginIo();
        final byte[] content = node.content.clone();
        new Thread(() -> {
            try (ParcelFileDescriptor.AutoCloseOutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                int length = content.length;
                if (readFailureAfterBytes >= 0) length = Math.min(length, readFailureAfterBytes);
                output.write(content, 0, length);
                if (readFailureAfterBytes >= 0 && length < content.length) {
                    throw new IOException("controlled read interruption");
                }
            } catch (IOException ignored) {
                // Closing the consumer or an injected interruption is observed as EOF/I/O by the reader.
            } finally {
                endIo();
            }
        }, "controlled-saf-read").start();
        return pipe[0];
    }

    private static ParcelFileDescriptor openWritePipe(Node node) throws FileNotFoundException {
        final ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createPipe();
        } catch (IOException error) {
            throw fileNotFound(error);
        }
        beginIo();
        new Thread(() -> {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ParcelFileDescriptor.AutoCloseInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(pipe[0])) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    int allowed = read;
                    if (writeFailureAfterBytes >= 0) allowed = Math.min(allowed, writeFailureAfterBytes - total);
                    if (allowed > 0) {
                        bytes.write(buffer, 0, allowed);
                        total += allowed;
                    }
                    if (writeFailureAfterBytes >= 0 && total >= writeFailureAfterBytes) {
                        throw new IOException("controlled write interruption");
                    }
                }
                if (appliesTo(configuredWriteFailure, node.id)) {
                    throwFailure(configuredFailure(configuredWriteFailure), node.id, "write");
                }
                node.content = bytes.toByteArray();
                node.modifiedAt = node.modifiedAt + 1L;
            } catch (Exception ignored) {
                // A failed stream deliberately leaves the previous committed content unchanged.
            } finally {
                endIo();
            }
        }, "controlled-saf-write").start();
        return pipe[1];
    }

    @Override
    public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        awaitSettled();
        documentId = normalizeDocumentId(documentId);
        renameCalls++;
        Node node = NODES.get(documentId);
        if (node == null) throw new FileNotFoundException(documentId);
        if ((node.flags & DocumentsContract.Document.FLAG_SUPPORTS_RENAME) == 0) {
            throw new UnsupportedOperationException("Rename is not supported");
        }
        if (configuredRenameFailureSource != null && configuredRenameFailureSource.equals(documentId)) {
            throwFailure(configuredFailure(configuredRenameFailure), documentId, "rename");
        }
        String returnedId = documentId;
        String returnedName = displayName;
        if (configuredRenameSource != null && configuredRenameSource.equals(documentId)
                && configuredRenameId != null) {
            returnedId = configuredRenameId;
            returnedName = configuredRenameName == null ? displayName : configuredRenameName;
        }
        if (!returnedId.equals(documentId) || !returnedName.equals(node.name)) {
            NODES.remove(documentId);
            NODES.put(returnedId, new Node(
                    returnedId,
                    node.parentId,
                    returnedName,
                    node.mimeType,
                    node.sizeBytes,
                    node.modifiedAt,
                    node.flags,
                    node.content,
                    node.unknownSize));
        }
        if (ambiguousRename && configuredRenameSource != null && configuredRenameSource.equals(documentId)) {
            throw new IllegalStateException("controlled ambiguous rename acknowledgement");
        }
        return returnedId;
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        documentId = normalizeDocumentId(documentId);
        deleteCalls++;
        Node node = NODES.get(documentId);
        if (node == null) throw new FileNotFoundException(documentId);
        if ((node.flags & DocumentsContract.Document.FLAG_SUPPORTS_DELETE) == 0) {
            throw new UnsupportedOperationException("Delete is not supported");
        }
        if (configuredDeleteFailure != null && appliesTo(configuredDeleteFailure, documentId)) {
            if (ambiguousDelete) {
                NODES.remove(documentId);
                throw new IllegalStateException("controlled ambiguous delete acknowledgement");
            }
            throwFailure(configuredFailure(configuredDeleteFailure), documentId, "delete");
        }
        NODES.remove(documentId);
    }

    private static void addNode(MatrixCursor cursor, Node node) {
        if (node == null) return;
        Long size = node.unknownSize || node.content == null ? null : (long) node.content.length;
        cursor.addRow(new Object[]{node.id, node.name, node.mimeType, size, node.modifiedAt, node.flags});
    }

    private static FileNotFoundException fileNotFound(IOException error) {
        FileNotFoundException wrapped = new FileNotFoundException(error.getMessage());
        wrapped.initCause(error);
        return wrapped;
    }

    private static final class Node {
        final String id;
        final String parentId;
        String name;
        final String mimeType;
        final Long sizeBytes;
        Long modifiedAt;
        int flags;
        byte[] content;
        boolean unknownSize;

        Node(String id, String parentId, String name, String mimeType, Long sizeBytes, Long modifiedAt,
             int flags, byte[] content, boolean unknownSize) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
            this.modifiedAt = modifiedAt;
            this.flags = flags;
            this.content = content;
            this.unknownSize = unknownSize;
        }
    }
}
