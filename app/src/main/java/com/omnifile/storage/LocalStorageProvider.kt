package com.omnifile.storage

import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

class LocalStorageProvider(
    rootDirectory: Path,
    override val id: ProviderId,
) : StorageProvider {
    private val rootDirectory = rootDirectory.toAbsolutePath().normalize()

    override suspend fun root(): StorageResult<StorageEntry> = guarded {
        entryFor(rootDirectory)
    }

    override suspend fun listChildren(directory: EntryRef): StorageResult<List<StorageEntry>> = guarded {
        val localRef = checkedRef(directory)
        if (!Files.isDirectory(localRef.path, LinkOption.NOFOLLOW_LINKS)) {
            throw UnsupportedOperationException("Only directories can list children")
        }

        Files.list(localRef.path).use { children ->
            children.iterator().asSequence()
                .map { entryFor(it, localRef) }
                .sortedBy { it.displayName }
                .toList()
        }
    }

    override suspend fun rename(
        entry: StorageEntry,
        requestedName: String,
    ): StorageResult<StorageEntry> {
        if (!isValidSingleComponent(requestedName)) {
            return StorageResult.Failure(StorageError.InvalidName(requestedName, ""))
        }

        return guarded {
            val sourceRef = checkedRef(entry.ref)
            val source = sourceRef.path
            if (source == rootDirectory) throw UnsupportedOperationException("Cannot rename Local root")

            val parent = source.parent
                ?: throw UnsupportedOperationException("Cannot rename entry without a parent")
            val target = parent.resolve(requestedName).normalize()
            if (target.parent != parent || !isWithinRoot(target)) {
                throw InvalidNameException(requestedName)
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw NameConflictException(requestedName)
            }

            try {
                Files.move(source, target)
            } catch (_: FileAlreadyExistsException) {
                throw NameConflictException(requestedName)
            }
            entryFor(target, LocalEntryRef(id, parent))
        }
    }

    override suspend fun delete(entry: StorageEntry): StorageResult<Unit> = guarded {
        val localRef = checkedRef(entry.ref)
        val path = localRef.path
        if (path == rootDirectory) throw UnsupportedOperationException("Cannot delete Local root")
        val attributes = Files.readAttributes(
            path,
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS,
        )
        when {
            attributes.isDirectory -> deleteEmptyDirectory(path)
            attributes.isRegularFile || attributes.isSymbolicLink -> Files.delete(path)
            else -> throw UnsupportedOperationException("Unsupported Local entry type")
        }
    }

    private fun entryFor(path: Path, parentRef: EntryRef? = null): StorageEntry {
        val normalized = path.toAbsolutePath().normalize()
        if (!isWithinRoot(normalized)) throw StaleReferenceException
        requireNoSymlinkAncestors(normalized)
        if (Files.notExists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw NoSuchFileException(normalized.toString())
        }

        val attributes = Files.readAttributes(
            normalized,
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS,
        )
        val directory = attributes.isDirectory
        val symbolicLink = attributes.isSymbolicLink
        val kind = if (directory) EntryKind.DIRECTORY else EntryKind.FILE
        val capabilities = buildSet {
            if (directory) {
                add(StorageCapability.LIST_CHILDREN)
            } else if (attributes.isRegularFile) {
                add(StorageCapability.READ_SEQUENTIAL)
                add(StorageCapability.READ_SEEKABLE)
            }
            if (normalized != rootDirectory) {
                add(StorageCapability.RENAME)
                if (attributes.isRegularFile || symbolicLink || (directory && isEmptyDirectory(normalized))) {
                    add(StorageCapability.DELETE)
                }
            }
        }
        val displayName = normalized.fileName?.toString() ?: "Local files"
        return StorageEntry(
            ref = LocalEntryRef(id, normalized),
            displayName = displayName,
            kind = kind,
            sizeBytes = if (attributes.isRegularFile) attributes.size() else null,
            modifiedAtEpochMillis = attributes.lastModifiedTime().toMillis(),
            mimeType = if (symbolicLink) null else readOptional { Files.probeContentType(normalized) },
            capabilities = capabilities,
            parentRef = parentRef,
        )
    }

    private fun checkedRef(ref: EntryRef): LocalEntryRef {
        val localRef = ref as? LocalEntryRef ?: throw StaleReferenceException
        if (localRef.providerId != id || !isWithinRoot(localRef.path)) {
            throw StaleReferenceException
        }
        requireNoSymlinkAncestors(localRef.path)
        if (Files.notExists(localRef.path, LinkOption.NOFOLLOW_LINKS)) {
            throw NoSuchFileException(localRef.path.toString())
        }
        return localRef
    }

    private fun requireNoSymlinkAncestors(path: Path) {
        if (path == rootDirectory) return
        val parent = path.parent ?: throw StaleReferenceException
        if (!isWithinRoot(parent)) throw StaleReferenceException

        var current = rootDirectory
        for (component in rootDirectory.relativize(parent)) {
            current = current.resolve(component)
            val attributes = Files.readAttributes(
                current,
                BasicFileAttributes::class.java,
                LinkOption.NOFOLLOW_LINKS,
            )
            if (!attributes.isDirectory || attributes.isSymbolicLink) {
                throw StaleReferenceException
            }
        }
    }

    private fun isValidSingleComponent(requestedName: String): Boolean {
        if (requestedName.isEmpty() || requestedName == "." || requestedName == "..") return false
        if ('\u0000' in requestedName || '/' in requestedName || '\\' in requestedName) return false
        return try {
            val path = rootDirectory.fileSystem.getPath(requestedName)
            !path.isAbsolute && path.nameCount == 1 && path.fileName.toString() == requestedName
        } catch (_: InvalidPathException) {
            false
        }
    }

    private fun deleteEmptyDirectory(path: Path) {
        try {
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    directory: Path,
                    attributes: BasicFileAttributes,
                ): FileVisitResult {
                    if (directory != path || !isWithinRoot(directory)) {
                        throw UnsupportedOperationException("Recursive directory deletion is unsupported")
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(
                    file: Path,
                    attributes: BasicFileAttributes,
                ): FileVisitResult = throw UnsupportedOperationException(
                    "Recursive directory deletion is unsupported",
                )

                override fun postVisitDirectory(
                    directory: Path,
                    error: IOException?,
                ): FileVisitResult {
                    if (error != null) throw error
                    if (directory != path || !isWithinRoot(directory)) {
                        throw UnsupportedOperationException("Recursive directory deletion is unsupported")
                    }
                    Files.delete(directory)
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (_: DirectoryNotEmptyException) {
            throw UnsupportedOperationException("Recursive directory deletion is unsupported")
        }
    }

    private fun isEmptyDirectory(path: Path): Boolean = try {
        Files.newDirectoryStream(path).use { directory -> !directory.iterator().hasNext() }
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
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
    } catch (error: InvalidNameException) {
        StorageResult.Failure(StorageError.InvalidName(error.requestedName, ""))
    } catch (error: NameConflictException) {
        StorageResult.Failure(StorageError.NameConflict(error.requestedName))
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

    private class InvalidNameException(val requestedName: String) : RuntimeException()

    private class NameConflictException(val requestedName: String) : RuntimeException()

    private object StaleReferenceException : RuntimeException()
}
