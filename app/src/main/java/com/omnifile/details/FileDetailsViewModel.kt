package com.omnifile.details

import com.omnifile.files.FilesRepository
import com.omnifile.storage.EntryKind
import com.omnifile.storage.StorageCapability
import com.omnifile.storage.StorageEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns one File Details surface.
 *
 * Ownership rules:
 * - At most one digest attempt is live. Starting a replacement, changing the selected source,
 *   cancelling, or closing revokes the previous generation's publication authority.
 * - A worker may publish only while it still owns the newest generation. Replacement therefore
 *   makes a late success or a late error unreachable, not merely unlikely.
 * - Source ownership is in-memory only. Nothing here is serialized into navigation routes or
 *   SavedState, so process death cannot reconstruct authority from a stale private locator.
 * - Compose never owns a stream or a buffer; this class and the digest core do.
 */
class FileDetailsViewModel(
    repository: FilesRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val ownsScope = scope == null
    private val lifecycleScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val calculator = FileDetailsDigestCalculator(repository, ioDispatcher)

    private val lock = Any()
    private val _uiState = MutableStateFlow<FileDetailsUiState>(FileDetailsUiState.Unavailable)
    val uiState: StateFlow<FileDetailsUiState> = _uiState.asStateFlow()

    /** In-memory provider-scoped selection. Never logged, never persisted, never routed. */
    private var source: StorageEntry? = null

    /** Monotonic publication authority token. */
    private val generation = AtomicLong(0)
    private var job: Job? = null
    private val abort = AtomicBoolean(false)

    /**
     * Selects the one supported source. [sourceLabel] is a safe display string chosen by the
     * caller that already knows the provider; no provider id, URI, or path is accepted here.
     */
    fun open(entry: StorageEntry, sourceLabel: String) {
        require(entry.kind == EntryKind.FILE) { "File Details accepts one regular file only" }
        val mine = revoke()
        synchronized(lock) { source = entry }
        publishReplacing(mine) {
            FileDetailsUiState.Metadata(
                metadata = FileDetailsMetadata(
                    displayName = entry.displayName,
                    sourceLabel = sourceLabel,
                    sizeBytes = entry.sizeBytes?.takeIf { it >= 0 },
                    modifiedAtEpochMillis = entry.modifiedAtEpochMillis,
                    mimeType = entry.mimeType,
                ),
                hashingSupported = StorageCapability.READ_SEQUENTIAL in entry.capabilities,
            )
        }
    }

    fun calculate() {
        val entry = source ?: return
        if ((_uiState.value as? FileDetailsUiState.Metadata)?.hashingSupported != true) return
        val mine = revoke()
        abort.set(false)
        publishOwned(mine) { copy(digest = DigestUiState.Calculating(0, entry.sizeBytes?.takeIf { it >= 0 })) }
        // Started lazily so the reference is published under the lock before any body can run.
        // A cancel arriving in that window therefore always finds a real owner to revoke.
        val owned = lifecycleScope.launch(ioDispatcher, start = CoroutineStart.LAZY) {
            val outcome = try {
                calculator.calculate(
                    entry = entry,
                    onProgress = { progress ->
                        publishOwned(mine) {
                            copy(digest = DigestUiState.Calculating(progress.bytesRead, progress.expectedBytes))
                        }
                    },
                    shouldAbort = { abort.get() || generation.get() != mine },
                )
            } catch (error: Throwable) {
                // An untyped provider failure must still reach a terminal, typed state rather than
                // stranding the surface in Calculating with no way forward.
                if (error is CancellationException) throw error
                DigestOutcome.Failed(DigestFailure.ReadFailed, 0)
            }
            publishOwned(mine) { copy(digest = outcome.toUiState()) }
        }
        synchronized(lock) { job = owned }
        owned.start()
    }

    /**
     * Requests a stop. Cancelling is reported as [DigestUiState.Cancelling] and only becomes
     * [DigestUiState.Cancelled] once the reader actually returned, so a non-cooperative
     * provider is never described as instantly cancellable.
     */
    fun cancel() {
        var running: Job? = null
        var mine = 0L
        synchronized(lock) {
            val active = job
            if (active != null && active.isActive) {
                abort.set(true)
                running = active
                mine = generation.incrementAndGet()
            }
        }
        val owned = running ?: return
        owned.cancel()
        publishOwned(mine) { copy(digest = DigestUiState.Cancelling) }
        lifecycleScope.launch(Dispatchers.Default) {
            owned.join()
            publishOwned(mine) {
                if (digest is DigestUiState.Cancelling) copy(digest = DigestUiState.Cancelled) else this
            }
        }
    }

    /** Retry is only offered for a read failure, which re-opens the same authorized source. */
    fun retry() {
        val digest = (_uiState.value as? FileDetailsUiState.Metadata)?.digest
        if ((digest as? DigestUiState.Failed)?.isRetryable != true) return
        calculate()
    }

    /** Leaving the surface revokes authority without claiming a terminal digest state. */
    fun close() {
        revoke()
        synchronized(lock) { source = null }
        synchronized(lock) { _uiState.value = FileDetailsUiState.Unavailable }
    }

    override fun onCleared() {
        revoke()
        synchronized(lock) { source = null }
        if (ownsScope) lifecycleScope.cancel()
    }

    /** Invalidates the current generation and returns the new authority token. */
    private fun revoke(): Long {
        val stale: Job?
        val mine: Long
        synchronized(lock) {
            mine = generation.incrementAndGet()
            stale = job
            job = null
            abort.set(true)
        }
        stale?.cancel()
        return mine
    }

    private inline fun publishState(next: () -> FileDetailsUiState) {
        synchronized(lock) { _uiState.value = next() }
    }

    /** Publishes only while [mine] still owns publication authority and metadata is still shown. */
    private fun publishOwned(
        mine: Long,
        next: FileDetailsUiState.Metadata.() -> FileDetailsUiState,
    ) {
        synchronized(lock) {
            if (generation.get() != mine) return
            val current = _uiState.value as? FileDetailsUiState.Metadata ?: return
            _uiState.value = current.next()
        }
    }

    /** Publishes a whole-state replacement only while [mine] still owns publication authority. */
    private fun publishReplacing(mine: Long, next: () -> FileDetailsUiState) {
        synchronized(lock) {
            if (generation.get() != mine) return
            _uiState.value = next()
        }
    }

    private fun DigestOutcome.toUiState(): DigestUiState = when (this) {
        is DigestOutcome.Complete -> DigestUiState.Complete(hex)
        DigestOutcome.Cancelled -> DigestUiState.Cancelled
        is DigestOutcome.SourceChanged -> DigestUiState.Failed(FileDetailsFailure.SOURCE_CHANGED)
        is DigestOutcome.Failed -> DigestUiState.Failed(
            when (failure) {
                DigestFailure.AccessUnavailable -> FileDetailsFailure.ACCESS_UNAVAILABLE
                DigestFailure.SourceUnavailable -> FileDetailsFailure.SOURCE_UNAVAILABLE
                DigestFailure.Unsupported -> FileDetailsFailure.UNSUPPORTED
                DigestFailure.ReadFailed, DigestFailure.CloseFailed -> FileDetailsFailure.READ_FAILURE
            },
        )
    }
}
