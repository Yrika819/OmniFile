package com.omnifile.search

import com.omnifile.storage.EntryKind
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.ArrayDeque
import java.util.Locale

class SearchEngine(
    private val batchSize: Int = 32,
    private val maxDepth: Int = 64,
    private val maxEntriesVisited: Int = 100_000,
    private val maxResults: Int = 10_000,
) {
    init {
        require(batchSize > 0)
        require(maxDepth >= 0)
        require(maxEntriesVisited > 0)
        require(maxResults > 0)
    }

    fun search(
        request: SearchRequest,
        roots: suspend (SearchScope) -> StorageResult<List<StorageEntry>>,
        children: suspend (StorageEntry) -> StorageResult<List<StorageEntry>>,
    ): Flow<SearchEmission> = flow {
        val query = SearchQuery.normalize(request.query)
        if (query.isEmpty()) {
            emit(
                SearchEmission.Completed(
                    hits = emptyList(),
                    failures = emptyList(),
                    entriesVisited = 0,
                    directoriesVisited = 0,
                    complete = true,
                    truncated = false,
                ),
            )
            return@flow
        }

        val rootEntries = when (val result = safelyLoadRoots(request.scope, roots)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> {
                if (result.error == StorageError.Cancelled) throw CancellationException("Search root cancelled")
                emit(
                    SearchEmission.Completed(
                        hits = emptyList(),
                        failures = emptyList(),
                        entriesVisited = 0,
                        directoriesVisited = 0,
                        complete = false,
                        truncated = false,
                        rootError = result.error,
                    ),
                )
                return@flow
            }
        }

        data class Frame(
            val directory: StorageEntry,
            val ancestors: List<StorageEntry>,
            val depth: Int,
        )

        val pending = ArrayDeque<Frame>()
        rootEntries
            .filter { it.kind == EntryKind.DIRECTORY }
            .sortedWith(entryComparator)
            .forEach { pending.addLast(Frame(it, emptyList(), 0)) }

        val visited = mutableSetOf<String>()
        val hits = mutableListOf<SearchHit>()
        val failures = mutableListOf<SearchSubtreeFailure>()
        val pendingHits = mutableListOf<SearchHit>()
        val pendingFailures = mutableListOf<SearchSubtreeFailure>()
        var entriesVisited = 0
        var directoriesVisited = 0
        var truncated = false
        var rootError: StorageError? = null

        suspend fun flush() {
            if (pendingHits.isEmpty() && pendingFailures.isEmpty()) return
            emit(
                SearchEmission.Batch(
                    hits = pendingHits.toList(),
                    failures = pendingFailures.toList(),
                    entriesVisited = entriesVisited,
                    directoriesVisited = directoriesVisited,
                ),
            )
            pendingHits.clear()
            pendingFailures.clear()
            currentCoroutineContext().ensureActive()
        }

        while (pending.isNotEmpty() && !truncated) {
            currentCoroutineContext().ensureActive()
            val frame = pending.removeFirst()
            if (!visited.add(frame.directory.ref.identityKey)) continue
            directoriesVisited += 1

            val childEntries = when (val result = safelyListChildren(frame.directory, children)) {
                is StorageResult.Success -> result.value.sortedWith(entryComparator)
                is StorageResult.Failure -> {
                    if (result.error == StorageError.Cancelled) {
                        throw CancellationException("Search subtree cancelled")
                    }
                    if (request.scope is SearchScope.CurrentFolder && frame.ancestors.isEmpty()) {
                        rootError = result.error
                        break
                    }
                    val failure = SearchSubtreeFailure(frame.directory, result.error)
                    failures += failure
                    pendingFailures += failure
                    if (pendingHits.size + pendingFailures.size >= batchSize) flush()
                    continue
                }
            }

            for (child in childEntries) {
                currentCoroutineContext().ensureActive()
                if (entriesVisited >= maxEntriesVisited || hits.size >= maxResults) {
                    truncated = true
                    break
                }
                entriesVisited += 1
                val childAncestors = frame.ancestors + frame.directory
                if (SearchQuery.matches(child.displayName, query) && hits.size < maxResults) {
                    val hit = SearchHit(child, childAncestors)
                    hits += hit
                    pendingHits += hit
                }
                if (child.kind == EntryKind.DIRECTORY) {
                    if (frame.depth >= maxDepth) {
                        truncated = true
                        break
                    }
                    pending.addLast(Frame(child, childAncestors, frame.depth + 1))
                }
                if (pendingHits.size + pendingFailures.size >= batchSize) flush()
            }
        }
        flush()
        currentCoroutineContext().ensureActive()

        emit(
            SearchEmission.Completed(
                hits = hits.toList(),
                failures = failures.toList(),
                entriesVisited = entriesVisited,
                directoriesVisited = directoriesVisited,
                complete = rootError == null && !truncated && failures.isEmpty(),
                truncated = truncated,
                rootError = rootError,
            ),
        )
    }

    private suspend fun safelyLoadRoots(
        scope: SearchScope,
        roots: suspend (SearchScope) -> StorageResult<List<StorageEntry>>,
    ): StorageResult<List<StorageEntry>> = try {
        roots(scope)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (security: SecurityException) {
        StorageResult.Failure(StorageError.PermissionDenied)
    } catch (failure: Exception) {
        StorageResult.Failure(StorageError.IoFailure(failure.message))
    }

    private suspend fun safelyListChildren(
        directory: StorageEntry,
        children: suspend (StorageEntry) -> StorageResult<List<StorageEntry>>,
    ): StorageResult<List<StorageEntry>> = try {
        children(directory)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (security: SecurityException) {
        StorageResult.Failure(StorageError.PermissionDenied)
    } catch (failure: Exception) {
        StorageResult.Failure(StorageError.IoFailure(failure.message))
    }

    private companion object {
        val entryComparator: Comparator<StorageEntry> = compareBy<StorageEntry> {
            it.displayName.lowercase(Locale.ROOT)
        }.thenBy { it.displayName }.thenBy { it.ref.identityKey }
    }
}
