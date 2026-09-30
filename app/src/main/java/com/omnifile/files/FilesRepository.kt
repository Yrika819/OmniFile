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
