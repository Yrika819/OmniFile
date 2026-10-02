package com.omnifile.ui.shell

/** Detail surfaces are owned by the shell but are not top-level destinations. */
enum class DetailSurface {
    FILES,
    CONTEXTUAL_SEARCH,
    ARCHIVE,
    PREVIEW,
    FILE_DETAILS,
}

enum class ArchiveOrigin {
    FILES_HOME,
    FILES_TOP_LEVEL_SEARCH,
    FILES_CONTEXTUAL_SEARCH,
    TOP_LEVEL_SEARCH,
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
    val archiveOrigin: ArchiveOrigin? = null,
    val previewOrigin: DetailSurface? = null,
    /**
     * Which surface owns the current File Details destination. Only [DetailSurface.FILES] is
     * legal, because the entry point is Files selection only. Like every other field here this
     * is an enum name: no URI, path, document id, or StorageEntry ever enters route state.
     */
    val fileDetailsOrigin: DetailSurface? = null,
) {
    init {
        val filesBacked = detail == DetailSurface.FILES ||
                detail == DetailSurface.FILE_DETAILS ||
                (detail == DetailSurface.PREVIEW && previewOrigin == DetailSurface.FILES)
        require(filesBacked || filesOrigin == null) {
            "Files origin is only valid for a Files-backed surface"
        }
        require(detail == DetailSurface.ARCHIVE || (detail == DetailSurface.PREVIEW && previewOrigin == DetailSurface.ARCHIVE) || archiveOrigin == null) {
            "Archive origin is only valid for the Archive detail surface"
        }
        require(detail == DetailSurface.PREVIEW || previewOrigin == null) {
            "Preview origin is only valid for the Preview detail surface"
        }
        require(fileDetailsOrigin == null || detail == DetailSurface.FILE_DETAILS) {
            "File Details origin is only valid for the File Details detail surface"
        }
        require(fileDetailsOrigin == null || fileDetailsOrigin == DetailSurface.FILES) {
            "File Details is nested in Files only"
        }
    }

    fun selectTopLevel(destination: TopLevelDestination): AppNavigationState =
        copy(
            topLevel = destination,
            detail = null,
            filesOrigin = null,
            archiveOrigin = null,
            previewOrigin = null,
            fileDetailsOrigin = null
        )

    fun openFiles(origin: FilesOrigin): AppNavigationState =
        copy(
            detail = DetailSurface.FILES,
            filesOrigin = origin,
            archiveOrigin = null,
            previewOrigin = null,
            fileDetailsOrigin = null
        )

    fun openContextualSearch(): AppNavigationState =
        copy(
            detail = DetailSurface.CONTEXTUAL_SEARCH,
            filesOrigin = null,
            archiveOrigin = null,
            previewOrigin = null,
            fileDetailsOrigin = null
        )

    fun closeDetail(): AppNavigationState =
        copy(detail = null, filesOrigin = null, archiveOrigin = null, previewOrigin = null, fileDetailsOrigin = null)

    fun openArchive(origin: ArchiveOrigin): AppNavigationState =
        copy(
            detail = DetailSurface.ARCHIVE,
            filesOrigin = null,
            archiveOrigin = origin,
            previewOrigin = null,
            fileDetailsOrigin = null
        )

    /**
     * File Details is a Files-nested surface. The route carries no source identity; the selected
     * entry stays owned in memory by the Activity-scoped ViewModel.
     */
    fun openFileDetails(): AppNavigationState {
        require(detail == DetailSurface.FILES) { "File Details is reachable from Files only" }
        return copy(detail = DetailSurface.FILE_DETAILS, fileDetailsOrigin = DetailSurface.FILES)
    }

    /** Back returns to the exact Files surface that opened it, keeping its folder context. */
    fun closeFileDetails(): AppNavigationState =
        copy(detail = DetailSurface.FILES, fileDetailsOrigin = null)

    /** Preview is nested inside the current detail or top-level Search destination. */
    fun openPreview(): AppNavigationState {
        require(detail != DetailSurface.PREVIEW)
        require(detail != DetailSurface.FILE_DETAILS) { "File Details does not open Preview" }
        return copy(detail = DetailSurface.PREVIEW, previewOrigin = detail)
    }

    fun closePreview(): AppNavigationState {
        if (detail != DetailSurface.PREVIEW) return this
        return copy(detail = previewOrigin, previewOrigin = null)
    }

    fun closeArchive(): AppNavigationState = when (archiveOrigin) {
        ArchiveOrigin.FILES_HOME -> copy(
            detail = DetailSurface.FILES,
            filesOrigin = FilesOrigin.HOME,
            archiveOrigin = null
        )

        ArchiveOrigin.FILES_TOP_LEVEL_SEARCH -> copy(
            detail = DetailSurface.FILES,
            filesOrigin = FilesOrigin.TOP_LEVEL_SEARCH,
            archiveOrigin = null
        )

        ArchiveOrigin.FILES_CONTEXTUAL_SEARCH -> copy(
            detail = DetailSurface.FILES,
            filesOrigin = FilesOrigin.CONTEXTUAL_SEARCH,
            archiveOrigin = null
        )

        ArchiveOrigin.TOP_LEVEL_SEARCH -> copy(
            topLevel = TopLevelDestination.SEARCH,
            detail = null,
            filesOrigin = null,
            archiveOrigin = null
        )

        ArchiveOrigin.CONTEXTUAL_SEARCH -> copy(
            detail = DetailSurface.CONTEXTUAL_SEARCH,
            filesOrigin = null,
            archiveOrigin = null
        )

        null -> closeDetail()
    }

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
