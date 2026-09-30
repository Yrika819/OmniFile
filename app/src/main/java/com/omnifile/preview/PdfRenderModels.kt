package com.omnifile.preview

/** Raw ARGB_8888 page pixels. Size is validated both here and at the IPC boundary. */
class PdfRenderedPage(val width: Int, val height: Int, internal val pixels: ByteArray) {
    init {
        require(isValidPdfRenderSize(width, height))
        require(pixels.size == width * height * 4)
    }
}

internal fun isValidPdfRenderSize(width: Int, height: Int): Boolean =
    width > 0 && height > 0 && width <= PreviewLimits.MAX_PDF_PAGE_SIDE &&
        height <= PreviewLimits.MAX_PDF_PAGE_SIDE &&
        width.toLong() * height.toLong() <= PreviewLimits.MAX_PDF_PAGE_PIXELS

interface PdfDocumentSession {
    val pageCount: Int
    suspend fun renderPage(pageIndex: Int): PdfRenderedPage
    suspend fun close()
    /** Synchronous generation invalidation for Preview replacement and ViewModel teardown. */
    fun invalidate() = Unit
}

/** Debug-only control used by instrumentation to deterministically exercise worker death. */
internal interface PdfRendererDeathTestHook {
    suspend fun killRendererForTest()
}

enum class PdfRendererFailureKind {
    TIMEOUT,
    WORKER_DIED,
    MALFORMED,
    ENCRYPTED_OR_UNSUPPORTED,
    INVALID_PAGE,
    RESOURCE_LIMIT,
    UNAVAILABLE,
}

class PdfRendererFailure(val kind: PdfRendererFailureKind) : Exception()

/** Narrow renderer seam used by deterministic host tests. */
interface PdfRendererClient {
    suspend fun open(snapshot: PdfSnapshot): Int
    suspend fun renderPage(pageIndex: Int): PdfRenderedPage
    suspend fun close()
    fun abort() = Unit
}

fun interface PdfRendererClientFactory {
    fun create(): PdfRendererClient
}

/** Session-scoped debug observation; carries no resource and does not delay the callback. */
internal interface PdfResponseArrivalTestHook {
    fun armAcceptedPageResponseForTest(): kotlinx.coroutines.Deferred<Unit>
}
