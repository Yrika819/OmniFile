package com.omnifile.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationStateTest {
    @Test
    fun defaultRouteIsHomeAndTopLevelSelectionDoesNotCreateBackHistory() {
        val initial = AppNavigationState()

        assertEquals(TopLevelDestination.HOME, initial.topLevel)
        assertNull(initial.detail)
        assertEquals(
            AppNavigationState(topLevel = TopLevelDestination.SETTINGS),
            initial.selectTopLevel(TopLevelDestination.SETTINGS),
        )
        assertEquals(
            AppNavigationState(topLevel = TopLevelDestination.HOME),
            initial.selectTopLevel(TopLevelDestination.HOME),
        )
    }

    @Test
    fun filesOpenedFromHomeReturnsHomeAtProviderRoot() {
        val files = AppNavigationState().openFiles(FilesOrigin.HOME)

        assertEquals(DetailSurface.FILES, files.detail)
        assertEquals(AppNavigationState(), files.closeFilesAtRoot())
    }

    @Test
    fun contextualFilesResultReturnsToContextualSearchAtItsBoundary() {
        val files = AppNavigationState().openContextualSearch()
            .openFiles(FilesOrigin.CONTEXTUAL_SEARCH)

        assertEquals(
            AppNavigationState(detail = DetailSurface.CONTEXTUAL_SEARCH),
            files.closeFilesAtRoot(),
        )
    }

    @Test
    fun filesOpenedFromSearchReturnsSearchAtProviderRoot() {
        val files = AppNavigationState(topLevel = TopLevelDestination.SEARCH)
            .openFiles(FilesOrigin.TOP_LEVEL_SEARCH)

        assertEquals(
            AppNavigationState(topLevel = TopLevelDestination.SEARCH),
            files.closeFilesAtRoot(),
        )
    }

    @Test
    fun contextualSearchIsADataSurfaceNotAnotherTopLevelDestination() {
        val search = AppNavigationState().openContextualSearch()

        assertEquals(TopLevelDestination.HOME, search.topLevel)
        assertEquals(DetailSurface.CONTEXTUAL_SEARCH, search.detail)
        assertEquals(
            AppNavigationState(detail = DetailSurface.FILES, filesOrigin = FilesOrigin.HOME),
            search.returnToFilesFromContextualSearch(),
        )
    }

    @Test
    fun contextualSearchOpenedFromSearchReturnsToSearchOwnedFiles() {
        val search = AppNavigationState(topLevel = TopLevelDestination.SEARCH)
            .openContextualSearch()

        assertEquals(
            AppNavigationState(
                topLevel = TopLevelDestination.SEARCH,
                detail = DetailSurface.FILES,
                filesOrigin = FilesOrigin.TOP_LEVEL_SEARCH,
            ),
            search.returnToFilesFromContextualSearch(),
        )
    }

    @Test
    fun archiveOpenedFromFilesReturnsToTheSameFilesOrigin() {
        val archive = AppNavigationState(topLevel = TopLevelDestination.SEARCH)
            .openFiles(FilesOrigin.TOP_LEVEL_SEARCH)
            .openArchive(ArchiveOrigin.FILES_TOP_LEVEL_SEARCH)

        assertEquals(DetailSurface.ARCHIVE, archive.detail)
        assertEquals(
            AppNavigationState(
                topLevel = TopLevelDestination.SEARCH,
                detail = DetailSurface.FILES,
                filesOrigin = FilesOrigin.TOP_LEVEL_SEARCH,
            ),
            archive.closeArchive(),
        )
    }

    @Test
    fun archiveOpenedDirectlyFromSearchReturnsToSearch() {
        val archive = AppNavigationState(topLevel = TopLevelDestination.SEARCH)
            .openArchive(ArchiveOrigin.TOP_LEVEL_SEARCH)

        assertEquals(AppNavigationState(topLevel = TopLevelDestination.SEARCH), archive.closeArchive())
    }

    @Test
    fun previewReturnsToTheExactFilesOrArchiveDetail() {
        val files = AppNavigationState(topLevel = TopLevelDestination.SEARCH)
            .openFiles(FilesOrigin.TOP_LEVEL_SEARCH)
        assertEquals(files, files.openPreview().closePreview())

        val archive = files.openArchive(ArchiveOrigin.FILES_TOP_LEVEL_SEARCH)
        assertEquals(archive, archive.openPreview().closePreview())
    }

    @Test
    fun previewOpenedFromTopLevelSearchReturnsToSearchWithItsScope() {
        val search = AppNavigationState(topLevel = TopLevelDestination.SEARCH)
        val preview = search.openPreview()

        assertEquals(DetailSurface.PREVIEW, preview.detail)
        assertEquals(search, preview.closePreview())
    }

    @Test
    fun selectingAnotherTopLevelDismissesDetailWithoutCyclingHistory() {
        val files = AppNavigationState().openFiles(FilesOrigin.HOME)
        val music = files.selectTopLevel(TopLevelDestination.MUSIC)

        assertEquals(AppNavigationState(topLevel = TopLevelDestination.MUSIC), music)
        assertEquals(
            AppNavigationState(topLevel = TopLevelDestination.SETTINGS),
            music.selectTopLevel(TopLevelDestination.SETTINGS)
        )
    }

    @Test
    fun fileDetailsIsReachableFromFilesAndBackReturnsToTheSameFilesContext() {
        val files = AppNavigationState(topLevel = TopLevelDestination.SEARCH)
            .openFiles(FilesOrigin.TOP_LEVEL_SEARCH)
        val details = files.openFileDetails()

        assertEquals(DetailSurface.FILE_DETAILS, details.detail)
        assertEquals(DetailSurface.FILES, details.fileDetailsOrigin)
        assertEquals("Back must restore the exact Files surface", files, details.closeFileDetails())
    }

    @Test
    fun fileDetailsCannotBeReachedFromAnySurfaceOtherThanFiles() {
        val fromHome = AppNavigationState()
        val fromArchive = fromHome.openArchive(ArchiveOrigin.TOP_LEVEL_SEARCH)
        val fromPreview = fromHome.openPreview()

        listOf(fromHome, fromArchive, fromPreview).forEach { state ->
            assertTrue(
                "File Details must not open from ${state.detail}",
                runCatching { state.openFileDetails() }.isFailure,
            )
        }
    }

    @Test
    fun fileDetailsCannotBeOpenedTwiceOrReachPreview() {
        val details = AppNavigationState().openFiles(FilesOrigin.HOME).openFileDetails()

        assertTrue(runCatching { details.openFileDetails() }.isFailure)
        assertTrue(runCatching { details.openPreview() }.isFailure)
    }

    @Test
    fun leavingFileDetailsForAnotherDestinationDoesNotLeaveItInRouteState() {
        val details = AppNavigationState().openFiles(FilesOrigin.HOME).openFileDetails()

        val music = details.selectTopLevel(TopLevelDestination.MUSIC)
        assertNull(music.fileDetailsOrigin)
        assertNull(music.detail)

        val reopened = AppNavigationState().openFiles(FilesOrigin.HOME).openFileDetails()
        assertNull(reopened.openArchive(ArchiveOrigin.FILES_HOME).fileDetailsOrigin)
        assertNull(reopened.closeDetail().fileDetailsOrigin)
        assertNull(reopened.openFiles(FilesOrigin.HOME).fileDetailsOrigin)
    }
}
