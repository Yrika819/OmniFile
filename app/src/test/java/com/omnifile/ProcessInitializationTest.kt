package com.omnifile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessInitializationTest {
    @Test
    fun onlyExactPdfRendererProcessSkipsTheMainApplicationGraph() {
        assertTrue(isPdfRendererProcessName("com.omnifile:pdf_renderer", "com.omnifile"))
        assertFalse(isPdfRendererProcessName("com.omnifile", "com.omnifile"))
        assertFalse(isPdfRendererProcessName("com.omnifile:pdf_renderer_test", "com.omnifile"))
    }
}
