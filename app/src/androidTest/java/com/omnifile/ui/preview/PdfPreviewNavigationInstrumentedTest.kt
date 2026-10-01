package com.omnifile.ui.preview

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnifile.MainActivity
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.UUID
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfPreviewNavigationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val fixtureName = "vs10-${UUID.randomUUID()}.pdf"
    private val fixture = InstrumentationRegistry.getInstrumentation().targetContext.filesDir.toPath().resolve(fixtureName)

    @After
    fun cleanUp() {
        Files.deleteIfExists(fixture)
    }

    @Test
    fun filesPreviewKeepsCurrentPdfPageAcrossRecreationAndBackReturnsToFiles() {
        Files.write(fixture, syntheticPdf(2))
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("home.root.local-app-files-v1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home.root.local-app-files-v1").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("files.entry.$fixtureName").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("files.entry.$fixtureName").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("PDF page 1 of 2 for $fixtureName").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Next page").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("2 / 2").fetchSemanticsNodes().isNotEmpty()
        }

        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("PDF page 2 of 2 for $fixtureName").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("2 / 2").assertExists()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("files.entry.$fixtureName").assertExists()
    }

    @Test
    fun searchPreviewBackReturnsToTheSearchResults() {
        Files.write(fixture, syntheticPdf(1))
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Search").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithTag("search.query").performTextInput(fixtureName)
        compose.onNodeWithTag("search.query").performImeAction()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("search.result.0").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("search.result.0").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("PDF page 1 of 1 for $fixtureName").fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("search.result.0").assertExists()
        compose.onNodeWithTag("search.query").assertExists()
    }

    @Test
    fun filesEmbeddedPdfPngUsesImagePreview() {
        Files.write(fixture, com.omnifile.preview.embeddedPdfPng())
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("home.root.local-app-files-v1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home.root.local-app-files-v1").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("files.entry.$fixtureName").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("files.entry.$fixtureName").performClick()
        assertImagePreview()
    }

    @Test
    fun searchEmbeddedPdfPngUsesImagePreview() {
        Files.write(fixture, com.omnifile.preview.embeddedPdfPng())
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Search").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithTag("search.query").performTextInput(fixtureName)
        compose.onNodeWithTag("search.query").performImeAction()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("search.result.0").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("search.result.0").performClick()
        assertImagePreview()
    }

    private fun assertImagePreview() {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("Preview of $fixtureName").fetchSemanticsNodes().isNotEmpty()
        }
        org.junit.Assert.assertEquals(0, compose.onAllNodesWithText("corrupt or malformed", substring = true).fetchSemanticsNodes().size)
        org.junit.Assert.assertEquals(0, compose.onAllNodesWithContentDescription("PDF page", substring = true).fetchSemanticsNodes().size)
    }

    private fun syntheticPdf(pageCount: Int): ByteArray {
        val bodies = mutableListOf<String>()
        val kids = (0 until pageCount).joinToString(" ") { index -> "${3 + index * 2} 0 R" }
        bodies += "<< /Type /Catalog /Pages 2 0 R >>"
        bodies += "<< /Type /Pages /Kids [$kids] /Count $pageCount >>"
        repeat(pageCount) { index ->
            val pageObject = 3 + index * 2
            val streamObject = pageObject + 1
            val content = "q 0.3 0.5 0.8 rg 8 8 104 144 re f Q"
            bodies += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 120 160] /Resources << >> /Contents $streamObject 0 R >>"
            bodies += "<< /Length ${content.toByteArray().size} >>\nstream\n$content\nendstream"
        }
        val output = ByteArrayOutputStream()
        output.write("%PDF-1.4\n".toByteArray())
        val offsets = mutableListOf(0)
        bodies.forEachIndexed { index, body ->
            offsets += output.size()
            output.write("${index + 1} 0 obj\n$body\nendobj\n".toByteArray())
        }
        val xref = output.size()
        output.write("xref\n0 ${bodies.size + 1}\n0000000000 65535 f \n".toByteArray())
        offsets.drop(1).forEach { offset -> output.write("%010d 00000 n \n".format(offset).toByteArray()) }
        output.write("trailer\n<< /Size ${bodies.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n".toByteArray())
        return output.toByteArray()
    }
}
