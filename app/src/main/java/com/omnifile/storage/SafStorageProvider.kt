package com.omnifile.storage

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/**
 * SAF browse, mutation, and conservative regular-file transfer adapter.
 *
 * The tree URI is an access boundary, not a universal object identity. Durable
 * locators carry the tree URI and current document ID in an explicit versioned
 * payload and every re-resolution is checked against this provider instance.
 */
class SafStorageProvider(
    private val contentResolver: ContentResolver,
    private val treeUri: Uri,
    override val id: ProviderId,
    private val grantFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
    private val finalizationProven: Boolean = false,
) : StorageTransferProvider {
    init {
        require(DocumentsContract.isTreeUri(treeUri)) { "SAF transfer requires a tree URI" }
    }

    private val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
    private val hasReadGrant = grantFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
    private val hasWriteGrant = grantFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0

    override val transferCapabilities: Set<TransferCapability> = buildSet {
        if (hasReadGrant) add(TransferCapability.READ_SEQUENTIAL)
        if (hasWriteGrant) {
            add(TransferCapability.DELETE)
            if (finalizationProven) {
                add(TransferCapability.CREATE_CHILD)
                add(TransferCapability.WRITE_SEQUENTIAL)
                add(TransferCapability.FINALIZE)
            }
        }
    }

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
            return StorageResult.Failure(StorageError.InvalidName(requestedName, "Name must be a single path component"))
        }

        return try {
            val sourceUri = documentUri(ref.documentId)
            val returnedUri = DocumentsContract.renameDocument(contentResolver, sourceUri, requestedName)
                ?: return StorageResult.Failure(StorageError.IoFailure("Provider declined rename"))
            val returnedDocumentId = canonicalDocumentId(returnedUri)
                ?: return StorageResult.Failure(StorageError.StaleReference)
            queryEntry(documentUri(returnedDocumentId), entry.parentRef, returnedDocumentId)
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
            if (DocumentsContract.deleteDocument(contentResolver, documentUri(ref.documentId))) {
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
        if (ref.providerId != id || ref.treeUri != treeUri || !isWithinSelectedTree(ref.documentId)) {
            return StorageResult.Failure(StorageError.StaleReference)
        }

        return try {
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, ref.documentId)
            val children = mutableListOf<StorageEntry>()
            query(uri) { cursor ->
                while (cursor.moveToNext()) children += cursor.toEntry(ref)
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
        } catch (error: IllegalStateException) {
            StorageResult.Failure(StorageError.IoFailure(error.message))
        }
    }

    override suspend fun encodeDurableLocator(ref: EntryRef): StorageResult<com.omnifile.operations.DurableLocator> =
        checkedRef(ref)?.let { StorageResult.Success(locatorFor(it.documentId)) }
            ?: StorageResult.Failure(StorageError.StaleReference)

    override suspend fun resolveDurableLocator(
        locator: com.omnifile.operations.DurableLocator,
    ): StorageResult<StorageEntry> {
        val parts = decodeLocator(locator) ?: return StorageResult.Failure(StorageError.StaleReference)
        return queryEntry(documentUri(parts.documentId), null, parts.documentId)
    }

    override suspend fun inspectTransfer(
        locator: com.omnifile.operations.DurableLocator,
    ): StorageResult<TransferFileFacts> {
        val parts = decodeLocator(locator) ?: return StorageResult.Failure(StorageError.StaleReference)
        return when (val result = queryEntry(documentUri(parts.documentId), null, parts.documentId)) {
            is StorageResult.Success -> {
                val entry = result.value
                StorageResult.Success(
                    TransferFileFacts(
                        locator = locatorFor(parts.documentId),
                        kind = entry.kind,
                        sizeBytes = entry.sizeBytes,
                        // A document ID is not content versioning, but it lets us
                        // detect replacement/rename while optional metadata is absent.
                        versionToken = listOf(
                            parts.documentId,
                            entry.sizeBytes ?: "unknown",
                            entry.modifiedAtEpochMillis ?: "unknown",
                            entry.mimeType ?: "unknown",
                        ).joinToString(":"),
                    ),
                )
            }
            is StorageResult.Failure -> result
        }
    }

    override suspend fun openSequentialRead(
        locator: com.omnifile.operations.DurableLocator,
    ): StorageResult<SequentialReadHandle> {
        val parts = decodeLocator(locator) ?: return StorageResult.Failure(StorageError.StaleReference)
        val entry = when (val result = queryEntry(documentUri(parts.documentId), null, parts.documentId)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return result
        }
        if (entry.kind != EntryKind.FILE || StorageCapability.READ_SEQUENTIAL !in entry.capabilities) {
            return StorageResult.Failure(StorageError.Unsupported)
        }
        return try {
            val descriptor = contentResolver.openFileDescriptor(documentUri(parts.documentId), "r")
                ?: return StorageResult.Failure(StorageError.IoFailure("Provider returned no descriptor"))
            StorageResult.Success(
                object : SequentialReadHandle {
                    private val input = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                    override val expectedBytes: Long? = entry.sizeBytes
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = input.read(buffer, offset, length)
                    override fun close() = input.close()
                },
            )
        } catch (error: OperationCanceledException) {
            StorageResult.Failure(StorageError.Cancelled)
        } catch (error: SecurityException) {
            StorageResult.Failure(StorageError.PermissionDenied)
        } catch (error: FileNotFoundException) {
            StorageResult.Failure(StorageError.NotFound)
        } catch (error: UnsupportedOperationException) {
            StorageResult.Failure(StorageError.Unsupported)
        } catch (error: IOException) {
            StorageResult.Failure(StorageError.IoFailure(error.message))
        } catch (error: IllegalArgumentException) {
            StorageResult.Failure(StorageError.StaleReference)
        } catch (error: IllegalStateException) {
            StorageResult.Failure(StorageError.IoFailure(error.message))
        }
    }

    override suspend fun createOperationPartial(
        destinationParent: com.omnifile.operations.DurableLocator,
        intendedFinalName: String,
        operationId: String,
    ): StorageResult<com.omnifile.operations.DurableLocator> {
        val parts = decodeLocator(destinationParent) ?: return StorageResult.Failure(StorageError.StaleReference)
        val parent = when (val result = queryEntry(documentUri(parts.documentId), null, parts.documentId)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return result
        }
        if (parent.kind != EntryKind.DIRECTORY || StorageCapability.CREATE_CHILD !in parent.capabilities) {
            return StorageResult.Failure(StorageError.Unsupported)
        }
        val safeOperationId = operationId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (safeOperationId.isBlank() || !isValidSingleComponent(intendedFinalName)) {
            return StorageResult.Failure(StorageError.InvalidName(intendedFinalName, "Invalid destination name"))
        }
        val partialName = ".omnifile-$safeOperationId.partial"
        return try {
            val createdUri = DocumentsContract.createDocument(
                contentResolver,
                documentUri(parts.documentId),
                "application/octet-stream",
                partialName,
            ) ?: return StorageResult.Failure(StorageError.IoFailure("Provider declined partial creation"))
            val createdDocumentId = canonicalDocumentId(createdUri)
                ?: return StorageResult.Failure(StorageError.StaleReference)
            when (val entry = queryEntry(documentUri(createdDocumentId), parent.ref, createdDocumentId)) {
                is StorageResult.Success -> if (
                    entry.value.kind == EntryKind.FILE &&
                    entry.value.displayName == partialName &&
                    entry.value.parentRef == parent.ref
                ) {
                    when (val children = listChildren(parent.ref)) {
                        is StorageResult.Success -> if (children.value.any { it.ref == entry.value.ref }) {
                            StorageResult.Success(locatorFor(createdDocumentId))
                        } else {
                            StorageResult.Failure(StorageError.StaleReference)
                        }
                        is StorageResult.Failure -> children
                    }
                } else {
                    StorageResult.Failure(StorageError.StaleReference)
                }
                is StorageResult.Failure -> entry
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

    override suspend fun openSequentialWrite(
        partial: com.omnifile.operations.DurableLocator,
        append: Boolean,
        operationId: String?,
    ): StorageResult<SequentialWriteHandle> {
        if (append) return StorageResult.Failure(StorageError.Unsupported)
        val parts = decodeLocator(partial) ?: return StorageResult.Failure(StorageError.StaleReference)
        val entry = when (val result = queryEntry(documentUri(parts.documentId), null, parts.documentId)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return result
        }
        if (entry.kind != EntryKind.FILE || !isOperationPartial(entry.displayName, operationId)) {
            return StorageResult.Failure(StorageError.StaleReference)
        }
        if (StorageCapability.WRITE !in entry.capabilities) {
            return StorageResult.Failure(StorageError.Unsupported)
        }
        return try {
            val descriptor = contentResolver.openFileDescriptor(documentUri(parts.documentId), "wt")
                ?: return StorageResult.Failure(StorageError.IoFailure("Provider returned no descriptor"))
            StorageResult.Success(
                object : SequentialWriteHandle {
                    private val output = ParcelFileDescriptor.AutoCloseOutputStream(descriptor)
                    override fun write(buffer: ByteArray, offset: Int, length: Int) = output.write(buffer, offset, length)
                    override fun flush() = output.flush()
                    override fun close() = output.close()
                },
            )
        } catch (error: SecurityException) {
            StorageResult.Failure(StorageError.PermissionDenied)
        } catch (error: FileNotFoundException) {
            StorageResult.Failure(StorageError.NotFound)
        } catch (error: UnsupportedOperationException) {
            StorageResult.Failure(StorageError.Unsupported)
        } catch (error: IOException) {
            StorageResult.Failure(StorageError.IoFailure(error.message))
        } catch (error: IllegalArgumentException) {
            StorageResult.Failure(StorageError.StaleReference)
        }
    }

    override suspend fun finalizeOperationPartial(
        partial: com.omnifile.operations.DurableLocator,
        destinationParent: com.omnifile.operations.DurableLocator,
        intendedFinalName: String,
        operationId: String?,
    ): StorageResult<FinalizationResult> {
        val partialParts = decodeLocator(partial) ?: return StorageResult.Failure(StorageError.StaleReference)
        val parentParts = decodeLocator(destinationParent) ?: return StorageResult.Failure(StorageError.StaleReference)
        if (!isValidSingleComponent(intendedFinalName) || partialParts.treeUri != parentParts.treeUri) {
            return StorageResult.Failure(StorageError.StaleReference)
        }
        val partialEntry = when (val result = queryEntry(documentUri(partialParts.documentId), null, partialParts.documentId)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return result
        }
        val parentEntry = when (val result = queryEntry(documentUri(parentParts.documentId), null, parentParts.documentId)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return result
        }
        if (partialEntry.kind != EntryKind.FILE || !isOperationPartial(partialEntry.displayName, operationId) ||
            parentEntry.kind != EntryKind.DIRECTORY
        ) {
            return StorageResult.Failure(StorageError.StaleReference)
        }
        if (StorageCapability.CREATE_CHILD !in parentEntry.capabilities) {
            return StorageResult.Success(FinalizationResult.Unsupported)
        }
        if (StorageCapability.RENAME !in partialEntry.capabilities) {
            return StorageResult.Success(FinalizationResult.Unsupported)
        }
        val children = when (val result = listChildren(parentEntry.ref)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return result
        }
        if (children.none { it.ref == partialEntry.ref }) {
            return StorageResult.Failure(StorageError.StaleReference)
        }
        if (children.any { it.displayName == intendedFinalName }) {
            return StorageResult.Failure(StorageError.NameConflict(intendedFinalName))
        }

        var attempted = false
        return try {
            attempted = true
            val returnedUri = DocumentsContract.renameDocument(
                contentResolver,
                documentUri(partialParts.documentId),
                intendedFinalName,
            ) ?: return StorageResult.Success(FinalizationResult.Ambiguous)
            val returnedDocumentId = canonicalDocumentId(returnedUri)
                ?: return StorageResult.Success(FinalizationResult.Ambiguous)
            when (val result = queryEntry(documentUri(returnedDocumentId), parentEntry.ref, returnedDocumentId)) {
                is StorageResult.Success -> when (val children = listChildren(parentEntry.ref)) {
                    is StorageResult.Success -> if (children.value.any { it.ref == result.value.ref }) {
                        StorageResult.Success(FinalizationResult.Finalized(locatorFor(returnedDocumentId)))
                    } else {
                        StorageResult.Success(FinalizationResult.Ambiguous)
                    }
                    is StorageResult.Failure -> StorageResult.Success(FinalizationResult.Ambiguous)
                }
                is StorageResult.Failure -> StorageResult.Success(FinalizationResult.Ambiguous)
            }
        } catch (error: OperationCanceledException) {
            if (attempted) StorageResult.Success(FinalizationResult.Ambiguous) else StorageResult.Failure(StorageError.Cancelled)
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            if (attempted) StorageResult.Success(FinalizationResult.Ambiguous) else StorageResult.Failure(StorageError.PermissionDenied)
        } catch (error: FileNotFoundException) {
            if (attempted) StorageResult.Success(FinalizationResult.Ambiguous) else StorageResult.Failure(StorageError.NotFound)
        } catch (error: UnsupportedOperationException) {
            if (attempted) StorageResult.Success(FinalizationResult.Ambiguous) else StorageResult.Success(FinalizationResult.Unsupported)
        } catch (error: IllegalArgumentException) {
            if (attempted) StorageResult.Success(FinalizationResult.Ambiguous) else StorageResult.Failure(StorageError.StaleReference)
        } catch (error: IOException) {
            if (attempted) StorageResult.Success(FinalizationResult.Ambiguous) else StorageResult.Failure(StorageError.IoFailure(error.message))
        } catch (error: IllegalStateException) {
            if (attempted) StorageResult.Success(FinalizationResult.Ambiguous) else StorageResult.Failure(StorageError.IoFailure(error.message))
        }
    }

    override suspend fun deleteDurableSource(source: com.omnifile.operations.DurableLocator): StorageResult<Unit> {
        val parts = decodeLocator(source) ?: return StorageResult.Failure(StorageError.StaleReference)
        val entry = when (val result = queryEntry(documentUri(parts.documentId), null, parts.documentId)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return result
        }
        if (entry.kind != EntryKind.FILE || StorageCapability.DELETE !in entry.capabilities) {
            return StorageResult.Failure(StorageError.Unsupported)
        }
        return try {
            if (DocumentsContract.deleteDocument(contentResolver, documentUri(parts.documentId))) {
                StorageResult.Success(Unit)
            } else {
                StorageResult.Failure(StorageError.IoFailure("Provider declined source delete"))
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
            StorageResult.Failure(StorageError.AmbiguousSourceDeletion)
        }
    }

    override suspend fun deleteOperationPartial(
        partial: com.omnifile.operations.DurableLocator,
        operationId: String?,
    ): StorageResult<Unit> {
        val parts = decodeLocator(partial) ?: return StorageResult.Failure(StorageError.StaleReference)
        val entry = when (val result = queryEntry(documentUri(parts.documentId), null, parts.documentId)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return result
        }
        if (entry.kind != EntryKind.FILE || !isOperationPartial(entry.displayName, operationId)) {
            return StorageResult.Failure(StorageError.StaleReference)
        }
        return try {
            if (StorageCapability.DELETE !in entry.capabilities) {
                StorageResult.Failure(StorageError.Unsupported)
            } else if (DocumentsContract.deleteDocument(contentResolver, documentUri(parts.documentId))) {
                StorageResult.Success(Unit)
            } else {
                StorageResult.Failure(StorageError.IoFailure("Provider declined partial delete"))
            }
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
            StorageResult.Failure(StorageError.AmbiguousSourceDeletion)
        }
    }

    suspend fun openSequentialWrite(
        partial: com.omnifile.operations.DurableLocator,
        append: Boolean,
    ): StorageResult<SequentialWriteHandle> = openSequentialWrite(partial, append, null)

    suspend fun finalizeOperationPartial(
        partial: com.omnifile.operations.DurableLocator,
        destinationParent: com.omnifile.operations.DurableLocator,
        intendedFinalName: String,
    ): StorageResult<FinalizationResult> = finalizeOperationPartial(partial, destinationParent, intendedFinalName, null)

    suspend fun deleteOperationPartial(
        partial: com.omnifile.operations.DurableLocator,
    ): StorageResult<Unit> = deleteOperationPartial(partial, null)

    private fun queryEntry(documentId: String): StorageResult<StorageEntry> =
        queryEntry(documentUri(documentId), null, documentId)

    private fun queryEntry(
        uri: Uri,
        parentRef: EntryRef?,
        authoritativeDocumentId: String? = null,
    ): StorageResult<StorageEntry> = try {
        var entry: StorageEntry? = null
        query(uri) { cursor ->
            if (cursor.moveToFirst()) entry = cursor.toEntry(parentRef, authoritativeDocumentId)
        }
        entry?.let { StorageResult.Success(it) } ?: StorageResult.Failure(StorageError.NotFound)
    } catch (error: OperationCanceledException) {
        StorageResult.Failure(StorageError.Cancelled)
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
    } catch (_: StaleReferenceException) {
        StorageResult.Failure(StorageError.StaleReference)
    } catch (error: IllegalStateException) {
        StorageResult.Failure(StorageError.IoFailure(error.message))
    }

    private fun checkedRef(ref: EntryRef): SafEntryRef? {
        val safRef = ref as? SafEntryRef ?: return null
        return safRef.takeIf {
            it.providerId == id && it.treeUri == treeUri && isWithinSelectedTree(it.documentId)
        }
    }

    private fun decodeLocator(locator: com.omnifile.operations.DurableLocator): SafLocatorParts? {
        if (locator.providerId != id || locator.encoding != SafDurableLocatorCodec.ENCODING) return null
        val parts = SafDurableLocatorCodec.decode(locator.value) ?: return null
        if (parts.treeUri != treeUri.toString() || !isWithinSelectedTree(parts.documentId)) return null
        return parts
    }

    private fun locatorFor(documentId: String): com.omnifile.operations.DurableLocator =
        com.omnifile.operations.DurableLocator(
            providerId = id,
            encoding = SafDurableLocatorCodec.ENCODING,
            value = SafDurableLocatorCodec.encode(treeUri.toString(), documentId),
        )

    private fun documentUri(documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    private fun canonicalDocumentId(uri: Uri): String? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority != treeUri.authority) return null
        return try {
            val documentId = DocumentsContract.getDocumentId(uri)
            documentId.takeIf(::isWithinSelectedTree)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun isWithinSelectedTree(documentId: String): Boolean = try {
        documentId == rootDocumentId || DocumentsContract.isChildDocument(
            contentResolver,
            documentUri(rootDocumentId),
            documentUri(documentId),
        )
    } catch (_: Exception) {
        false
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
        if (!isWithinSelectedTree(documentId)) throw StaleReferenceException
        val displayName = getString(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) ?: "Unnamed"
        val mimeType = getString(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
        val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
        val flags = getLong(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS)).toInt()
        val capabilities = buildSet {
            if (isDirectory) {
                add(StorageCapability.LIST_CHILDREN)
                if (hasWriteGrant && finalizationProven && flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0) {
                    add(StorageCapability.CREATE_CHILD)
                }
                if (hasWriteGrant && finalizationProven) add(StorageCapability.WRITE)
            } else if (hasReadGrant) {
                add(StorageCapability.READ_SEQUENTIAL)
            }
            if (hasWriteGrant && flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0) {
                add(StorageCapability.WRITE)
            }
            if (documentId != rootDocumentId && hasWriteGrant) {
                if (flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0) add(StorageCapability.RENAME)
                if (flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0) add(StorageCapability.DELETE)
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

    private fun isOperationPartial(name: String, operationId: String? = null): Boolean {
        if (!name.startsWith(".omnifile-") || !name.endsWith(".partial")) return false
        if (operationId == null) return true
        val safeOperationId = operationId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return name == ".omnifile-$safeOperationId.partial"
    }

    private fun isValidSingleComponent(name: String): Boolean =
        name.isNotEmpty() && name != "." && name != ".." && '\u0000' !in name && '/' !in name && '\\' !in name

    private data class SafEntryRef(
        override val providerId: ProviderId,
        val treeUri: Uri,
        val documentId: String,
    ) : EntryRef {
        override val identityKey: String = "${providerId.value}\u0000$treeUri\u0000$documentId"
    }

    companion object {
        fun providerIdFor(treeUri: Uri): ProviderId {
            val bytes = MessageDigest.getInstance("SHA-256").digest(treeUri.toString().toByteArray(Charsets.UTF_8))
            val suffix = bytes.take(12).joinToString("") { "%02x".format(it) }
            return ProviderId("saf-tree-$suffix")
        }

        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
    }

    private object StaleReferenceException : RuntimeException()
}
