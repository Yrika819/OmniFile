package com.omnifile.preview

import android.graphics.Bitmap
import com.omnifile.storage.EntryKind
import com.omnifile.storage.EntryRef
import com.omnifile.storage.StorageCapability
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.SequentialReadHandle
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult

/** Preview identity stays provider-scoped and never contains a path or transport URI. */
data class PreviewItem(
    val identity: EntryRef,
    val displayName: String,
    val locationLabel: String,
    val mimeType: String?,
    val sizeBytes: Long?,
)

data class PreviewCapabilities(
    val sequentialReadable: Boolean,
    /** The adapter can request a fresh sequential handle for another decode pass. */
    val canReopen: Boolean,
    /** Provider adapter grants the bounded PDF pipeline permission to stage this source. */
    val canStagePdf: Boolean = false,
)

/** A fresh handle is obtained for every pass; providers own all locators and transport details. */
class PreviewSource(
    val identity: EntryRef,
    val sourceLabel: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val capabilities: PreviewCapabilities,
    private val opener: suspend () -> StorageResult<SequentialReadHandle>,
) {
    suspend fun open(): StorageResult<SequentialReadHandle> {
        val scope = com.omnifile.storage.ReadAcquisitionScope.current() ?: return opener()
        return scope.operation {
            when (val result = opener()) {
                is StorageResult.Failure -> result
                is StorageResult.Success -> {
                    val lease = scope.own(result.value) { it.close() }
                    StorageResult.Success(object : SequentialReadHandle {
                        override val expectedBytes: Long? get() = result.value.expectedBytes
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                            scope.blockingOperation { result.value.read(buffer, offset, length) }
                        override fun close() = lease.close()
                    })
                }
            }
        }
    }
}

data class PreviewRequest(val item: PreviewItem, val source: PreviewSource, val startingPage: Int = 0) {
    init {
        require(item.identity.providerId == source.identity.providerId)
        require(item.identity.identityKey == source.identity.identityKey)
    }
}

enum class PreviewContentType { TEXT, IMAGE, PDF, UNKNOWN }

object PreviewCapabilityResolver {
    fun resolve(entry: StorageEntry, source: PreviewSource?): PreviewCapabilities? {
        if (entry.kind != EntryKind.FILE || StorageCapability.READ_SEQUENTIAL !in entry.capabilities) return null
        if (source == null || source.identity.providerId != entry.ref.providerId ||
            source.identity.identityKey != entry.ref.identityKey || !source.capabilities.sequentialReadable
        ) return null
        return source.capabilities
    }
}

object PreviewLimits {
    const val CLASSIFICATION_BYTES = 8 * 1024
    const val MAX_TEXT_BYTES = 256 * 1024
    const val MAX_TEXT_CHARACTERS = 64 * 1024
    const val MAX_IMAGE_ENCODED_BYTES = 32 * 1024 * 1024
    const val MAX_IMAGE_DIMENSION = 100_000
    const val MAX_IMAGE_PIXELS = 8_000_000L
    const val DEFAULT_IMAGE_TARGET_WIDTH = 1600
    const val DEFAULT_IMAGE_TARGET_HEIGHT = 1600
    const val MAX_CONSECUTIVE_NO_PROGRESS_READS = 8
    const val MAX_PDF_BYTES = 16 * 1024 * 1024
    const val MAX_PDF_PAGES = 100
    const val MAX_PDF_PAGE_PIXELS = 1_000_000L
    const val MAX_PDF_PAGE_SIDE = 1600
    const val PDF_ACQUISITION_TIMEOUT_MILLIS = 10_000L
    const val PDF_RENDER_TIMEOUT_MILLIS = 10_000L
}

data class DecodedText(val text: String, val truncated: Boolean)

/** Content claims are hints only. Strong image signatures and bounded text evidence decide V1. */
object PreviewContentClassifier {
    fun classify(sample: ByteArray, mimeType: String?, displayName: String): PreviewContentType {
        if (isPdf(sample)) return PreviewContentType.PDF
        if (isPng(sample) || isJpeg(sample) || isBmp(sample)) return PreviewContentType.IMAGE
        if (isKnownUnsupportedFormat(sample)) return PreviewContentType.UNKNOWN
        if (sample.isEmpty()) {
            val extension = displayName.substringAfterLast('.', "").lowercase()
            return if (mimeType?.startsWith("text/", ignoreCase = true) == true || extension in TEXT_EXTENSIONS) {
                PreviewContentType.TEXT
            } else {
                PreviewContentType.UNKNOWN
            }
        }
        if (looksBinary(sample)) return PreviewContentType.UNKNOWN
        return PreviewContentType.TEXT
    }

    private fun isPng(bytes: ByteArray): Boolean = bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4e.toByte() &&
        bytes[3] == 0x47.toByte() && bytes[4] == 0x0d.toByte() && bytes[5] == 0x0a.toByte() &&
        bytes[6] == 0x1a.toByte() && bytes[7] == 0x0a.toByte()

    private fun isJpeg(bytes: ByteArray): Boolean = bytes.size >= 3 &&
        bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()

    private fun isBmp(bytes: ByteArray): Boolean = bytes.size >= 2 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()

    private fun isPdf(bytes: ByteArray): Boolean {
        val signature = byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte(), '-'.code.toByte())
        val lastStart = minOf(bytes.size - signature.size, 1024)
        if (lastStart < 0) return false
        for (start in 0..lastStart) {
            if (signature.indices.all { bytes[start + it] == signature[it] }) return true
        }
        return false
    }

    private fun isKnownUnsupportedFormat(bytes: ByteArray): Boolean =
        (bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
                ((bytes[2] == 3.toByte() && bytes[3] == 4.toByte()) ||
                    (bytes[2] == 5.toByte() && bytes[3] == 6.toByte()) ||
                    (bytes[2] == 7.toByte() && bytes[3] == 8.toByte())))

    private fun looksBinary(bytes: ByteArray): Boolean {
        var controls = 0
        for (byte in bytes) {
            val value = byte.toInt() and 0xff
            if (value == 0 || (value < 0x20 && value !in setOf(0x09, 0x0a, 0x0c, 0x0d)) || value == 0x7f) controls++
        }
        return controls * 20 > bytes.size
    }

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "csv", "tsv", "json", "xml", "html", "htm", "log", "yaml", "yml",
        "ini", "cfg", "properties", "kt", "java", "kts", "gradle", "sh", "c", "h", "cpp", "css", "js",
    )
}

object PreviewTextDecoder {
    fun decode(bytes: ByteArray, truncated: Boolean): DecodedText {
        var start = if (
            bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte()
        ) 3 else 0
        var end = bytes.size
        if (truncated) end = dropIncompleteUtf8Tail(bytes, start, end)
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)
        val decoded = decoder.decode(java.nio.ByteBuffer.wrap(bytes, start, end - start)).toString()
        start = 0
        val overCharacterLimit = decoded.length > PreviewLimits.MAX_TEXT_CHARACTERS
        val text = if (overCharacterLimit) decoded.substring(start, PreviewLimits.MAX_TEXT_CHARACTERS) else decoded
        return DecodedText(text, truncated || overCharacterLimit)
    }

    private fun dropIncompleteUtf8Tail(bytes: ByteArray, start: Int, end: Int): Int {
        if (end <= start) return end
        var lead = end - 1
        while (lead >= start && (bytes[lead].toInt() and 0xc0) == 0x80) lead--
        if (lead < start) return end
        val first = bytes[lead].toInt() and 0xff
        val expected = when {
            first in 0xc2..0xdf -> 2
            first in 0xe0..0xef -> 3
            first in 0xf0..0xf4 -> 4
            else -> 1
        }
        return if (expected > end - lead) lead else end
    }
}

sealed interface PreviewError {
    data object ProviderUnavailable : PreviewError
    data object SourceVanished : PreviewError
    data object PermissionOrGrantMissing : PreviewError
    data object CorruptOrMalformed : PreviewError
    data object ResourceLimit : PreviewError
    data object PdfInputTooLarge : PreviewError
    data object PdfPageCountLimit : PreviewError
    data object EncryptedOrUnsupported : PreviewError
    data object StagingFailure : PreviewError
    data object AcquisitionTimeout : PreviewError
    data object AcquisitionBusy : PreviewError
    data object RendererTimeout : PreviewError
    data object RendererFailure : PreviewError
    data object Cancelled : PreviewError
    data class IoFailure(val detail: String?) : PreviewError
    data object Unknown : PreviewError
}

sealed interface PreviewPayload {
    data class Text(val content: String, val truncated: Boolean, val bytesRead: Int) : PreviewPayload
    data class Image(val bitmap: Bitmap, val width: Int, val height: Int) : PreviewPayload
    data class PdfPage(
        val page: PdfRenderedPage,
        val pageIndex: Int,
        val pageCount: Int,
        val session: PdfDocumentSession,
    ) : PreviewPayload
    data object Unsupported : PreviewPayload
    data class Failure(val error: PreviewError) : PreviewPayload
}

sealed interface PreviewUiState {
    data object Idle : PreviewUiState
    data class Loading(val item: PreviewItem) : PreviewUiState
    data class Ready(val item: PreviewItem, val sourceLabel: String, val payload: PreviewPayload) : PreviewUiState
    data class Unsupported(val item: PreviewItem) : PreviewUiState
    data class Error(val item: PreviewItem, val error: PreviewError) : PreviewUiState
}

internal fun previewError(error: StorageError): PreviewError = when (error) {
    StorageError.PermissionDenied -> PreviewError.PermissionOrGrantMissing
    StorageError.ProviderUnavailable -> PreviewError.ProviderUnavailable
    StorageError.NotFound, StorageError.StaleReference, StorageError.SourceChanged -> PreviewError.SourceVanished
    StorageError.Unsupported -> PreviewError.Unknown
    StorageError.Cancelled -> PreviewError.Cancelled
    is StorageError.IoFailure -> PreviewError.IoFailure(error.detail)
    else -> PreviewError.Unknown
}

internal fun StorageResult.Failure.toPreviewPayload(): PreviewPayload = when (val mapped = previewError(error)) {
    PreviewError.PermissionOrGrantMissing, PreviewError.ProviderUnavailable, PreviewError.Cancelled -> PreviewPayload.Failure(mapped)
    else -> PreviewPayload.Failure(mapped)
}
