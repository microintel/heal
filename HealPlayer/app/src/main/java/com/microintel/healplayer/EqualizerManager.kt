package com.microintel.healplayer

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * System equalizer attached to the player's audio session (set up by [PlaybackService]).
 * The UI reads/writes the Compose state here; settings are saved and re-applied on every start.
 */
object EqualizerManager {

    private const val PREFS = "heal_eq"
    private var eq: Equalizer? = null
    private var bb: BassBoost? = null
    private var virt: Virtualizer? = null
    private var appCtx: Context? = null

    /** true once the equalizer is attached to the player. */
    var available by mutableStateOf(false); private set
    var enabled by mutableStateOf(false); private set
    /** index into [presets], or -1 for a custom curve. */
    var preset by mutableIntStateOf(-1); private set

    var minLevel by mutableIntStateOf(-1500); private set   // millibel
    var maxLevel by mutableIntStateOf(1500); private set
    var bandFreqs by mutableStateOf<List<Int>>(emptyList()); private set   // Hz
    var presets by mutableStateOf<List<String>>(emptyList()); private set
    val levels = mutableStateListOf<Int>()                  // millibel per band

    // "Enhance sound" knobs, 0..1000
    var bassAvailable by mutableStateOf(false); private set
    var immersiveAvailable by mutableStateOf(false); private set
    var bass by mutableIntStateOf(0); private set
    var immersive by mutableIntStateOf(0); private set

    fun attach(ctx: Context, sessionId: Int) {
        appCtx = ctx.applicationContext
        release()
        if (sessionId == 0 || sessionId == -1) return
        try {
            val e = Equalizer(0, sessionId)
            eq = e
            val bands = e.numberOfBands.toInt()
            val range = e.bandLevelRange
            minLevel = range[0].toInt()
            maxLevel = range[1].toInt()
            bandFreqs = List(bands) { e.getCenterFreq(it.toShort()) / 1000 }
            presets = List(e.numberOfPresets.toInt()) { e.getPresetName(it.toShort()) }

            val sp = prefs()
            val saved = sp?.getString("levels", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
            val savedPreset = sp?.getInt("preset", -1) ?: -1
            val on = sp?.getBoolean("enabled", false) ?: false
            when {
                saved != null && saved.size == bands -> {
                    saved.forEachIndexed { i, v -> e.setBandLevel(i.toShort(), v.coerceIn(minLevel, maxLevel).toShort()) }
                    preset = if (savedPreset in presets.indices) savedPreset else -1
                }
                else -> preset = -1
            }
            e.enabled = on
            enabled = on
            readLevels(e)
            available = true

            // bass boost + virtualizer are optional: not every device has them
            bass = (sp?.getInt("bass", 0) ?: 0).coerceIn(0, 1000)
            immersive = (sp?.getInt("immersive", 0) ?: 0).coerceIn(0, 1000)
            try {
                val b = BassBoost(0, sessionId)
                if (b.strengthSupported) {
                    bb = b
                    b.setStrength(bass.toShort())
                    b.enabled = on
                    bassAvailable = true
                } else b.release()
            } catch (_: Throwable) { bb = null; bassAvailable = false }
            try {
                val v = Virtualizer(0, sessionId)
                if (v.strengthSupported) {
                    virt = v
                    v.setStrength(immersive.toShort())
                    v.enabled = on
                    immersiveAvailable = true
                } else v.release()
            } catch (_: Throwable) { virt = null; immersiveAvailable = false }
        } catch (t: Throwable) {
            // device without equalizer support
            eq = null
            available = false
        }
    }

    fun release() {
        try { eq?.release() } catch (_: Throwable) { }
        try { bb?.release() } catch (_: Throwable) { }
        try { virt?.release() } catch (_: Throwable) { }
        eq = null
        bb = null
        virt = null
        available = false
        bassAvailable = false
        immersiveAvailable = false
    }

    fun enable(on: Boolean) {
        val e = eq ?: return
        try {
            e.enabled = on
            enabled = on
            try { bb?.enabled = on } catch (_: Throwable) { }
            try { virt?.enabled = on } catch (_: Throwable) { }
        } catch (_: Throwable) { }
        save()
    }

    /** "Custom" tile: just make sure the equalizer is on and treat the current curve as custom. */
    fun useCustom() {
        if (!enabled) enable(true)
        preset = -1
        save()
    }

    fun changeBass(v: Int) {
        val b = bb ?: return
        if (!enabled) enable(true)
        try { b.setStrength(v.coerceIn(0, 1000).toShort()); bass = v.coerceIn(0, 1000) } catch (_: Throwable) { }
        save()
    }

    fun changeImmersive(v: Int) {
        val x = virt ?: return
        if (!enabled) enable(true)
        try { x.setStrength(v.coerceIn(0, 1000).toShort()); immersive = v.coerceIn(0, 1000) } catch (_: Throwable) { }
        save()
    }

    fun setBand(index: Int, level: Int) {
        val e = eq ?: return
        if (index !in levels.indices) return
        val v = level.coerceIn(minLevel, maxLevel)
        try {
            if (!enabled) enable(true)
            e.setBandLevel(index.toShort(), v.toShort())
            levels[index] = v
            preset = -1
        } catch (_: Throwable) { }
        save()
    }

    fun usePreset(index: Int) {
        val e = eq ?: return
        if (index !in presets.indices) return
        try {
            if (!enabled) enable(true)
            e.usePreset(index.toShort())
            readLevels(e)
            preset = index
        } catch (_: Throwable) { }
        save()
    }

    fun reset() {
        val e = eq ?: return
        try {
            for (i in levels.indices) e.setBandLevel(i.toShort(), 0)
            readLevels(e)
            preset = -1
        } catch (_: Throwable) { }
        save()
    }

    private fun readLevels(e: Equalizer) {
        levels.clear()
        for (i in 0 until e.numberOfBands) levels.add(e.getBandLevel(i.toShort()).toInt())
    }

    private fun prefs() = appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun save() {
        prefs()?.edit()
            ?.putBoolean("enabled", enabled)
            ?.putInt("preset", preset)
            ?.putString("levels", levels.joinToString(","))
            ?.putInt("bass", bass)
            ?.putInt("immersive", immersive)
            ?.apply()
    }
}
