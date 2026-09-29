package com.omnifile.ui.search

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omnifile.search.SearchHit
import com.omnifile.search.SearchScope
import com.omnifile.search.SearchUiState
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
class SearchScreenComposeInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resultsShowScopeDuplicateContextAndNavigateByResultIdentity() {
        val root = entry("Documents", EntryKind.DIRECTORY)
        val firstFolder = entry("First", EntryKind.DIRECTORY, root)
        val secondFolder = entry("Second", EntryKind.DIRECTORY, root)
        val first = entry("report.txt", EntryKind.FILE, firstFolder)
        val second = entry("report.txt", EntryKind.FILE, secondFolder)
        val hits = listOf(SearchHit(first, listOf(root, firstFolder)), SearchHit(second, listOf(root, secondFolder)))
        var opened: SearchHit? = null
        composeRule.setContent {
            OmniFileTheme {
                SearchScreen(
                    state = SearchUiState.Results(
                        query = "report",
                        scope = SearchScope.CurrentFolder(root.ref.providerId, root),
                        hits = hits,
                        failures = emptyList(),
                        entriesVisited = 4,
                        directoriesVisited = 3,
                        complete = true,
                        truncated = false,
                    ),
                    onBack = {},
                    onQueryChanged = {},
                    onSubmitQuery = {},
                    onClearQuery = {},
                    onOpenResult = { opened = it },
                )
            }
        }

        composeRule.onNodeWithTag("search.scope.current").assertExists()
        composeRule.onNodeWithText("Current folder · Documents").assertExists()
        composeRule.onNodeWithText("Documents / First", substring = true).assertExists()
        composeRule.onNodeWithText("Documents / Second", substring = true).assertExists()
        composeRule.onNodeWithTag("search.result.1").performClick()
        assertEquals(second.ref, opened?.entry?.ref)
    }

    @Test
    fun archiveSearchResultInvokesOpenResultCallback() {
        val root = rootEntry()
        val archive = entry("fixture.zip", EntryKind.FILE, root)
        val hit = SearchHit(archive, listOf(root))
        var opened: SearchHit? = null
        composeRule.setContent {
            OmniFileTheme {
                SearchScreen(
                    state = SearchUiState.Results(
                        query = "fixture",
                        scope = SearchScope.CurrentFolder(root.ref.providerId, root),
                        hits = listOf(hit),
                        failures = emptyList(),
                        entriesVisited = 1,
                        directoriesVisited = 1,
                        complete = true,
                        truncated = false,
                    ),
                    onBack = {},
                    onQueryChanged = {},
                    onSubmitQuery = {},
                    onClearQuery = {},
                    onOpenResult = { opened = it },
                )
            }
        }

        composeRule.onNodeWithTag("search.result.0").performClick()
        assertEquals(archive.ref, opened?.entry?.ref)
    }

    @Test
    fun idleSearchStillShowsTheCurrentFolderScope() {
        val root = rootEntry()
        composeRule.setContent {
            OmniFileTheme {
                SearchScreen(
                    state = SearchUiState.Idle(SearchScope.CurrentFolder(root.ref.providerId, root)),
                    onBack = {},
                    onQueryChanged = {},
                    onSubmitQuery = {},
                    onClearQuery = {},
                    onOpenResult = {},
                )
            }
        }

        composeRule.onNodeWithTag("search.scope.current").assertExists()
        composeRule.onNodeWithText("Current folder · Documents").assertExists()
    }

    @Test
    fun queryClearAndBackAreExposedAsIndependentActions() {
        // SearchScreen composes its clear affordance conditionally on the query
        // (SearchScreen.kt: `if (query.isNotEmpty())`). A plain captured var is
        // not observable, so the composition never recomposes when the query
        // changes and the clear button's presence is evaluated against a stale
        // value. Whether the click then reached a live node depended on an
        // unrelated recomposition happening to land first, which made this test
        // fail intermittently across API levels. Use snapshot state, matching
        // FilesScreenComposeInstrumentedTest, so the screen is driven the way
        // the real observable state drives it.
        val query = mutableStateOf("initial")
        var backCalls = 0
        composeRule.setContent {
            OmniFileTheme {
                SearchScreen(
                    state = SearchUiState.Results(
                        query = query.value,
                        scope = SearchScope.CurrentFolder(rootEntry().ref.providerId, rootEntry()),
                        hits = emptyList(),
                        failures = emptyList(),
                        entriesVisited = 0,
                        directoriesVisited = 0,
                        complete = true,
                        truncated = false,
                    ),
                    onBack = { backCalls++ },
                    onQueryChanged = { query.value = it },
                    onSubmitQuery = {},
                    onClearQuery = { query.value = "" },
                    onOpenResult = {},
                )
            }
        }

        composeRule.onNodeWithTag("search.query").performTextReplacement("changed")
        composeRule.onNodeWithTag("search.clear").performClick()
        composeRule.onNodeWithTag("search.back").performClick()
        assertEquals(1, backCalls)
        assertEquals("", query.value)
    }

    private fun rootEntry() = entry("Documents", EntryKind.DIRECTORY)

    private fun entry(name: String, kind: EntryKind, parent: StorageEntry? = null) = StorageEntry(
        ref = TestEntryRef(ProviderId("ui-search"), "${parent?.ref?.identityKey ?: "root"}/$name"),
        displayName = name,
        kind = kind,
        sizeBytes = if (kind == EntryKind.FILE) 10 else null,
        modifiedAtEpochMillis = null,
        mimeType = if (kind == EntryKind.FILE) "text/plain" else null,
        capabilities = if (kind == EntryKind.DIRECTORY) setOf(StorageCapability.LIST_CHILDREN) else emptySet(),
        parentRef = parent?.ref,
    )

    private data class TestEntryRef(
        override val providerId: ProviderId,
        val token: String,
    ) : EntryRef {
        override val identityKey: String = "${providerId.value}\u0000$token"
    }
}
