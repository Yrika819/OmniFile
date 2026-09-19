package com.omnifile.storage

import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.isDirectory

class LocalStorageProvider(
    rootDirectory: Path,
    override val id: ProviderId,
) : StorageProvider {
    private val rootDirectory = rootDirectory.toAbsolutePath().normalize()

    override suspend fun root(): StorageResult<StorageEntry> = guarded {
        entryFor(rootDirectory)
    }

    override suspend fun listChildren(directory: EntryRef): StorageResult<List<StorageEntry>> = guarded {
        val localRef = directory as? LocalEntryRef
            ?: throw StaleReferenceException
        if (localRef.providerId != id || !isWithinRoot(localRef.path)) {
            throw StaleReferenceException
        }
        if (Files.notExists(localRef.path)) {
            throw NoSuchFileException(localRef.path.toString())
        }
        if (!localRef.path.isDirectory()) {
            throw UnsupportedOperationException("Only directories can list children")
        }

        Files.list(localRef.path).use { children ->
            children.iterator().asSequence()
                .map { entryFor(it, localRef) }
                .sortedBy { it.displayName }
                .toList()
        }
    }

    private fun entryFor(path: Path, parentRef: EntryRef? = null): StorageEntry {
        val normalized = path.toAbsolutePath().normalize()
        if (!isWithinRoot(normalized)) throw StaleReferenceException
        if (Files.notExists(normalized)) throw NoSuchFileException(normalized.toString())

        val directory = Files.isDirectory(normalized)
        val kind = if (directory) EntryKind.DIRECTORY else EntryKind.FILE
        val capabilities = if (directory) {
            setOf(StorageCapability.LIST_CHILDREN)
        } else {
            setOf(StorageCapability.READ_SEQUENTIAL, StorageCapability.READ_SEEKABLE)
        }
        val displayName = normalized.fileName?.toString() ?: "Local files"
        return StorageEntry(
            ref = LocalEntryRef(id, normalized),
            displayName = displayName,
            kind = kind,
            sizeBytes = if (directory) null else readOptional { Files.size(normalized) },
            modifiedAtEpochMillis = readOptional { Files.getLastModifiedTime(normalized).toMillis() },
            mimeType = readOptional { Files.probeContentType(normalized) },
            capabilities = capabilities,
            parentRef = parentRef,
        )
    }

    private fun isWithinRoot(path: Path): Boolean = path == rootDirectory || path.startsWith(rootDirectory)

    private fun <T> readOptional(block: () -> T): T? = try {
        block()
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun <T> guarded(block: () -> T): StorageResult<T> = try {
        StorageResult.Success(block())
    } catch (_: StaleReferenceException) {
        StorageResult.Failure(StorageError.StaleReference)
    } catch (_: NoSuchFileException) {
        StorageResult.Failure(StorageError.NotFound)
    } catch (_: FileNotFoundException) {
        StorageResult.Failure(StorageError.NotFound)
    } catch (_: AccessDeniedException) {
        StorageResult.Failure(StorageError.PermissionDenied)
    } catch (_: SecurityException) {
        StorageResult.Failure(StorageError.PermissionDenied)
    } catch (_: UnsupportedOperationException) {
        StorageResult.Failure(StorageError.Unsupported)
    } catch (error: IOException) {
        StorageResult.Failure(StorageError.IoFailure(error.message))
    }

    private data class LocalEntryRef(
        override val providerId: ProviderId,
        val path: Path,
    ) : EntryRef

    private object StaleReferenceException : RuntimeException()
}
