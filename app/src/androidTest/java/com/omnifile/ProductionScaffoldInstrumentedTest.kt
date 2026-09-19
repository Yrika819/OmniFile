package com.omnifile

import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionScaffoldInstrumentedTest {
    @Test
    fun packageIdentityIsFrozen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.omnifile", context.packageName)
    }

    @Test
    fun temporaryScaffoldIsDisplayed() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
            assertTrue(contentRoot.childCount > 0)
        }
        scenario.close()
    }
}
