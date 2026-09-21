package com.omnifile.search

import com.omnifile.storage.EntryKind
import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError

/** Scope semantics are independent from the surface that opened Search. */
sealed interface SearchScope {
    /** All roots currently available through OmniFile's supported-root registry. */
    data object ThisDevice : SearchScope

    /** The directory is the search root; matches are descendants of this entry. */
    data class CurrentFolder(
        val providerId: ProviderId,
        val directory: StorageEntry,
    ) : SearchScope {
        init {
            require(directory.ref.providerId == providerId) { "Search scope provider must match the directory provider" }
            require(directory.kind == EntryKind.DIRECTORY) { "Search scope must start at a directory" }
        }
    }
}

data class SearchRootFailure(
    val rootId: String,
    val label: String,
    val error: StorageError,
)

data class SearchRootResolution(
    val roots: List<StorageEntry>,
    val failures: List<SearchRootFailure> = emptyList(),
)

data class SearchRequest(val scope: SearchScope, val query: String)

data class SearchHit(
    val entry: StorageEntry,
    /** Directory entries from the search root through the hit's containing directory. */
    val ancestors: List<StorageEntry>,
) {
    val providerId: ProviderId get() = entry.ref.providerId
    val identity: EntryRef get() = entry.ref
}

data class SearchSubtreeFailure(val directory: StorageEntry, val error: StorageError)

sealed interface SearchEmission {
    data class Batch(
        val hits: List<SearchHit>,
        val failures: List<SearchSubtreeFailure>,
        val entriesVisited: Int,
        val directoriesVisited: Int,
    ) : SearchEmission

    data class Completed(
        val hits: List<SearchHit>,
        val failures: List<SearchSubtreeFailure>,
        val entriesVisited: Int,
        val directoriesVisited: Int,
        val complete: Boolean,
        val truncated: Boolean,
        val rootError: StorageError? = null,
    ) : SearchEmission
}

sealed interface SearchUiState {
    data class Idle(val scope: SearchScope? = null) : SearchUiState

    data class Searching(
        val query: String,
        val scope: SearchScope,
        val hits: List<SearchHit> = emptyList(),
        val failures: List<SearchSubtreeFailure> = emptyList(),
        val rootFailures: List<SearchRootFailure> = emptyList(),
        val entriesVisited: Int = 0,
        val directoriesVisited: Int = 0,
    ) : SearchUiState

    data class Results(
        val query: String,
        val scope: SearchScope,
        val hits: List<SearchHit>,
        val failures: List<SearchSubtreeFailure>,
        val entriesVisited: Int,
        val directoriesVisited: Int,
        val complete: Boolean,
        val truncated: Boolean,
        val rootFailures: List<SearchRootFailure> = emptyList(),
    ) : SearchUiState

    data class Error(
        val query: String,
        val scope: SearchScope,
        val rootError: StorageError,
        val hits: List<SearchHit> = emptyList(),
        val failures: List<SearchSubtreeFailure> = emptyList(),
        val rootFailures: List<SearchRootFailure> = emptyList(),
    ) : SearchUiState
}
