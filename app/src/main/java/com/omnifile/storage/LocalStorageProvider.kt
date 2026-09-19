package com.omnifile.storage

import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.nio.file.SecureDirectoryStream
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes

class LocalStorageProvider(
    rootDirectory: Path,
    override val id: ProviderId,
) : StorageProvider {
    private val rootDirectory = rootDirectory.toAbsolutePath().normalize()
    private val filesystemRoot = rootDirectory.root
        ?: throw IllegalArgumentException("Local root must be absolute")
    // Stable pre-existing ancestors are canonicalized once; mutations still traverse this path
    // from the filesystem-root descriptor with NOFOLLOW_LINKS, and validation rejects changes.
    private val secureRootDirectory = try {
        this.rootDirectory.toRealPath()
    } catch (_: NoSuchFileException) {
        this.rootDirectory
    }

    override suspend fun root(): StorageResult<StorageEntry> = guarded {
        validateConfiguredRoot()
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
    ): StorageResult<StorageEntry> = StorageResult.Failure(StorageError.Unsupported)

    override suspend fun delete(entry: StorageEntry): StorageResult<Unit> = guarded {
        val localRef = checkedRef(entry.ref)
        val path = localRef.path
        if (path == rootDirectory) throw UnsupportedOperationException("Cannot delete Local root")
        withSecureParent(path) { parent, name ->
            val attributes = childAttributes(parent, name)
            when {
                attributes.isRegularFile || attributes.isSymbolicLink -> parent.deleteFile(name)
                attributes.isDirectory -> deleteEmptyDirectory(parent, name)
                else -> throw UnsupportedOperationException("Unsupported Local entry type")
            }
        }
    }

    private fun entryFor(path: Path, parentRef: EntryRef? = null): StorageEntry {
        val normalized = path.toAbsolutePath().normalize()
        if (!isWithinRoot(normalized)) throw StaleReferenceException
        validateConfiguredRoot()
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
                if (canDelete(normalized, attributes)) {
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
        validateConfiguredRoot()
        requireNoSymlinkAncestors(localRef.path)
        if (Files.notExists(localRef.path, LinkOption.NOFOLLOW_LINKS)) {
            throw NoSuchFileException(localRef.path.toString())
        }
        return localRef
    }

    private fun validateConfiguredRoot() {
        val rootAttributes = Files.readAttributes(
            rootDirectory,
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS,
        )
        if (rootAttributes.isSymbolicLink || !rootAttributes.isDirectory) {
            throw UnsupportedOperationException("Configured Local root is not a real directory")
        }
        if (rootDirectory.toRealPath() != secureRootDirectory) {
            throw UnsupportedOperationException("Configured Local root changed")
        }
        withSecureParent(rootDirectory) { parent, name ->
            val attributes = childAttributes(parent, name)
            if (attributes.isSymbolicLink || !attributes.isDirectory) {
                throw UnsupportedOperationException("Configured Local root is not a real directory")
            }
        }
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

    private fun <T> withSecureParent(
        path: Path,
        block: (SecureDirectoryStream<Path>, Path) -> T,
    ): T {
        if (!isWithinRoot(path)) throw StaleReferenceException
        val securePath = secureRootDirectory.resolve(rootDirectory.relativize(path))
        val relative = filesystemRoot.relativize(securePath)
        if (relative.nameCount == 0) throw UnsupportedOperationException("Cannot mutate Local root")

        val streams = mutableListOf<SecureDirectoryStream<Path>>()
        var current = openSecureFilesystemRoot()
        streams += current
        try {
            for (index in 0 until relative.nameCount - 1) {
                current = current.newDirectoryStream(
                    relative.getName(index),
                    LinkOption.NOFOLLOW_LINKS,
                )
                streams += current
            }
            return block(current, relative.getName(relative.nameCount - 1))
        } finally {
            streams.asReversed().forEach { it.close() }
        }
    }

    private fun openSecureFilesystemRoot(): SecureDirectoryStream<Path> {
        val stream = Files.newDirectoryStream(filesystemRoot)
        return stream as? SecureDirectoryStream<Path>
            ?: run {
                stream.close()
                throw UnsupportedOperationException("Secure directory operations unavailable")
            }
    }

    private fun childAttributes(
        parent: SecureDirectoryStream<Path>,
        name: Path,
    ): BasicFileAttributes {
        val view = parent.getFileAttributeView(
            name,
            BasicFileAttributeView::class.java,
            LinkOption.NOFOLLOW_LINKS,
        ) ?: throw UnsupportedOperationException("Secure file attributes unavailable")
        return view.readAttributes()
    }

    private fun deleteEmptyDirectory(parent: SecureDirectoryStream<Path>, name: Path) {
        val child = parent.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)
        try {
            if (child.iterator().hasNext()) {
                throw UnsupportedOperationException("Recursive directory deletion is unsupported")
            }
        } finally {
            child.close()
        }
        try {
            parent.deleteDirectory(name)
        } catch (_: DirectoryNotEmptyException) {
            throw UnsupportedOperationException("Recursive directory deletion is unsupported")
        }
    }

    private fun canDelete(path: Path, attributes: BasicFileAttributes): Boolean = try {
        if (!attributes.isRegularFile && !attributes.isSymbolicLink && !attributes.isDirectory) {
            false
        } else {
            withSecureParent(path) { parent, name ->
                val current = childAttributes(parent, name)
                when {
                    current.isRegularFile || current.isSymbolicLink -> true
                    current.isDirectory -> {
                        val child = parent.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)
                        try {
                            !child.iterator().hasNext()
                        } finally {
                            child.close()
                        }
                    }
                    else -> false
                }
            }
        }
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: UnsupportedOperationException) {
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
    } catch (_: NotDirectoryException) {
        StorageResult.Failure(StorageError.Unsupported)
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
