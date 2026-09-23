package com.omnifile.ui.preview

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omnifile.preview.PreviewError
import com.omnifile.preview.PreviewItem
import com.omnifile.preview.PreviewPayload
import com.omnifile.preview.PreviewUiState
import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.ui.theme.OmniFileTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewScreenInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun rendersLoadingTextImageUnsupportedAndRetryableErrorStates() {
        val item = PreviewItem(TestRef("preview-file"), "readme.txt", "Local / Notes", "text/plain", 5)
        composeRule.setContent {
            OmniFileTheme {
                PreviewScreen(PreviewUiState.Loading(item), onBack = {}, onRetry = {})
            }
        }
        assertEquals(1, composeRule.onAllNodesWithTag("preview.loading").fetchSemanticsNodes().size)

        composeRule.setContent {
            OmniFileTheme {
                PreviewScreen(
                    PreviewUiState.Ready(item, "Local storage", PreviewPayload.Text("hello", true, 5)),
                    onBack = {}, onRetry = {},
                )
            }
        }
        assertEquals(1, composeRule.onAllNodesWithTag("preview.text").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithTag("preview.truncated").fetchSemanticsNodes().size)

        val bitmap = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            OmniFileTheme {
                PreviewScreen(
                    PreviewUiState.Ready(item.copy(displayName = "photo.png"), "Local storage", PreviewPayload.Image(bitmap, 2, 3)),
                    onBack = {}, onRetry = {},
                )
            }
        }
        assertEquals(1, composeRule.onAllNodesWithContentDescription("Preview of photo.png").fetchSemanticsNodes().size)

        composeRule.setContent {
            OmniFileTheme {
                PreviewScreen(PreviewUiState.Unsupported(item), onBack = {}, onRetry = {})
            }
        }
        assertEquals(1, composeRule.onAllNodesWithTag("preview.unsupported").fetchSemanticsNodes().size)
        composeRule.runOnIdle { bitmap.recycle() }

        var retries = 0
        composeRule.setContent {
            OmniFileTheme {
                PreviewScreen(PreviewUiState.Error(item, PreviewError.ProviderUnavailable), onBack = {}, onRetry = { retries++ })
            }
        }
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    private data class TestRef(override val identityKey: String) : EntryRef {
        override val providerId = ProviderId("preview-ui-test")
    }
}
