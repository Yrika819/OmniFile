package dev.poc.filemanager.storagecap;

// POC-ONLY — NOT PRODUCTION AUTHORITY

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class SyntheticDocumentsProvider extends DocumentsProvider {
    private static final String ROOT = "root";
    private static final String PIPE = "pipe";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor c = new MatrixCursor(resolveRootProjection(projection));
        MatrixCursor.RowBuilder r = c.newRow();
        r.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT);
        r.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT);
        r.add(DocumentsContract.Root.COLUMN_TITLE, "POC-001 Synthetic Provider");
        r.add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_CREATE | DocumentsContract.Root.FLAG_LOCAL_ONLY);
        r.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*");
        r.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, 1024L * 1024L);
        return c;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor c = new MatrixCursor(resolveDocumentProjection(projection));
        includeDocument(c, documentId);
        return c;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) throws FileNotFoundException {
        if (!ROOT.equals(parentDocumentId)) throw new FileNotFoundException(parentDocumentId);
        MatrixCursor c = new MatrixCursor(resolveDocumentProjection(projection));
        includeDocument(c, PIPE);
        return c;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
        if (!PIPE.equals(documentId)) throw new FileNotFoundException(documentId);
        if (!mode.contains("r")) throw new FileNotFoundException("synthetic pipe is read-only");
        try {
            final ParcelFileDescriptor[] pair = ParcelFileDescriptor.createPipe();
            Thread t = new Thread(() -> {
                try (FileOutputStream out = new FileOutputStream(pair[1].getFileDescriptor())) {
                    byte[] block = "synthetic-pipe-data\n".getBytes(StandardCharsets.UTF_8);
                    for (int i = 0; i < 4096; i++) {
                        if (signal != null && signal.isCanceled()) break;
                        out.write(block);
                    }
                    out.flush();
                } catch (IOException ignored) {
                } finally {
                    try { pair[1].close(); } catch (IOException ignored) {}
                }
            }, "poc001-pipe-writer");
            t.setDaemon(true);
            t.start();
            return pair[0];
        } catch (IOException e) {
            throw new FileNotFoundException(e.toString());
        }
    }

    private void includeDocument(MatrixCursor c, String id) throws FileNotFoundException {
        MatrixCursor.RowBuilder r = c.newRow();
        if (ROOT.equals(id)) {
            r.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, ROOT);
            r.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "POC-001 Synthetic Provider");
            r.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR);
            r.add(DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE);
            r.add(DocumentsContract.Document.COLUMN_SIZE, 0L);
            r.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis());
        } else if (PIPE.equals(id)) {
            r.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, PIPE);
            r.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "nonseekable-pipe.bin");
            r.add(DocumentsContract.Document.COLUMN_MIME_TYPE, "application/octet-stream");
            r.add(DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL);
            r.add(DocumentsContract.Document.COLUMN_SIZE, null);
            r.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis());
        } else {
            throw new FileNotFoundException(id);
        }
    }

    private static String[] resolveRootProjection(String[] projection) {
        return projection != null ? projection : new String[] {
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
        };
    }

    private static String[] resolveDocumentProjection(String[] projection) {
        return projection != null ? projection : new String[] {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
    }
}
