package com.omnifile.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri

class SafTreeGrantStore(
    context: Context,
    private val contentResolver: ContentResolver = context.contentResolver,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun persistReadGrant(uri: Uri, resultFlags: Int): Boolean {
        val readFlag = resultFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (readFlag == 0) return false
        return try {
            contentResolver.takePersistableUriPermission(uri, readFlag)
            preferences.edit().putString(KEY_TREE_URI, uri.toString()).apply()
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun restoredReadTree(): Uri? {
        val stored = preferences.getString(KEY_TREE_URI, null) ?: return null
        val uri = Uri.parse(stored)
        return contentResolver.persistedUriPermissions
            .firstOrNull { it.uri == uri && it.isReadPermission }
            ?.uri
    }

    companion object {
        private const val PREFERENCES_NAME = "saf-tree-grants"
        private const val KEY_TREE_URI = "selected-tree-uri"
    }
}
