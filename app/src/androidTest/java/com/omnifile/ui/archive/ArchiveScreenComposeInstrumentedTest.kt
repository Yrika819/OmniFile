package com.omnifile.ui.archive

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omnifile.archive.ArchiveContainer
import com.omnifile.archive.ArchiveDocument
import com.omnifile.archive.ArchiveEntryStatus
import com.omnifile.archive.ArchiveNode
import com.omnifile.archive.ArchivePath
import com.omnifile.archive.ArchiveUiState
import com.omnifile.storage.EntryKind
import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageCapability
import com.omnifile.storage.StorageEntry
import com.omnifile.ui.theme.OmniFileTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveScreenComposeInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun supportedFileEntryInvokesSharedPreviewCallback() {
        val source = StorageEntry(
            ref = TestRef("archive-container"),
            displayName = "fixture.zip",
            kind = EntryKind.FILE,
            sizeBytes = 100,
            modifiedAtEpochMillis = null,
            mimeType = "application/zip",
            capabilities = setOf(StorageCapability.READ_SEQUENTIAL),
        )
        val document = ArchiveDocument(ArchiveContainer(source), emptyList())
        val node = ArchiveNode(
            ref = com.omnifile.archive.ArchiveEntryRef(source.ref.providerId, source.ref.identityKey, "ordinal:0"),
            displayName = "notes.txt",
            path = ArchivePath.ROOT.child("notes.txt"),
            kind = EntryKind.FILE,
            sizeBytes = 12,
            compressedSizeBytes = 12,
            modifiedAtEpochMillis = null,
            status = ArchiveEntryStatus.SUPPORTED,
            sourceOrdinals = listOf(0),
        )
        var previewed: ArchiveNode? = null
        composeRule.setContent {
            OmniFileTheme {
                ArchiveScreen(
                    state = ArchiveUiState.Content(document, ArchivePath.ROOT, listOf(node)),
                    onBack = {},
                    onOpenDirectory = {},
                    onPreview = { previewed = it },
                    onEnterSelection = {},
                    onToggleSelection = {},
                    onExtract = {},
                    onCancelExtraction = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Preview").performClick()
        assertEquals(node.ref, previewed?.ref)
    }

    private data class TestRef(override val identityKey: String) : EntryRef {
        override val providerId = ProviderId("archive-screen-test")
    }
}
