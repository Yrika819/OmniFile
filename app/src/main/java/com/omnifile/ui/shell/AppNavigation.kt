package com.omnifile.ui.shell

/** Detail surfaces are owned by the shell but are not top-level destinations. */
enum class DetailSurface {
    FILES,
    CONTEXTUAL_SEARCH,
}

enum class FilesOrigin {
    HOME,
    TOP_LEVEL_SEARCH,
    CONTEXTUAL_SEARCH,
}

data class AppNavigationState(
    val topLevel: TopLevelDestination = TopLevelDestination.HOME,
    val detail: DetailSurface? = null,
    val filesOrigin: FilesOrigin? = null,
) {
    init {
        require(detail == DetailSurface.FILES || filesOrigin == null) {
            "Files origin is only valid for the Files detail surface"
        }
    }

    fun selectTopLevel(destination: TopLevelDestination): AppNavigationState =
        copy(topLevel = destination, detail = null, filesOrigin = null)

    fun openFiles(origin: FilesOrigin): AppNavigationState =
        copy(detail = DetailSurface.FILES, filesOrigin = origin)

    fun openContextualSearch(): AppNavigationState =
        copy(detail = DetailSurface.CONTEXTUAL_SEARCH, filesOrigin = null)

    fun closeDetail(): AppNavigationState =
        copy(detail = null, filesOrigin = null)

    /** Contextual Search is opened from Files and must return to that Files surface. */
    fun returnToFilesFromContextualSearch(): AppNavigationState = copy(
        detail = DetailSurface.FILES,
        filesOrigin = if (topLevel == TopLevelDestination.SEARCH) {
            FilesOrigin.TOP_LEVEL_SEARCH
        } else {
            FilesOrigin.HOME
        },
    )

    /** Returns to the owning top-level destination when Files is at its provider root. */
    fun closeFilesAtRoot(): AppNavigationState = when (filesOrigin) {
        FilesOrigin.TOP_LEVEL_SEARCH -> copy(
            topLevel = TopLevelDestination.SEARCH,
            detail = null,
            filesOrigin = null,
        )
        FilesOrigin.CONTEXTUAL_SEARCH -> copy(
            detail = DetailSurface.CONTEXTUAL_SEARCH,
            filesOrigin = null,
        )
        FilesOrigin.HOME, null -> copy(
            topLevel = TopLevelDestination.HOME,
            detail = null,
            filesOrigin = null,
        )
    }
}
