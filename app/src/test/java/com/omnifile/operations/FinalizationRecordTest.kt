package com.omnifile.operations

import com.omnifile.storage.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class FinalizationRecordTest {
    @Test
    fun v2EncodingRoundTripsDelimiterAndDotBearingLocatorFields() {
        val locator = DurableLocator(
            providerId = ProviderId("provider\u001f.with.dot"),
            encoding = "saf-document-v1",
            value = "tree\u001fvalue.with.dot",
        )

        assertEquals(locator, FinalizationRecord.decode(FinalizationRecord.encode(locator)))
    }

    @Test
    fun legacyV1EncodingRemainsReadable() {
        val locator = DurableLocator(ProviderId("provider"), "local/root-relative-v1", "root/child")
        val legacy = "finalization-v1\u001fprovider\u001flocal/root-relative-v1\u001froot/child"

        assertEquals(locator, FinalizationRecord.decode(legacy))
    }
}
