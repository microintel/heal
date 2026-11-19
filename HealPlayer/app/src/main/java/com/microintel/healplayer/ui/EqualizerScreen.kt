package com.microintel.healplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.microintel.healplayer.EqualizerManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Equalizer: presets grid, manual band sliders and the "Enhance sound" knobs (bass / immersive audio). */
@Composable
fun EqualizerScreen(onBack: (() -> Unit)? = null) {
    if (onBack != null) BackHandler(onBack = onBack)
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val em = EqualizerManager

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { } },   // never let touches fall through to the screen below
    ) {
        // header: back · centred title · on/off switch
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp, bottom = 4.dp)) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
            }
            Text(
                "Equaliser", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
            Switch(
                checked = em.enabled,
                onCheckedChange = { em.enable(it) },
                enabled = em.available,
                colors = SwitchDefaults.colors(checkedTrackColor = HealTeal),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp),
            )
        }

        if (!em.available) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Equaliser isn't ready yet. Start a song and open this screen again.\n(Some devices don't support it.)",
                    color = dim, textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp),
        ) {
            // ── Recommended presets ──
            SectionTitle("Recommended presets")
            val tiles = listOf("Custom") + em.presets
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in tiles.indices.toList().chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (idx in row) {
                            val selected = if (idx == 0) em.preset == -1 else em.preset == idx - 1
                            PresetTile(tiles[idx], selected, Modifier.weight(1f)) {
                                if (idx == 0) em.useCustom() else em.usePreset(idx - 1)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            // ── Manual adjustment ──
            Spacer(Modifier.height(28.dp))
            SectionTitle("Manual adjustment")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (i in em.levels.indices) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BandSlider(
                            level = em.levels[i], min = em.minLevel, max = em.maxLevel, enabled = em.enabled,
                            onChange = { em.setBand(i, it) },
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(freqLabel(em.bandFreqs.getOrElse(i) { 0 }), style = MaterialTheme.typography.labelMedium, color = dim)
                        val db = Math.round(em.levels[i] / 100f)
                        Text(
                            if (db > 0) "+$db" else "$db",
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }

            // ── Enhance sound ──
            if (em.bassAvailable || em.immersiveAvailable) {
                Spacer(Modifier.height(20.dp))
                SectionTitle("Enhance sound")
                Row(Modifier.padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Headphones, null, Modifier.size(16.dp), tint = dim)
                    Spacer(Modifier.width(6.dp))
                    Text("Put in earphones before adjusting sound effects", style = MaterialTheme.typography.bodySmall, color = dim)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (em.bassAvailable) Knob("Bass", em.bass, em.enabled) { em.changeBass(it) }
                    if (em.immersiveAvailable) Knob("Immersive audio", em.immersive, em.enabled) { em.changeImmersive(it) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
    )
}

private fun freqLabel(hz: Int): String =
    if (hz >= 1000) String.format("%.1fkHz", hz / 1000f) else "${hz}Hz"

@Composable
private fun PresetTile(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = HealTeal
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.height(56.dp).clip(shape)
            .background(
                if (selected) Brush.linearGradient(listOf(accent, lerp(accent, Color.White, 0.35f)))
                else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, maxLines = 1, textAlign = TextAlign.Center,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

/** Thin vertical slider: bottom = max cut, top = max boost; the part below the thumb is filled. */
@Composable
private fun BandSlider(level: Int, min: Int, max: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val accent = if (enabled) HealTeal else MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f)
    val span = (max - min).coerceAtLeast(1)

    fun fromY(y: Float, h: Float, pad: Float): Int {
        val f = 1f - ((y - pad) / (h - 2 * pad)).coerceIn(0f, 1f)
        val raw = min + f * span
        return (Math.round(raw / 100f) * 100).coerceIn(min, max)   // whole-dB steps
    }

    Canvas(
        Modifier.width(48.dp).height(300.dp)
            .pointerInput(span) {
                detectTapGestures { o -> onChange(fromY(o.y, size.height.toFloat(), 14.dp.toPx())) }
            }
            .pointerInput(span) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    onChange(fromY(change.position.y, size.height.toFloat(), 14.dp.toPx()))
                }
            },
    ) {
        val pad = 14.dp.toPx()
        val cx = size.width / 2f
        val top = pad
        val bottom = size.height - pad
        val frac = (level - min).toFloat() / span
        val ty = bottom - frac * (bottom - top)

        drawLine(track, Offset(cx, top), Offset(cx, ty), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(accent, Offset(cx, ty), Offset(cx, bottom), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(accent, radius = 13.dp.toPx(), center = Offset(cx, ty))
    }
}

/** Rotary knob, 0..1000, with a dotted ring that lights up as the value rises. */
@Composable
private fun Knob(label: String, value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val accent = if (enabled) HealTeal else MaterialTheme.colorScheme.onSurfaceVariant
    val dots = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
    val ring = MaterialTheme.colorScheme.surfaceVariant
    val body = lerp(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, 0.45f)
    val sweep = 135f   // knob runs from -135° to +135° (0° = straight up)

    fun setFrom(pos: Offset, w: Float, h: Float) {
        val a = Math.toDegrees(atan2((pos.x - w / 2f).toDouble(), -(pos.y - h / 2f).toDouble())).toFloat()
        if (abs(a) > sweep + 10f) return   // dead zone at the bottom: never flips from min to max
        val f = ((a.coerceIn(-sweep, sweep) + sweep) / (2 * sweep)).coerceIn(0f, 1f)
        onChange((f * 1000f).toInt())
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.size(132.dp)
                .pointerInput(Unit) { detectTapGestures { setFrom(it, size.width.toFloat(), size.height.toFloat()) } }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { setFrom(it, size.width.toFloat(), size.height.toFloat()) },
                        onDrag = { change, _ ->
                            change.consume()
                            setFrom(change.position, size.width.toFloat(), size.height.toFloat())
                        },
                    )
                },
        ) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val outer = size.width / 2f
            val dotR = outer - 7.dp.toPx()
            val n = 15
            val v = value / 1000f
            for (i in 0 until n) {
                val f = i / (n - 1).toFloat()
                val ang = Math.toRadians((-sweep + 2 * sweep * f).toDouble())
                val p = Offset(c.x + dotR * sin(ang).toFloat(), c.y - dotR * cos(ang).toFloat())
                val lit = f <= v + 0.001f && value > 0
                drawCircle(if (lit) accent else dots, radius = (if (lit) 3.6f - 1.4f * f else 2f).dp.toPx(), center = p)
            }
            drawCircle(ring, radius = outer * 0.70f, center = c)
            drawCircle(body, radius = outer * 0.58f, center = c)
            val a = Math.toRadians((-sweep + 2 * sweep * v).toDouble())
            val s = Offset(c.x + outer * 0.22f * sin(a).toFloat(), c.y - outer * 0.22f * cos(a).toFloat())
            val e = Offset(c.x + outer * 0.46f * sin(a).toFloat(), c.y - outer * 0.46f * cos(a).toFloat())
            drawLine(lerp(accent, Color.White, 0.5f), s, e, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
