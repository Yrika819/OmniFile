package com.omnifile.preview

import com.omnifile.storage.EntryKind
import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageCapability
import com.omnifile.storage.StorageEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewCapabilityTest {
    @Test
    fun capabilityRequiresAReadableMatchingProviderSource() {
        val ref = TestRef(ProviderId("local-test"), "file:one")
        val entry = StorageEntry(ref, "one.txt", EntryKind.FILE, 3, null, "text/plain", setOf(StorageCapability.READ_SEQUENTIAL))
        val source = PreviewSource(ref, "Local storage", "text/plain", 3, PreviewCapabilities(true, true)) {
            error("Capability resolution must not open the source")
        }

        assertEquals(PreviewCapabilities(true, true), PreviewCapabilityResolver.resolve(entry, source))
        assertNull(PreviewCapabilityResolver.resolve(entry, null))
    }

    @Test
    fun capabilityRejectsDirectoryMissingReadFlagAndIdentityMismatch() {
        val ref = TestRef(ProviderId("local-test"), "file:one")
        val source = PreviewSource(ref, "Local storage", null, null, PreviewCapabilities(true, true)) {
            error("Capability resolution must not open the source")
        }
        val directory = StorageEntry(ref, "folder", EntryKind.DIRECTORY, null, null, null, emptySet())
        val unreadable = StorageEntry(ref, "one.bin", EntryKind.FILE, null, null, null, emptySet())
        val other = PreviewSource(TestRef(ProviderId("local-test"), "file:two"), "Local storage", null, null, PreviewCapabilities(true, true)) {
            error("Capability resolution must not open the source")
        }

        assertNull(PreviewCapabilityResolver.resolve(directory, source))
        assertNull(PreviewCapabilityResolver.resolve(unreadable, source))
        assertNull(PreviewCapabilityResolver.resolve(unreadable.copy(capabilities = setOf(StorageCapability.READ_SEQUENTIAL)), other))
        assertTrue(source.capabilities.canReopen)
    }

    private data class TestRef(override val providerId: ProviderId, override val identityKey: String) : EntryRef
}
