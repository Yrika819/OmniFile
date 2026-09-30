package com.omnifile.preview

import kotlin.math.floor
import kotlin.math.sqrt

data class PdfRenderDimensions(val width: Int, val height: Int)

object PdfPageGeometry {
    fun bounded(pageWidth: Int, pageHeight: Int): PdfRenderDimensions? {
        if (pageWidth <= 0 || pageHeight <= 0) return null
        val maxSideScale = PreviewLimits.MAX_PDF_PAGE_SIDE.toDouble() / maxOf(pageWidth, pageHeight)
        val pixelScale = sqrt(PreviewLimits.MAX_PDF_PAGE_PIXELS.toDouble() / (pageWidth.toDouble() * pageHeight))
        val scale = minOf(1.0, maxSideScale, pixelScale)
        val width = maxOf(1, floor(pageWidth * scale).toInt())
        val height = maxOf(1, floor(pageHeight * scale).toInt())
        return if (isValidPdfRenderSize(width, height)) PdfRenderDimensions(width, height) else null
    }
}
