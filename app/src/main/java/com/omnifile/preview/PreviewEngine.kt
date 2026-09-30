package com.omnifile.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.omnifile.storage.SequentialReadHandle
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

open class PreviewEngine(private val pdfController: PdfPreviewController? = null) {
    open suspend fun load(request: PreviewRequest): PreviewPayload {
        val source = request.source
        if (!source.capabilities.sequentialReadable) return PreviewPayload.Unsupported
        if (source.identity.providerId != request.item.identity.providerId ||
            source.identity.identityKey != request.item.identity.identityKey
        ) return PreviewPayload.Unsupported
        val firstHandle = when (val opened = source.open()) {
            is StorageResult.Success -> opened.value
            is StorageResult.Failure -> return PreviewPayload.Failure(previewError(opened.error))
        }
        var textPayload: PreviewPayload? = null
        val contentType = try {
            firstHandle.use { handle ->
                val prefix = readAtMost(handle, PreviewLimits.CLASSIFICATION_BYTES)
                val type = PreviewContentClassifier.classify(prefix, source.mimeType, request.item.displayName)
                if (type == PreviewContentType.TEXT) {
                    textPayload = readText(handle, source, request, prefix)
                }
                type
            }
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (error: IOException) {
            return PreviewPayload.Failure(PreviewError.IoFailure(error.message))
        } catch (_: SecurityException) {
            return PreviewPayload.Failure(PreviewError.PermissionOrGrantMissing)
        }
        return when (contentType) {
            PreviewContentType.IMAGE -> {
                if (!source.capabilities.canReopen) PreviewPayload.Unsupported
                else decodeImage(source)
            }
            PreviewContentType.PDF -> {
                if (!source.capabilities.canStagePdf) PreviewPayload.Unsupported
                else when (val opened = pdfController?.open(request, request.startingPage)) {
                    null -> PreviewPayload.Unsupported
                    is PdfOpenResult.Ready -> opened.payload
                    is PdfOpenResult.Failure -> PreviewPayload.Failure(opened.error)
                }
            }
            PreviewContentType.TEXT -> textPayload ?: PreviewPayload.Unsupported
            PreviewContentType.UNKNOWN -> PreviewPayload.Unsupported
        }
    }

    private suspend fun readText(
        handle: SequentialReadHandle,
        source: PreviewSource,
        request: PreviewRequest,
        prefix: ByteArray,
    ): PreviewPayload {
        val output = ByteArrayOutputStream(minOf(PreviewLimits.MAX_TEXT_BYTES, maxOf(prefix.size, 1024)))
        output.write(prefix, 0, minOf(prefix.size, PreviewLimits.MAX_TEXT_BYTES))
        var truncated = prefix.size > PreviewLimits.MAX_TEXT_BYTES
        if (!truncated && prefix.size == PreviewLimits.CLASSIFICATION_BYTES) {
            try {
                var noProgressReads = 0
                while (output.size() < PreviewLimits.MAX_TEXT_BYTES) {
                    currentCoroutineContext().ensureActive()
                    val remaining = PreviewLimits.MAX_TEXT_BYTES - output.size()
                    val buffer = ByteArray(minOf(8 * 1024, remaining))
                    val count = handle.read(buffer, 0, buffer.size)
                    if (count < 0) break
                    if (count == 0) {
                        noProgressReads++
                        if (noProgressReads > PreviewLimits.MAX_CONSECUTIVE_NO_PROGRESS_READS) {
                            return PreviewPayload.Failure(PreviewError.IoFailure("Provider read made no progress"))
                        }
                        continue
                    }
                    noProgressReads = 0
                    output.write(buffer, 0, count)
                }
                if (output.size() == PreviewLimits.MAX_TEXT_BYTES) {
                    val extra = ByteArray(1)
                    val count = handle.read(extra, 0, 1)
                    if (count == 0) return PreviewPayload.Failure(PreviewError.IoFailure("Provider read made no progress"))
                    truncated = count > 0
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (error: SecurityException) {
                return PreviewPayload.Failure(PreviewError.PermissionOrGrantMissing)
            } catch (error: IOException) {
                return PreviewPayload.Failure(PreviewError.IoFailure(error.message))
            }
        }
        val bytes = output.toByteArray()
        val fullType = PreviewContentClassifier.classify(bytes, source.mimeType, request.item.displayName)
        if (fullType != PreviewContentType.TEXT) return PreviewPayload.Unsupported
        val decoded = PreviewTextDecoder.decode(bytes, truncated)
        return PreviewPayload.Text(decoded.text, decoded.truncated, bytes.size)
    }

    private suspend fun decodeImage(source: PreviewSource): PreviewPayload {
        val bounds = when (val opened = source.open()) {
            is StorageResult.Success -> opened.value
            is StorageResult.Failure -> return PreviewPayload.Failure(previewError(opened.error))
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsInput = HandleInputStream(bounds)
        val boundsLimit = EncodedLimitInputStream(boundsInput, PreviewLimits.MAX_IMAGE_ENCODED_BYTES)
        try {
            BufferedInputStream(boundsLimit, 16 * 1024).use { BitmapFactory.decodeStream(it, null, options) }
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (error: EncodedLimitException) {
            return PreviewPayload.Failure(PreviewError.ResourceLimit)
        } catch (error: SecurityException) {
            return PreviewPayload.Failure(PreviewError.PermissionOrGrantMissing)
        } catch (_: IOException) {
            return PreviewPayload.Failure(boundsInput.ioFailure?.let { PreviewError.IoFailure(it.message) } ?: PreviewError.CorruptOrMalformed)
        } catch (_: RuntimeException) {
            return PreviewPayload.Failure(PreviewError.CorruptOrMalformed)
        }
        boundsInput.ioFailure?.let { return PreviewPayload.Failure(PreviewError.IoFailure(it.message)) }
        if (boundsLimit.exceeded) return PreviewPayload.Failure(PreviewError.ResourceLimit)
        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) return PreviewPayload.Failure(PreviewError.CorruptOrMalformed)
        if (width > PreviewLimits.MAX_IMAGE_DIMENSION || height > PreviewLimits.MAX_IMAGE_DIMENSION) {
            return PreviewPayload.Failure(PreviewError.ResourceLimit)
        }
        val sampleSize = sampleSize(width, height)
        val decodeHandle = when (val opened = source.open()) {
            is StorageResult.Success -> opened.value
            is StorageResult.Failure -> return PreviewPayload.Failure(previewError(opened.error))
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decodeInput = HandleInputStream(decodeHandle)
        val decodeLimit = EncodedLimitInputStream(decodeInput, PreviewLimits.MAX_IMAGE_ENCODED_BYTES)
        val bitmap = try {
            BufferedInputStream(decodeLimit, 32 * 1024).use { BitmapFactory.decodeStream(it, null, decodeOptions) }
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (_: EncodedLimitException) {
            return PreviewPayload.Failure(PreviewError.ResourceLimit)
        } catch (_: SecurityException) {
            return PreviewPayload.Failure(PreviewError.PermissionOrGrantMissing)
        } catch (_: OutOfMemoryError) {
            return PreviewPayload.Failure(PreviewError.ResourceLimit)
        } catch (error: IOException) {
            return PreviewPayload.Failure(decodeInput.ioFailure?.let { PreviewError.IoFailure(it.message) } ?: PreviewError.CorruptOrMalformed)
        } catch (_: RuntimeException) {
            return PreviewPayload.Failure(PreviewError.CorruptOrMalformed)
        }
        decodeInput.ioFailure?.let { return PreviewPayload.Failure(PreviewError.IoFailure(it.message)) }
        if (decodeLimit.exceeded) return PreviewPayload.Failure(PreviewError.ResourceLimit)
        return if (bitmap == null) PreviewPayload.Failure(PreviewError.CorruptOrMalformed)
        else PreviewPayload.Image(bitmap, width, height)
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (true) {
            val sampledWidth = (width.toLong() + sample - 1) / sample
            val sampledHeight = (height.toLong() + sample - 1) / sample
            val overTarget = sampledWidth > PreviewLimits.DEFAULT_IMAGE_TARGET_WIDTH ||
                sampledHeight > PreviewLimits.DEFAULT_IMAGE_TARGET_HEIGHT
            val overPixels = sampledWidth * sampledHeight > PreviewLimits.MAX_IMAGE_PIXELS
            if (!overTarget && !overPixels) return sample
            if (sample > (1 shl 28)) return sample
            sample *= 2
        }
    }

    private suspend fun readAtMost(handle: SequentialReadHandle, maximum: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maximum, 1024))
        val buffer = ByteArray(1024)
        var noProgressReads = 0
        while (output.size() < maximum) {
            currentCoroutineContext().ensureActive()
            val count = handle.read(buffer, 0, minOf(buffer.size, maximum - output.size()))
            if (count < 0) break
            if (count == 0) {
                noProgressReads++
                if (noProgressReads > PreviewLimits.MAX_CONSECUTIVE_NO_PROGRESS_READS) {
                    throw IOException("Provider read made no progress")
                }
                continue
            }
            noProgressReads = 0
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private class HandleInputStream(private val handle: SequentialReadHandle) : InputStream() {
        private val one = ByteArray(1)
        var ioFailure: IOException? = null
            private set

        override fun read(): Int {
            return try {
                val count = handle.read(one, 0, 1)
                if (count == 0) throw IOException("Provider read made no progress")
                if (count < 0) -1 else one[0].toInt() and 0xff
            } catch (error: IOException) {
                ioFailure = error
                throw error
            }
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            return try {
                handle.read(buffer, offset, length).also {
                    if (it == 0) throw IOException("Provider read made no progress")
                }
            } catch (error: IOException) {
                ioFailure = error
                throw error
            }
        }
        override fun close() {
            try {
                handle.close()
            } catch (error: IOException) {
                ioFailure = error
                throw error
            }
        }
    }

    private class EncodedLimitInputStream(input: InputStream, private val maximum: Int) : java.io.FilterInputStream(input) {
        var bytesRead = 0
            private set
        var exceeded = false
            private set

        override fun read(): Int {
            if (bytesRead >= maximum) {
                val extra = `in`.read()
                if (extra >= 0) {
                    exceeded = true
                    throw EncodedLimitException()
                }
                return -1
            }
            val value = `in`.read()
            if (value >= 0) bytesRead++
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            val remaining = maximum - bytesRead
            if (remaining <= 0) return read().let { if (it < 0) -1 else { buffer[offset] = it.toByte(); 1 } }
            val count = `in`.read(buffer, offset, minOf(length, remaining))
            if (count > 0) bytesRead += count
            return count
        }
    }

    private class EncodedLimitException : IOException("Image input exceeded preview bound")
}
