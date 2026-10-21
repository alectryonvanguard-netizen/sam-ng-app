package com.cinemotion.editor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Simple tempo and beat-grid estimator.
 *
 * Decodes up to [maxMs] of the audio, builds an energy-onset curve, finds the
 * dominant tempo (80-170 BPM) with autocorrelation, then aligns a constant beat
 * grid to the strongest phase. Works best on music with a clear pulse.
 */
object BeatDetector {

    class Result(val bpm: Float, val beatsMs: List<Long>)

    private const val HOP = 1024

    fun detect(context: Context, uri: Uri, maxMs: Long = 120_000L): Result? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, uri, null)

            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = i
                    format = f
                    break
                }
            }
            if (track < 0 || format == null) return null

            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val energies = ArrayList<Float>()
            var acc = 0.0
            var accN = 0
            var inputDone = false
            var outputDone = false
            var totalFrames = 0L
            val deadline = System.nanoTime() + 25_000_000_000L

            while (!outputDone && System.nanoTime() < deadline) {
                if (totalFrames > sampleRate.toLong() * maxMs / 1000L) break

                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val buf = codec.getInputBuffer(ii)
                        val n = if (buf != null) extractor.readSampleData(buf, 0) else -1
                        if (n < 0) {
                            codec.queueInputBuffer(
                                ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(ii, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val oi = codec.dequeueOutputBuffer(info, 10_000)
                if (oi >= 0) {
                    val out = codec.getOutputBuffer(oi)
                    if (out != null && info.size > 0) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val sb = out.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val frames = sb.remaining() / channels
                        for (f in 0 until frames) {
                            var s = 0f
                            for (c in 0 until channels) s += sb.get(f * channels + c).toFloat()
                            val m = s / channels / 32768f
                            acc += (m * m).toDouble()
                            accN++
                            if (accN == HOP) {
                                energies.add(sqrt(acc / HOP).toFloat())
                                acc = 0.0
                                accN = 0
                            }
                        }
                        totalFrames += frames
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = codec.outputFormat
                    sampleRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                }
            }

            return analyse(energies.toFloatArray(), sampleRate)
        } catch (_: Exception) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Exception) { }
            try { codec?.release() } catch (_: Exception) { }
            try { extractor.release() } catch (_: Exception) { }
        }
    }

    private fun analyse(e: FloatArray, sampleRate: Int): Result? {
        val n = e.size
        if (n < 200 || sampleRate <= 0) return null

        val hopSec = HOP / sampleRate.toFloat()

        // Onset strength: positive energy change minus its local average.
        val flux = FloatArray(n)
        for (i in 1 until n) flux[i] = max(0f, e[i] - e[i - 1])

        val adj = FloatArray(n)
        val w = 16
        for (i in 0 until n) {
            var s = 0f
            var c = 0
            for (j in max(0, i - w)..min(n - 1, i + w)) {
                s += flux[j]
                c++
            }
            adj[i] = max(0f, flux[i] - s / c)
        }

        val minLag = (60f / 170f / hopSec).roundToInt().coerceAtLeast(2)
        val maxLag = (60f / 80f / hopSec).roundToInt().coerceAtMost(n / 2)
        if (maxLag <= minLag + 2) return null

        val ac = FloatArray(maxLag + 2)
        for (lag in minLag..maxLag) {
            var s = 0f
            for (i in 0 until n - lag) s += adj[i] * adj[i + lag]
            ac[lag] = s
        }

        var bestLag = minLag
        for (lag in minLag..maxLag) if (ac[lag] > ac[bestLag]) bestLag = lag
        if (ac[bestLag] <= 0f) return null

        var period = bestLag.toFloat()
        if (bestLag > minLag && bestLag < maxLag) {
            val a = ac[bestLag - 1]
            val b = ac[bestLag]
            val c = ac[bestLag + 1]
            val denom = a - 2f * b + c
            if (denom != 0f) period += 0.5f * (a - c) / denom
        }

        // Find the grid phase that lands on the strongest onsets.
        var bestPhase = 0f
        var bestScore = -1f
        var phase = 0f
        while (phase < period) {
            var score = 0f
            var pos = phase
            while (pos < n) {
                score += adj[pos.roundToInt().coerceIn(0, n - 1)]
                pos += period
            }
            if (score > bestScore) {
                bestScore = score
                bestPhase = phase
            }
            phase += 1f
        }

        val beats = ArrayList<Long>()
        var pos = bestPhase
        while (pos < n) {
            beats.add((pos * hopSec * 1000f).toLong())
            pos += period
        }
        if (beats.size < 4) return null

        return Result(60f / (period * hopSec), beats)
    }
}
