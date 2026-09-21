package com.omnifile.search

import java.util.Locale

object SearchQuery {
    fun normalize(raw: String): String = raw.trim()

    fun matches(displayName: String, normalizedQuery: String): Boolean {
        if (normalizedQuery.isEmpty()) return false
        return displayName.lowercase(Locale.ROOT).contains(normalizedQuery.lowercase(Locale.ROOT))
    }
}
