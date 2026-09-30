package com.omnifile.preview

import com.omnifile.storage.SequentialReadHandle
import com.omnifile.storage.StorageResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers

sealed interface PdfStageResult {
    data class Ready(val snapshot: PdfSnapshot, internal val acquisitionLease: com.omnifile.storage.ReadLease<PdfSnapshot>? = null) : PdfStageResult
    data class Failure(val error: PreviewError) : PdfStageResult
}

/** One app-owned, random-identity snapshot. It contains no source locator or user filename. */
class PdfSnapshot internal constructor(
    internal val id: String,
    internal val file: File,
    private val store: PdfSnapshotStore,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    val sizeBytes: Long get() = file.length()

    override fun close() {
        if (closed.compareAndSet(false, true)) store.release(this)
    }
}

/** Owns only <noBackupFilesDir>/preview/pdf and its immediate snapshot children. */
class PdfSnapshotStore(
    noBackupFilesDir: File,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val acquisitions: com.omnifile.storage.ReadAcquisitionExecutor = com.omnifile.storage.ReadAcquisitionExecutor.appWide,
) {
    private val noBackupRoot = noBackupFilesDir.canonicalFile
    internal val workspace = File(File(noBackupRoot, "preview"), "pdf")
    private val expectedWorkspace = File(noBackupRoot, "preview/pdf").absoluteFile.normalize()
    private val active = ConcurrentHashMap<String, File>()
    @Volatile private var workspaceAvailable = false

    init {
        workspaceAvailable = runCatching { reconcileAbandoned(); true }.getOrDefault(false)
    }

    /** Deletes only UUID-named regular files directly owned by this workspace. */
    @Synchronized
    fun reconcileAbandoned(): Int {
        ensureWorkspace()
        val canonicalRoot = expectedWorkspace
        var removed = 0
        workspace.listFiles().orEmpty().forEach { child ->
            if (isOwnedArtifact(child, canonicalRoot)) {
                if (Files.deleteIfExists(child.toPath())) removed++
            }
        }
        workspaceAvailable = true
        return removed
    }

    suspend fun stage(request: PreviewRequest): PdfStageResult = stage(request.item, request.source)

    internal suspend fun stage(item: PreviewItem, source: PreviewSource): PdfStageResult {
        val scope = com.omnifile.storage.ReadAcquisitionScope.current()
        if (scope != null) return stageScoped(item, source, scope)
        return try {
            acquisitions.acquire(dispose = { result: PdfStageResult ->
                (result as? PdfStageResult.Ready)?.disposeCandidate()
            }, successful = { it is PdfStageResult.Ready }, transfer = { (it as? PdfStageResult.Ready)?.transferCandidate() }) { stageScoped(item, source, it) }
        } catch (error: com.omnifile.storage.ReadAcquisitionException) {
            PdfStageResult.Failure(acquisitionError(error))
        }
    }

    private suspend fun stageScoped(
        item: PreviewItem,
        source: PreviewSource,
        scope: com.omnifile.storage.ReadAcquisitionScope,
    ): PdfStageResult {
        if (source.identity.providerId != item.identity.providerId ||
            source.identity.identityKey != item.identity.identityKey
        ) return PdfStageResult.Failure(PreviewError.SourceVanished)
        if (!source.capabilities.canStagePdf || !source.capabilities.sequentialReadable) {
            return PdfStageResult.Failure(PreviewError.EncryptedOrUnsupported)
        }
        if (source.sizeBytes != null && source.sizeBytes > PreviewLimits.MAX_PDF_BYTES) {
            return PdfStageResult.Failure(PreviewError.PdfInputTooLarge)
        }
        if (!workspaceAvailable) return PdfStageResult.Failure(PreviewError.StagingFailure)

        val id = UUID.randomUUID().toString()
        val candidate = File(workspace, "$id.candidate")
        var fileLease: com.omnifile.storage.ReadLease<File>? = null
        try {
            // Claim the path before creation; a late creation stays worker-owned until own().
            scope.blockingOperation(dispose = { created: Boolean -> if (created) deleteOwned(candidate) }) {
                if (!isDirectOwnedPath(candidate)) throw IOException("PDF workspace unavailable")
                candidate.createNewFile()
            }.also { if (!it) throw IOException("PDF candidate unavailable") }
            fileLease = scope.own(candidate, afterWorkerReturns = true) { deleteOwned(it) }
            val input = when (val opened = source.open()) {
                is StorageResult.Success -> opened.value
                is StorageResult.Failure -> return PdfStageResult.Failure(previewError(opened.error))
            }
            input.use {
                if (it.expectedBytes != null && it.expectedBytes!! > PreviewLimits.MAX_PDF_BYTES) {
                    return PdfStageResult.Failure(PreviewError.PdfInputTooLarge)
                }
                val output = scope.blockingOperation(dispose = { stream: FileOutputStream -> stream.close() }) {
                    FileOutputStream(candidate, false)
                }
                val outputLease = scope.own(output) { it.close() }
                try {
                    val buffer = ByteArray(32 * 1024)
                    var bytesWritten = 0
                    var noProgressReads = 0
                    while (true) {
                        val count = try { it.read(buffer, 0, minOf(buffer.size, PreviewLimits.MAX_PDF_BYTES - bytesWritten + 1)) }
                        catch (_: IOException) { throw PdfSourceReadFailure() }
                        if (count < 0) break
                        if (count == 0) {
                            if (++noProgressReads > PreviewLimits.MAX_CONSECUTIVE_NO_PROGRESS_READS) throw PdfSourceReadFailure()
                            continue
                        }
                        noProgressReads = 0
                        if (count > PreviewLimits.MAX_PDF_BYTES - bytesWritten) throw PdfInputTooLarge()
                        scope.blockingOperation { output.write(buffer, 0, count) }
                        bytesWritten += count
                    }
                    scope.blockingOperation { output.fd.sync() }
                } finally { outputLease.close() }
            }
            scope.blockingOperation {
                if (!isOwnedArtifact(candidate, workspace.canonicalFile) || candidate.length() > PreviewLimits.MAX_PDF_BYTES) {
                    throw IOException("PDF candidate unavailable")
                }
            }
            // No filesystem operation in the commit decision, and no ready-looking rename.
            val adopted = fileLease.adopt { file ->
                val snapshot = PdfSnapshot(id, file, this)
                val deliveryLease = scope.own(snapshot, afterWorkerReturns = true) { it.close() }
                check(active.putIfAbsent(id, file) == null)
                PdfStageResult.Ready(snapshot, deliveryLease)
            }
            fileLease = null
            return adopted
        } catch (_: PdfInputTooLarge) {
            return PdfStageResult.Failure(PreviewError.PdfInputTooLarge)
        } catch (_: PdfSourceReadFailure) {
            return PdfStageResult.Failure(PreviewError.ProviderUnavailable)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: SecurityException) {
            return PdfStageResult.Failure(PreviewError.PermissionOrGrantMissing)
        } catch (_: IOException) {
            return PdfStageResult.Failure(PreviewError.StagingFailure)
        } finally { fileLease?.close() }
    }

    internal fun release(snapshot: PdfSnapshot) {
        val owned = active[snapshot.id] ?: return
        if (owned == snapshot.file) {
            deleteOwned(snapshot.file)
            active.remove(snapshot.id, snapshot.file)
        }
    }

    private fun deleteOwned(file: File) {
        if (isOwnedArtifact(file, workspace.canonicalFile)) Files.deleteIfExists(file.toPath())
    }

    private fun isOwnedArtifact(file: File, canonicalRoot: File): Boolean {
        if (!isDirectOwnedPath(file) || Files.isSymbolicLink(file.toPath()) ||
            !Files.isRegularFile(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
        ) return false
        return runCatching { file.canonicalFile.parentFile == canonicalRoot }.getOrDefault(false)
    }

    private fun isDirectOwnedPath(file: File): Boolean {
        if (!ARTIFACT_NAME.matches(file.name) || Files.isSymbolicLink(workspace.toPath())) return false
        val directParent = file.toPath().toAbsolutePath().normalize().parent?.toFile()
        if (directParent != expectedWorkspace) return false
        return runCatching { workspace.canonicalFile == expectedWorkspace }.getOrDefault(false)
    }

    private fun ensureWorkspace() {
        val preview = File(noBackupRoot, "preview")
        if (Files.isSymbolicLink(preview.toPath())) throw IOException("PDF preview workspace unavailable")
        if (!preview.exists() && !preview.mkdir() && !preview.isDirectory) {
            throw IOException("PDF preview workspace unavailable")
        }
        if (!preview.isDirectory) throw IOException("PDF preview workspace unavailable")
        if (Files.isSymbolicLink(workspace.toPath())) throw IOException("PDF preview workspace unavailable")
        if (!workspace.exists() && !workspace.mkdir() && !workspace.isDirectory) {
            throw IOException("PDF preview workspace unavailable")
        }
        if (!workspace.isDirectory || workspace.absoluteFile.normalize() != expectedWorkspace ||
            workspace.canonicalFile != expectedWorkspace
        ) throw IOException("PDF preview workspace unavailable")
    }

    private class PdfInputTooLarge : IOException()
    private class PdfSourceReadFailure : IOException()

    private companion object {
        val ARTIFACT_NAME = Regex("[0-9a-fA-F-]{36}\\.(partial|ready|candidate)")

    }
}


internal fun PdfStageResult.Ready.disposeCandidate() {
    acquisitionLease?.close() ?: snapshot.close()
}

internal fun PdfStageResult.Ready.transferCandidate() {
    acquisitionLease?.transfer()
}
