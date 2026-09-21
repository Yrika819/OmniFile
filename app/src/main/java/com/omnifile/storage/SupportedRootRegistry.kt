package com.omnifile.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.security.MessageDigest

/** A provider-neutral source that OmniFile can currently browse and search. */
enum class SupportedRootSource { LOCAL, SAF }

data class SupportedRoot(
    val id: String,
    val providerId: ProviderId,
    val label: String,
    val source: SupportedRootSource,
    val entry: StorageEntry,
)

data class SupportedRootFailure(
    val id: String,
    val label: String,
    val source: SupportedRootSource,
    val error: StorageError,
)

data class SupportedRootSnapshot(
    val roots: List<SupportedRoot>,
    val aggregationRoots: List<SupportedRoot>,
    val failures: List<SupportedRootFailure>,
) {
    val isComplete: Boolean
        get() = failures.isEmpty()
}

data class SafRootCandidate(
    val id: String,
    val label: String,
    val uri: Uri,
    val provider: StorageProvider?,
    val initialError: StorageError? = null,
)

/**
 * Enumerates only roots that have a live provider contract. It is deliberately
 * separate from SearchEngine so generic traversal never learns about Uri/Path.
 */
class SupportedRootRegistry(
    private val localProvider: StorageProvider,
    private val safCandidates: () -> List<SafRootCandidate>,
    private val contentResolver: ContentResolver? = null,
    private val provenContains: ((ancestor: Uri, descendant: Uri) -> Boolean)? = null,
) {
    suspend fun snapshot(): SupportedRootSnapshot {
        val resolved = mutableListOf<ResolvedRoot>()
        val failures = mutableListOf<SupportedRootFailure>()
        val containsSafRoot: (Uri, Uri) -> Boolean = provenContains ?: { ancestor, descendant ->
            provenContainsWithResolver(ancestor, descendant)
        }

        when (val result = safelyRoot(localProvider)) {
            is StorageResult.Success -> if (result.value.kind == EntryKind.DIRECTORY) {
                resolved += ResolvedRoot(
                    SupportedRoot(
                        id = LOCAL_ROOT_ID,
                        providerId = result.value.ref.providerId,
                        label = "OmniFile storage",
                        source = SupportedRootSource.LOCAL,
                        entry = result.value,
                    ),
                    uri = null,
                )
            } else {
                failures += SupportedRootFailure(LOCAL_ROOT_ID, "Local storage", SupportedRootSource.LOCAL, StorageError.StaleReference)
            }
            is StorageResult.Failure -> failures += SupportedRootFailure(
                LOCAL_ROOT_ID,
                "Local storage",
                SupportedRootSource.LOCAL,
                result.error,
            )
        }

        val seenUris = mutableSetOf<String>()
        safCandidates().sortedBy { it.id }.forEach { candidate ->
            if (!seenUris.add(candidate.uri.toString())) return@forEach
            val provider = candidate.provider
            if (provider == null) {
                failures += SupportedRootFailure(
                    candidate.id,
                    candidate.label,
                    SupportedRootSource.SAF,
                    candidate.initialError ?: StorageError.PermissionDenied,
                )
                return@forEach
            }
            when (val result = safelyRoot(provider)) {
                is StorageResult.Success -> if (result.value.kind == EntryKind.DIRECTORY) {
                    resolved += ResolvedRoot(
                        SupportedRoot(
                            id = candidate.id,
                            providerId = result.value.ref.providerId,
                            label = candidate.label,
                            source = SupportedRootSource.SAF,
                            entry = result.value,
                        ),
                        uri = candidate.uri,
                    )
                } else {
                    failures += SupportedRootFailure(candidate.id, candidate.label, SupportedRootSource.SAF, StorageError.StaleReference)
                }
                is StorageResult.Failure -> failures += SupportedRootFailure(
                    candidate.id,
                    candidate.label,
                    SupportedRootSource.SAF,
                    result.error,
                )
            }
        }

        val aggregation = resolved.toMutableList()
        val remove = mutableSetOf<String>()
        for (ancestor in resolved) {
            for (descendant in resolved) {
                if (ancestor.root.id == descendant.root.id || ancestor.uri == null || descendant.uri == null) continue
                if (containsSafRoot(ancestor.uri, descendant.uri)) remove += descendant.root.id
            }
        }
        aggregation.removeAll { it.root.id in remove }
        return SupportedRootSnapshot(
            roots = resolved.map { it.root },
            aggregationRoots = aggregation.map { it.root },
            failures = failures,
        )
    }

    private suspend fun safelyRoot(provider: StorageProvider): StorageResult<StorageEntry> = try {
        provider.root()
    } catch (_: SecurityException) {
        StorageResult.Failure(StorageError.PermissionDenied)
    } catch (failure: Exception) {
        StorageResult.Failure(StorageError.IoFailure(failure.message))
    }

    private fun provenContainsWithResolver(ancestor: Uri, descendant: Uri): Boolean {
        val resolver = contentResolver ?: return false
        return try {
            val ancestorDocument = DocumentsContract.buildDocumentUriUsingTree(
                ancestor,
                DocumentsContract.getTreeDocumentId(ancestor),
            )
            val descendantDocument = DocumentsContract.buildDocumentUriUsingTree(
                descendant,
                DocumentsContract.getTreeDocumentId(descendant),
            )
            DocumentsContract.isChildDocument(resolver, ancestorDocument, descendantDocument)
        } catch (_: RuntimeException) {
            false
        }
    }

    private data class ResolvedRoot(val root: SupportedRoot, val uri: Uri?)

    companion object {
        const val LOCAL_ROOT_ID = "local-app-files-v1"

        fun safRootId(uri: Uri): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
            return "saf-" + digest.joinToString("") { "%02x".format(it) }.take(24)
        }
    }
}
