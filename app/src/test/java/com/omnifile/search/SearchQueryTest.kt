package com.omnifile.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {
    @Test
    fun normalizesOnlyOuterWhitespace() {
        assertEquals("日本語 .txt", SearchQuery.normalize("  日本語 .txt  "))
        assertEquals("", SearchQuery.normalize(" \t\n "))
    }

    @Test
    fun matchesCaseInsensitivelyWithoutFuzzyMatching() {
        assertTrue(SearchQuery.matches("Résumé.TXT", "sumé"))
        assertTrue(SearchQuery.matches("報告書_日本語.txt", "日本語"))
        assertFalse(SearchQuery.matches("report.txt", "rprt"))
        assertFalse(SearchQuery.matches("report.txt", ""))
    }
}
