package com.omnifile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessInitializationTest {
    @Test
    fun isolatedProcessSkipsTheMainApplicationGraph() {
        assertFalse(shouldInitializeMainAppGraph(isolatedProcess = true))
        assertTrue(shouldInitializeMainAppGraph(isolatedProcess = false))
    }
}
