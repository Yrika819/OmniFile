package com.omnifile.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omnifile.files.FilesSelectionState
import com.omnifile.files.FilesUiState
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
class FilesScreenComposeInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectionToggleCloseAndCapabilityGatingAreExercisedThroughCompose() {
        val entries = listOf(
            entry("alpha.txt", setOf(StorageCapability.RENAME, StorageCapability.DELETE)),
            entry("beta.txt", setOf(StorageCapability.DELETE)),
            entry("read-only.txt", emptySet()),
        )
        composeRule.setContent { SelectionHarness(entries) }

        composeRule.onNodeWithTag("files.entry.alpha.txt")
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.onNodeWithText("1 selected").assertExists()
        composeRule.onNodeWithText("alpha.txt").assertIsSelected()

        composeRule.onNodeWithText("beta.txt").performClick()
        composeRule.onNodeWithText("2 selected").assertExists()
        composeRule.onNodeWithTag("files.action.rename").assertDoesNotExist()
        composeRule.onNodeWithTag("files.action.delete").assertIsEnabled()

        composeRule.onNodeWithText("read-only.txt").performClick()
        composeRule.onNodeWithText("3 selected").assertExists()
        composeRule.onNodeWithText("Delete").assertIsNotEnabled()

        composeRule.onNodeWithText("read-only.txt").performClick()
        composeRule.onNodeWithText("2 selected").assertExists()
        composeRule.onNodeWithTag("files.action.delete").assertIsEnabled()

        composeRule.onNodeWithText("beta.txt").performClick()
        composeRule.onNodeWithText("1 selected").assertExists()
        composeRule.onNodeWithTag("files.action.rename").assertExists()
        composeRule.onNodeWithTag("files.action.delete").assertIsEnabled()

        composeRule.onNodeWithTag("files.selection.close").performClick()
        composeRule.onNodeWithText("1 selected").assertDoesNotExist()
    }

    @Test
    fun renameDialogPopulatesExistingNameAndSubmitsOnlyAfterConfirmation() {
        val entries = listOf(entry("alpha.txt", setOf(StorageCapability.RENAME)))
        var submittedName: String? = null
        composeRule.setContent {
            SelectionHarness(entries, onRenameSelected = { submittedName = it })
        }

        composeRule.onNodeWithTag("files.entry.alpha.txt")
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.onNodeWithTag("files.action.rename").performClick()
        val renameField = composeRule.onNodeWithTag("files.dialog.rename.field").fetchSemanticsNode()
        assertEquals("alpha.txt", renameField.config[SemanticsProperties.EditableText]?.text)
        composeRule.onNodeWithTag("files.dialog.rename.cancel").performClick()
        assertEquals(null, submittedName)
        composeRule.onNodeWithTag("files.dialog.rename").assertDoesNotExist()

        composeRule.onNodeWithTag("files.action.rename").performClick()
        composeRule.onNodeWithTag("files.dialog.rename.field").performTextReplacement("renamed.txt")
        composeRule.onNodeWithTag("files.dialog.rename.confirm").performClick()
        assertEquals("renamed.txt", submittedName)
    }

    @Test
    fun deleteDialogDoesNotSubmitOnOpenOrCancelAndSubmitsOnceOnConfirm() {
        val entries = listOf(entry("alpha.txt", setOf(StorageCapability.DELETE)))
        var deleteCalls = 0
        composeRule.setContent {
            SelectionHarness(entries, onDeleteSelected = { deleteCalls++ })
        }

        composeRule.onNodeWithTag("files.entry.alpha.txt")
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.onNodeWithTag("files.action.delete").performClick()
        assertEquals(0, deleteCalls)
        composeRule.onNodeWithTag("files.dialog.delete.cancel").performClick()
        assertEquals(0, deleteCalls)

        composeRule.onNodeWithTag("files.action.delete").performClick()
        composeRule.onNodeWithTag("files.dialog.delete.confirm").performClick()
        assertEquals(1, deleteCalls)
    }

    @Test
    fun distinctEntryRefsWithCollidingHashCodesRenderAsDistinctRows() {
        val entries = listOf(
            entry("first.txt", emptySet(), CollisionEntryRef("first")),
            entry("second.txt", emptySet(), CollisionEntryRef("second")),
        )
        composeRule.setContent {
            OmniFileTheme {
                FilesScreen(
                    state = content(entries),
                    onSelectLocal = {},
                    onPickTree = {},
                    onOpenDirectory = {},
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

        composeRule.onNodeWithText("first.txt").assertExists()
        composeRule.onNodeWithText("second.txt").assertExists()
    }

    @Composable
    private fun SelectionHarness(
        entries: List<StorageEntry>,
        onRenameSelected: (String) -> Unit = {},
        onDeleteSelected: () -> Unit = {},
    ) {
        var selectedRefs by remember { mutableStateOf<Set<EntryRef>>(emptySet()) }
        val root = rootEntry()
        val state = content(
            entries = entries,
            selection = selectedRefs.takeIf { it.isNotEmpty() }?.let {
                FilesSelectionState(ProviderId("ui-test"), root.ref, it)
            },
        )
        OmniFileTheme {
            FilesScreen(
                state = state,
                onSelectLocal = {},
                onPickTree = {},
                onOpenDirectory = {},
                onEnterSelection = { selectedRefs = setOf(it.ref) },
                onToggleSelection = { entry ->
                    selectedRefs = if (entry.ref in selectedRefs) selectedRefs - entry.ref else selectedRefs + entry.ref
                },
                onClearSelection = { selectedRefs = emptySet() },
                onRenameSelected = onRenameSelected,
                onDeleteSelected = onDeleteSelected,
                mutationInFlight = false,
                onBack = {},
                onRetry = {},
            )
        }
    }

    private fun content(
        entries: List<StorageEntry>,
        selection: FilesSelectionState? = null,
    ) = FilesUiState.Content(rootEntry(), entries, listOf("Local files"), selection)

    private fun rootEntry() = StorageEntry(
        ref = TestEntryRef(ProviderId("ui-test"), "root"),
        displayName = "Local files",
        kind = EntryKind.DIRECTORY,
        sizeBytes = null,
        modifiedAtEpochMillis = null,
        mimeType = null,
        capabilities = setOf(StorageCapability.LIST_CHILDREN),
    )

    private fun entry(
        name: String,
        capabilities: Set<StorageCapability>,
        ref: EntryRef = TestEntryRef(ProviderId("ui-test"), name),
    ) = StorageEntry(
        ref = ref,
        displayName = name,
        kind = EntryKind.FILE,
        sizeBytes = 1L,
        modifiedAtEpochMillis = null,
        mimeType = "text/plain",
        capabilities = capabilities,
        parentRef = rootEntry().ref,
    )

    private data class TestEntryRef(
        override val providerId: ProviderId,
        val token: String,
    ) : EntryRef {
        override val identityKey: String = "${providerId.value}\u0000$token"
    }

    private class CollisionEntryRef(private val token: String) : EntryRef {
        override val providerId = ProviderId("ui-test")
        override val identityKey: String = "${providerId.value}\u0000$token"

        override fun hashCode(): Int = 7

        override fun equals(other: Any?): Boolean = other is CollisionEntryRef && token == other.token
    }
}
