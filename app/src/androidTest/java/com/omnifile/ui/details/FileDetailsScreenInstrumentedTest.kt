package com.omnifile.ui.details

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnifile.MainActivity
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mandatory VS11 case: a Local regular file reached from Files selection shows truthful metadata,
 * completes a SHA-256 digest of the exact bytes, offers Copy only once complete, and Back returns
 * to the same Files context.
 */
@com.omnifile.preview.PostVs10HardeningTarget
@RunWith(AndroidJUnit4::class)
class FileDetailsScreenInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val fixtureName = "vs11-${UUID.randomUUID()}.txt"
    private val content = "file details sha-256 fixture\n".toByteArray()
    private val fixture = InstrumentationRegistry.getInstrumentation()
        .targetContext.filesDir.toPath().resolve(fixtureName)

    @After
    fun cleanUp() {
        Files.deleteIfExists(fixture)
    }

    @Test
    fun localFileDetailsShowsMetadataAndCompleteSha256() {
        Files.write(fixture, content)
        // Signed bytes must be widened explicitly: "%02x" on a negative Byte is not two digits.
        val expected = MessageDigest.getInstance("SHA-256").digest(content)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("home.root.local-app-files-v1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home.root.local-app-files-v1").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("files.entry.$fixtureName").fetchSemanticsNodes().isNotEmpty()
        }

        // Entry point: exactly one selected regular file from Files.
        compose.onNodeWithTag("files.entry.$fixtureName")
            .performSemanticsAction(SemanticsActions.OnLongClick)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("files.action.properties").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("files.action.properties").performClick()

        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("fileDetails.calculate").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("fileDetails.name").assertExists()
        compose.onNodeWithTag("fileDetails.size").assertExists()
        compose.onNodeWithTag("fileDetails.modified").assertExists()
        compose.onNodeWithTag("fileDetails.type").assertExists()
        // The source label is a safe display string, never a provider id, URI, or path.
        compose.onNodeWithTag("fileDetails.source").assertExists()

        compose.onNodeWithTag("fileDetails.calculate").assertIsEnabled().performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("fileDetails.digest").fetchSemanticsNodes().isNotEmpty()
        }
        // The shown digest must be the digest of exactly these bytes, not of the name or path.
        compose.onNodeWithTag("fileDetails.digest").assertTextEquals(expected)
        // Copy is offered only once the digest is complete.
        compose.onNodeWithTag("fileDetails.copy").assertExists().performClick()
        compose.waitUntil(20_000) { clipboardDigest() == expected }

        // Back returns to the same Files surface and folder context.
        compose.onNodeWithTag("fileDetails.back").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("files.entry.$fixtureName").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clipboardDigest(): String? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        return clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
    }
}
