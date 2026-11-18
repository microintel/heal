package com.microintel.healplayer

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Simple streaming tone generator (sine / square / triangle / saw, 20–20000 Hz). */
object ToneGenerator {

    enum class Waveform(val label: String) { SINE("sine"), SQUARE("square"), TRI("tri"), SAW("saw") }

    @Volatile var frequency = 440f
    @Volatile var waveform = Waveform.SINE
    @Volatile var volume = 0.3f

    @Volatile private var running = false
    private var thread: Thread? = null

    val isRunning: Boolean get() = running

    fun start() {
        if (running) return
        running = true
        thread = Thread {
            val sr = 44100
            val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sr)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, 4096) * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()

            val buf = ShortArray(1024)
            var phase = 0.0
            var curF = frequency.toDouble()
            var curV = 0f                        // fades in from silence (no click)

            fun fill(targetV: Float) {
                val targetF = frequency
                val wf = waveform
                for (i in buf.indices) {
                    curF += (targetF - curF) * 0.0005          // glide between frequencies
                    curV += (targetV - curV) * 0.002f          // smooth volume changes
                    phase += curF / sr
                    if (phase >= 1.0) phase -= 1.0
                    val p = phase.toFloat()
                    val v = when (wf) {
                        Waveform.SINE -> sin(2.0 * PI * p).toFloat()
                        Waveform.SQUARE -> if (p < 0.5f) 1f else -1f
                        Waveform.TRI -> 4f * abs(p - 0.5f) - 1f
                        Waveform.SAW -> 2f * p - 1f
                    }
                    buf[i] = (v * curV * Short.MAX_VALUE).toInt().toShort()
                }
            }

            while (running) {
                fill(volume)
                track.write(buf, 0, buf.size)
            }
            repeat(6) {                                  // fade out before stopping
                fill(0f)
                track.write(buf, 0, buf.size)
            }
            track.stop()
            track.release()
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        thread = null
    }
}
