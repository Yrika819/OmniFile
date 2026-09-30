package com.omnifile.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class PreviewContentTest {
    @Test
    fun utf8BomIsRemovedAndUnicodeIsPreserved() {
        val bytes = byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + "こんにちは 🌱".toByteArray()
        val result = PreviewTextDecoder.decode(bytes, truncated = false)
        assertEquals("こんにちは 🌱", result.text)
        assertFalse(result.truncated)
    }

    @Test
    fun malformedUtf8UsesReplacementCharacter() {
        val result = PreviewTextDecoder.decode(byteArrayOf(0x61, 0xc3.toByte(), 0x28), truncated = false)
        assertEquals("a�(", result.text)
    }

    @Test
    fun incompleteUtf8AtTruncationBoundaryDoesNotInventReplacement() {
        val result = PreviewTextDecoder.decode(byteArrayOf(0x61, 0xe2.toByte(), 0x82.toByte()), truncated = true)
        assertEquals("a", result.text)
        assertTrue(result.truncated)
    }

    @Test
    fun binaryLookingContentIsNotTextEvenWithTxtExtension() {
        val sample = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        assertEquals(PreviewContentType.UNKNOWN, PreviewContentClassifier.classify(sample, "text/plain", "notes.txt"))
    }

    @Test
    fun strongImageSignatureOverridesSpoofedExtensionAndMime() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        assertEquals(PreviewContentType.IMAGE, PreviewContentClassifier.classify(png, "text/plain", "notes.txt"))
    }

    @Test
    fun textBytesOverrideImageExtensionAndMime() {
        val bytes = "Plain readable text".toByteArray(StandardCharsets.UTF_8)
        assertEquals(PreviewContentType.TEXT, PreviewContentClassifier.classify(bytes, "image/png", "not-really.png"))
    }

    @Test
    fun unknownBinaryDoesNotBecomePreviewableFromExtensionAlone() {
        assertEquals(PreviewContentType.UNKNOWN, PreviewContentClassifier.classify(byteArrayOf(0x7f, 0x00, 0x11), "application/octet-stream", "payload.txt"))
    }

    @Test
    fun pdfSignatureIsRecognizedByContentEvidence() {
        val printablePdfHeader = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj".toByteArray()
        assertEquals(PreviewContentType.PDF, PreviewContentClassifier.classify(printablePdfHeader, "text/plain", "document.txt"))
    }

    @Test
    fun pdfExtensionAloneDoesNotClassifyContentAsPdf() {
        val textWithPdfName = "ordinary readable text".toByteArray()
        assertEquals(PreviewContentType.TEXT, PreviewContentClassifier.classify(textWithPdfName, "application/pdf", "foo.pdf"))
    }

    @Test
    fun pdfSignatureWithinHeaderAllowanceOverridesMisleadingExtension() {
        val bytes = ByteArray(128) { ' '.code.toByte() } + "%PDF-1.4\n".toByteArray()
        assertEquals(PreviewContentType.PDF, PreviewContentClassifier.classify(bytes, "text/plain", "payload.bin"))
    }

    @Test
    fun textReadLimitIsExplicitAndBounded() {
        assertEquals(256 * 1024, PreviewLimits.MAX_TEXT_BYTES)
        assertEquals(32 * 1024 * 1024, PreviewLimits.MAX_IMAGE_ENCODED_BYTES)
    }
}
