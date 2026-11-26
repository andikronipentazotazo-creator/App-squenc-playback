package com.example.audio

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

data class DecodedPcmAudio(
    val left: FloatArray,
    val right: FloatArray,
    val isStereo: Boolean
)

/**
 * Decodes standard PCM WAV files (8-bit, 16-bit, 24-bit, or 32-bit float) from
 * SAF document URIs or local cache files and resamples linearly to the engine sample rate.
 */
object WavDecoder {

    fun decodeFromUri(context: Context, uri: Uri, targetSampleRate: Int): DecodedPcmAudio? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                decodeFromStream(stream, targetSampleRate)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun decodeFromStream(inputStream: InputStream, targetSampleRate: Int): DecodedPcmAudio? {
        val bytes = inputStream.readBytes()
        if (bytes.size < 44) return null

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
            bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) return null
        if (bytes[8] != 'W'.code.toByte() || bytes[9] != 'A'.code.toByte() ||
            bytes[10] != 'V'.code.toByte() || bytes[11] != 'E'.code.toByte()
        ) return null

        var offset = 12
        var audioFormat = 1
        var numChannels = 1
        var sampleRate = targetSampleRate
        var bitsPerSample = 16
        var dataStart = -1
        var dataSize = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = buf.getInt(offset + 4)
            val payloadOffset = offset + 8
            if (chunkId == "fmt " && payloadOffset + 16 <= bytes.size) {
                audioFormat = buf.getShort(payloadOffset).toInt() and 0xFFFF
                numChannels = (buf.getShort(payloadOffset + 2).toInt() and 0xFFFF).coerceAtLeast(1)
                sampleRate = buf.getInt(payloadOffset + 4).coerceAtLeast(8000)
                bitsPerSample = (buf.getShort(payloadOffset + 14).toInt() and 0xFFFF).coerceAtLeast(8)
            } else if (chunkId == "data") {
                dataStart = payloadOffset
                dataSize = chunkSize.coerceAtMost(bytes.size - payloadOffset).coerceAtLeast(0)
                break
            }
            val paddedSize = if (chunkSize % 2 != 0) chunkSize + 1 else chunkSize
            offset = payloadOffset + paddedSize.coerceAtLeast(0)
        }

        if (dataStart < 0 || dataSize <= 0) return null

        val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(1)
        val frameSize = bytesPerSample * numChannels
        val frameCount = dataSize / frameSize
        if (frameCount <= 0) return null

        val rawLeft = FloatArray(frameCount)
        val rawRight = FloatArray(frameCount)

        for (i in 0 until frameCount) {
            val frameOffset = dataStart + i * frameSize
            rawLeft[i] = readSampleNormalized(bytes, buf, frameOffset, bitsPerSample, audioFormat)
            rawRight[i] = if (numChannels >= 2) {
                readSampleNormalized(bytes, buf, frameOffset + bytesPerSample, bitsPerSample, audioFormat)
            } else {
                rawLeft[i]
            }
        }

        if (sampleRate == targetSampleRate) {
            return DecodedPcmAudio(rawLeft, rawRight, numChannels >= 2)
        }

        val ratio = targetSampleRate.toDouble() / sampleRate.toDouble()
        val outLength = (frameCount * ratio).roundToInt().coerceAtLeast(1)
        val resampledL = FloatArray(outLength)
        val resampledR = FloatArray(outLength)
        for (i in 0 until outLength) {
            val srcIdx = i / ratio
            val i0 = srcIdx.toInt().coerceIn(0, frameCount - 1)
            val i1 = (i0 + 1).coerceIn(0, frameCount - 1)
            val frac = (srcIdx - i0).toFloat()
            resampledL[i] = rawLeft[i0] + (rawLeft[i1] - rawLeft[i0]) * frac
            resampledR[i] = rawRight[i0] + (rawRight[i1] - rawRight[i0]) * frac
        }

        return DecodedPcmAudio(resampledL, resampledR, numChannels >= 2)
    }

    private fun readSampleNormalized(
        bytes: ByteArray,
        buf: ByteBuffer,
        offset: Int,
        bitsPerSample: Int,
        audioFormat: Int
    ): Float {
        return when {
            audioFormat == 3 && bitsPerSample == 32 && offset + 4 <= bytes.size ->
                buf.getFloat(offset).coerceIn(-1f, 1f)
            bitsPerSample == 16 && offset + 2 <= bytes.size ->
                buf.getShort(offset) / 32768f
            bitsPerSample == 8 && offset < bytes.size ->
                ((bytes[offset].toInt() and 0xFF) - 128) / 128f
            bitsPerSample == 24 && offset + 3 <= bytes.size -> {
                val b0 = bytes[offset].toInt() and 0xFF
                val b1 = bytes[offset + 1].toInt() and 0xFF
                val b2 = bytes[offset + 2].toInt() // signed MSB
                val value = (b2 shl 16) or (b1 shl 8) or b0
                value / 8388608f
            }
            else -> 0f
        }
    }
}
