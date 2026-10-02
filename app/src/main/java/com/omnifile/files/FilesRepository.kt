package com.omnifile.files

import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageProvider
import com.omnifile.storage.SequentialReadHandle
import com.omnifile.storage.StorageTransferProvider
import com.omnifile.storage.StorageResult
import com.omnifile.preview.PreviewSource
import com.omnifile.preview.PreviewSourceProvider

class FilesRepository(providers: Map<ProviderId, StorageProvider>) {
    private val providers = providers.toMutableMap()

    fun register(provider: StorageProvider) {
        providers[provider.id] = provider
    }

    fun transferProvider(providerId: ProviderId): StorageTransferProvider? =
        providers[providerId] as? StorageTransferProvider

    fun playbackSourceProvider(providerId: ProviderId): com.omnifile.storage.PlaybackSourceProvider? =
        providers[providerId] as? com.omnifile.storage.PlaybackSourceProvider

    suspend fun root(providerId: ProviderId): StorageResult<StorageEntry> =
        providers[providerId]?.root() ?: StorageResult.Failure(StorageError.StaleReference)

    suspend fun children(entry: StorageEntry): StorageResult<List<StorageEntry>> =
        providers[entry.ref.providerId]?.listChildren(entry.ref)
            ?: StorageResult.Failure(StorageError.StaleReference)

    suspend fun previewSource(entry: StorageEntry): StorageResult<PreviewSource> {
        val provider = providers[entry.ref.providerId] as? PreviewSourceProvider
            ?: return StorageResult.Failure(StorageError.Unsupported)
        return provider.openPreviewSource(entry)
    }

    suspend fun openSequentialRead(entry: StorageEntry): StorageResult<SequentialReadHandle> {
        val provider = providers[entry.ref.providerId] as? StorageTransferProvider
            ?: return StorageResult.Failure(StorageError.Unsupported)
        val locator = when (val result = provider.encodeDurableLocator(entry.ref)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return result
        }
        return provider.openSequentialRead(locator)
    }

    /**
     * Re-resolves the same provider-scoped entry through the provider's own durable locator.
     * The locator is used and discarded here, so callers can detect source change without ever
     * holding a provider locator.
     */
    suspend fun refreshEntry(entry: StorageEntry): StorageResult<StorageEntry> {
        val provider = providers[entry.ref.providerId] as? StorageTransferProvider
            ?: return StorageResult.Failure(StorageError.Unsupported)
        val locator = when (val result = provider.encodeDurableLocator(entry.ref)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return result
        }
        return provider.resolveDurableLocator(locator)
    }

    /**
     * Optional, provider-neutral version evidence for a selected entry.
     *
     * Returns the provider's opaque version token, or null when the provider cannot prove one.
     * This is the only version evidence a caller outside this class can obtain, and it exists so
     * a same-path replacement that preserves size and modified time is still detectable.
     *
     * The locator is encoded, used, and discarded here, so neither a [com.omnifile.operations.DurableLocator],
     * a Local path, a SAF URI, nor a document id ever leaves the repository boundary. The token
     * itself is opaque internal evidence: callers may only compare it for equality, and it must
     * never be logged, rendered, or persisted.
     *
     * Local returns a token containing the filesystem `fileKey` whenever the platform provides
     * one, which is what makes replacement-with-identical-metadata detectable. SAF returns a token
     * only when it was constructed with proven source versioning. A provider with neither is a
     * legitimate case, not a failure.
     */
    suspend fun inspectReadVersion(entry: StorageEntry): String? {
        val provider = providers[entry.ref.providerId] as? StorageTransferProvider
            ?: return null
        val locator = when (val result = provider.encodeDurableLocator(entry.ref)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return null
        }
        return when (val facts = provider.inspectTransfer(locator)) {
            // Only the token crosses back out; the facts' locator stays inside this class.
            is StorageResult.Success -> facts.value.versionToken
            // Unavailable is not a change and never blocks hashing: callers fall back to the
            // size, identity, and modified-time evidence they can prove themselves.
            is StorageResult.Failure -> null
        }
    }

    suspend fun rename(
        entry: StorageEntry,
        requestedName: String,
    ): StorageResult<StorageEntry> =
        providers[entry.ref.providerId]?.rename(entry, requestedName)
            ?: StorageResult.Failure(StorageError.StaleReference)

    suspend fun delete(entry: StorageEntry): StorageResult<Unit> =
        providers[entry.ref.providerId]?.delete(entry)
            ?: StorageResult.Failure(StorageError.StaleReference)
}
