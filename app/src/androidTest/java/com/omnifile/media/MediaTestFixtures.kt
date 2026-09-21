package com.omnifile.media

import java.io.File
import kotlin.math.sin

/**
 * Deterministic disposable synthetic audio for VS07 instrumentation.
 *
 * - WAV: PCM16 mono sine generated in code (no external tools, no corpus).
 * - FLAC: `assets/vs07-primary.flac` is the P05-003 PoC fixture
 *   (`p05_003_primary.flac`), a 1 s 16 kHz mono synthetic tone generated with
 *   libav/ffmpeg (Lavf63.1.101 header) for the disposable PoC harness. It is
 *   synthetic, 14931 bytes, and contains no copyrighted material.
 */
object MediaTestFixtures {
    const val WAV_DURATION_MS = 2000
    const val WAV_SAMPLE_RATE = 16000
    const val FLAC_ASSET = "vs07-primary.flac"
    const val FLAC_DURATION_MS = 1000L

    fun writeToneWav(
        file: File,
        durationMs: Int = WAV_DURATION_MS,
        sampleRate: Int = WAV_SAMPLE_RATE,
        frequencyHz: Double = 440.0,
    ) {
        file.writeBytes(toneWavBytes(durationMs, sampleRate, frequencyHz))
    }

    fun toneWavBytes(
        durationMs: Int = WAV_DURATION_MS,
        sampleRate: Int = WAV_SAMPLE_RATE,
        frequencyHz: Double = 440.0,
    ): ByteArray {
        val sampleCount = durationMs * sampleRate / 1000
        val data = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val sample = (sin(2.0 * Math.PI * frequencyHz * i / sampleRate) * 12000.0)
                .toInt().toShort()
            data[2 * i] = (sample.toInt() and 0xFF).toByte()
            data[2 * i + 1] = (sample.toInt() shr 8 and 0xFF).toByte()
        }
        val out = java.io.ByteArrayOutputStream(44 + data.size)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.writeIntLe(36 + data.size)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        out.writeIntLe(16)
        out.writeShortLe(1) // PCM
        out.writeShortLe(1) // mono
        out.writeIntLe(sampleRate)
        out.writeIntLe(sampleRate * 2)
        out.writeShortLe(2)
        out.writeShortLe(16)
        out.write("data".toByteArray(Charsets.US_ASCII))
        out.writeIntLe(data.size)
        out.write(data)
        return out.toByteArray()
    }

    private fun java.io.OutputStream.writeIntLe(value: Int) {
        write(
            byteArrayOf(
                (value and 0xFF).toByte(),
                (value shr 8 and 0xFF).toByte(),
                (value shr 16 and 0xFF).toByte(),
                (value shr 24 and 0xFF).toByte(),
            ),
        )
    }

    private fun java.io.OutputStream.writeShortLe(value: Int) {
        write(
            byteArrayOf(
                (value and 0xFF).toByte(),
                (value shr 8 and 0xFF).toByte(),
            ),
        )
    }
}
