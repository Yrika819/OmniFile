package com.omnifile.storage;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TestDocumentsProvider extends DocumentsProvider {
    public static final String AUTHORITY = "com.omnifile.test.documents";
    public static final android.net.Uri ROOT_URI = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root");

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
    private static int renameCalls;
    private static int deleteCalls;
    private static String configuredRenameSource;
    private static String configuredRenameId;
    private static String configuredRenameName;
    private static String configuredRenameFailure;
    private static String configuredDeleteFailure;

    static {
        reset();
    }

    public static void reset() {
        int directoryFlags = DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;
        int fileFlags = DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                | DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                | DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
        NODES.clear();
        NODES.put("root", new Node("root", null, "Controlled SAF", DocumentsContract.Document.MIME_TYPE_DIR, null, null, directoryFlags));
        NODES.put("root/folder", new Node("root/folder", "root", "Folder A", DocumentsContract.Document.MIME_TYPE_DIR, null, 1000L, directoryFlags));
        NODES.put("root/empty", new Node("root/empty", "root", "empty", DocumentsContract.Document.MIME_TYPE_DIR, null, 2000L, directoryFlags));
        NODES.put("root/alpha", new Node("root/alpha", "root", "alpha.txt", "text/plain", 5L, 3000L, fileFlags));
        NODES.put("root/folder/nested", new Node("root/folder/nested", "root/folder", "nested.txt", "text/plain", 6L, 4000L, fileFlags));
        renameCalls = 0;
        deleteCalls = 0;
        configuredRenameSource = null;
        configuredRenameId = null;
        configuredRenameName = null;
        configuredRenameFailure = null;
        configuredDeleteFailure = null;
    }

    public static void enableRootMutationFlags() {
        Node root = NODES.get("root");
        root.flags |= DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                | DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
    }

    public static void setRenameSupported(String documentId, boolean supported) {
        setFlag(documentId, DocumentsContract.Document.FLAG_SUPPORTS_RENAME, supported);
    }

    public static void setDeleteSupported(String documentId, boolean supported) {
        setFlag(documentId, DocumentsContract.Document.FLAG_SUPPORTS_DELETE, supported);
    }

    public static void configureRename(String sourceId, String returnedId, String returnedName) {
        configuredRenameSource = sourceId;
        configuredRenameId = returnedId;
        configuredRenameName = returnedName;
    }

    public static void configureRenameFailure(String documentId) {
        configuredRenameFailure = documentId;
    }

    public static void configureDeleteFailure(String documentId) {
        configuredDeleteFailure = documentId;
    }

    public static int renameCalls() {
        return renameCalls;
    }

    public static int deleteCalls() {
        return deleteCalls;
    }

    private static void setFlag(String documentId, int flag, boolean enabled) {
        Node node = NODES.get(documentId);
        if (node == null) throw new IllegalArgumentException("Unknown document " + documentId);
        if (enabled) node.flags |= flag;
        else node.flags &= ~flag;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(ROOT_COLUMNS);
        cursor.addRow(new Object[]{"root", "root", "Controlled SAF", "Synthetic test tree", "*/*", DocumentsContract.Root.FLAG_LOCAL_ONLY, 0});
        return cursor;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) {
        MatrixCursor cursor = new MatrixCursor(DOCUMENT_COLUMNS);
        addNode(cursor, NODES.get(documentId));
        return cursor;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(DOCUMENT_COLUMNS);
        List<Node> children = new ArrayList<>();
        for (Node node : NODES.values()) {
            if (parentDocumentId.equals(node.parentId)) children.add(node);
        }
        children.sort(Comparator.comparing(node -> node.name));
        for (Node node : children) addNode(cursor, node);
        return cursor;
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String childDocumentId) {
        return NODES.containsKey(childDocumentId)
                && (parentDocumentId.equals(childDocumentId)
                || childDocumentId.startsWith(parentDocumentId + "/"));
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) {
        throw new UnsupportedOperationException("Not needed for browse tests");
    }

    @Override
    public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        renameCalls++;
        Node node = NODES.get(documentId);
        if (node == null) throw new FileNotFoundException(documentId);
        if ((node.flags & DocumentsContract.Document.FLAG_SUPPORTS_RENAME) == 0) {
            throw new UnsupportedOperationException("Rename is not supported");
        }
        if (configuredRenameFailure != null && configuredRenameFailure.equals(documentId)) {
            throw new IllegalStateException("controlled rename failure");
        }
        if (configuredRenameSource != null && configuredRenameSource.equals(documentId)) {
            String returnedId = configuredRenameId;
            String returnedName = configuredRenameName;
            NODES.remove(documentId);
            NODES.put(returnedId, new Node(
                    returnedId,
                    node.parentId,
                    returnedName,
                    node.mimeType,
                    node.sizeBytes,
                    node.modifiedAt,
                    node.flags));
            return returnedId;
        }
        return documentId;
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        deleteCalls++;
        Node node = NODES.get(documentId);
        if (node == null) throw new FileNotFoundException(documentId);
        if ((node.flags & DocumentsContract.Document.FLAG_SUPPORTS_DELETE) == 0) {
            throw new UnsupportedOperationException("Delete is not supported");
        }
        if (configuredDeleteFailure != null && configuredDeleteFailure.equals(documentId)) {
            throw new IllegalStateException("controlled delete failure");
        }
        NODES.remove(documentId);
    }

    private static void addNode(MatrixCursor cursor, Node node) {
        if (node == null) return;
        cursor.addRow(new Object[]{node.id, node.name, node.mimeType, node.sizeBytes, node.modifiedAt, node.flags});
    }

    private static final class Node {
        final String id;
        final String parentId;
        final String name;
        final String mimeType;
        final Long sizeBytes;
        final Long modifiedAt;
        int flags;

        Node(String id, String parentId, String name, String mimeType, Long sizeBytes, Long modifiedAt, int flags) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
            this.modifiedAt = modifiedAt;
            this.flags = flags;
        }
    }
}
