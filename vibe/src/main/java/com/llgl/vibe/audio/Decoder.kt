package com.llgl.vibe.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.llgl.vibe.analysis.Biquad
import java.io.IOException
import java.nio.ByteOrder
import kotlin.math.max

/** Decodes anything the phone can play into mono floats at about 16 kHz, enough for beats, bass and pitch. */
object Decoder {
    class Pcm(val samples: FloatArray, val sampleRate: Int)

    private class Growable(initial: Int) {
        var data = FloatArray(initial.coerceAtLeast(1024))
        var size = 0

        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }

        fun trimmed(): FloatArray = data.copyOf(size)
    }

    /** Blocking; call off the main thread. [cancelled] is polled between buffers. */
    fun decode(context: Context, uri: Uri, onProgress: (Float) -> Unit, cancelled: () -> Boolean = { false }): Pcm {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            extractor.release()
            throw IOException("파일을 열 수 없어요: ${e.message}")
        }
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = f
                break
            }
        }
        if (trackIndex < 0 || format == null) {
            extractor.release()
            throw IOException("오디오 트랙이 없어요")
        }
        extractor.selectTrack(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: Exception) {
            extractor.release()
            throw IOException("이 형식($mime)은 디코더가 없어요")
        }
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        var encoding = AudioFormat.ENCODING_PCM_16BIT
        var factor = max(1, sampleRate / 16000)
        var lowPass = Biquad.lowPass(sampleRate, sampleRate / (2f * factor) * 0.8f)
        val out = Growable(if (durationUs > 0) (durationUs / 1_000_000.0 * sampleRate / factor).toInt() + 1024 else 1 shl 20)
        var acc = 0f
        var k = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            while (!outputDone) {
                if (cancelled()) break
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val n = extractor.readSampleData(buffer, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outIndex)!!
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    buffer.order(ByteOrder.nativeOrder())
                    if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                        val fb = buffer.asFloatBuffer()
                        val frames = fb.remaining() / channels
                        for (fr in 0 until frames) {
                            var mono = 0f
                            for (ch in 0 until channels) mono += fb.get()
                            acc += lowPass.process(mono / channels)
                            if (++k == factor) {
                                out.add(acc / factor)
                                acc = 0f
                                k = 0
                            }
                        }
                    } else {
                        val sb = buffer.asShortBuffer()
                        val frames = sb.remaining() / channels
                        for (fr in 0 until frames) {
                            var mono = 0f
                            for (ch in 0 until channels) mono += sb.get() / 32768f
                            acc += lowPass.process(mono / channels)
                            if (++k == factor) {
                                out.add(acc / factor)
                                acc = 0f
                                k = 0
                            }
                        }
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    codec.releaseOutputBuffer(outIndex, false)
                    if (durationUs > 0) onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    encoding = if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) f.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    factor = max(1, sampleRate / 16000)
                    lowPass = Biquad.lowPass(sampleRate, sampleRate / (2f * factor) * 0.8f)
                }
            }
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            codec.release()
            extractor.release()
        }
        if (out.size < sampleRate / factor) throw IOException("디코딩된 소리가 너무 짧아요")
        return Pcm(out.trimmed(), sampleRate / factor)
    }
}
