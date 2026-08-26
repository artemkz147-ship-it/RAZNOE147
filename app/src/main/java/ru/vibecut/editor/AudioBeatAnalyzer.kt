package ru.vibecut.editor

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class BeatPoint(
    val timeMs: Long,
    val strength: Float,
    val strong: Boolean,
)

data class BeatMap(
    val durationMs: Long,
    val bpm: Int,
    val confidence: Float,
    val beats: List<BeatPoint>,
) {
    val strongBeatCount: Int get() = beats.count { it.strong }
}

class AudioBeatAnalyzer(private val context: Context) {
    private val generation = AtomicInteger(0)
    private val main = Handler(Looper.getMainLooper())

    fun cancel() {
        generation.incrementAndGet()
    }

    fun analyze(
        uri: Uri,
        onDone: (BeatMap) -> Unit,
        onError: (String) -> Unit,
    ) {
        val token = generation.incrementAndGet()
        Thread {
            runCatching { analyzeBlocking(uri, token) }
                .onSuccess { map ->
                    if (token == generation.get()) main.post { onDone(map) }
                }
                .onFailure { error ->
                    if (token == generation.get()) main.post {
                        onError(error.message ?: "Не удалось проанализировать музыку")
                    }
                }
        }.apply { name = "VibeCut-BeatAnalyzer"; isDaemon = true }.start()
    }

    private fun analyzeBlocking(uri: Uri, token: Int): BeatMap {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrack = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    inputFormat = format
                    break
                }
            }
            require(audioTrack >= 0 && inputFormat != null) { "В файле нет звуковой дорожки" }
            extractor.selectTrack(audioTrack)
            val mime = inputFormat!!.getString(MediaFormat.KEY_MIME) ?: error("Неизвестный формат звука")
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            var sampleRate = inputFormat.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var channelCount = inputFormat.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, 2).coerceAtLeast(1)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            val windowFramesTarget = 1024
            var frameInWindow = 0
            var sumSquares = 0.0
            var totalFrames = 0L
            val energies = ArrayList<Float>(16_384)
            val timesMs = ArrayList<Long>(16_384)
            var inputEnded = false
            var outputEnded = false
            val info = MediaCodec.BufferInfo()

            fun consumeSample(value: Float) {
                sumSquares += value.toDouble() * value.toDouble()
            }

            fun finishFrame() {
                frameInWindow++
                totalFrames++
                if (frameInWindow >= windowFramesTarget) {
                    val rms = sqrt(sumSquares / (frameInWindow * channelCount).coerceAtLeast(1).toDouble()).toFloat()
                    energies += rms
                    timesMs += (totalFrames * 1000L / sampleRate.coerceAtLeast(1))
                    frameInWindow = 0
                    sumSquares = 0.0
                }
            }

            while (!outputEnded) {
                if (token != generation.get()) error("Анализ отменён")
                if (!inputEnded) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inIndex) ?: error("Нет входного буфера декодера")
                        inputBuffer.clear()
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime.coerceAtLeast(0L), 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = decoder.outputFormat
                        sampleRate = f.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channelCount = f.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, channelCount).coerceAtLeast(1)
                        pcmEncoding = f.getIntegerOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outIndex >= 0) {
                        val buffer = decoder.getOutputBuffer(outIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            buffer.order(ByteOrder.LITTLE_ENDIAN)
                            when (pcmEncoding) {
                                AudioFormat.ENCODING_PCM_FLOAT -> {
                                    val fb = buffer.asFloatBuffer()
                                    var channel = 0
                                    while (fb.hasRemaining()) {
                                        consumeSample(fb.get().coerceIn(-1f, 1f))
                                        channel++
                                        if (channel >= channelCount) { channel = 0; finishFrame() }
                                    }
                                }
                                else -> {
                                    val sb = buffer.asShortBuffer()
                                    var channel = 0
                                    while (sb.hasRemaining()) {
                                        consumeSample(sb.get() / 32768f)
                                        channel++
                                        if (channel >= channelCount) { channel = 0; finishFrame() }
                                    }
                                }
                            }
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outIndex, false)
                    }
                }
            }

            if (frameInWindow > 0) {
                val rms = sqrt(sumSquares / (frameInWindow * channelCount).coerceAtLeast(1).toDouble()).toFloat()
                energies += rms
                timesMs += (totalFrames * 1000L / sampleRate.coerceAtLeast(1))
            }
            require(energies.size >= 8) { "Слишком короткая музыка для анализа ритма" }
            return buildBeatMap(energies, timesMs)
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun buildBeatMap(energy: List<Float>, timesMs: List<Long>): BeatMap {
        val logEnergy = FloatArray(energy.size) { i -> ln(1.0 + energy[i].coerceAtLeast(0f) * 80.0).toFloat() }
        val novelty = FloatArray(energy.size)
        for (i in 1 until energy.size) {
            val d1 = (logEnergy[i] - logEnergy[i - 1]).coerceAtLeast(0f)
            val d2 = if (i >= 2) (logEnergy[i] - logEnergy[i - 2]).coerceAtLeast(0f) * 0.35f else 0f
            novelty[i] = d1 + d2
        }
        val smoothed = FloatArray(novelty.size)
        for (i in novelty.indices) {
            var sum = 0f
            var weight = 0f
            for (k in -2..2) {
                val j = i + k
                if (j in novelty.indices) {
                    val w = when (abs(k)) { 0 -> 1f; 1 -> .65f; else -> .35f }
                    sum += novelty[j] * w
                    weight += w
                }
            }
            smoothed[i] = if (weight > 0f) sum / weight else novelty[i]
        }

        val candidates = ArrayList<Pair<Int, Float>>()
        for (i in 3 until smoothed.lastIndex - 3) {
            val start = max(0, i - 24)
            val end = min(smoothed.lastIndex, i + 24)
            var mean = 0.0
            var sq = 0.0
            var count = 0
            for (j in start..end) {
                val v = smoothed[j].toDouble()
                mean += v
                sq += v * v
                count++
            }
            mean /= count.coerceAtLeast(1)
            val variance = (sq / count.coerceAtLeast(1) - mean * mean).coerceAtLeast(0.0)
            val threshold = (mean + sqrt(variance) * 0.85).toFloat()
            val localMax = smoothed[i] >= smoothed[i - 1] && smoothed[i] >= smoothed[i + 1] &&
                smoothed[i] >= smoothed[i - 2] && smoothed[i] >= smoothed[i + 2]
            if (localMax && smoothed[i] > threshold && smoothed[i] > 0.015f) {
                candidates += i to smoothed[i]
            }
        }

        val accepted = ArrayList<Pair<Int, Float>>()
        val minGapMs = 180L
        for (candidate in candidates.sortedBy { it.first }) {
            val time = timesMs[candidate.first]
            val last = accepted.lastOrNull()
            if (last == null || time - timesMs[last.first] >= minGapMs) {
                accepted += candidate
            } else if (candidate.second > last.second) {
                accepted[accepted.lastIndex] = candidate
            }
        }
        require(accepted.size >= 3) { "Не удалось уверенно найти ритм. Попробуйте трек с более выраженными ударами." }

        val strengths = accepted.map { it.second }.sorted()
        val medianStrength = strengths[strengths.size / 2]
        val strongThreshold = strengths[(strengths.size * 0.72f).toInt().coerceIn(0, strengths.lastIndex)]
        val maxStrength = strengths.last().coerceAtLeast(0.0001f)
        val beats = accepted.map { (index, value) ->
            BeatPoint(
                timeMs = timesMs[index],
                strength = (value / maxStrength).coerceIn(0f, 1f),
                strong = value >= strongThreshold,
            )
        }

        val intervals = beats.zipWithNext { a, b -> b.timeMs - a.timeMs }
            .filter { it in 220L..1500L }
        val bpm = estimateBpm(intervals)
        val regularity = if (intervals.size < 2) 0f else {
            val target = 60_000f / bpm.coerceAtLeast(1)
            val errors = intervals.map { interval ->
                var x = interval.toFloat()
                while (x < target * .67f) x *= 2f
                while (x > target * 1.5f) x /= 2f
                abs(x - target) / target
            }
            (1f - errors.average().toFloat()).coerceIn(0f, 1f)
        }
        val density = (beats.size / max(1f, timesMs.last().toFloat() / 1000f)).coerceIn(0f, 4f) / 4f
        val confidence = (regularity * .78f + density * .12f + min(1f, medianStrength / maxStrength * 1.8f) * .10f).coerceIn(0f, 1f)
        return BeatMap(timesMs.last(), bpm, confidence, beats)
    }

    private fun estimateBpm(intervals: List<Long>): Int {
        if (intervals.isEmpty()) return 120
        val bpms = intervals.map { 60_000f / it.coerceAtLeast(1L) }.map { raw ->
            var bpm = raw
            while (bpm < 72f) bpm *= 2f
            while (bpm > 180f) bpm /= 2f
            bpm
        }.sorted()
        val median = bpms[bpms.size / 2]
        var best = median
        var bestScore = Float.NEGATIVE_INFINITY
        for (candidate in 72..180) {
            var score = 0f
            for (bpm in bpms) {
                val diff = abs(bpm - candidate)
                val half = abs(bpm * 2f - candidate)
                val dbl = abs(bpm / 2f - candidate)
                score += 1f / (1f + min(diff, min(half, dbl)).pow(2) / 16f)
            }
            score -= abs(candidate - median) * .005f
            if (score > bestScore) { bestScore = score; best = candidate.toFloat() }
        }
        return best.toInt().coerceIn(72, 180)
    }
}

private fun MediaFormat.getIntegerOr(key: String, fallback: Int): Int =
    if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(fallback) else fallback
