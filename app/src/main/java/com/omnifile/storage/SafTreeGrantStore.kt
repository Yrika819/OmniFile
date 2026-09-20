package com.omnifile.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.util.Base64

/** Persisted SAF tree access is an input to capability checks, never an authority by itself. */
data class PersistedTreeGrant(
    val uri: Uri,
    val modeFlags: Int,
) {
    val canRead: Boolean
        get() = modeFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
    val canWrite: Boolean
        get() = modeFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0
}

class SafTreeGrantStore(
    context: Context,
    private val contentResolver: ContentResolver = context.contentResolver,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** Takes only read/write bits present in the returned Intent and verifies the persisted grant. */
    fun persistGrant(uri: Uri, resultFlags: Int): Boolean {
        val requestedFlags = resultFlags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (requestedFlags == 0 || !DocumentsContract.isTreeUri(uri) || uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority.isNullOrBlank()) {
            return false
        }
        return try {
            contentResolver.takePersistableUriPermission(uri, requestedFlags)
            val actual = currentGrant(uri) ?: return false
            val stored = preferences.getStringSet(KEY_TREE_GRANTS, emptySet()).orEmpty().toMutableSet()
            stored.removeIf { decode(it)?.uri == uri }
            stored += encode(PersistedTreeGrant(uri, actual))
            preferences.edit().putStringSet(KEY_TREE_GRANTS, stored).apply()
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    /** Compatibility entry point retained for callers that already have a result flag mask. */
    fun persistReadGrant(uri: Uri, resultFlags: Int): Boolean =
        persistGrant(uri, resultFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun grantFor(uri: Uri): PersistedTreeGrant? = currentGrant(uri)?.let { PersistedTreeGrant(uri, it) }

    fun restoredGrants(): List<PersistedTreeGrant> {
        val persisted = contentResolver.persistedUriPermissions
            .mapNotNull { permission ->
                val flags = buildSet {
                    if (permission.isReadPermission) add(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (permission.isWritePermission) add(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }.fold(0) { result, flag -> result or flag }
                if (flags == 0 || !DocumentsContract.isTreeUri(permission.uri)) null else PersistedTreeGrant(permission.uri, flags)
            }
        if (persisted.isNotEmpty()) return persisted

        // VS03 stored one read-only URI. Revalidate it instead of stranding it on upgrade.
        val legacyUri = preferences.getString(KEY_LEGACY_TREE_URI, null)?.let(Uri::parse)
        return legacyUri?.let { uri -> grantFor(uri) }?.let(::listOf).orEmpty()
    }

    /** Records explicit source-tree intent without granting access by preference alone. */
    fun rememberSelectedReadTree(uri: Uri): Boolean {
        val grant = grantFor(uri) ?: return false
        if (!grant.canRead) return false
        return preferences.edit()
            .putString(KEY_SELECTED_READ_TREE_URI, uri.toString())
            .commit()
    }

    /** Restores explicit source intent, then a unique broad readable tree. */
    fun restoredReadTree(): Uri? {
        val remembered = preferences.getString(KEY_SELECTED_READ_TREE_URI, null)?.let(Uri::parse)
        return selectRestoredReadTree(restoredGrants(), remembered)
    }

    fun restoredWriteTree(): Uri? = restoredGrants().firstOrNull { it.canWrite }?.uri

    private fun currentGrant(uri: Uri): Int? = contentResolver.persistedUriPermissions
        .firstOrNull { it.uri == uri }
        ?.takeIf { DocumentsContract.isTreeUri(it.uri) }
        ?.let { permission ->
            var flags = 0
            if (permission.isReadPermission) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (permission.isWritePermission) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            flags.takeIf { it != 0 }
        }

    private fun encode(grant: PersistedTreeGrant): String {
        val uri = Base64.getUrlEncoder().withoutPadding().encodeToString(grant.uri.toString().toByteArray(Charsets.UTF_8))
        return "$uri.${grant.modeFlags}"
    }

    private fun decode(value: String): PersistedTreeGrant? {
        val separator = value.lastIndexOf('.')
        if (separator <= 0 || separator == value.lastIndex) return null
        return try {
            val uri = Uri.parse(String(Base64.getUrlDecoder().decode(value.substring(0, separator)), Charsets.UTF_8))
            if (!DocumentsContract.isTreeUri(uri)) return null
            PersistedTreeGrant(uri, value.substring(separator + 1).toInt())
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "saf-tree-grants"
        private const val KEY_TREE_GRANTS = "tree-grants-v1"
        private const val KEY_LEGACY_TREE_URI = "selected-tree-uri"
        private const val KEY_SELECTED_READ_TREE_URI = "selected-read-tree-uri-v1"
    }
}


/**
 * Restores only a currently readable, explicitly remembered tree. Without a
 * remembered tree, a unique shallowest tree is safe to restore; an equal-depth
 * tie is intentionally left for explicit user selection rather than resolved by
 * platform permission enumeration order.
 */
internal fun selectRestoredReadTree(
    grants: List<PersistedTreeGrant>,
    rememberedUri: Uri?,
    depthOf: (Uri) -> Int = ::treeDepthForSelection,
): Uri? {
    val readable = grants.filter { it.canRead }
    rememberedUri?.let { remembered ->
        readable.firstOrNull { it.uri.toString() == remembered.toString() }?.let { return it.uri }
    }
    val minimumDepth = readable.minOfOrNull { depthOf(it.uri) } ?: return null
    val candidates = readable.filter { depthOf(it.uri) == minimumDepth }
    return candidates.singleOrNull()?.uri
}

private fun treeDepthForSelection(uri: Uri): Int = try {
    DocumentsContract.getTreeDocumentId(uri).count { it == '/' }
} catch (_: IllegalArgumentException) {
    Int.MAX_VALUE
}
