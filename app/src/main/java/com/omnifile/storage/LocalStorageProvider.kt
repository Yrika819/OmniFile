package com.omnifile.storage

import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SecureDirectoryStream
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes

class LocalStorageProvider(
    rootDirectory: Path,
    override val id: ProviderId,
) : StorageTransferProvider, PlaybackSourceProvider, com.omnifile.preview.PreviewSourceProvider {
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
    private val secureAnchorDirectory = secureRootDirectory.parent ?: filesystemRoot

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

    /** Archive extraction uses these provider-owned, single-component operations. */
    suspend fun child(parent: EntryRef, name: String): StorageResult<StorageEntry> = guarded {
        val localParent = checkedRef(parent)
        if (!Files.isDirectory(localParent.path, LinkOption.NOFOLLOW_LINKS) || !isValidSingleComponent(name)) {
            throw NotDirectoryException(localParent.path.toString())
        }
        val child = localParent.path.resolve(name)
        if (!isWithinRoot(child) || Files.notExists(child, LinkOption.NOFOLLOW_LINKS)) {
            throw NoSuchFileException(child.toString())
        }
        entryFor(child, localParent)
    }

    suspend fun createDirectory(parent: EntryRef, name: String): StorageResult<StorageEntry> = guarded {
        val localParent = checkedRef(parent)
        if (!Files.isDirectory(localParent.path, LinkOption.NOFOLLOW_LINKS) || !isValidSingleComponent(name)) {
            throw NotDirectoryException(localParent.path.toString())
        }
        val child = localParent.path.resolve(name)
        if (!isWithinRoot(child)) throw StaleReferenceException
        withSecureParent(child) { secureParent, childName ->
            try {
                childAttributes(secureParent, childName)
                throw FileAlreadyExistsException(name)
            } catch (_: NoSuchFileException) {
                // The parent is descriptor-validated; the final component is created
                // only after the no-follow existence check.
                Files.createDirectory(child)
            }
        }
        entryFor(child, localParent)
    }


    override suspend fun openPreviewSource(entry: StorageEntry): StorageResult<com.omnifile.preview.PreviewSource> {
        if (entry.ref.providerId != id || entry.kind != EntryKind.FILE ||
            StorageCapability.READ_SEQUENTIAL !in entry.capabilities
        ) return StorageResult.Failure(StorageError.Unsupported)
        val locator = when (val result = ReadAcquisitionScope.current()?.operation { encodeDurableLocator(entry.ref) } ?: encodeDurableLocator(entry.ref)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return result
        }
        return StorageResult.Success(
            com.omnifile.preview.PreviewSource(
                identity = entry.ref,
                sourceLabel = "Local storage",
                mimeType = entry.mimeType,
                sizeBytes = entry.sizeBytes,
                capabilities = com.omnifile.preview.PreviewCapabilities(
                    sequentialReadable = true,
                    canReopen = true,
                    canStagePdf = true,
                ),
            ) { openSequentialRead(locator) },
        )
    }

    override suspend fun resolvePlaybackSource(entry: StorageEntry): StorageResult<PlaybackSource> = guarded {
        val localRef = checkedRef(entry.ref)
        if (entry.kind != EntryKind.FILE) {
            throw UnsupportedOperationException("Only regular files are playable")
        }
        val attributes = Files.readAttributes(
            localRef.path,
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS,
        )
        if (attributes.isSymbolicLink || !attributes.isRegularFile) {
            throw UnsupportedOperationException("Only regular Local files are playable")
        }
        if (!Files.isReadable(localRef.path)) {
            throw AccessDeniedException(localRef.path.toString())
        }
        PlaybackSource(
            transportUri = localRef.path.toUri().toString(),
            seekSupport = SeekSupport.SEEKABLE,
            sourceLabel = "Local storage",
        )
    }

    override val transferCapabilities: Set<TransferCapability> = setOf(
        TransferCapability.READ_SEQUENTIAL,
        TransferCapability.CREATE_CHILD,
        TransferCapability.WRITE_SEQUENTIAL,
        TransferCapability.FINALIZE,
        TransferCapability.DELETE,
        TransferCapability.MOVE_SOURCE,
    )

    override suspend fun encodeDurableLocator(ref: EntryRef): StorageResult<com.omnifile.operations.DurableLocator> = guarded {
        val localRef = checkedRef(ref)
        locatorFor(localRef.path)
    }

    override suspend fun resolveDurableLocator(
        locator: com.omnifile.operations.DurableLocator,
    ): StorageResult<StorageEntry> = guarded {
        entryFor(resolveDurablePath(locator))
    }

    override suspend fun inspectTransfer(
        locator: com.omnifile.operations.DurableLocator,
    ): StorageResult<TransferFileFacts> = guarded {
        val path = resolveDurablePath(locator)
        val attributes = Files.readAttributes(
            path,
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS,
        )
        if (attributes.isSymbolicLink || (!attributes.isRegularFile && !attributes.isDirectory)) {
            throw UnsupportedOperationException("Unsupported Local transfer entry")
        }
        TransferFileFacts(
            locator = locatorFor(path),
            kind = if (attributes.isDirectory) EntryKind.DIRECTORY else EntryKind.FILE,
            sizeBytes = if (attributes.isRegularFile) attributes.size() else null,
            versionToken = "${attributes.size()}:${attributes.lastModifiedTime().toMillis()}:${attributes.fileKey()}",
        )
    }

    override suspend fun openSequentialRead(
        locator: com.omnifile.operations.DurableLocator,
    ): StorageResult<SequentialReadHandle> {
        val scope = ReadAcquisitionScope.current()
        if (scope == null) return guarded {
            val path = resolveDurablePath(locator)
            val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            if (!attributes.isRegularFile || attributes.isSymbolicLink) {
                throw UnsupportedOperationException("Only regular Local files are transferable")
            }
            LocalReadHandle(
                input = Files.newInputStream(path, StandardOpenOption.READ),
                expectedBytes = attributes.size(),
            )
        }

        return try {
            val path = scope.blockingOperation { resolveDurablePath(locator) }
            val attributes = scope.blockingOperation {
                Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            }
            if (!attributes.isRegularFile || attributes.isSymbolicLink) {
                throw UnsupportedOperationException("Only regular Local files are transferable")
            }
            val input = scope.blockingOperation(dispose = { stream: InputStream -> stream.close() }) {
                Files.newInputStream(path, StandardOpenOption.READ)
            }
            // Keep the raw stream leased until both handle and result packaging succeed.
            scope.own(input) { it.close() }.adopt { stream ->
                StorageResult.Success(LocalReadHandle(stream, attributes.size()))
            }
        } catch (error: Exception) {
            guarded { throw error }
        }
    }

    override suspend fun createOperationPartial(
        destinationParent: com.omnifile.operations.DurableLocator,
        intendedFinalName: String,
        operationId: String,
    ): StorageResult<com.omnifile.operations.DurableLocator> = guarded {
        val parent = resolveDurablePath(destinationParent)
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw NotDirectoryException(parent.toString())
        }
        if (!isValidSingleComponent(intendedFinalName)) {
            throw IllegalArgumentException("Invalid destination name")
        }
        val safeOperationId = operationId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (safeOperationId.isBlank()) throw IllegalArgumentException("Invalid operation ID")
        val partial = parent.resolve(".omnifile-$safeOperationId.partial")
        if (!isWithinRoot(partial) || Files.exists(partial, LinkOption.NOFOLLOW_LINKS)) {
            throw FileAlreadyExistsException(partial.toString())
        }
        withSecureParent(partial) { secureParent, name ->
            secureParent.newByteChannel(
                name,
                setOf(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
            ).use { }
        }
        locatorFor(partial)
    }

    override suspend fun openSequentialWrite(
        partial: com.omnifile.operations.DurableLocator,
        append: Boolean,
        operationId: String?,
    ): StorageResult<SequentialWriteHandle> = guarded {
        if (append) throw UnsupportedOperationException("Local true resume is not yet proven")
        val path = resolveDurablePath(partial)
        if (!isOperationPartial(path, operationId)) throw StaleReferenceException
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (!attributes.isRegularFile || attributes.isSymbolicLink) {
            throw UnsupportedOperationException("Only operation-owned regular files are writable")
        }
        LocalWriteHandle(
            output = Files.newOutputStream(
                path,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
            ),
        )
    }

    override suspend fun finalizeOperationPartial(
        partial: com.omnifile.operations.DurableLocator,
        destinationParent: com.omnifile.operations.DurableLocator,
        intendedFinalName: String,
        operationId: String?,
    ): StorageResult<FinalizationResult> = guarded {
        if (!isValidSingleComponent(intendedFinalName)) {
            throw IllegalArgumentException("Invalid destination name")
        }
        val partialPath = resolveDurablePath(partial)
        val parent = resolveDurablePath(destinationParent)
        if (partialPath.parent != parent || !isOperationPartial(partialPath, operationId)) {
            throw StaleReferenceException
        }
        val attributes = Files.readAttributes(partialPath, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (!attributes.isRegularFile || attributes.isSymbolicLink) {
            throw UnsupportedOperationException("Only operation-owned regular files can be finalized")
        }
        val finalPath = parent.resolve(intendedFinalName)
        try {
            withSecureParent(partialPath) { secureParent, partialName ->
                val finalName = Paths.get(intendedFinalName)
                try {
                    childAttributes(secureParent, finalName)
                    throw FileAlreadyExistsException(finalPath.toString())
                } catch (_: NoSuchFileException) {
                    // The descriptor-relative check preserves the no-overwrite contract.
                }
                secureParent.move(partialName, secureParent, finalName)
            }
            FinalizationResult.Finalized(locatorFor(finalPath))
        } catch (_: FileAlreadyExistsException) {
            throw FileAlreadyExistsException(intendedFinalName)
        }
    }

    override suspend fun deleteDurableSource(
        source: com.omnifile.operations.DurableLocator,
    ): StorageResult<Unit> = guarded {
        val path = resolveDurablePath(source)
        if (path == rootDirectory) throw UnsupportedOperationException("Cannot delete Local root")
        withSecureParent(path) { parent, name ->
            val attributes = childAttributes(parent, name)
            if (!attributes.isRegularFile || attributes.isSymbolicLink) {
                throw UnsupportedOperationException("Only regular Local files are movable")
            }
            parent.deleteFile(name)
        }
    }

    override suspend fun deleteOperationPartial(
        partial: com.omnifile.operations.DurableLocator,
        operationId: String?,
    ): StorageResult<Unit> = guarded {
        val path = resolveDurablePath(partial)
        if (!isOperationPartial(path, operationId)) throw StaleReferenceException
        withSecureParent(path) { secureParent, name ->
            val attributes = childAttributes(secureParent, name)
            if (!attributes.isRegularFile || attributes.isSymbolicLink) {
                throw StaleReferenceException
            }
            secureParent.deleteFile(name)
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

    private fun locatorFor(path: Path): com.omnifile.operations.DurableLocator {
        val normalized = path.toAbsolutePath().normalize()
        if (!isWithinRoot(normalized)) throw StaleReferenceException
        return com.omnifile.operations.DurableLocator(
            providerId = id,
            encoding = LOCAL_LOCATOR_ENCODING,
            value = rootDirectory.relativize(normalized).toString().ifEmpty { "." },
        )
    }

    private fun resolveDurablePath(locator: com.omnifile.operations.DurableLocator): Path {
        if (locator.providerId != id || locator.encoding != LOCAL_LOCATOR_ENCODING) {
            throw StaleReferenceException
        }
        val relative = Paths.get(locator.value)
        if (relative.isAbsolute || locator.value.isBlank()) throw StaleReferenceException
        val resolved = rootDirectory.resolve(relative).normalize()
        if (!isWithinRoot(resolved)) throw StaleReferenceException
        validateConfiguredRoot()
        requireNoSymlinkAncestors(resolved)
        return resolved
    }

    private fun isOperationPartial(path: Path, operationId: String? = null): Boolean {
        val name = path.fileName?.toString() ?: return false
        if (!name.startsWith(".omnifile-") || !name.endsWith(".partial")) return false
        if (operationId == null) return false
        val safeOperationId = operationId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return name == ".omnifile-$safeOperationId.partial"
    }

    private fun isValidSingleComponent(name: String): Boolean =
        name.isNotEmpty() && name != "." && name != ".." &&
            '\u0000' !in name && '/' !in name && '\\' !in name

    private class LocalReadHandle(
        private val input: InputStream,
        override val expectedBytes: Long,
    ) : SequentialReadHandle {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = input.read(buffer, offset, length)
        override fun close() = input.close()
    }

    private class LocalWriteHandle(
        private val output: OutputStream,
    ) : SequentialWriteHandle {
        override fun write(buffer: ByteArray, offset: Int, length: Int) = output.write(buffer, offset, length)
        override fun flush() = output.flush()
        override fun close() = output.close()
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
                add(StorageCapability.CREATE_CHILD)
                add(StorageCapability.WRITE)
            } else if (attributes.isRegularFile) {
                add(StorageCapability.READ_SEQUENTIAL)
                add(StorageCapability.READ_SEEKABLE)
            }
            if (normalized != rootDirectory) {
                if (canDelete(normalized, attributes)) {
                    add(StorageCapability.DELETE)
                    if (attributes.isRegularFile) add(StorageCapability.MOVE_SOURCE)
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
        // Browsing an app-private root must not require listing an inaccessible
        // filesystem ancestor. Descriptor-relative traversal remains required for
        // destructive mutations and is intentionally kept in withSecureParent().
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
        val relative = secureAnchorDirectory.relativize(securePath)
        if (relative.nameCount == 0) throw UnsupportedOperationException("Cannot mutate Local root")

        val streams = mutableListOf<SecureDirectoryStream<Path>>()
        var current = openSecureDirectory(secureAnchorDirectory)
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

    private fun openSecureDirectory(directory: Path): SecureDirectoryStream<Path> {
        val stream = Files.newDirectoryStream(directory)
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
    } catch (error: FileAlreadyExistsException) {
        StorageResult.Failure(StorageError.NameConflict(error.file ?: ""))
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

    private companion object {
        const val LOCAL_LOCATOR_ENCODING = "root-relative-v1"
    }

    private data class LocalEntryRef(
        override val providerId: ProviderId,
        val path: Path,
    ) : EntryRef {
        override val identityKey: String = "${providerId.value}\u0000$path"
    }

    private object StaleReferenceException : RuntimeException()
}
