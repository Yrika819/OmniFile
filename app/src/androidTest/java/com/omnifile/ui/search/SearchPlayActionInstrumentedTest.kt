package com.omnifile.ui.search

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Search Play affordance: eligible audio results expose Play that routes to the
 * same playback command, navigation behaviour is unchanged, and non-audio
 * results never claim playability.
 */
@RunWith(AndroidJUnit4::class)
class SearchPlayActionInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playActionAppearsOnlyForEligibleAudioResults() {
        val audio = audioEntry("tone.wav")
        val text = textEntry("notes.txt")
        var played: SearchHit? = null
        var opened: SearchHit? = null
        composeRule.setContent {
            OmniFileTheme {
                SearchScreen(
                    state = results(listOf(SearchHit(audio, emptyList()), SearchHit(text, emptyList()))),
                    onBack = {},
                    onQueryChanged = {},
                    onSubmitQuery = {},
                    onClearQuery = {},
                    onOpenResult = { opened = it },
                    onPlayResult = { played = it },
                )
            }
        }

        composeRule.onNodeWithTag("search.play.0").assertExists()
        composeRule.onNodeWithTag("search.play.1").assertDoesNotExist()

        composeRule.onNodeWithTag("search.play.0").performClick()
        assertEquals("tone.wav", played?.entry?.displayName)
        assertNull(opened)

        composeRule.onNodeWithTag("search.result.0").performClick()
        assertEquals("tone.wav", opened?.entry?.displayName)
    }

    @Test
    fun cannotPlayWhenNoPlayCallbackIsProvided() {
        val audio = audioEntry("tone.wav")
        composeRule.setContent {
            OmniFileTheme {
                SearchScreen(
                    state = results(listOf(SearchHit(audio, emptyList()))),
                    onBack = {},
                    onQueryChanged = {},
                    onSubmitQuery = {},
                    onClearQuery = {},
                    onOpenResult = {},
                )
            }
        }

        composeRule.onNodeWithTag("search.play.0").assertExists()
        composeRule.onNodeWithTag("search.play.0").performClick()
        composeRule.onNodeWithText("tone.wav").assertExists()
    }

    private fun results(hits: List<SearchHit>) = SearchUiState.Results(
        query = "tone",
        scope = SearchScope.ThisDevice,
        hits = hits,
        failures = emptyList(),
        entriesVisited = hits.size,
        directoriesVisited = 0,
        complete = true,
        truncated = false,
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
        override val providerId: ProviderId = ProviderId("search-test")
        override val identityKey: String = "search-test/$objectId"
    }
}
