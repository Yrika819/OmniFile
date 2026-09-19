package com.omnifile.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun mutationCapabilitiesHaveTheSameMeaningForEveryProviderType() {
        val localEntry = entry(ProviderId("local"), "local.txt", EntryKind.FILE)
        val safEntry = entry(ProviderId("saf"), "saf.txt", EntryKind.FILE)

        assertEquals(localEntry.capabilities, safEntry.capabilities)
        assertTrue(StorageCapability.RENAME in localEntry.capabilities)
        assertTrue(StorageCapability.DELETE in localEntry.capabilities)
        assertTrue(StorageCapability.RENAME in safEntry.capabilities)
        assertTrue(StorageCapability.DELETE in safEntry.capabilities)
    }

    @Test
    fun browseRootsNeverExposeRenameOrDelete() {
        val root = StorageEntry(
            ref = TestEntryRef(ProviderId("provider"), "root"),
            displayName = "Provider root",
            kind = EntryKind.DIRECTORY,
            sizeBytes = null,
            modifiedAtEpochMillis = null,
            mimeType = null,
            capabilities = setOf(StorageCapability.LIST_CHILDREN),
        )

        assertFalse(StorageCapability.RENAME in root.capabilities)
        assertFalse(StorageCapability.DELETE in root.capabilities)
    }

    @Test
    fun invalidNameAndConflictErrorsRetainTypedContextAndReadableText() {
        val invalid = StorageError.InvalidName("bad/name", "Name must be a single path component")
        val conflict = StorageError.NameConflict("existing.txt")

        assertEquals("bad/name", invalid.requestedName)
        assertEquals("Name must be a single path component", invalid.message)
        assertEquals("existing.txt", conflict.requestedName)
        assertTrue(conflict.message.isNotBlank())
    }

    @Test
    fun partialDeleteClassifiesEachItemWithoutClaimingTotalSuccess() {
        val deleted = entry(ProviderId("provider"), "deleted.txt", EntryKind.FILE)
        val failed = entry(ProviderId("provider"), "failed.txt", EntryKind.FILE)
        val result = StorageError.PartialDelete(
            outcomes = listOf(
                DeleteItemResult(deleted, DeleteItemOutcome.Deleted),
                DeleteItemResult(failed, DeleteItemOutcome.Failed(StorageError.PermissionDenied)),
            ),
        )

        assertEquals(2, result.outcomes.size)
        assertEquals(deleted, result.outcomes[0].entry)
        assertEquals(deleted.ref, result.outcomes[0].entry.ref)
        assertEquals(DeleteItemOutcome.Deleted, result.outcomes[0].outcome)
        assertEquals(failed, result.outcomes[1].entry)
        assertEquals(failed.ref, result.outcomes[1].entry.ref)
        assertEquals(
            DeleteItemOutcome.Failed(StorageError.PermissionDenied),
            result.outcomes[1].outcome,
        )
        assertTrue(result.hasFailures)
    }

    private fun entry(providerId: ProviderId, name: String, kind: EntryKind) = StorageEntry(
        ref = TestEntryRef(providerId, name),
        displayName = name,
        kind = kind,
        sizeBytes = null,
        modifiedAtEpochMillis = null,
        mimeType = null,
        capabilities = setOf(
            StorageCapability.RENAME,
            StorageCapability.DELETE,
        ),
    )

    private data class TestEntryRef(
        override val providerId: ProviderId,
        val token: String,
    ) : EntryRef
}
