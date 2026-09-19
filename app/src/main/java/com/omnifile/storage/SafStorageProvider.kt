package com.omnifile.storage

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.OperationCanceledException
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CancellationException

class SafStorageProvider(
    private val contentResolver: ContentResolver,
    private val treeUri: Uri,
    override val id: ProviderId,
) : StorageProvider {
    private val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)

    override suspend fun root(): StorageResult<StorageEntry> = queryEntry(rootDocumentId)

    override suspend fun listChildren(directory: EntryRef): StorageResult<List<StorageEntry>> {
        val ref = directory as? SafEntryRef
            ?: return StorageResult.Failure(StorageError.StaleReference)
        if (ref.providerId != id || ref.treeUri != treeUri) {
            return StorageResult.Failure(StorageError.StaleReference)
        }

        return try {
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, ref.documentId)
            val children = mutableListOf<StorageEntry>()
            query(uri) { cursor ->
                while (cursor.moveToNext()) {
                    children += cursor.toEntry(ref)
                }
            }
            StorageResult.Success(children.sortedBy { it.displayName })
        } catch (error: OperationCanceledException) {
            StorageResult.Failure(StorageError.Cancelled)
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            StorageResult.Failure(StorageError.PermissionDenied)
        } catch (error: FileNotFoundException) {
            StorageResult.Failure(StorageError.NotFound)
        } catch (error: IllegalArgumentException) {
            StorageResult.Failure(StorageError.StaleReference)
        } catch (error: IOException) {
            StorageResult.Failure(StorageError.IoFailure(error.message))
        }
    }

    private fun queryEntry(documentId: String): StorageResult<StorageEntry> = try {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        var entry: StorageEntry? = null
        query(uri) { cursor ->
            if (cursor.moveToFirst()) {
                entry = cursor.toEntry()
            }
        }
        entry?.let { StorageResult.Success(it) } ?: StorageResult.Failure(StorageError.NotFound)
    } catch (error: OperationCanceledException) {
        StorageResult.Failure(StorageError.Cancelled)
    } catch (error: SecurityException) {
        StorageResult.Failure(StorageError.PermissionDenied)
    } catch (error: FileNotFoundException) {
        StorageResult.Failure(StorageError.NotFound)
    } catch (error: IllegalArgumentException) {
        StorageResult.Failure(StorageError.StaleReference)
    } catch (error: IOException) {
        StorageResult.Failure(StorageError.IoFailure(error.message))
    }

    private fun query(uri: Uri, block: (Cursor) -> Unit) {
        contentResolver.query(uri, PROJECTION, null, null, null)?.use(block)
            ?: throw IOException("Provider returned no cursor")
    }

    private fun Cursor.toEntry(parentRef: EntryRef? = null): StorageEntry {
        val documentId = getString(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
        val displayName = getString(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
            ?: "Unnamed"
        val mimeType = getString(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
        val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
        val flags = getLong(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS)).toInt()
        val capabilities = buildSet {
            if (isDirectory) add(StorageCapability.LIST_CHILDREN)
            if (flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0) {
                add(StorageCapability.WRITE)
            }
            if (flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0) {
                add(StorageCapability.RENAME)
            }
            if (flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0) {
                add(StorageCapability.DELETE)
            }
        }
        return StorageEntry(
            ref = SafEntryRef(id, treeUri, documentId),
            displayName = displayName,
            kind = if (isDirectory) EntryKind.DIRECTORY else EntryKind.FILE,
            sizeBytes = nullableLong(DocumentsContract.Document.COLUMN_SIZE),
            modifiedAtEpochMillis = nullableLong(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            mimeType = mimeType,
            capabilities = capabilities,
            parentRef = parentRef,
        )
    }

    private fun Cursor.nullableLong(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    private data class SafEntryRef(
        override val providerId: ProviderId,
        val treeUri: Uri,
        val documentId: String,
    ) : EntryRef

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
    }
}
