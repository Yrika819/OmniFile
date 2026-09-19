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

    override suspend fun rename(
        entry: StorageEntry,
        requestedName: String,
    ): StorageResult<StorageEntry> {
        val ref = checkedRef(entry.ref)
            ?: return StorageResult.Failure(StorageError.StaleReference)
        if (ref.documentId == rootDocumentId || StorageCapability.RENAME !in entry.capabilities) {
            return StorageResult.Failure(StorageError.Unsupported)
        }
        if (!isValidSingleComponent(requestedName)) {
            return StorageResult.Failure(StorageError.InvalidName(requestedName, ""))
        }

        return try {
            val sourceUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, ref.documentId)
            val returnedUri = DocumentsContract.renameDocument(contentResolver, sourceUri, requestedName)
                ?: return StorageResult.Failure(StorageError.IoFailure("Provider declined rename"))
            val returnedDocumentId = DocumentsContract.getDocumentId(returnedUri)
            queryEntry(returnedUri, entry.parentRef, returnedDocumentId)
        } catch (error: OperationCanceledException) {
            StorageResult.Failure(StorageError.Cancelled)
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            StorageResult.Failure(StorageError.PermissionDenied)
        } catch (error: FileNotFoundException) {
            StorageResult.Failure(StorageError.NotFound)
        } catch (error: UnsupportedOperationException) {
            StorageResult.Failure(StorageError.Unsupported)
        } catch (error: IllegalArgumentException) {
            StorageResult.Failure(StorageError.StaleReference)
        } catch (error: IOException) {
            StorageResult.Failure(StorageError.IoFailure(error.message))
        } catch (error: IllegalStateException) {
            StorageResult.Failure(StorageError.IoFailure(error.message))
        }
    }

    override suspend fun delete(entry: StorageEntry): StorageResult<Unit> {
        val ref = checkedRef(entry.ref)
            ?: return StorageResult.Failure(StorageError.StaleReference)
        if (ref.documentId == rootDocumentId || StorageCapability.DELETE !in entry.capabilities) {
            return StorageResult.Failure(StorageError.Unsupported)
        }

        return try {
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, ref.documentId)
            if (DocumentsContract.deleteDocument(contentResolver, documentUri)) {
                StorageResult.Success(Unit)
            } else {
                StorageResult.Failure(StorageError.IoFailure("Provider declined delete"))
            }
        } catch (error: OperationCanceledException) {
            StorageResult.Failure(StorageError.Cancelled)
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            StorageResult.Failure(StorageError.PermissionDenied)
        } catch (error: FileNotFoundException) {
            StorageResult.Failure(StorageError.NotFound)
        } catch (error: UnsupportedOperationException) {
            StorageResult.Failure(StorageError.Unsupported)
        } catch (error: IllegalArgumentException) {
            StorageResult.Failure(StorageError.StaleReference)
        } catch (error: IOException) {
            StorageResult.Failure(StorageError.IoFailure(error.message))
        } catch (error: IllegalStateException) {
            StorageResult.Failure(StorageError.IoFailure(error.message))
        }
    }

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

    private fun queryEntry(documentId: String): StorageResult<StorageEntry> =
        queryEntry(DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId), null, null)

    private fun queryEntry(
        uri: Uri,
        parentRef: EntryRef?,
        authoritativeDocumentId: String? = null,
    ): StorageResult<StorageEntry> = try {
        var entry: StorageEntry? = null
        query(uri) { cursor ->
            if (cursor.moveToFirst()) {
                entry = cursor.toEntry(parentRef, authoritativeDocumentId)
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

    private fun checkedRef(ref: EntryRef): SafEntryRef? {
        val safRef = ref as? SafEntryRef ?: return null
        return safRef.takeIf { it.providerId == id && it.treeUri == treeUri }
    }

    private fun query(uri: Uri, block: (Cursor) -> Unit) {
        contentResolver.query(uri, PROJECTION, null, null, null)?.use(block)
            ?: throw IOException("Provider returned no cursor")
    }

    private fun Cursor.toEntry(
        parentRef: EntryRef? = null,
        authoritativeDocumentId: String? = null,
    ): StorageEntry {
        val queriedDocumentId = getString(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
        val documentId = authoritativeDocumentId ?: queriedDocumentId
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
            if (documentId != rootDocumentId) {
                if (flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0) {
                    add(StorageCapability.RENAME)
                }
                if (flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0) {
                    add(StorageCapability.DELETE)
                }
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

    private fun isValidSingleComponent(name: String): Boolean {
        if (name.isEmpty() || name == "." || name == "..") return false
        if ('\u0000' in name || '/' in name || '\\' in name) return false
        return true
    }

    private data class SafEntryRef(
        override val providerId: ProviderId,
        val treeUri: Uri,
        val documentId: String,
    ) : EntryRef {
        override val identityKey: String = "${providerId.value}\u0000$treeUri\u0000$documentId"
    }

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
