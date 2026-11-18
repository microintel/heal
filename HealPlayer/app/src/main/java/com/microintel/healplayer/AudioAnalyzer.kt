package com.microintel.healplayer

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Listens to the PCM audio that ExoPlayer is about to play (no microphone / no extra
 * permission needed) and keeps the latest waveform + frequency spectrum for the visualizers.
 */
@OptIn(UnstableApi::class)
object AudioAnalyzer : TeeAudioProcessor.AudioBufferSink {

    const val SAMPLES = 72          // waveform points
    const val BANDS = 48            // frequency bars
    const val MIN_HZ = 40f
    const val MAX_HZ = 16000f

    private const val WINDOW = 1024
    private const val N = 1024      // FFT size

    private val ring = FloatArray(WINDOW)
    private var pos = 0
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private var sampleRate = 44100

    // FFT scratch
    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val hann = FloatArray(N) { (0.5 - 0.5 * cos(2.0 * Math.PI * it / (N - 1))).toFloat() }
    private val cosT = FloatArray(N / 2) { cos(-2.0 * Math.PI * it / N).toFloat() }
    private val sinT = FloatArray(N / 2) { sin(-2.0 * Math.PI * it / N).toFloat() }
    private var lastFftNs = 0L

    /** Latest waveform, [SAMPLES] values in -1..1. */
    @Volatile
    var wave: FloatArray = FloatArray(SAMPLES)
        private set

    /** Latest spectrum, [BANDS] log-spaced bands (MIN_HZ..MAX_HZ), each 0..1. */
    @Volatile
    var spectrum: FloatArray = FloatArray(BANDS)
        private set

    fun bandEdge(i: Int): Float = MIN_HZ * Math.pow((MAX_HZ / MIN_HZ).toDouble(), i.toDouble() / BANDS).toFloat()
    fun bandCenter(i: Int): Float = sqrt(bandEdge(i) * bandEdge(i + 1))

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.sampleRate = sampleRateHz.coerceAtLeast(8000)
        this.channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        ring.fill(0f)
        pos = 0
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val b = buffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder())
        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                val frame = 2 * channels
                while (b.remaining() >= frame) {
                    var sum = 0f
                    for (c in 0 until channels) sum += b.getShort() / 32768f
                    push(sum / channels)
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                val frame = 4 * channels
                while (b.remaining() >= frame) {
                    var sum = 0f
                    for (c in 0 until channels) sum += b.getFloat()
                    push(sum / channels)
                }
            }
            else -> return
        }
        snapshotWave()
        computeSpectrum()
    }

    private fun push(v: Float) {
        ring[pos] = v
        pos = (pos + 1) % WINDOW
    }

    private fun snapshotWave() {
        val raw = FloatArray(SAMPLES)
        for (i in 0 until SAMPLES) {
            raw[i] = ring[(pos + (i * (WINDOW - 1)) / (SAMPLES - 1)) % WINDOW]
        }
        // light 1-2-1 smoothing so the lines look like a wave, not noise
        val out = FloatArray(SAMPLES)
        for (i in 0 until SAMPLES) {
            val a = raw[maxOf(i - 1, 0)]
            val c = raw[minOf(i + 1, SAMPLES - 1)]
            out[i] = (a + 2f * raw[i] + c) / 4f
        }
        wave = out
    }

    private fun computeSpectrum() {
        val now = System.nanoTime()
        if (now - lastFftNs < 16_000_000L) return // ~60 spectra / second at most
        lastFftNs = now

        for (i in 0 until N) {
            re[i] = ring[(pos + i) % WINDOW] * hann[i]
            im[i] = 0f
        }
        fft()

        val half = N / 2
        val scale = 4f / N // a full-scale sine -> ~1.0
        val mag = FloatArray(half) { sqrt(re[it] * re[it] + im[it] * im[it]) * scale }

        val out = FloatArray(BANDS)
        val nyq = sampleRate / 2f
        val binHz = sampleRate.toFloat() / N
        for (b in 0 until BANDS) {
            val lo = bandEdge(b)
            val hi = bandEdge(b + 1)
            if (lo >= nyq) continue
            val pLo = lo / binHz
            val pHi = hi / binHz
            var m = 0f
            if (pHi - pLo > 1f) {
                // wide band: keep the loudest bin so narrow peaks never vanish
                val s = maxOf(1, pLo.toInt())
                val e = minOf(half - 1, Math.ceil(pHi.toDouble()).toInt())
                for (k in s..e) if (mag[k] > m) m = mag[k]
            } else {
                // narrow low band: interpolate between neighbouring bins
                val pc = (pLo + pHi) / 2f
                val i0 = pc.toInt().coerceIn(0, half - 1)
                val i1 = minOf(half - 1, i0 + 1)
                val fr = pc - i0
                m = mag[i0] * (1f - fr) + mag[i1] * fr
            }
            // dB scale + gentle tilt (music loses energy toward the highs)
            val db = 20f * log10(m + 1e-6f) + 3f * log2(bandCenter(b) / 500f)
            out[b] = ((db + 78f) / 60f).coerceIn(0f, 1f)
        }
        spectrum = out
    }

    /** In-place radix-2 FFT on re/im. */
    private fun fft() {
        var j = 0
        for (i in 1 until N) {
            var bit = N shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= N) {
            val step = N / len
            val halfLen = len / 2
            var i = 0
            while (i < N) {
                for (k in 0 until halfLen) {
                    val wr = cosT[k * step]
                    val wi = sinT[k * step]
                    val a = i + k
                    val b = a + halfLen
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
                i += len
            }
            len = len shl 1
        }
    }
}
