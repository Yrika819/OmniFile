package com.omnifile.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageModelTest {
    @Test
    fun unknownMetadataRemainsUnknown() {
        val entry = StorageEntry(
            ref = TestEntryRef(ProviderId("test"), "entry"),
            displayName = "virtual",
            kind = EntryKind.FILE,
            sizeBytes = null,
            modifiedAtEpochMillis = null,
            mimeType = null,
            capabilities = setOf(StorageCapability.READ_SEQUENTIAL),
        )

        assertEquals(null, entry.sizeBytes)
        assertEquals(null, entry.modifiedAtEpochMillis)
        assertEquals(null, entry.mimeType)
        assertTrue(StorageCapability.READ_SEQUENTIAL in entry.capabilities)
        assertTrue(StorageCapability.READ_SEEKABLE !in entry.capabilities)
    }

    private data class TestEntryRef(
        override val providerId: ProviderId,
        val token: String,
    ) : EntryRef
}
