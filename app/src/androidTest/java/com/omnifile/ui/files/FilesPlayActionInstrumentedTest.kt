package com.omnifile.ui.files

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omnifile.files.FilesUiState
import com.omnifile.storage.EntryKind
import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageCapability
import com.omnifile.storage.StorageEntry
import com.omnifile.ui.theme.OmniFileTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Files Play affordance: only eligible single audio entries expose Play, normal
 * row taps stay unchanged, and selection mode never shows Play.
 */
@RunWith(AndroidJUnit4::class)
class FilesPlayActionInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playActionAppearsOnlyForEligibleAudioEntries() {
        var played: StorageEntry? = null
        composeRule.setContent {
            OmniFileTheme {
                FilesScreen(
                    state = content(listOf(audioEntry("tone.wav"), textEntry("notes.txt"))),
                    onSelectLocal = {},
                    onPickTree = {},
                    onOpenDirectory = {},
                    onPlayEntry = { played = it },
                    onEnterSelection = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onRenameSelected = {},
                    onDeleteSelected = {},
                    mutationInFlight = false,
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag("files.play.tone.wav").assertExists()
        composeRule.onNodeWithTag("files.play.notes.txt").assertDoesNotExist()

        composeRule.onNodeWithTag("files.play.tone.wav").performClick()
        assertEquals("tone.wav", played?.displayName)
    }

    @Test
    fun plainRowTapDoesNotStartPlayback() {
        var played: StorageEntry? = null
        var openedDirectory: StorageEntry? = null
        composeRule.setContent {
            OmniFileTheme {
                FilesScreen(
                    state = content(listOf(audioEntry("tone.wav"))),
                    onSelectLocal = {},
                    onPickTree = {},
                    onOpenDirectory = { openedDirectory = it },
                    onPlayEntry = { played = it },
                    onEnterSelection = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onRenameSelected = {},
                    onDeleteSelected = {},
                    mutationInFlight = false,
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag("files.entry.tone.wav").performClick()
        composeRule.waitForIdle()
        assertNull(played)
        assertNull(openedDirectory)
    }

    @Test
    fun selectionModeHidesPlayAction() {
        var playCalls = 0
        composeRule.setContent {
            OmniFileTheme {
                FilesScreen(
                    state = content(listOf(audioEntry("tone.wav"))),
                    onSelectLocal = {},
                    onPickTree = {},
                    onOpenDirectory = {},
                    onPlayEntry = { playCalls++ },
                    onEnterSelection = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onRenameSelected = {},
                    onDeleteSelected = {},
                    mutationInFlight = false,
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag("files.entry.tone.wav")
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.waitForIdle()
        assertEquals(0, playCalls)
    }

    private fun content(entries: List<StorageEntry>) = FilesUiState.Content(
        location = directoryEntry(),
        entries = entries,
        breadcrumb = listOf("Local files"),
    )

    private fun directoryEntry() = StorageEntry(
        ref = TestEntryRef("root"),
        displayName = "Local files",
        kind = EntryKind.DIRECTORY,
        sizeBytes = null,
        modifiedAtEpochMillis = null,
        mimeType = null,
        capabilities = setOf(StorageCapability.LIST_CHILDREN),
    )

    private fun audioEntry(name: String) = StorageEntry(
        ref = TestEntryRef(name),
        displayName = name,
        kind = EntryKind.FILE,
        sizeBytes = 128L,
        modifiedAtEpochMillis = 1000L,
        mimeType = "audio/x-wav",
        capabilities = setOf(StorageCapability.READ_SEQUENTIAL),
    )

    private fun textEntry(name: String) = StorageEntry(
        ref = TestEntryRef(name),
        displayName = name,
        kind = EntryKind.FILE,
        sizeBytes = 12L,
        modifiedAtEpochMillis = 1000L,
        mimeType = "text/plain",
        capabilities = setOf(StorageCapability.READ_SEQUENTIAL),
    )

    private class TestEntryRef(val objectId: String) : EntryRef {
        override val providerId: ProviderId = ProviderId("ui-test")
        override val identityKey: String = "ui-test/$objectId"
    }
}
