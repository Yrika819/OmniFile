package com.omnifile.ui.preview

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omnifile.preview.PreviewError
import com.omnifile.preview.PreviewItem
import com.omnifile.preview.PreviewPayload
import com.omnifile.preview.PreviewUiState
import com.omnifile.preview.PdfDocumentSession
import com.omnifile.preview.PdfRenderedPage
import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.operations.DurableLocator
import com.omnifile.operations.OperationSnapshot
import com.omnifile.operations.OperationState
import com.omnifile.operations.OperationType
import com.omnifile.operations.SourceDeleteState
import com.omnifile.operations.TransferStage
import com.omnifile.ui.operations.OperationsPanel
import com.omnifile.ui.theme.OmniFileTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@com.omnifile.preview.PostVs10HardeningTarget
@RunWith(AndroidJUnit4::class)
class PreviewScreenInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun rendersLoadingTextImageUnsupportedAndRetryableErrorStates() {
        val item = PreviewItem(TestRef("preview-file"), "readme.txt", "Local / Notes", "text/plain", 5)
        val uiState = mutableStateOf<PreviewUiState>(PreviewUiState.Loading(item))
        var retries = 0
        val bitmap = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            OmniFileTheme {
                PreviewScreen(uiState.value, onBack = {}, onRetry = { retries++ })
            }
        }
        assertEquals(1, composeRule.onAllNodesWithTag("preview.loading").fetchSemanticsNodes().size)

        composeRule.runOnIdle {
            uiState.value = PreviewUiState.Ready(item, "Local storage", PreviewPayload.Text("hello", true, 5))
        }
        assertEquals(1, composeRule.onAllNodesWithTag("preview.text").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithTag("preview.truncated").fetchSemanticsNodes().size)

        composeRule.runOnIdle {
            uiState.value = PreviewUiState.Ready(
                item.copy(displayName = "photo.png"),
                "Local storage",
                PreviewPayload.Image(bitmap, 2, 3),
            )
        }
        assertEquals(1, composeRule.onAllNodesWithContentDescription("Preview of photo.png").fetchSemanticsNodes().size)

        composeRule.runOnIdle {
            uiState.value = PreviewUiState.Unsupported(item)
        }
        assertEquals(1, composeRule.onAllNodesWithTag("preview.unsupported").fetchSemanticsNodes().size)
        composeRule.runOnIdle {
            bitmap.recycle()
            uiState.value = PreviewUiState.Error(item, PreviewError.ProviderUnavailable)
        }

        composeRule.onNodeWithText("Retry").performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun ioFailureDoesNotRenderProviderPathOrUriDetails() {
        val detail = "/private/fixture/path.txt content://test/documents/private-id"
        val item = PreviewItem(TestRef("io-failure"), "readme.txt", "Local storage", "text/plain", null)
        composeRule.setContent {
            OmniFileTheme {
                PreviewScreen(
                    PreviewUiState.Error(item, PreviewError.IoFailure(detail)),
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        assertEquals(1, composeRule.onAllNodesWithText("The file could not be read.").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText(detail, substring = true).fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("Retry").fetchSemanticsNodes().size)
    }

    @Test
    fun pdfWorkerFailureShowsOnlySanitizedTextAndRetry() {
        val item = PreviewItem(TestRef("pdf-worker"), "report.pdf", "Local storage", "application/pdf", null)
        composeRule.setContent {
            OmniFileTheme {
                PreviewScreen(
                    PreviewUiState.Error(item, PreviewError.RendererFailure),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("The PDF renderer stopped unexpectedly.").assertExists()
        composeRule.onNodeWithText("Retry").assertExists()
    }

    @Test
    fun pdfPreviewShowsBoundedPageAndAccessiblePreviousNextControls() {
        val item = PreviewItem(TestRef("pdf-file"), "document.pdf", "Local / Docs", "application/pdf", 512)
        val uiState = mutableStateOf<PreviewUiState>(
            PreviewUiState.Ready(
                item,
                "Local storage",
                PreviewPayload.PdfPage(PdfRenderedPage(2, 3, ByteArray(24)), 0, 2, ScreenPdfSession()),
            ),
        )
        var previous = 0
        var next = 0
        composeRule.setContent {
            OmniFileTheme {
                PreviewScreen(
                    uiState.value,
                    onBack = {},
                    onRetry = {},
                    onPreviousPage = { previous++ },
                    onNextPage = { next++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("PDF page 1 of 2 for document.pdf").assertExists()
        composeRule.onNodeWithText("1 / 2").assertExists()
        composeRule.onNodeWithContentDescription("Previous page").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Next page").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(1, next); assertEquals(0, previous) }

        composeRule.runOnIdle {
            uiState.value = PreviewUiState.Ready(
                item,
                "Local storage",
                PreviewPayload.PdfPage(PdfRenderedPage(3, 2, ByteArray(24)), 1, 2, ScreenPdfSession()),
            )
        }
        composeRule.onNodeWithText("2 / 2").assertExists()
        composeRule.onNodeWithContentDescription("Previous page").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Next page").assertIsNotEnabled()
    }

    @Test
    fun textPreviewKeepsAReadableLineInShortLandscapeLikeContentArea() {
        val item = PreviewItem(TestRef("short-landscape"), "notes.txt", "Acceptance folder", "text/plain", 5)
        composeRule.setContent {
            OmniFileTheme {
                Column(Modifier.fillMaxWidth().height(350.dp)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        PreviewScreen(
                            PreviewUiState.Ready(
                                item,
                                "Selected storage",
                                PreviewPayload.Text("VS09 fixture — 東京 ✓", false, 21),
                            ),
                            onBack = {},
                            onRetry = {},
                        )
                    }
                    OperationsPanel(
                        operations = listOf(
                            completedCopy("vs09-local-notes.txt"),
                            completedCopy("vs09-local-image.png"),
                            completedCopy("vs09-local-fixture.zip"),
                        ),
                        onCancel = {},
                        applyNavigationBarsPadding = false,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("operations.panel").assertExists()
        composeRule.onNodeWithTag("preview.text").assertHeightIsAtLeast(24.dp)
    }

    private fun completedCopy(name: String) = OperationSnapshot(
        operationId = "copy-$name",
        batchId = "vs09-acceptance",
        type = OperationType.COPY,
        state = OperationState.COMPLETE,
        stage = TransferStage.COMPLETE,
        source = DurableLocator(ProviderId("preview-ui-test"), "fixture", "source"),
        destinationParent = DurableLocator(ProviderId("preview-ui-test"), "fixture", "destination"),
        intendedFinalName = name,
        partialDestination = null,
        expectedBytes = 51,
        bytesCompleted = 51,
        sourceVersion = null,
        verificationDescription = null,
        finalizationDescription = null,
        sourceDeleteState = SourceDeleteState.NOT_REQUIRED,
        cancellationRequested = false,
        errorCode = null,
        errorMessage = null,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 2,
    )

    private data class TestRef(override val identityKey: String) : EntryRef {
        override val providerId = ProviderId("preview-ui-test")
    }

    private class ScreenPdfSession : PdfDocumentSession {
        override val pageCount = 2
        override suspend fun renderPage(pageIndex: Int) = PdfRenderedPage(1, 1, ByteArray(4))
        override suspend fun close() = Unit
    }
}
