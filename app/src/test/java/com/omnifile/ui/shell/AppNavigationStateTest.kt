package com.omnifile.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun selectingAnotherTopLevelDismissesDetailWithoutCyclingHistory() {
        val files = AppNavigationState().openFiles(FilesOrigin.HOME)
        val music = files.selectTopLevel(TopLevelDestination.MUSIC)

        assertEquals(AppNavigationState(topLevel = TopLevelDestination.MUSIC), music)
        assertEquals(AppNavigationState(topLevel = TopLevelDestination.SETTINGS), music.selectTopLevel(TopLevelDestination.SETTINGS))
    }
}
