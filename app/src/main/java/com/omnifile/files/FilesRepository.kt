package com.omnifile.files

import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageProvider
import com.omnifile.storage.StorageResult

class FilesRepository(providers: Map<ProviderId, StorageProvider>) {
    private val providers = providers.toMutableMap()

    fun register(provider: StorageProvider) {
        providers[provider.id] = provider
    }

    suspend fun root(providerId: ProviderId): StorageResult<StorageEntry> =
        providers[providerId]?.root() ?: StorageResult.Failure(StorageError.StaleReference)

    suspend fun children(entry: StorageEntry): StorageResult<List<StorageEntry>> =
        providers[entry.ref.providerId]?.listChildren(entry.ref)
            ?: StorageResult.Failure(StorageError.StaleReference)
}
