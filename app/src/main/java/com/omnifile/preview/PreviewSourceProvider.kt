package com.omnifile.preview

import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageResult

/** Optional provider adapter for sequential preview reads. Transport locators stay provider-owned. */
interface PreviewSourceProvider {
    suspend fun openPreviewSource(entry: StorageEntry): StorageResult<PreviewSource>
}
