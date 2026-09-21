package com.omnifile.media

import com.omnifile.storage.EntryKind
import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageCapability
import com.omnifile.storage.StorageEntry

data class TestEntryRef(
    override val providerId: ProviderId,
    val objectId: String,
) : EntryRef {
    override val identityKey: String = "${providerId.value} $objectId"
}

fun testEntry(
    providerId: String,
    objectId: String,
    displayName: String,
    kind: EntryKind = EntryKind.FILE,
    mimeType: String? = null,
    capabilities: Set<StorageCapability> = setOf(StorageCapability.READ_SEQUENTIAL),
) = StorageEntry(
    ref = TestEntryRef(ProviderId(providerId), objectId),
    displayName = displayName,
    kind = kind,
    sizeBytes = 100L,
    modifiedAtEpochMillis = 1000L,
    mimeType = mimeType,
    capabilities = capabilities,
)
