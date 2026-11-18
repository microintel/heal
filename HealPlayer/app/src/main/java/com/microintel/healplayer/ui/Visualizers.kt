package com.microintel.healplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microintel.healplayer.AudioAnalyzer
import com.microintel.healplayer.VizMode
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

private const val SAMPLES = AudioAnalyzer.SAMPLES
private const val GAIN = 1.8f
private const val TWO_PI = 6.2831855f
private val TEAL = Color(0xFF4FD8BE)
private val INDIGO = Color(0xFF6366F1)
private val ROSE = Color(0xFFE11D48)

// ───────────────────────── Switcher ─────────────────────────

/**
 * Pinch-zoom + drag. Two fingers always count; one finger only when [singleFinger] says so
 * (otherwise the touch is left alone so the tab pager can swipe).
 */
internal fun Modifier.panZoom(singleFinger: () -> Boolean, onGesture: (Offset, Float) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val multi = event.changes.count { it.pressed } > 1
                if (multi || singleFinger()) {
                    val z = event.calculateZoom()
                    val pan = event.calculatePan()
                    if (z != 1f || pan != Offset.Zero) {
                        onGesture(pan, z)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

/** [resetKey] changes -> every visualizer snaps back to its default ("perfect") view. */
@Composable
fun VizPanel(
    mode: VizMode,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    resetKey: Int = 0,
    freq3D: Boolean = true,
    onVolumeExceeded: () -> Unit = {},
) {
    when (mode) {
        VizMode.WAVE3D -> Wave3D(isPlaying, modifier, resetKey)
        VizMode.WAVE2D -> Wave2D(isPlaying, modifier, resetKey)
        VizMode.FREQ -> if (freq3D) Frequencies3D(isPlaying, modifier, resetKey) else Frequencies2D(isPlaying, modifier, resetKey)
        VizMode.BRAIN -> BrainViz(isPlaying, modifier, resetKey, onVolumeExceeded)
    }
}

@Composable
fun VizModeChips(mode: VizMode, onChange: (VizMode) -> Unit, modifier: Modifier = Modifier) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        for (m in VizMode.values()) {
            val sel = m == mode
            Text(
                m.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                color = if (sel) HealTeal else dim,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (sel) HealTeal.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onChange(m) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun VizModeIcons(mode: VizMode, onChange: (VizMode) -> Unit, modifier: Modifier = Modifier) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (m in VizMode.values()) {
            val sel = m == mode
            val icon: ImageVector = when (m) {
                VizMode.WAVE3D -> Icons.Rounded.ViewInAr
                VizMode.WAVE2D -> Icons.Rounded.ShowChart
                VizMode.FREQ -> Icons.Rounded.BarChart
                VizMode.BRAIN -> Icons.Rounded.Psychology
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(if (sel) HealTeal.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onChange(m) },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, m.label, Modifier.size(22.dp), tint = if (sel) HealTeal else dim) }
        }
    }
}

// ───────────────────────── 3D wave ─────────────────────────
// Same scene as the Heal web app (waterfall of waveform rows), plus depth colour,
// translucent "terrain" fill under every row and a beat-reactive camera.

private const val ROWS = 24
private const val ROW_SPACING = 0.42f
private const val ROW_WIDTH = 7f
private const val STEP = 1f / 60f

@Composable
fun Wave3D(isPlaying: Boolean, modifier: Modifier = Modifier, resetKey: Int = 0) {
    val rows = remember { Array(ROWS) { FloatArray(SAMPLES) } }
    val energy = remember { FloatArray(2) } // [0] raw, [1] smoothed
    var tick by remember { mutableLongStateOf(0L) }
    var yaw by remember { mutableFloatStateOf(0.35f) }
    var pitch by remember { mutableFloatStateOf(0.28f) }
    var auto by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val playing by rememberUpdatedState(isPlaying)
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(resetKey) {
        if (first[0]) first[0] = false
        else { yaw = 0.35f; pitch = 0.28f; zoom = 1f; auto = false }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        var acc = 0f
        var idlePhase = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                if (auto) yaw += dt * 0.07f
                acc += dt
                var n = 0
                while (acc >= STEP && n < 3) {
                    acc -= STEP
                    n++
                    val oldest = rows[ROWS - 1]
                    for (i in ROWS - 1 downTo 1) rows[i] = rows[i - 1]
                    rows[0] = oldest
                    if (playing) {
                        val w = AudioAnalyzer.wave
                        for (j in 0 until SAMPLES) oldest[j] = (w[j] * GAIN).coerceIn(-1f, 1f)
                    } else {
                        idlePhase += 0.02f
                        for (j in 0 until SAMPLES) {
                            oldest[j] = sin(j / (SAMPLES - 1f) * TWO_PI * 3f + idlePhase) * 0.55f
                        }
                    }
                }
                if (n == 3) acc = 0f
                var s = 0f
                for (v in rows[0]) s += v * v
                energy[0] = sqrt(s / SAMPLES)
                energy[1] += (energy[0] - energy[1]) * 0.12f
                tick = now
            }
        }
    }

    Canvas(
        modifier.panZoom({ true }) { pan, z ->
            auto = false
            yaw -= pan.x * 0.01f
            pitch = (pitch + pan.y * 0.01f).coerceIn(-0.1f, 1.5f)
            zoom = (zoom * z).coerceIn(0.5f, 4f)
        }
    ) {
        if (tick < 0L) return@Canvas // reads `tick` so the canvas redraws every frame

        val w = size.width
        val h = size.height
        val e = energy[1]
        val dist = (15.2f - e * 1.6f) / zoom // camera pushes in on loud parts
        val tx = 0f
        val ty = 0.3f
        val tz = -((ROWS - 1) * ROW_SPACING) / 2f
        val cp = cos(pitch)
        val camX = tx + dist * cp * sin(yaw)
        val camY = ty + dist * sin(pitch)
        val camZ = tz + dist * cp * cos(yaw)

        var fx = tx - camX; var fy = ty - camY; var fz = tz - camZ
        val fl = sqrt(fx * fx + fy * fy + fz * fz); fx /= fl; fy /= fl; fz /= fl
        var rx = -fz; var rz = fx
        val rl = sqrt(rx * rx + rz * rz); rx /= rl; rz /= rl
        val ux = -rz * fy; val uy = rz * fx - rx * fz; val uz = rx * fy
        val focal = minOf(w * 1.5f, h * 2.8f)
        val p = FloatArray(2)

        fun proj(x: Float, y: Float, z: Float): Boolean {
            val dx = x - camX; val dy = y - camY; val dz = z - camZ
            val zc = dx * fx + dy * fy + dz * fz
            if (zc < 0.1f) return false
            p[0] = w / 2f + (dx * rx + dz * rz) / zc * focal
            p[1] = h / 2f - (dx * ux + dy * uy + dz * uz) / zc * focal
            return true
        }

        // floor grid
        val gridColor = Color.Gray.copy(alpha = 0.18f)
        val half = ROW_WIDTH * 1.5f / 2f
        for (i in 0..16) {
            val c = -half + i * (half * 2f / 16f)
            if (proj(-half, -1.3f, tz + c)) {
                val ax = p[0]; val ay = p[1]
                if (proj(half, -1.3f, tz + c)) drawLine(gridColor, Offset(ax, ay), Offset(p[0], p[1]), 1f)
            }
            if (proj(c, -1.3f, tz - half)) {
                val ax = p[0]; val ay = p[1]
                if (proj(c, -1.3f, tz + half)) drawLine(gridColor, Offset(ax, ay), Offset(p[0], p[1]), 1f)
            }
        }

        val line = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val glow = Stroke(width = (6 + e * 8).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val x0 = -0.5f * ROW_WIDTH
        val x1 = 0.5f * ROW_WIDTH

        for (r in ROWS - 1 downTo 0) {
            val row = rows[r]
            val z = -r * ROW_SPACING
            val amp = 1.1f * (1f - r.toFloat() / ROWS * 0.35f)
            val path = Path()
            var started = false
            for (s in 0 until SAMPLES) {
                val x = (s / (SAMPLES - 1f) - 0.5f) * ROW_WIDTH
                if (!proj(x, row[s] * amp, z)) continue
                if (!started) { path.moveTo(p[0], p[1]); started = true } else path.lineTo(p[0], p[1])
            }
            if (!started) continue

            val t = r / (ROWS - 1f)
            val c = lerp(TEAL, INDIGO, t)       // teal up front -> indigo in the distance
            val alpha = 1f - t * 0.85f

            // translucent terrain under the line, down to the floor
            if (proj(x1, -1.3f, z)) {
                val ax = p[0]; val ay = p[1]
                if (proj(x0, -1.3f, z)) {
                    val fill = Path()
                    fill.addPath(path)
                    fill.lineTo(ax, ay)
                    fill.lineTo(p[0], p[1])
                    fill.close()
                    drawPath(fill, c.copy(alpha = alpha * (0.07f + e * 0.10f)))
                }
            }
            drawPath(path, c.copy(alpha = alpha * (0.10f + e * 0.12f)), style = glow)
            drawPath(path, c.copy(alpha = alpha), style = line)
        }
    }
}

// ───────────────────────── 2D wave ─────────────────────────

@Composable
fun Wave2D(isPlaying: Boolean, modifier: Modifier = Modifier, resetKey: Int = 0) {
    val cur = remember { FloatArray(SAMPLES) }
    val energy = remember { FloatArray(1) }
    var tick by remember { mutableLongStateOf(0L) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var start by remember { mutableFloatStateOf(0f) }      // first visible sample (fractional)
    var widthPx by remember { mutableFloatStateOf(1f) }
    val playing by rememberUpdatedState(isPlaying)
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(resetKey) {
        if (first[0]) first[0] = false else { zoom = 1f; start = 0f }
    }

    LaunchedEffect(Unit) {
        var idle = 0f
        while (true) {
            withFrameNanos { now ->
                if (playing) {
                    val w = AudioAnalyzer.wave
                    for (j in 0 until SAMPLES) cur[j] = cur[j] * 0.45f + (w[j] * GAIN).coerceIn(-1f, 1f) * 0.55f
                } else {
                    idle += 0.02f
                    for (j in 0 until SAMPLES) cur[j] = sin(j / (SAMPLES - 1f) * TWO_PI * 3f + idle) * 0.55f
                }
                var s = 0f
                for (v in cur) s += v * v
                energy[0] = sqrt(s / SAMPLES)
                tick = now
            }
        }
    }

    Canvas(
        modifier.clipToBounds()
            .onSizeChanged { widthPx = it.width.toFloat() }
            .panZoom({ zoom > 1.01f }) { pan, z ->
                val oldLen = (SAMPLES - 1f) / zoom
                zoom = (zoom * z).coerceIn(1f, 8f)
                val len = (SAMPLES - 1f) / zoom
                start += (oldLen - len) / 2f                       // zoom around the centre
                start -= pan.x / widthPx * len                     // drag to move along the wave
                start = start.coerceIn(0f, (SAMPLES - 1f) - len)
            }
    ) {
        if (tick < 0L) return@Canvas
        val w = size.width
        val h = size.height
        val mid = h / 2f
        val ampZoom = (1f + (zoom - 1f) * 0.1f).coerceAtMost(1.25f)
        val amp = h / 2f * 0.78f * ampZoom
        val e = energy[0]
        val len = (SAMPLES - 1f) / zoom

        val xs = FloatArray(SAMPLES) { (it - start) / len * w }
        val ys = FloatArray(SAMPLES) { mid - cur[it] * amp }
        val path = Path()
        path.moveTo(xs[0], ys[0])
        for (i in 1 until SAMPLES - 1) {
            path.quadraticBezierTo(xs[i], ys[i], (xs[i] + xs[i + 1]) / 2f, (ys[i] + ys[i + 1]) / 2f)
        }
        path.lineTo(xs[SAMPLES - 1], ys[SAMPLES - 1])

        // faint centre line (classic scope feel)
        drawLine(TEAL.copy(alpha = 0.18f), Offset(0f, mid), Offset(w, mid), 1.dp.toPx())

        // soft fill between the curve and the centre line
        val fill = Path()
        fill.addPath(path)
        fill.lineTo(xs[SAMPLES - 1], mid)
        fill.lineTo(xs[0], mid)
        fill.close()
        drawPath(
            fill,
            Brush.verticalGradient(
                listOf(TEAL.copy(alpha = 0.22f + e * 0.30f), TEAL.copy(alpha = 0.03f), Color.Transparent),
                startY = mid - amp,
                endY = mid + amp,
            ),
        )

        val stroke = Brush.horizontalGradient(listOf(TEAL, INDIGO, TEAL), startX = 0f, endX = w)
        drawPath(path, stroke, alpha = 0.18f + e * 0.2f, style = Stroke(11.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, stroke, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ───────────────────────── Frequencies ─────────────────────────

private fun spectrumColor(t: Float): Color =
    if (t < 0.5f) lerp(TEAL, INDIGO, t * 2f) else lerp(INDIGO, ROSE, (t - 0.5f) * 2f)

@Composable
fun Frequencies2D(isPlaying: Boolean, modifier: Modifier = Modifier, resetKey: Int = 0) {
    val bands = AudioAnalyzer.BANDS
    val level = remember { FloatArray(bands) }
    val peak = remember { FloatArray(bands) }
    var tick by remember { mutableLongStateOf(0L) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var start by remember { mutableFloatStateOf(0f) }      // first visible band (fractional)
    var widthPx by remember { mutableFloatStateOf(1f) }
    val playing by rememberUpdatedState(isPlaying)
    val measurer = rememberTextMeasurer()
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = remember { TextStyle(fontSize = 10.sp) }
    val tickLabels = remember(measurer) {
        listOf(100f to "100", 1000f to "1k", 10000f to "10k").map { (hz, t) -> hz to measurer.measure(t, labelStyle) }
    }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(resetKey) {
        if (first[0]) first[0] = false else { zoom = 1f; start = 0f }
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now ->
                val sp = if (playing) AudioAnalyzer.spectrum else null
                for (i in 0 until bands) {
                    val v = sp?.get(i) ?: 0f
                    level[i] = if (v > level[i]) level[i] + (v - level[i]) * 0.6f else level[i] * 0.90f
                    if (level[i] < 0.004f) level[i] = 0f
                    peak[i] = if (level[i] > peak[i]) level[i] else maxOf(0f, peak[i] - 0.008f)
                }
                tick = now
            }
        }
    }

    Canvas(
        modifier.clipToBounds()
            .onSizeChanged { widthPx = it.width.toFloat() }
            .panZoom({ zoom > 1.01f }) { pan, z ->
                val oldLen = bands / zoom
                zoom = (zoom * z).coerceIn(1f, 8f)
                val len = bands / zoom
                start += (oldLen - len) / 2f
                start -= pan.x / widthPx * len
                start = start.coerceIn(0f, bands - len)
            }
    ) {
        if (tick < 0L) return@Canvas
        val w = size.width
        val h = size.height
        val pad = 14.dp.toPx()
        val labelH = 20.dp.toPx()
        val topPad = 26.dp.toPx()
        val baseY = h - labelH
        val areaH = (baseY - topPad).coerceAtLeast(1f)
        val len = bands / zoom
        val slot = (w - 2 * pad) / len
        val gap = minOf(3.dp.toPx(), slot * 0.25f)
        val barW = (slot - gap).coerceAtLeast(1f)

        // dominant frequency readout
        var best = 0
        for (i in 1 until bands) if (level[i] > level[best]) best = i
        if (level[best] > 0.25f) {
            val hz = AudioAnalyzer.bandCenter(best)
            val txt = if (hz >= 1000f) "%.1f kHz".format(hz / 1000f) else "%.0f Hz".format(hz)
            val r = measurer.measure(txt, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
            drawText(r, color = spectrumColor(best / (bands - 1f)), topLeft = Offset(w - pad - r.size.width, 6.dp.toPx()))
        }

        val iFrom = kotlin.math.floor(start).toInt().coerceAtLeast(0)
        val iTo = kotlin.math.ceil(start + len).toInt().coerceAtMost(bands - 1)
        for (i in iFrom..iTo) {
            val x = pad + (i - start) * slot
            val c = spectrumColor(i / (bands - 1f))
            val bh = maxOf(2.dp.toPx(), level[i] * areaH)
            val top = baseY - bh
            drawRoundRect(
                Brush.verticalGradient(listOf(c, c.copy(alpha = 0.35f)), startY = top, endY = baseY),
                topLeft = Offset(x, top),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(barW / 2f),
            )
            if (peak[i] > 0.03f) {
                drawRoundRect(
                    c,
                    topLeft = Offset(x, baseY - peak[i] * areaH - 5.dp.toPx()),
                    size = Size(barW, 2.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx()),
                )
            }
        }

        // Hz labels on a log scale
        val span = ln(AudioAnalyzer.MAX_HZ / AudioAnalyzer.MIN_HZ)
        for ((hz, layout) in tickLabels) {
            val fx = ln(hz / AudioAnalyzer.MIN_HZ) / span
            val x = pad + (fx * bands - start) * slot
            if (x in pad..(w - pad)) {
                drawText(layout, color = dim, topLeft = Offset(x - layout.size.width / 2f, baseY + 5.dp.toPx()))
            }
        }
    }
}

private const val BAR_ROWS = 6          // live row + 5 history rows behind it
private const val BAR_ROW_GAP = 0.55f
private const val BAR_SPAN = 7f
private const val BAR_MAX_H = 2.6f
private const val FLOOR_Y = -1.3f

/** 3D spectrum bars: a live row of boxes that beat with the music, with a short history trailing behind. */
@Composable
fun Frequencies3D(isPlaying: Boolean, modifier: Modifier = Modifier, resetKey: Int = 0) {
    val bands = AudioAnalyzer.BANDS
    val level = remember { FloatArray(bands) }
    val hist = remember { Array(BAR_ROWS - 1) { FloatArray(bands) } }
    var tick by remember { mutableLongStateOf(0L) }
    var yaw by remember { mutableFloatStateOf(0.45f) }
    var pitch by remember { mutableFloatStateOf(0.45f) }
    var auto by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val playing by rememberUpdatedState(isPlaying)
    val energy = remember { FloatArray(1) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(resetKey) {
        if (first[0]) first[0] = false
        else { yaw = 0.45f; pitch = 0.45f; zoom = 1f; auto = false }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        var acc = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                if (auto) yaw += dt * 0.07f
                val sp = if (playing) AudioAnalyzer.spectrum else null
                var tot = 0f
                for (i in 0 until bands) {
                    val v = ((sp?.get(i) ?: 0f) * GAIN).coerceIn(0f, 1f)
                    level[i] = if (v > level[i]) level[i] + (v - level[i]) * 0.6f else level[i] * 0.90f
                    if (level[i] < 0.004f) level[i] = 0f
                    tot += level[i]
                }
                energy[0] += (tot / bands - energy[0]) * 0.12f
                acc += dt
                if (acc >= 0.07f) {                       // push a snapshot into the trailing rows
                    acc = 0f
                    val oldest = hist[hist.size - 1]
                    for (i in hist.size - 1 downTo 1) hist[i] = hist[i - 1]
                    hist[0] = oldest
                    System.arraycopy(level, 0, oldest, 0, bands)
                }
                tick = now
            }
        }
    }

    Canvas(
        modifier.panZoom({ true }) { pan, z ->
            auto = false
            yaw -= pan.x * 0.01f
            pitch = (pitch + pan.y * 0.01f).coerceIn(-1.5f, 1.5f)   // can look from above or below
            zoom = (zoom * z).coerceIn(0.5f, 4f)
        }
    ) {
        if (tick < 0L) return@Canvas

        val w = size.width
        val h = size.height
        val e = energy[0]
        val dist = (14.5f - e * 1.8f) / zoom
        val tx = 0f
        val ty = 0.3f
        val tz = -((BAR_ROWS - 1) * BAR_ROW_GAP) / 2f
        val cp = cos(pitch)
        val camX = tx + dist * cp * sin(yaw)
        val camY = ty + dist * sin(pitch)
        val camZ = tz + dist * cp * cos(yaw)

        var fx = tx - camX; var fy = ty - camY; var fz = tz - camZ
        val fl = sqrt(fx * fx + fy * fy + fz * fz); fx /= fl; fy /= fl; fz /= fl
        var rx = -fz; var rz = fx
        val rl = sqrt(rx * rx + rz * rz); rx /= rl; rz /= rl
        val ux = -rz * fy; val uy = rz * fx - rx * fz; val uz = rx * fy
        val focal = minOf(w * 1.5f, h * 2.8f)
        val pts = FloatArray(16)   // projected corners of one box (8 x,y pairs)
        val p = FloatArray(2)

        fun proj(x: Float, y: Float, z: Float): Boolean {
            val dx = x - camX; val dy = y - camY; val dz = z - camZ
            val zc = dx * fx + dy * fy + dz * fz
            if (zc < 0.1f) return false
            p[0] = w / 2f + (dx * rx + dz * rz) / zc * focal
            p[1] = h / 2f - (dx * ux + dy * uy + dz * uz) / zc * focal
            return true
        }

        // floor grid
        val gridColor = Color.Gray.copy(alpha = 0.18f)
        val half = BAR_SPAN * 1.15f / 2f
        for (i in 0..16) {
            val c = -half + i * (half * 2f / 16f)
            if (proj(-half, FLOOR_Y, tz + c)) {
                val ax = p[0]; val ay = p[1]
                if (proj(half, FLOOR_Y, tz + c)) drawLine(gridColor, Offset(ax, ay), Offset(p[0], p[1]), 1f)
            }
            if (proj(c, FLOOR_Y, tz - half)) {
                val ax = p[0]; val ay = p[1]
                if (proj(c, FLOOR_Y, tz + half)) drawLine(gridColor, Offset(ax, ay), Offset(p[0], p[1]), 1f)
            }
        }

        // boxes, farthest first (painter's algorithm)
        val total = BAR_ROWS * bands
        val pitchX = BAR_SPAN / bands
        val bw = pitchX * 0.34f            // half width of a bar
        val bd = BAR_ROW_GAP * 0.30f       // half depth of a bar
        val order = (0 until total).sortedByDescending { id ->
            val r = id / bands
            val i = id % bands
            val cx = (i - (bands - 1) / 2f) * pitchX
            val cz = -r * BAR_ROW_GAP
            val dx = cx - camX; val dz = cz - camZ; val dy = FLOOR_Y + 0.8f - camY
            dx * dx + dy * dy + dz * dz
        }

        for (id in order) {
            val r = id / bands
            val i = id % bands
            val lv = if (r == 0) level[i] else hist[r - 1][i]
            val bh = maxOf(0.04f, lv * BAR_MAX_H)
            val cx = (i - (bands - 1) / 2f) * pitchX
            val cz = -r * BAR_ROW_GAP
            val x0 = cx - bw; val x1 = cx + bw
            val z0 = cz - bd; val z1 = cz + bd
            val y0 = FLOOR_Y; val y1 = FLOOR_Y + bh

            // corner order: 0..3 bottom (x0z0, x1z0, x1z1, x0z1), 4..7 top
            var ok = true
            val xs = floatArrayOf(x0, x1, x1, x0, x0, x1, x1, x0)
            val zs = floatArrayOf(z0, z0, z1, z1, z0, z0, z1, z1)
            for (k in 0 until 8) {
                if (!proj(xs[k], if (k < 4) y0 else y1, zs[k])) { ok = false; break }
                pts[k * 2] = p[0]; pts[k * 2 + 1] = p[1]
            }
            if (!ok) continue

            val base = spectrumColor(i / (bands - 1f))
            val fade = 1f - r * 0.13f
            val glow = 0.85f + lv * 0.15f

            fun face(a: Int, b: Int, c: Int, d: Int, shade: Float) {
                val path = Path()
                path.moveTo(pts[a * 2], pts[a * 2 + 1])
                path.lineTo(pts[b * 2], pts[b * 2 + 1])
                path.lineTo(pts[c * 2], pts[c * 2 + 1])
                path.lineTo(pts[d * 2], pts[d * 2 + 1])
                path.close()
                val col = lerp(Color.Black, base, (shade * glow).coerceIn(0f, 1f))
                drawPath(path, col.copy(alpha = fade))
            }

            // only faces turned toward the camera
            if (camY > y1) face(4, 5, 6, 7, 1.00f)            // top
            if (camY < y0) face(0, 1, 2, 3, 0.35f)            // bottom (seen from below)
            if (camZ > z1) face(3, 2, 6, 7, 0.78f)            // front (+z)
            if (camZ < z0) face(0, 1, 5, 4, 0.45f)            // back (-z)
            if (camX < x0) face(0, 3, 7, 4, 0.60f)            // left (-x)
            if (camX > x1) face(1, 2, 6, 5, 0.55f)            // right (+x)
        }
    }
}
