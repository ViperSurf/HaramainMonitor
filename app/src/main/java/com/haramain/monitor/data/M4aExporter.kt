package com.haramain.monitor.data

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Transcodes one of our WAV recordings (PCM16 stereo) to M4A/AAC using the
 * platform encoder — no bundled codecs, no native code. Runs off the UI thread.
 * Returns the .m4a file next to the source, or null on failure.
 */
object M4aExporter {

    suspend fun export(source: File): File? = withContext(Dispatchers.IO) {
        val target = File(source.parentFile, source.nameWithoutExtension + ".m4a")
        try {
            transcode(source, target)
            if (target.exists() && target.length() > 0) target else null
        } catch (_: Exception) {
            target.delete()
            null
        }
    }

    private fun transcode(source: File, target: File) {
        val bytes = source.readBytes()
        require(bytes.size > 44) { "WAV too small" }
        val sampleRate = (bytes[24].toInt() and 0xFF) or ((bytes[25].toInt() and 0xFF) shl 8) or
            ((bytes[26].toInt() and 0xFF) shl 16) or ((bytes[27].toInt() and 0xFF) shl 24)
        require(sampleRate in 8000..192000) { "Bad sample rate" }
        val pcm = bytes.copyOfRange(44, bytes.size)

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 160_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var trackIndex = -1
        var muxerStarted = false
        var inputOffset = 0
        var framesFed = 0L
        var inputDone = false
        var outputDone = false

        try {
            while (!outputDone) {
                // Feed input
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuffer = codec.getInputBuffer(inIndex)!!
                        inBuffer.clear()
                        val remaining = pcm.size - inputOffset
                        val chunk = minOf(remaining, inBuffer.remaining())
                        if (chunk > 0) inBuffer.put(pcm, inputOffset, chunk)
                        val ptsUs = framesFed * 1_000_000L / sampleRate
                        if (remaining <= 0 || chunk == remaining) {
                            // Last chunk may be partial-frame aligned; PCM16 stereo frames are 4 bytes.
                            codec.queueInputBuffer(inIndex, 0, chunk, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, chunk, ptsUs, 0)
                        }
                        inputOffset += chunk
                        framesFed += chunk / 4
                    }
                }
                // Drain output
                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val outBuffer = codec.getOutputBuffer(outIndex)!!
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outBuffer, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            if (muxerStarted) try { muxer.stop() } catch (_: Exception) {}
            muxer.release()
        }
    }
}
