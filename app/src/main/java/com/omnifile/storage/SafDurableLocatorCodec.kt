package com.omnifile.storage

import java.util.Base64

/** Versioned, provider-scoped facts needed to re-resolve one SAF document. */
data class SafLocatorParts(
    val treeUri: String,
    val documentId: String,
)

object SafDurableLocatorCodec {
    const val ENCODING = "saf-document-v1"

    fun encode(treeUri: String, documentId: String): String {
        require(treeUri.isNotBlank()) { "Tree URI must not be blank" }
        require(documentId.isNotBlank()) { "Document ID must not be blank" }
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return listOf(treeUri, documentId).joinToString(".") { value ->
            encoder.encodeToString(value.toByteArray(Charsets.UTF_8))
        }
    }

    fun decode(value: String): SafLocatorParts? {
        val encoded = value.split('.')
        if (encoded.size != 2 || encoded.any { it.isBlank() }) return null
        return try {
            val decoded = encoded.map { token ->
                String(Base64.getUrlDecoder().decode(token), Charsets.UTF_8)
            }
            decoded[0].takeIf { it.isNotBlank() }?.let { treeUri ->
                decoded[1].takeIf { it.isNotBlank() }?.let { documentId ->
                    SafLocatorParts(treeUri, documentId)
                }
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
