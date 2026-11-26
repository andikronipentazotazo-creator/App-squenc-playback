package com.example.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Memory-efficient 16-bit PCM stereo/mono audio buffer for full backing tracks & songs.
 */
data class DecodedTrackPcm(
    val left: ShortArray,
    val right: ShortArray,
    val isStereo: Boolean
)

/**
 * Universal Android audio file decoder supporting WAV, MP3, M4A, AAC, OGG, and FLAC.
 * Uses fast direct PCM WAV decoding when applicable and Android MediaExtractor + MediaCodec
 * for compressed audio formats, resampling cleanly to the DSP engine sample rate.
 */
object AudioFileDecoder {

    private const val MAX_TRACK_SECONDS = 480 // 8 minutes max per loaded track to prevent OOM

    fun decodeAudioUri(context: Context, uri: Uri, targetSampleRate: Int): DecodedTrackPcm? {
        // Fast path for uncompressed PCM WAV
        WavDecoder.decodeFromUri(context, uri, targetSampleRate)?.let { wav ->
            val maxSamples = targetSampleRate * MAX_TRACK_SECONDS
            val len = wav.left.size.coerceAtMost(maxSamples)
            val leftShorts = ShortArray(len) { i ->
                (wav.left[i].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
            }
            val rightShorts = ShortArray(len) { i ->
                (wav.right[i].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
            }
            return DecodedTrackPcm(leftShorts, rightShorts, wav.isStereo)
        }

        // General path via MediaExtractor + MediaCodec (MP3, AAC, M4A, OGG, FLAC, etc.)
        return decodeWithMediaCodec(context, uri, targetSampleRate)
    }

    private fun decodeWithMediaCodec(
        context: Context,
        uri: Uri,
        targetSampleRate: Int
    ): DecodedTrackPcm? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIdx = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    trackFormat = fmt
                    break
                }
            }
            if (audioTrackIdx < 0 || trackFormat == null) return null

            extractor.selectTrack(audioTrackIdx)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null
            var srcSampleRate = if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
            } else targetSampleRate
            var srcChannels = if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else 2
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                trackFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)
            ) {
                pcmEncoding = trackFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val maxSourceFrames = srcSampleRate * MAX_TRACK_SECONDS
            val leftChunks = ArrayList<ShortArray>()
            val rightChunks = ArrayList<ShortArray>()
            var totalDecodedFrames = 0

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone && totalDecodedFrames < maxSourceFrames) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(
                                    inIdx, 0, sampleSize,
                                    extractor.sampleTime.coerceAtLeast(0L), 0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFmt = codec.outputFormat
                        if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            srcSampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
                        }
                        if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            srcChannels = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                            newFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)
                        ) {
                            pcmEncoding = newFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            outBuf.order(ByteOrder.LITTLE_ENDIAN)

                            val (chunkL, chunkR) = extractPcmChunk(outBuf, srcChannels, pcmEncoding)
                            if (chunkL.isNotEmpty()) {
                                leftChunks.add(chunkL)
                                rightChunks.add(chunkR)
                                totalDecodedFrames += chunkL.size
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            if (totalDecodedFrames == 0) return null

            val mergedL = ShortArray(totalDecodedFrames)
            val mergedR = ShortArray(totalDecodedFrames)
            var cursor = 0
            for (i in leftChunks.indices) {
                val cL = leftChunks[i]
                val cR = rightChunks[i]
                cL.copyInto(mergedL, cursor)
                cR.copyInto(mergedR, cursor)
                cursor += cL.size
            }

            if (srcSampleRate == targetSampleRate) {
                return DecodedTrackPcm(mergedL, mergedR, srcChannels >= 2)
            }

            // Linear resample to engine sample rate
            val ratio = targetSampleRate.toDouble() / srcSampleRate.toDouble()
            val maxTargetFrames = targetSampleRate * MAX_TRACK_SECONDS
            val outFrames = (totalDecodedFrames * ratio).roundToInt().coerceIn(1, maxTargetFrames)
            val resampledL = ShortArray(outFrames)
            val resampledR = ShortArray(outFrames)
            for (i in 0 until outFrames) {
                val srcPos = i / ratio
                val i0 = srcPos.toInt().coerceIn(0, totalDecodedFrames - 1)
                val i1 = (i0 + 1).coerceIn(0, totalDecodedFrames - 1)
                val frac = (srcPos - i0).toFloat()
                resampledL[i] = (mergedL[i0] + (mergedL[i1] - mergedL[i0]) * frac).roundToInt().toShort()
                resampledR[i] = (mergedR[i0] + (mergedR[i1] - mergedR[i0]) * frac).roundToInt().toShort()
            }

            return DecodedTrackPcm(resampledL, resampledR, srcChannels >= 2)
        } catch (_: Exception) {
            return null
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun extractPcmChunk(
        buf: java.nio.ByteBuffer,
        channels: Int,
        pcmEncoding: Int
    ): Pair<ShortArray, ShortArray> {
        val bytesPerSample = when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            AudioFormat.ENCODING_PCM_8BIT -> 1
            else -> 2
        }
        val frameBytes = bytesPerSample * channels.coerceAtLeast(1)
        val frameCount = buf.remaining() / frameBytes
        if (frameCount <= 0) return ShortArray(0) to ShortArray(0)

        val outL = ShortArray(frameCount)
        val outR = ShortArray(frameCount)

        for (i in 0 until frameCount) {
            val l = readSampleShort(buf, pcmEncoding)
            var r = l
            for (ch in 1 until channels) {
                val sample = readSampleShort(buf, pcmEncoding)
                if (ch == 1) r = sample
            }
            outL[i] = l
            outR[i] = r
        }
        return outL to outR
    }

    private fun readSampleShort(buf: java.nio.ByteBuffer, pcmEncoding: Int): Short {
        return when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val f = buf.getFloat().coerceIn(-1f, 1f)
                (f * 32767f).roundToInt().toShort()
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                val b = (buf.get().toInt() and 0xFF) - 128
                (b shl 8).toShort()
            }
            else -> buf.getShort()
        }
    }
}
