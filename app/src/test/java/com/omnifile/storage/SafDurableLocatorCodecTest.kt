package com.omnifile.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafDurableLocatorCodecTest {
    @Test
    fun versionedCodecRoundTripsOpaqueTreeAndDocumentFacts() {
        val tree = "content://com.example.documents/tree/root%2Ffolder"
        val document = "root/folder/opaque.document/id"

        val encoded = SafDurableLocatorCodec.encode(tree, document)
        val decoded = SafDurableLocatorCodec.decode(encoded)

        assertEquals(SafDurableLocatorCodec.ENCODING, "saf-document-v1")
        assertEquals(SafLocatorParts(tree, document), decoded)
    }

    @Test
    fun malformedOrUnversionedPayloadDoesNotResolve() {
        assertNull(SafDurableLocatorCodec.decode("tree/document/without-codec"))
        assertNull(SafDurableLocatorCodec.decode(""))
        assertNull(SafDurableLocatorCodec.decode("%%%%.%%%%"))
    }
}
