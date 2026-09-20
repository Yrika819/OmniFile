package com.omnifile.storage

import android.content.Intent
import android.net.TestUri
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafTreeGrantStoreTest {
    private val read = Intent.FLAG_GRANT_READ_URI_PERMISSION

    @Test
    fun rememberedEqualDepthTreeWinsRegardlessOfGrantEnumerationOrder() {
        val treeA = TestUri("content://provider/tree/a")
        val treeB = TestUri("content://provider/tree/b")
        val grants = listOf(
            PersistedTreeGrant(treeA, read),
            PersistedTreeGrant(treeB, read),
        )
        val depths = mapOf(treeA.toString() to 1, treeB.toString() to 1)
        val depthOf: (Uri) -> Int = { uri -> depths.getValue(uri.toString()) }

        assertEquals(treeB.toString(), selectRestoredReadTree(grants, treeB, depthOf)?.toString())
        assertEquals(treeB.toString(), selectRestoredReadTree(grants.reversed(), treeB, depthOf)?.toString())
    }

    @Test
    fun aRevokedRememberedTreeIsNotUsedAndAnEqualDepthTieRequiresSelection() {
        val treeA = TestUri("content://provider/tree/a")
        val treeB = TestUri("content://provider/tree/b")

        assertEquals(
            treeA.toString(),
            selectRestoredReadTree(
                grants = listOf(PersistedTreeGrant(treeA, read)),
                rememberedUri = treeB,
                depthOf = { 1 },
            )?.toString(),
        )
        assertNull(
            selectRestoredReadTree(
                grants = listOf(
                    PersistedTreeGrant(treeA, read),
                    PersistedTreeGrant(treeB, read),
                ),
                rememberedUri = null,
                depthOf = { 1 },
            ),
        )
    }

    @Test
    fun uniqueBroadTreeRemainsTheSafeFallback() {
        val root = TestUri("content://provider/tree/root")
        val child = TestUri("content://provider/tree/root%2Fchild")

        assertEquals(
            root.toString(),
            selectRestoredReadTree(
                grants = listOf(
                    PersistedTreeGrant(child, read),
                    PersistedTreeGrant(root, read),
                ),
                rememberedUri = null,
                depthOf = { uri -> if (uri.toString() == root.toString()) 0 else 1 },
            )?.toString(),
        )
    }

    @Test
    fun writeOnlyGrantCannotBecomeRememberedReadTree() {
        val writeOnly = TestUri("content://provider/tree/write-only")

        assertNull(
            selectRestoredReadTree(
                grants = listOf(PersistedTreeGrant(writeOnly, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)),
                rememberedUri = writeOnly,
                depthOf = { 0 },
            ),
        )
    }
}
