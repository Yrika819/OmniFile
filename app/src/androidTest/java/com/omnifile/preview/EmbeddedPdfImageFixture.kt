package com.omnifile.preview

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

/** A valid PNG with a CRC-correct tEXt chunk before IDAT and early PDF-looking metadata. */
internal fun embeddedPdfPng(): ByteArray {
    val bitmap = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
    val encoded = ByteArrayOutputStream()
    try { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, encoded)) }
    finally { bitmap.recycle() }
    val png = encoded.toByteArray()
    val chunkBody = "tEXtComment\u0000%PDF-1.7".toByteArray(Charsets.US_ASCII)
    val chunk = ByteArrayOutputStream()
    DataOutputStream(chunk).use {
        it.writeInt(chunkBody.size - 4)
        it.write(chunkBody)
        it.writeInt(CRC32().apply { update(chunkBody) }.value.toInt())
    }
    // PNG signature (8) + IHDR length/type/body/CRC (25).
    return png.copyOfRange(0, 33) + chunk.toByteArray() + png.copyOfRange(33, png.size)
}

/** Normal supported images exercise the real platform decoder alongside the metadata regression. */
internal fun normalSupportedImages(): Map<String, ByteArray> {
    val bitmap = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
    fun encoded(format: Bitmap.CompressFormat): ByteArray = ByteArrayOutputStream().apply {
        check(bitmap.compress(format, 100, this))
    }.toByteArray()
    val png: ByteArray
    val jpeg: ByteArray
    try { png = encoded(Bitmap.CompressFormat.PNG); jpeg = encoded(Bitmap.CompressFormat.JPEG) }
    finally { bitmap.recycle() }
    // 24-bit BI_RGB BMP: 54-byte header, three padded 8-byte rows (2 pixels each).
    val bmp = java.nio.ByteBuffer.allocate(78).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
        put('B'.code.toByte()); put('M'.code.toByte()); putInt(78); putInt(0); putInt(54)
        putInt(40); putInt(2); putInt(3); putShort(1); putShort(24)
        putInt(0); putInt(24); putInt(0); putInt(0); putInt(0); putInt(0)
    }.array()
    return mapOf("normal.png" to png, "normal.jpg" to jpeg, "normal.bmp" to bmp)
}

internal fun embeddedPdfSupportedImages(): Map<String, ByteArray> {
    val normal = normalSupportedImages()
    // JPEG COM marker immediately after SOI; byte-zero JPEG magic and decoding remain valid.
    val comment = "%PDF-1.7".toByteArray(Charsets.US_ASCII)
    val jpeg = normal.getValue("normal.jpg")
    val embeddedJpeg = jpeg.copyOfRange(0, 2) +
        byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0, (comment.size + 2).toByte()) +
        comment + jpeg.copyOfRange(2, jpeg.size)
    // Five ordinary color bytes in the first BMP scanline, within its declared pixel data.
    val embeddedBmp = normal.getValue("normal.bmp").copyOf().apply {
        "%PDF-".toByteArray(Charsets.US_ASCII).copyInto(this, 54)
    }
    return normal + mapOf("embedded.pdf" to embeddedPdfPng(),
        "embedded-jpeg.pdf" to embeddedJpeg, "embedded-bmp.pdf" to embeddedBmp)
}
