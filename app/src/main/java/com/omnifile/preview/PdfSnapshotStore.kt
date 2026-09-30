package com.omnifile.preview

import com.omnifile.storage.SequentialReadHandle
import com.omnifile.storage.StorageResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface PdfStageResult {
    data class Ready(val snapshot: PdfSnapshot) : PdfStageResult
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
    private val clockNanos: () -> Long = System::nanoTime,
    private val stageIdleTimeoutMillis: Long = PreviewLimits.PDF_STAGE_IDLE_TIMEOUT_MILLIS,
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
        val partial = File(workspace, "$id.partial")
        var ready: File? = null
        var handle: SequentialReadHandle? = null
        var closeHandle: (() -> Unit)? = null
        try {
            if (!isDirectOwnedPath(partial) || !partial.createNewFile()) {
                return PdfStageResult.Failure(PreviewError.StagingFailure)
            }
            handle = when (val opened = source.open()) {
                is StorageResult.Success -> opened.value
                is StorageResult.Failure -> return PdfStageResult.Failure(previewError(opened.error))
            }
            val expected = handle.expectedBytes
            if (expected != null && expected > PreviewLimits.MAX_PDF_BYTES) {
                return PdfStageResult.Failure(PreviewError.PdfInputTooLarge)
            }

            val input = handle ?: return PdfStageResult.Failure(PreviewError.StagingFailure)
            val inputClosed = AtomicBoolean(false)
            fun closeInput() {
                if (inputClosed.compareAndSet(false, true)) input.close()
            }
            closeHandle = ::closeInput
            try {
                FileOutputStream(partial, false).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var noProgressReads = 0
                    var lastProgressAt = clockNanos()
                    var bytesWritten = 0
                    while (true) {
                        if (clockNanos() - lastProgressAt >= stageIdleTimeoutMillis * 1_000_000L) {
                            throw PdfStageTimeout()
                        }
                        val requestLength = minOf(buffer.size, PreviewLimits.MAX_PDF_BYTES - bytesWritten + 1)
                        val timedOut = AtomicBoolean(false)
                        val deadline = READ_DEADLINES.schedule({
                            timedOut.set(true)
                            runCatching { closeInput() }
                        }, stageIdleTimeoutMillis, TimeUnit.MILLISECONDS)
                        val maybeCount = try {
                            try {
                                withTimeoutOrNull(stageIdleTimeoutMillis) {
                                    withContext(ioDispatcher) {
                                        runInterruptible { input.read(buffer, 0, requestLength) }
                                    }
                                }
                            } catch (cancel: CancellationException) {
                                if (timedOut.get()) throw PdfStageTimeout()
                                throw cancel
                            } catch (_: IOException) {
                                if (timedOut.get()) throw PdfStageTimeout()
                                throw PdfSourceReadFailure()
                            }
                        } finally {
                            deadline.cancel(false)
                        }
                        val count = maybeCount ?: throw PdfStageTimeout()
                        if (timedOut.get()) throw PdfStageTimeout()
                        if (count < 0) break
                        if (count == 0) {
                            noProgressReads++
                            if (noProgressReads > PreviewLimits.MAX_CONSECUTIVE_NO_PROGRESS_READS) {
                                throw PdfSourceReadFailure()
                            }
                            continue
                        }
                        noProgressReads = 0
                        lastProgressAt = clockNanos()
                        if (count > PreviewLimits.MAX_PDF_BYTES - bytesWritten) {
                            throw PdfInputTooLarge()
                        }
                        output.write(buffer, 0, count)
                        bytesWritten += count
                    }
                    output.fd.sync()
                }
            } finally {
                try {
                    closeInput()
                } catch (_: IOException) {
                    throw PdfSourceReadFailure()
                }
            }
            handle = null // close completed successfully before the READY transition
            val finalFile = File(workspace, "$id.ready")
            if (!isDirectOwnedPath(finalFile) || Files.exists(finalFile.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) ||
                !partial.renameTo(finalFile) ||
                !isOwnedArtifact(finalFile, workspace.canonicalFile) || finalFile.length() > PreviewLimits.MAX_PDF_BYTES
            ) {
                return PdfStageResult.Failure(PreviewError.StagingFailure)
            }
            ready = finalFile
            val snapshot = PdfSnapshot(id, finalFile, this)
            if (active.putIfAbsent(id, finalFile) != null) {
                return PdfStageResult.Failure(PreviewError.StagingFailure)
            }
            ready = null
            return PdfStageResult.Ready(snapshot)
        } catch (_: PdfInputTooLarge) {
            return PdfStageResult.Failure(PreviewError.PdfInputTooLarge)
        } catch (_: PdfStageTimeout) {
            return PdfStageResult.Failure(PreviewError.StagingTimeout)
        } catch (_: PdfSourceReadFailure) {
            return PdfStageResult.Failure(PreviewError.ProviderUnavailable)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: SecurityException) {
            return PdfStageResult.Failure(PreviewError.PermissionOrGrantMissing)
        } catch (_: IOException) {
            return PdfStageResult.Failure(PreviewError.StagingFailure)
        } catch (_: Exception) {
            return PdfStageResult.Failure(PreviewError.StagingFailure)
        } finally {
            runCatching { closeHandle?.invoke() ?: handle?.close() }
            ready?.let { deleteOwned(it) }
            deleteOwned(partial)
        }
    }

    internal fun release(snapshot: PdfSnapshot) {
        val owned = active[snapshot.id] ?: return
        if (owned == snapshot.file && active.remove(snapshot.id, snapshot.file)) deleteOwned(snapshot.file)
    }

    private fun deleteOwned(file: File) {
        runCatching {
            if (isOwnedArtifact(file, workspace.canonicalFile)) Files.deleteIfExists(file.toPath())
        }
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
    private class PdfStageTimeout : IOException()
    private class PdfSourceReadFailure : IOException()

    private companion object {
        val ARTIFACT_NAME = Regex("[0-9a-fA-F-]{36}\\.(partial|ready)")
        val READ_DEADLINES = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "omnifile-pdf-stage-deadline").apply { isDaemon = true }
        }
    }
}
