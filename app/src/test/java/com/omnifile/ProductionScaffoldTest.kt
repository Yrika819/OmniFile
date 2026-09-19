package com.omnifile

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductionScaffoldTest {
    @Test
    fun applicationIdIsFrozen() {
        assertEquals("com.omnifile", BuildConfig.APPLICATION_ID)
    }
}
