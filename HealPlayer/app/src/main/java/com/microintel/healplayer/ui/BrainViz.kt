package com.microintel.healplayer.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microintel.healplayer.AudioAnalyzer
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/*
 * Brain visualizer — a port of the Heal web app's 3D "Brain" scene.
 *
 *  Overview : speaker -> travelling waveform "waterfall" + sound rings -> ear, with the little
 *             wireframe brain. Tap the brain to fly in.
 *  Hearing  : sound waves in the ear canal -> eardrum (it really flexes, and tears at full
 *             volume) -> malleus / incus / stapes -> cochlea (hair cells light up per pitch)
 *             -> auditory nerve pulses -> a folded brain whose neural network lights up.
 *
 * Everything is a software 3D projection drawn on a Compose Canvas, fed live by AudioAnalyzer.
 */

// ───────────────────────── constants ─────────────────────────

private const val B_SAMPLES = AudioAnalyzer.SAMPLES
private const val B_GAIN = 1.8f
private const val B_TWO_PI = 6.2831855f
private const val B_PI = 3.1415927f
private const val B_ROWS = 24
private const val B_ROW_SPACING = 0.42f
private const val B_ROW_WIDTH = 7f
private const val B_STEP = 1f / 60f
private const val SPEAKER_Z = 1.1f
private val EAR_Z: Float = -((B_ROWS - 1) * B_ROW_SPACING) - 1.1f
private const val RING_COUNT = 8
private const val NEURONS = 10

/**
 * Volume limit (fraction of the phone's maximum media volume). Above it, while the Brain visualizer is
 * open and music is playing, the eardrum tears and the song is stopped.
 */
private const val VOLUME_LIMIT = 0.85f
private const val RUPTURE_ENABLED = true

// cortex shading buckets (quads of the same shade are batched into one path)
private const val LV = 28
private const val CV = 6
private const val L_MIN = 0.22f
private const val L_MAX = 1.55f

private const val HEAR_YAW = 1.43f
private const val HEAR_PITCH = 0.1f

private const val HL = 12
private const val AIR_N = 260
private const val AIR_X0 = -4.9f
private const val AIR_X1 = -1.75f
private const val DRUM_X = -1.7f
private const val DRUM_R = 0.42f
private const val DRUM_CX = -0.1f
private const val DRUM_CY = 0.08f
private const val SHRED_N = 44
private const val HAIR_N = 40
private const val CN = 180
private const val NV = 60
private const val PULSES = 22
private const val BN = 46
private const val BRAIN_X = 2.25f
private const val BRAIN_Y = 2.3f

private val INDIGO_B = Color(0xFF6366F1)
private val AMBER = Color(0xFFFFB020)
private val NERVE_C = Color(0xFFD9B84A)
private val NERVE_HOT = Color(0xFFFFC94D)
private val COCHLEA_C = Color(0xFFF0B36B)
private val BONE_C = Color(0xFFF4E8CF)
private val SKIN_C = Color(0xFFE8B49A)
private val SKIN_D = Color(0xFFD99A82)
private val GHOST_C = Color(0xFFF2B8A0)
private val GROOVE_C = Color(0xFFA85A6C)
private val CREST_C = Color(0xFFEFB4B8)
private val CEREB_C = Color(0xFFD28896)
private val STEM_C = Color(0xFFE0A0AE)
private val SPEAKER_C = Color(0xFF22262E)

private val CHIP_LABELS = listOf("Sound", "Eardrum", "Bones", "Cochlea", "Nerve", "Brain")

private fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v

private fun airRadius(x: Float): Float =
    if (x < -4.6f) 1.0f else if (x < -3.4f) 0.7f + (0.34f - 0.7f) * ((x + 4.6f) / 1.2f) else 0.34f

/** jagged outline of the tear (unit-disc radius for a given angle) */
private fun drumJag(th: Float): Float =
    0.85f + 0.12f * sin(5f * th + 1f) + 0.07f * sin(11f * th + 2f) + 0.06f * sin(23f * th + 0.5f) + 0.04f * sin(37f * th)

// ───────────────────────── camera ─────────────────────────

private class BvCam {
    var cx = 0f; var cy = 0f; var cz = 0f
    var fx = 0f; var fy = 0f; var fz = -1f
    var rx = 1f; var rz = 0f
    var ux = 0f; var uy = 1f; var uz = 0f
    var focal = 1f; var w = 1f; var h = 1f
    var sx = 0f; var sy = 0f; var sd = 1f

    fun set(w: Float, h: Float, tx: Float, ty: Float, tz: Float, yaw: Float, pitch: Float, dist: Float, focal: Float) {
        this.w = w; this.h = h; this.focal = focal
        val cp = cos(pitch)
        cx = tx + dist * cp * sin(yaw)
        cy = ty + dist * sin(pitch)
        cz = tz + dist * cp * cos(yaw)
        fx = tx - cx; fy = ty - cy; fz = tz - cz
        val fl = sqrt(fx * fx + fy * fy + fz * fz)
        fx /= fl; fy /= fl; fz /= fl
        rx = -fz; rz = fx
        val rl = sqrt(rx * rx + rz * rz)
        rx /= rl; rz /= rl
        ux = -rz * fy; uy = rz * fx - rx * fz; uz = rx * fy
    }

    /** projects a world point; result in sx / sy (pixels) and sd (depth). false = behind the camera */
    fun proj(x: Float, y: Float, z: Float): Boolean {
        val dx = x - cx; val dy = y - cy; val dz = z - cz
        val zc = dx * fx + dy * fy + dz * fz
        if (zc < 0.1f) return false
        sx = w / 2f + (dx * rx + dz * rz) / zc * focal
        sy = h / 2f - (dx * ux + dy * uy + dz * uz) / zc * focal
        sd = zc
        return true
    }

    fun dist2(x: Float, y: Float, z: Float): Float {
        val dx = x - cx; val dy = y - cy; val dz = z - cz
        return dx * dx + dy * dy + dz * dz
    }
}

// ───────────────────────── geometry helpers ─────────────────────────

private class BvIco(val vx: FloatArray, val vy: FloatArray, val vz: FloatArray, val edges: IntArray)

/** icosahedron subdivided once (42 vertices, 120 edges) — the little wireframe brain */
private fun buildIco(): BvIco {
    val t = (1f + sqrt(5f)) / 2f
    val base = arrayOf(
        floatArrayOf(-1f, t, 0f), floatArrayOf(1f, t, 0f), floatArrayOf(-1f, -t, 0f), floatArrayOf(1f, -t, 0f),
        floatArrayOf(0f, -1f, t), floatArrayOf(0f, 1f, t), floatArrayOf(0f, -1f, -t), floatArrayOf(0f, 1f, -t),
        floatArrayOf(t, 0f, -1f), floatArrayOf(t, 0f, 1f), floatArrayOf(-t, 0f, -1f), floatArrayOf(-t, 0f, 1f),
    )
    val faces = arrayOf(
        intArrayOf(0, 11, 5), intArrayOf(0, 5, 1), intArrayOf(0, 1, 7), intArrayOf(0, 7, 10), intArrayOf(0, 10, 11),
        intArrayOf(1, 5, 9), intArrayOf(5, 11, 4), intArrayOf(11, 10, 2), intArrayOf(10, 7, 6), intArrayOf(7, 1, 8),
        intArrayOf(3, 9, 4), intArrayOf(3, 4, 2), intArrayOf(3, 2, 6), intArrayOf(3, 6, 8), intArrayOf(3, 8, 9),
        intArrayOf(4, 9, 5), intArrayOf(2, 4, 11), intArrayOf(6, 2, 10), intArrayOf(8, 6, 7), intArrayOf(9, 8, 1),
    )
    val verts = ArrayList<FloatArray>()
    fun norm(v: FloatArray): FloatArray {
        val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }
    for (b in base) verts.add(norm(b))
    val cache = HashMap<Long, Int>()
    fun mid(a: Int, b: Int): Int {
        val key = if (a < b) a.toLong() * 10000L + b else b.toLong() * 10000L + a
        val hit = cache[key]
        if (hit != null) return hit
        val va = verts[a]; val vb = verts[b]
        verts.add(norm(floatArrayOf((va[0] + vb[0]) / 2f, (va[1] + vb[1]) / 2f, (va[2] + vb[2]) / 2f)))
        cache[key] = verts.size - 1
        return verts.size - 1
    }
    val edgeSet = HashSet<Long>()
    fun edge(a: Int, b: Int) {
        edgeSet.add(if (a < b) a.toLong() * 10000L + b else b.toLong() * 10000L + a)
    }
    fun tri(a: Int, b: Int, c: Int) { edge(a, b); edge(b, c); edge(c, a) }
    for (f in faces) {
        val a = f[0]; val b = f[1]; val c = f[2]
        val ab = mid(a, b); val bc = mid(b, c); val ca = mid(c, a)
        tri(a, ab, ca); tri(b, bc, ab); tri(c, ca, bc); tri(ab, bc, ca)
    }
    val ed = IntArray(edgeSet.size * 2)
    var k = 0
    for (key in edgeSet) { ed[k++] = (key / 10000L).toInt(); ed[k++] = (key % 10000L).toInt() }
    return BvIco(
        FloatArray(verts.size) { verts[it][0] },
        FloatArray(verts.size) { verts[it][1] },
        FloatArray(verts.size) { verts[it][2] },
        ed,
    )
}

/** quad-mesh ellipsoid used for the cerebrum and cerebellum. Normals/crest precomputed once. */
private class BvMesh(val nl: Int, val nm: Int, shape: (Float, Float, Float, FloatArray) -> Unit) {
    val n = (nl + 1) * (nm + 1)
    val x = FloatArray(n); val y = FloatArray(n); val z = FloatArray(n)
    val crest = FloatArray(n)
    val q = nl * nm
    val qcx = FloatArray(q); val qcy = FloatArray(q); val qcz = FloatArray(q)
    val qnx = FloatArray(q); val qny = FloatArray(q); val qnz = FloatArray(q)
    val qcrest = FloatArray(q)

    init {
        val out = FloatArray(4)
        for (i in 0..nl) {
            val th = i * B_PI / nl
            for (j in 0..nm) {
                val ph = j * B_TWO_PI / nm
                val ux = sin(th) * cos(ph); val uy = cos(th); val uz = sin(th) * sin(ph)
                shape(ux, uy, uz, out)
                val v = i * (nm + 1) + j
                x[v] = out[0]; y[v] = out[1]; z[v] = out[2]; crest[v] = out[3]
            }
        }
        for (i in 0 until nl) {
            for (j in 0 until nm) {
                val a = i * (nm + 1) + j; val b = a + 1; val c = a + nm + 1; val d = c + 1
                val id = i * nm + j
                qcx[id] = (x[a] + x[b] + x[c] + x[d]) / 4f
                qcy[id] = (y[a] + y[b] + y[c] + y[d]) / 4f
                qcz[id] = (z[a] + z[b] + z[c] + z[d]) / 4f
                qcrest[id] = (crest[a] + crest[b] + crest[c] + crest[d]) / 4f
                // normal = (a->d) x (b->c), flipped so it points away from the centre
                val ex = x[d] - x[a]; val ey = y[d] - y[a]; val ez = z[d] - z[a]
                val fx = x[c] - x[b]; val fy = y[c] - y[b]; val fz = z[c] - z[b]
                var nx = ey * fz - ez * fy; var ny = ez * fx - ex * fz; var nz = ex * fy - ey * fx
                val nlen = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
                nx /= nlen; ny /= nlen; nz /= nlen
                if (nx * qcx[id] + ny * qcy[id] + nz * qcz[id] < 0f) { nx = -nx; ny = -ny; nz = -nz }
                qnx[id] = nx; qny[id] = ny; qnz[id] = nz
            }
        }
    }
}

private fun smooth01(a: Float, b: Float, v: Float): Float {
    val t = clamp01((v - a) / (b - a))
    return t * t * (3f - 2f * t)
}

/**
 * Cerebrum (front = +x, up = +y, the hemispheres are split along z = 0). Winding gyri from domain-warped
 * sines, plus the landmarks you see on a real brain: longitudinal fissure, lateral (Sylvian) fissure,
 * central sulcus, a temporal lobe bulging below, a rounded frontal pole and a tapering occipital pole.
 */
private fun cerebrumShape(ux: Float, uy: Float, uz: Float, out: FloatArray) {
    val wx = ux + 0.16f * sin(4.7f * uy + 2.1f * uz + 1.0f)
    val wy = uy + 0.16f * sin(5.3f * uz + 3.1f * ux)
    val wz = uz + 0.16f * sin(4.1f * ux + 1.7f * uy + 0.6f)
    val n1 = sin(9.5f * wx + 2.3f * sin(6.2f * wy + 1.1f) + 1.7f * wz)
    val n2 = sin(8.5f * wy + 2.0f * sin(5.1f * wz + 0.7f) + 1.3f * wx)
    val n3 = sin(10.5f * wz + 1.8f * sin(6.8f * wx + 2.1f))
    val n = abs(n1 * 0.45f + n2 * 0.35f + n3 * 0.30f)
    var crest = smooth01(0.02f, 0.40f, n)                       // ~0 along the sulci, 1 on the gyri

    val side = smooth01(0.08f, 0.35f, abs(uz))                  // lateral surface (not the midline)
    // lateral fissure: from the lower front, rising toward the back
    val dS = uy - (-0.30f + 0.37f * (0.55f - ux))
    val syl = exp(-(dS * dS) / 0.0035f) * side * smooth01(-0.6f, -0.35f, ux) * (1f - smooth01(0.5f, 0.72f, ux))
    // central sulcus: from the top, slanting forward and down
    val dC = ux - (-0.02f + 0.33f * (0.95f - uy))
    val cen = exp(-(dC * dC) / 0.0025f) * side * smooth01(0.05f, 0.4f, uy)
    val land = max(syl, cen)
    crest *= 1f - 0.85f * land

    var rad = 1f + 0.05f * crest - 0.075f * land
    // deep longitudinal fissure between the hemispheres (upper half)
    rad *= 1f - 0.19f * exp(-(uz * uz) / 0.0035f) * max(0f, uy + 0.2f)
    // temporal lobe bulging below the lateral fissure
    val tx = (ux + 0.05f) / 0.5f; val ty = (uy + 0.55f) / 0.3f
    rad *= 1f + 0.11f * exp(-(tx * tx + ty * ty)) * min(1f, abs(uz) * 3f)

    val occ = smooth01(-0.5f, -1f, ux)                          // occipital pole narrows
    out[0] = ux * 1.5f * rad
    var oy = uy * (if (uy < 0f) 0.74f else 0.97f) * rad * (1f - 0.12f * occ)
    if (uy < 0f) oy += 0.12f * smooth01(0.25f, 0.9f, ux) * (-uy)   // orbital surface rises toward the front
    out[1] = oy
    out[2] = uz * 0.88f * rad * (1f - 0.18f * occ)
    out[3] = crest
}

/** cerebellum: tightly packed horizontal folia */
private fun cerebellumShape(ux: Float, uy: Float, uz: Float, out: FloatArray) {
    val th = acos(uy.coerceIn(-1f, 1f))
    val stripe = 0.5f + 0.5f * sin(th * 17f)
    val qr = 1f - 0.018f * (1f - stripe)
    out[0] = ux * 0.58f * qr
    out[1] = uy * 0.36f * qr
    out[2] = uz * 0.52f * qr
    out[3] = stripe
}

private fun catmullRom(pts: Array<FloatArray>, perSeg: Int): Array<FloatArray> {
    val out = ArrayList<FloatArray>()
    val n = pts.size
    for (i in 0 until n - 1) {
        val p0 = pts[max(0, i - 1)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[min(n - 1, i + 2)]
        for (s in 0 until perSeg) {
            val t = s / perSeg.toFloat(); val t2 = t * t; val t3 = t2 * t
            val o = FloatArray(3)
            for (c in 0..2) {
                o[c] = 0.5f * ((2f * p1[c]) + (-p0[c] + p2[c]) * t +
                    (2f * p0[c] - 5f * p1[c] + 4f * p2[c] - p3[c]) * t2 +
                    (-p0[c] + 3f * p1[c] - 3f * p2[c] + p3[c]) * t3)
            }
            out.add(o)
        }
    }
    out.add(pts[n - 1].copyOf())
    return out.toTypedArray()
}

private fun bezierPts(out: ArrayList<Float>, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, steps: Int) {
    for (s in 1..steps) {
        val t = s / steps.toFloat(); val u = 1f - t
        out.add(u * u * u * x0 + 3f * u * u * t * x1 + 3f * u * t * t * x2 + t * t * t * x3)
        out.add(u * u * u * y0 + 3f * u * u * t * y1 + 3f * u * t * t * y2 + t * t * t * y3)
    }
}

// ───────────────────────── simulation state ─────────────────────────

private class BvSim {
    // audio-driven
    val rows = Array(B_ROWS) { FloatArray(B_SAMPLES) }
    var energy = 0f
    var animT = 0f
    var acc = 0f
    var idle = 0f
    var songFreq = 300f
    var volume = 0f
    var overLimit = false                 // media volume above VOLUME_LIMIT
    var tearing = false                   // latched while over the limit once playback started it
    var shake = 0f                        // camera shake after the tear
    private val tmpRow = FloatArray(B_SAMPLES)
    private val taper = FloatArray(B_SAMPLES) { sin(it / (B_SAMPLES - 1f) * B_PI).pow(0.6f) }
    val wpx = FloatArray(B_SAMPLES); val wpy = FloatArray(B_SAMPLES)
    var flight = 0f                       // 0 = overview camera, 1 = hearing camera

    // tap target of the little brain, written while drawing (screen pixels)
    var hitX = -10000f; var hitY = -10000f; var hitR = 0f

    // overview: rings + little brain
    val ringT = FloatArray(RING_COUNT) { it / RING_COUNT.toFloat() }
    var brainRotY = 0f; var brainRotX = 0f; var brainScale = 1f
    val flash = FloatArray(NEURONS)
    val neuron = Array(NEURONS) { nd ->
        val phi = acos(1f - 2f * (nd + 0.5f) / NEURONS)
        val theta = B_PI * (1f + sqrt(5f)) * (nd + 0.5f)
        floatArrayOf(0.42f * sin(phi) * cos(theta), 0.42f * sin(phi) * sin(theta), 0.42f * cos(phi))
    }
    val ico = buildIco()

    // hearing: air + wavefronts
    val hist = FloatArray(HL)
    var histI = 0
    var env = 0f; var phase = 0f; var cycles = 3f
    var drum = 0f; var drumAmp = 0f
    val airRestX = FloatArray(AIR_N); val airRestY = FloatArray(AIR_N); val airRestZ = FloatArray(AIR_N)
    val airP = FloatArray(AIR_N); val airX = FloatArray(AIR_N)

    // eardrum + rupture
    var rupt = 0f; var ruptBurst = false
    var stS = 0f; var hp = 0f; var holeF = 0f; var cond = 1f
    var jitter = 0f; var curl = 0f
    var flashR = 0f; var labelA = 0f
    var shredLife = 0f; var shredOn = false
    val shX = FloatArray(SHRED_N); val shY = FloatArray(SHRED_N); val shZ = FloatArray(SHRED_N)
    val shVX = FloatArray(SHRED_N); val shVY = FloatArray(SHRED_N); val shVZ = FloatArray(SHRED_N)
    val cracks = Array(9) { FloatArray(22) }

    // cochlea / hair cells / nerve / pulses
    val coX = FloatArray(CN + 1); val coY = FloatArray(CN + 1); val coZ = FloatArray(CN + 1)
    val hairAct = FloatArray(HAIR_N)
    val hX = FloatArray(HAIR_N); val hY = FloatArray(HAIR_N); val hZ = FloatArray(HAIR_N)
    var hairMean = 0f
    val nvX = FloatArray(NV + 1); val nvY = FloatArray(NV + 1); val nvZ = FloatArray(NV + 1)
    val pOn = BooleanArray(PULSES); val pStage = IntArray(PULSES); val pS = FloatArray(PULSES)
    val pK = IntArray(PULSES); val pInten = FloatArray(PULSES); val pSpeed = FloatArray(PULSES)
    val pX = FloatArray(PULSES); val pY = FloatArray(PULSES); val pZ = FloatArray(PULSES)
    var activePulses = 0
    var arrivals = 0f

    // brain network
    val cerebrum = BvMesh(38, 60, ::cerebrumShape)
    val cerebellum = BvMesh(34, 24, ::cerebellumShape)
    val shadePaths = Array(LV * CV) { Path() }
    val shadeUsed = BooleanArray(LV * CV)
    var nodeCount = 0
    val nodeX = FloatArray(BN); val nodeY = FloatArray(BN); val nodeZ = FloatArray(BN)
    val nodeAct = FloatArray(BN); val nodeDepth = IntArray(BN); val nodeLag = IntArray(BN)
    var edges = IntArray(0)
    val bHist = FloatArray(96)
    var bHistI = 0
    var bEntry = 0f; var bMean = 0f
    val glowA = FloatArray(3)
    val vsx = FloatArray(cerebrum.n); val vsy = FloatArray(cerebrum.n); val vok = BooleanArray(cerebrum.n)
    val path = Path()

    // outer ear outline (precomputed polygons)
    val pinna: FloatArray
    val ridge: FloatArray

    val chipOn = BooleanArray(6)

    init {
        // air particles rest positions
        for (i in 0 until AIR_N) {
            val ax0 = AIR_X0 + Random.nextFloat() * (AIR_X1 - AIR_X0)
            val rr = sqrt(Random.nextFloat()) * airRadius(ax0) * 0.88f
            val aa = Random.nextFloat() * B_TWO_PI
            airRestX[i] = ax0; airRestY[i] = rr * sin(aa); airRestZ[i] = rr * cos(aa)
            airP[i] = (ax0 - AIR_X0) / (AIR_X1 - AIR_X0)
            airX[i] = ax0
        }
        // eardrum cracks
        for (kc in 0 until 9) {
            val ka = kc / 9f * B_TWO_PI + (Random.nextFloat() - 0.5f) * 0.5f
            val kl = 0.7f + Random.nextFloat() * 0.3f
            for (ks in 0..10) {
                val kt = ks / 10f
                val jit = if (ks > 0) (Random.nextFloat() - 0.5f) * 0.09f else 0f
                cracks[kc][ks * 2] = DRUM_CX + cos(ka) * kt * kl - sin(ka) * jit
                cracks[kc][ks * 2 + 1] = DRUM_CY + sin(ka) * kt * kl + cos(ka) * jit
            }
        }
        // cochlea spiral (base = high pitch -> apex = low pitch)
        for (cs in 0..CN) {
            val ct = cs / CN.toFloat()
            val ang = B_PI + ct * 2.5f * B_TWO_PI
            val rad = 0.62f * (1f - 0.72f * ct)
            coX[cs] = 0.3f + rad * cos(ang); coY[cs] = -0.12f + rad * sin(ang); coZ[cs] = -0.1f + 0.2f * ct
        }
        val cum = FloatArray(CN + 1)
        for (cs in 1..CN) {
            val dx = coX[cs] - coX[cs - 1]; val dy = coY[cs] - coY[cs - 1]; val dz = coZ[cs] - coZ[cs - 1]
            cum[cs] = cum[cs - 1] + sqrt(dx * dx + dy * dy + dz * dz)
        }
        var seg = 1
        for (k in 0 until HAIR_N) {
            val target = cum[CN] * k / (HAIR_N - 1f)
            while (seg < CN && cum[seg] < target) seg++
            val span = (cum[seg] - cum[seg - 1]).coerceAtLeast(1e-6f)
            val f = ((target - cum[seg - 1]) / span).coerceIn(0f, 1f)
            hX[k] = coX[seg - 1] + (coX[seg] - coX[seg - 1]) * f
            hY[k] = coY[seg - 1] + (coY[seg] - coY[seg - 1]) * f
            hZ[k] = coZ[seg - 1] + (coZ[seg] - coZ[seg - 1]) * f + 0.075f
        }
        // auditory nerve: cochlea apex -> brain
        val nerve = catmullRom(
            arrayOf(
                floatArrayOf(coX[CN], coY[CN], coZ[CN]),
                floatArrayOf(0.6f, 0.5f, 0.12f),
                floatArrayOf(0.95f, 0.8f, 0.14f),
                floatArrayOf(1.5f, 1.05f, 0.12f),
                floatArrayOf(2.1f, 1.3f, 0.1f),
            ),
            15,
        )
        for (k in 0..NV) { nvX[k] = nerve[k][0]; nvY[k] = nerve[k][1]; nvZ[k] = nerve[k][2] }

        buildNetwork()

        val pl = ArrayList<Float>()
        pl.add(0f); pl.add(0.95f)
        bezierPts(pl, 0f, 0.95f, -0.95f, 1.1f, -1.35f, 0.25f, -1.15f, -0.45f, 10)
        bezierPts(pl, -1.15f, -0.45f, -1.08f, -0.95f, -0.55f, -1.12f, -0.2f, -0.82f, 10)
        bezierPts(pl, -0.2f, -0.82f, -0.5f, -0.5f, -0.62f, 0.3f, -0.42f, 0.58f, 10)
        bezierPts(pl, -0.42f, 0.58f, -0.32f, 0.74f, -0.15f, 0.82f, 0f, 0.95f, 10)
        pinna = pl.toFloatArray()
        val rl = ArrayList<Float>()
        rl.add(-0.18f); rl.add(0.5f)
        bezierPts(rl, -0.18f, 0.5f, -0.55f, 0.62f, -0.8f, 0.2f, -0.72f, -0.3f, 10)
        bezierPts(rl, -0.72f, -0.3f, -0.66f, -0.55f, -0.45f, -0.62f, -0.3f, -0.5f, 10)
        bezierPts(rl, -0.3f, -0.5f, -0.45f, -0.3f, -0.5f, 0.1f, -0.18f, 0.5f, 10)
        ridge = rl.toFloatArray()
    }

    private fun buildNetwork() {
        var seed = 7L
        fun rnd(): Float {
            seed = (seed * 16807L) % 2147483647L
            return (seed - 1L) / 2147483646f
        }
        var guard = 0
        while (nodeCount < BN && guard < 4000) {
            guard++
            val px = (rnd() * 2f - 1f) * 1.15f
            val py = (rnd() * 2f - 1f) * 0.72f
            val pz = (rnd() * 2f - 1f) * 0.5f
            if ((px * px) / 1.32f + (py * py) / 0.52f + (pz * pz) / 0.25f > 1f) continue
            var far = true
            for (q in 0 until nodeCount) {
                val dx = nodeX[q] - px; val dy = nodeY[q] - py; val dz = nodeZ[q] - pz
                if (dx * dx + dy * dy + dz * dz < 0.1156f) { far = false; break }
            }
            if (!far) continue
            nodeX[nodeCount] = px; nodeY[nodeCount] = py; nodeZ[nodeCount] = pz
            nodeCount++
        }
        val nb = Array(nodeCount) { ArrayList<Int>() }
        val seen = HashSet<Int>()
        val el = ArrayList<Int>()
        for (i in 0 until nodeCount) {
            val near = (0 until nodeCount).filter { it != i }.sortedBy { j ->
                val dx = nodeX[i] - nodeX[j]; val dy = nodeY[i] - nodeY[j]; val dz = nodeZ[i] - nodeZ[j]
                dx * dx + dy * dy + dz * dz
            }
            for (k in 0 until min(3, near.size)) {
                val j = near[k]
                val key = min(i, j) * 1000 + max(i, j)
                if (!seen.add(key)) continue
                el.add(i); el.add(j)
                nb[i].add(j); nb[j].add(i)
            }
        }
        edges = el.toIntArray()
        // hop distance from the auditory-cortex end where the nerve arrives
        val order = (0 until nodeCount).sortedBy { i ->
            val dx = nodeX[i] + 0.2f; val dy = nodeY[i] + 0.55f; val dz = nodeZ[i] - 0.1f
            dx * dx + dy * dy + dz * dz
        }
        val visited = BooleanArray(nodeCount)
        val queue = ArrayDeque<Int>()
        for (k in 0 until min(3, order.size)) { queue.addLast(order[k]); visited[order[k]] = true; nodeDepth[order[k]] = 0 }
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (o in nb[cur]) {
                if (!visited[o]) { visited[o] = true; nodeDepth[o] = nodeDepth[cur] + 1; queue.addLast(o) }
            }
        }
        for (i in 0 until nodeCount) nodeLag[i] = (nodeDepth[i] * 5f + Random.nextFloat() * 3f).roundToInt()
    }

    fun envAt(p: Float): Float {
        val f = clamp01(p) * (HL - 1)
        val i0 = f.toInt(); val fr = f - i0
        val a = hist[(histI - i0 + HL * 2) % HL]
        val b = hist[(histI - min(i0 + 1, HL - 1) + HL * 2) % HL]
        return a + (b - a) * fr
    }

    fun resetDrum() {
        rupt = 0f; flashR = 0f; ruptBurst = false; shredLife = 0f; shredOn = false; labelA = 0f; shake = 0f; tearing = false
    }

    fun resetHearing() {
        resetDrum()
        env = 0f; drum = 0f; drumAmp = 0f
        java.util.Arrays.fill(hairAct, 0f)
        java.util.Arrays.fill(nodeAct, 0f)
        java.util.Arrays.fill(bHist, 0f)
        for (i in 0 until PULSES) pOn[i] = false
        bEntry = 0f; bMean = 0f; hairMean = 0f
    }

    private fun burstShreds() {
        for (i in 0 until SHRED_N) {
            val a = Random.nextFloat() * B_TWO_PI
            val rr = Random.nextFloat() * 0.45f
            val lx = (DRUM_CX + cos(a) * rr) * DRUM_R
            val ly = (DRUM_CY + sin(a) * rr) * DRUM_R
            shX[i] = -1.68f; shY[i] = ly; shZ[i] = -lx
            shVX[i] = 0.010f + Random.nextFloat() * 0.022f
            shVY[i] = (Random.nextFloat() - 0.4f) * 0.03f
            shVZ[i] = (Random.nextFloat() - 0.5f) * 0.03f
        }
        shredOn = true; shredLife = 1f
        flashR = 1f
        shake = 1f
    }

    private fun spawnPulse(k: Int, inten: Float) {
        for (i in 0 until PULSES) {
            if (pOn[i]) continue
            pOn[i] = true; pStage[i] = 0; pS[i] = 0f; pK[i] = k; pInten[i] = inten
            pSpeed[i] = 0.9f + Random.nextFloat() * 0.25f
            pX[i] = hX[k]; pY[i] = hY[k]; pZ[i] = hZ[k]
            return
        }
    }

    /** advances the whole scene by one 1/60 s tick */
    fun step(playing: Boolean, hearing: Boolean, wave: FloatArray, spec: FloatArray) {
        // --- waveform waterfall ---
        val oldest = rows[B_ROWS - 1]
        for (i in B_ROWS - 1 downTo 1) rows[i] = rows[i - 1]
        rows[0] = oldest
        if (playing) {
            for (j in 0 until B_SAMPLES) oldest[j] = tanh(wave[j] * B_GAIN)      // soft limit, no flat tops
        } else {
            idle += 0.02f
            for (j in 0 until B_SAMPLES) oldest[j] = sin(j / (B_SAMPLES - 1f) * B_TWO_PI * 3f + idle) * 0.55f
        }
        cleanRow(oldest)
        var sq = 0f
        for (v in oldest) sq += v * v
        energy = if (playing) sqrt(sq / B_SAMPLES) else 0.12f
        animT += 0.016f

        // dominant pitch (drives how tightly packed the visible waves are)
        if (playing) {
            var best = 0
            for (i in 1 until AudioAnalyzer.BANDS) if (spec[i] > spec[best]) best = i
            if (spec[best] > 0.15f) songFreq += (AudioAnalyzer.bandCenter(best) - songFreq) * 0.1f
        }

        // --- travelling rings ---
        val speed = 0.0055f + energy * 0.004f
        for (i in 0 until RING_COUNT) {
            ringT[i] += speed
            if (ringT[i] > 1f) ringT[i] -= 1f
        }

        // --- little brain ---
        brainRotY += 0.004f + energy * 0.01f
        brainRotX += 0.0015f
        brainScale = 1f + energy * 0.25f
        for (i in 0 until NEURONS) {
            flash[i] *= 0.9f
            if (Random.nextFloat() < 0.01f + energy * 0.06f) flash[i] = 1f
        }

        // --- camera flight ---
        flight = if (hearing) min(1f, flight + 1f / 70f) else max(0f, flight - 1f / 70f)

        if (hearing) stepHearing(playing, spec)
    }

    /** smooth a waveform row and fade both ends to rest so every line starts and finishes cleanly */
    private fun cleanRow(r: FloatArray) {
        for (pass in 0 until 2) {
            tmpRow[0] = r[0]; tmpRow[B_SAMPLES - 1] = r[B_SAMPLES - 1]
            for (i in 1 until B_SAMPLES - 1) tmpRow[i] = (r[i - 1] + 2f * r[i] + r[i + 1]) * 0.25f
            System.arraycopy(tmpRow, 0, r, 0, B_SAMPLES)
        }
        for (i in 0 until B_SAMPLES) r[i] *= taper[i]
    }

    private fun stepHearing(playing: Boolean, spec: FloatArray) {
        val twoPi = B_TWO_PI

        // how loud is the sound right now (fast attack, gentle release)
        val raw = if (playing) min(1f, energy * 2f) else 0.05f + 0.025f * sin(animT * 2.2f)
        env = if (raw > env) env * 0.35f + raw * 0.65f else env * 0.88f + raw * 0.12f
        histI = (histI + 1) % HL
        hist[histI] = env

        val cycT = 1.5f + 5f * (ln(max(songFreq, 20f) / 60f) / ln(300f)).coerceIn(0f, 1f)
        cycles += (cycT - cycles) * 0.05f
        phase += 0.45f

        // 1) air molecules in the canal
        for (i in 0 until AIR_N) {
            val p = airP[i]
            airX[i] = airRestX[i] + 0.2f * envAt(p) * sin(p * cycles * twoPi - phase)
        }

        // 2) eardrum beats with the sound (and tears at full volume)
        if (RUPTURE_ENABLED) {
            if (overLimit) {
                if (playing) tearing = true                       // stays latched after the song is stopped
                if (tearing && rupt < 1f) rupt = min(1f, rupt + 1f / 170f)
            } else {
                tearing = false
                if (rupt > 0f) {                                  // volume lowered: the drum heals
                    rupt = max(0f, rupt - 1f / 90f)
                    if (rupt == 0f) resetDrum()
                }
            }
        }
        stS = clamp01(rupt / 0.3f)
        hp = clamp01((rupt - 0.5f) / 0.4f)
        holeF = 0.6f * (1f - (1f - hp).pow(3))
        cond = 1f - clamp01((rupt - 0.5f) / 0.35f)
        if (rupt >= 0.5f && !ruptBurst) { ruptBurst = true; burstShreds() }
        if (rupt < 0.5f) ruptBurst = false

        drum = 0.2f * envAt(1f) * sin(cycles * twoPi - phase) * (1f + 1.3f * stS) * cond
        drumAmp = drumAmp * 0.9f + abs(drum) * 0.1f
        jitter = (1f - hp) * stS * 0.02f * sin(phase * 6.3f)
        curl = 0.17f * hp

        if (shredOn) {
            for (i in 0 until SHRED_N) {
                shX[i] += shVX[i]; shY[i] += shVY[i]; shZ[i] += shVZ[i]
                shVY[i] -= 0.0007f
            }
            shredLife -= 0.011f
            if (shredLife <= 0f) shredOn = false
        }
        if (flashR > 0.01f) flashR *= 0.93f else flashR = 0f
        if (shake > 0.01f) shake *= 0.92f else shake = 0f
        labelA += ((if (rupt >= 0.55f) 1f else 0f) - labelA) * 0.08f

        // 4) cochlea: each hair cell listens to its own pitch (base = high, apex = low)
        var sum = 0f
        var sq = 0f
        if (playing) {
            val lnSpan = ln(AudioAnalyzer.MAX_HZ / AudioAnalyzer.MIN_HZ)
            for (i in 0 until HAIR_N) {
                val fk = 20f * 1000f.pow(1f - i / (HAIR_N - 1f))
                val bf = ln(max(fk, AudioAnalyzer.MIN_HZ) / AudioAnalyzer.MIN_HZ) / lnSpan * AudioAnalyzer.BANDS
                val lo = floor(bf - 1f).toInt().coerceIn(0, AudioAnalyzer.BANDS - 1)
                val hi = ceil(bf + 1f).toInt().coerceIn(lo, AudioAnalyzer.BANDS - 1)
                var mx = 0f
                for (b in lo..hi) if (spec[b] > mx) mx = spec[b]
                val v = (mx * B_GAIN).coerceIn(0f, 1f).pow(1.6f) * cond   // a torn drum passes nothing on
                hairAct[i] = max(v, hairAct[i] * 0.86f)
            }
        } else {
            for (i in 0 until HAIR_N) hairAct[i] *= 0.9f
        }
        for (i in 0 until HAIR_N) { sum += hairAct[i]; sq += hairAct[i] * hairAct[i] }
        hairMean = min(1f, sum / HAIR_N * 4f)

        // 5) hair cells fire electrical pulses onto the auditory nerve
        if (sq > 0.05f) {
            val tries = if (sq > 3f) 2 else 1
            for (t in 0 until tries) {
                if (Random.nextFloat() < min(0.75f, sq * 0.35f + 0.05f)) {
                    val pick = Random.nextFloat() * sq
                    var acc2 = 0f
                    var kk = 0
                    for (i in 0 until HAIR_N) { acc2 += hairAct[i] * hairAct[i]; if (acc2 >= pick) { kk = i; break } }
                    spawnPulse(kk, min(1f, 0.4f + hairAct[kk]))
                }
            }
        }
        activePulses = 0
        arrivals = 0f
        for (i in 0 until PULSES) {
            if (!pOn[i]) continue
            activePulses++
            if (pStage[i] == 0) {
                pS[i] += 0.14f * pSpeed[i]
                if (pS[i] >= 1f) {
                    pStage[i] = 1; pS[i] = 0f
                    pX[i] = coX[CN]; pY[i] = coY[CN]; pZ[i] = coZ[CN]
                } else {
                    val k = pK[i]
                    pX[i] = hX[k] + (coX[CN] - hX[k]) * pS[i]
                    pY[i] = hY[k] + (coY[CN] - hY[k]) * pS[i]
                    pZ[i] = hZ[k] + (coZ[CN] - hZ[k]) * pS[i]
                }
            } else {
                pS[i] += 0.021f * pSpeed[i]
                if (pS[i] >= 1f) {
                    pOn[i] = false
                    arrivals += pInten[i]
                    continue
                }
                val f = pS[i] * NV
                val i0 = min(NV - 1, f.toInt()); val fr = f - i0
                pX[i] = nvX[i0] + (nvX[i0 + 1] - nvX[i0]) * fr
                pY[i] = nvY[i0] + (nvY[i0 + 1] - nvY[i0]) * fr
                pZ[i] = nvZ[i0] + (nvZ[i0 + 1] - nvZ[i0]) * fr
            }
        }

        // 6) brain: the signal arrives at the auditory cortex and spreads outward
        bEntry = min(1f, bEntry * 0.88f + arrivals * 0.3f)
        bHistI = (bHistI + 1) % bHist.size
        bHist[bHistI] = bEntry
        var msum = 0f
        for (i in 0 until nodeCount) {
            val target = bHist[(bHistI - nodeLag[i] + bHist.size * 2) % bHist.size] * (0.7f + 0.3f * Random.nextFloat())
            nodeAct[i] = max(target, nodeAct[i] * 0.9f)
            if (bMean > 0.12f && Random.nextFloat() < 0.012f) nodeAct[i] = max(nodeAct[i], 0.45f + bMean * 0.5f)
            msum += nodeAct[i]
        }
        bMean = bMean * 0.9f + (msum / max(1, nodeCount)) * 0.1f
        val gsum = FloatArray(3); val gcnt = IntArray(3)
        for (i in 0 until nodeCount) {
            val gi = if (nodeDepth[i] <= 1) 0 else if (nodeDepth[i] <= 4) 1 else 2
            gsum[gi] += nodeAct[i]; gcnt[gi]++
        }
        for (i in 0 until 3) glowA[i] = if (gcnt[i] > 0) min(0.7f, (gsum[i] / gcnt[i]) * 1.4f) else 0f

        chipOn[0] = env > 0.09f
        chipOn[1] = drumAmp > 0.012f
        chipOn[2] = drumAmp > 0.012f
        chipOn[3] = hairMean > 0.06f
        chipOn[4] = activePulses > 0
        chipOn[5] = bMean > 0.07f
    }
}

// ───────────────────────── text layouts ─────────────────────────

private class BvText(
    val ear: TextLayoutResult,
    val brain: TextLayoutResult,
    val labels: List<TextLayoutResult>,   // Sound waves, Ear, Signal, Brain
    val torn: TextLayoutResult,
)

private fun ease(k: Float): Float = if (k < 0.5f) 2f * k * k else 1f - (-2f * k + 2f).pow(2) / 2f

// ───────────────────────── composable ─────────────────────────

/**
 * [resetKey] changes -> the camera of the current view snaps back to its default.
 * [onVolumeExceeded] is called when the media volume is above the limit while music plays here: the
 * eardrum tears on screen and the caller is expected to stop the song.
 */
@Composable
fun BrainViz(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    resetKey: Int = 0,
    onVolumeExceeded: () -> Unit = {},
) {
    val sim = remember { BvSim() }
    val cam = remember { BvCam() }
    var tick by remember { mutableLongStateOf(0L) }
    var hearing by remember { mutableStateOf(false) }
    var yaw by remember { mutableFloatStateOf(0.35f) }
    var pitch by remember { mutableFloatStateOf(0.28f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var auto by remember { mutableStateOf(true) }
    var hYaw by remember { mutableFloatStateOf(HEAR_YAW) }
    var hPitch by remember { mutableFloatStateOf(HEAR_PITCH) }
    var hZoom by remember { mutableFloatStateOf(1f) }
    var caption by remember { mutableStateOf("") }
    val chips = remember { mutableStateListOf(false, false, false, false, false, false) }
    val playing by rememberUpdatedState(isPlaying)
    val hearingNow by rememberUpdatedState(hearing)
    val exceededCb by rememberUpdatedState(onVolumeExceeded)
    val first = remember { booleanArrayOf(true) }
    val context = LocalContext.current

    LaunchedEffect(resetKey) {
        if (first[0]) first[0] = false
        else if (hearing) { hYaw = HEAR_YAW; hPitch = HEAR_PITCH; hZoom = 1f }
        else { yaw = 0.35f; pitch = 0.28f; zoom = 1f; auto = false }
    }

    LaunchedEffect(Unit) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        fun readVolume() {
            if (am == null) return
            val mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            sim.volume = if (mx > 0) am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / mx else 0f
            sim.overLimit = sim.volume > VOLUME_LIMIT
        }
        readVolume()
        var last = 0L
        var tickN = 0
        var captionKey = ""
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                if (auto && !hearingNow) yaw += dt * 0.07f
                sim.acc += dt
                var n = 0
                while (sim.acc >= B_STEP && n < 3) {
                    sim.acc -= B_STEP
                    n++
                    tickN++
                    if (tickN % 15 == 0) readVolume()
                    // too loud while music plays: dive into the ear so the eardrum tear is shown
                    if (playing && sim.overLimit && !hearing) {
                        sim.resetHearing()
                        caption = ""
                        hearing = true
                    }
                    sim.step(playing, hearing, AudioAnalyzer.wave, AudioAnalyzer.spectrum)
                    // the drum has torn: stop the song (the tear animation keeps playing)
                    if (playing && sim.overLimit && sim.rupt >= 0.55f) exceededCb()
                    if (hearingNow && tickN % 5 == 0) {
                        for (i in 0 until 6) if (chips[i] != sim.chipOn[i]) chips[i] = sim.chipOn[i]
                        val torn = sim.rupt >= 0.55f
                        val strained = !torn && sim.overLimit && sim.rupt > 0f
                        val key = (if (playing) "1" else "0") + (if (torn) "T" else if (strained) "S" else "")
                        if (key != captionKey) {
                            captionKey = key
                            caption = if (torn)
                                "⚠ Volume limit exceeded: the eardrum tore, so the song was stopped to protect your hearing. Lower the volume to see it heal."
                            else if (strained)
                                "⚠ Volume is above the safe limit: the eardrum is stretching and cracking. Lower the volume now."
                            else if (playing)
                                "Live: your music is travelling from the ear to the brain as an electrical signal."
                            else
                                "Press play and watch the sound travel from ear to brain."
                        }
                    }
                }
                if (n == 3) sim.acc = 0f
                tick = now
            }
        }
    }

    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val txt = remember(measurer) {
        val big = TextStyle(fontSize = 40.sp)
        val lab = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        BvText(
            ear = measurer.measure("👂", big),
            brain = measurer.measure("🧠", big),
            labels = listOf("Sound waves", "Ear", "Signal", "Brain").map { measurer.measure(it, lab) },
            torn = measurer.measure("Eardrum torn!", TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)),
        )
    }
    val accent = HealTeal
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = Color.Gray.copy(alpha = 0.18f)

    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { off ->
                        if (!hearing && sim.flight == 0f) {
                            val dx = off.x - sim.hitX; val dy = off.y - sim.hitY
                            if (dx * dx + dy * dy <= sim.hitR * sim.hitR) {
                                sim.resetHearing()
                                caption = ""
                                hearing = true
                            }
                        }
                    }
                }
                .panZoom({ true }) { pan, z ->
                    if (hearingNow) {
                        hYaw -= pan.x * 0.01f
                        hPitch = (hPitch + pan.y * 0.01f).coerceIn(-1.4f, 1.4f)
                        hZoom = (hZoom * z).coerceIn(0.5f, 4f)
                    } else {
                        auto = false
                        yaw -= pan.x * 0.01f
                        pitch = (pitch + pan.y * 0.01f).coerceIn(-0.1f, 1.5f)
                        zoom = (zoom * z).coerceIn(0.5f, 4f)
                    }
                }
        ) {
            if (tick < 0L) return@Canvas   // reads `tick` so the canvas redraws every frame

            val w = size.width
            val h = size.height
            val focal = minOf(w * 1.5f, h * 2.8f)

            // overview camera
            val e = ease(sim.flight)
            val dist0 = (15.2f - sim.energy * 1.6f) / zoom
            // hearing camera: fit the whole pathway (about 10.4 x 6.6 units) in view
            val ppuH = minOf(w / 10.4f, h / 6.6f) * hZoom
            val dist1 = focal / ppuH
            fun mix(a: Float, b: Float) = a + (b - a) * e
            // little camera shake when the eardrum rips
            val shkX = if (sim.shake > 0f) (Random.nextFloat() - 0.5f) * 0.06f * sim.shake else 0f
            val shkY = if (sim.shake > 0f) (Random.nextFloat() - 0.5f) * 0.04f * sim.shake else 0f
            cam.set(
                w, h,
                mix(0f, 0f), mix(0.3f, 1.45f), mix(-((B_ROWS - 1) * B_ROW_SPACING) / 2f, EAR_Z + 0.8f),
                mix(yaw - B_TWO_PI * ((yaw - hYaw) / B_TWO_PI).roundToInt(), hYaw) + shkX, mix(pitch, hPitch) + shkY, mix(dist0, dist1), focal,
            )

            if (hearing) drawHearing(sim, cam, accent, txt, labelColor)
            else drawOverview(sim, cam, accent, txt, gridColor)

            // danger tint: pulsing red edges while the drum is straining, a red flash when it tears
            if (hearing && (sim.stS > 0f || sim.flashR > 0.01f)) {
                val pulse = 0.6f + 0.4f * sin(sim.phase * 0.5f)
                val a = clamp01(sim.stS * 0.30f * pulse + sim.flashR * 0.30f)
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.Transparent, Color(0xFFFF1A2A).copy(alpha = a)),
                        center = Offset(w / 2f, h / 2f), radius = maxOf(w, h) * 0.75f,
                    ),
                )
            }
        }

        if (hearing) {
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(end = 48.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (i in 0 until 6) {
                            val on = chips[i]
                            Text(
                                "${i + 1} ${CHIP_LABELS[i]}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (on) Color.White else dim,
                                modifier = Modifier.clip(CircleShape)
                                    .background(if (on) accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (caption.isNotEmpty()) {
                        Text(
                            caption,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                    .clickable { hearing = false },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "Close hearing view", Modifier.size(22.dp), tint = accent) }
        } else {
            Text(
                "🧠 Tap the brain to see how you hear",
                fontSize = 12.sp,
                color = accent,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

// ───────────────────────── shared drawing helpers ─────────────────────────

private fun DrawScope.glow(x: Float, y: Float, r: Float, color: Color, alpha: Float) {
    if (alpha <= 0.01f || r < 1f) return
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = clamp01(alpha)), Color.Transparent), center = Offset(x, y), radius = r),
        radius = r, center = Offset(x, y),
    )
}

private fun DrawScope.emoji(layout: TextLayoutResult, x: Float, y: Float, sizePx: Float) {
    val s = sizePx / layout.size.height.toFloat()
    withTransform({ scale(s, s, Offset(x, y)) }) {
        drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f))
    }
}

private fun DrawScope.labelAt(layout: TextLayoutResult, x: Float, y: Float, heightPx: Float, color: Color, alpha: Float = 1f) {
    val s = heightPx / layout.size.height.toFloat()
    withTransform({ scale(s, s, Offset(x, y)) }) {
        drawText(layout, color = color, topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f), alpha = alpha)
    }
}

/** an axis-aligned box, only the faces that look toward the camera */
private fun DrawScope.box(cam: BvCam, cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float, base: Color, path: Path) {
    val xs = floatArrayOf(cx - hx, cx + hx, cx + hx, cx - hx, cx - hx, cx + hx, cx + hx, cx - hx)
    val ys = floatArrayOf(cy - hy, cy - hy, cy - hy, cy - hy, cy + hy, cy + hy, cy + hy, cy + hy)
    val zs = floatArrayOf(cz - hz, cz - hz, cz + hz, cz + hz, cz - hz, cz - hz, cz + hz, cz + hz)
    val px = FloatArray(8); val py = FloatArray(8)
    for (k in 0 until 8) {
        if (!cam.proj(xs[k], ys[k], zs[k])) return
        px[k] = cam.sx; py[k] = cam.sy
    }
    fun face(a: Int, b: Int, c: Int, d: Int, shade: Float) {
        path.reset()
        path.moveTo(px[a], py[a]); path.lineTo(px[b], py[b]); path.lineTo(px[c], py[c]); path.lineTo(px[d], py[d]); path.close()
        drawPath(path, lerp(Color.Black, base, shade))
        drawPath(path, Color.White.copy(alpha = 0.08f), style = Stroke(1f))
    }
    val y1 = cy + hy; val y0 = cy - hy
    if (cam.cy > y1) face(4, 5, 6, 7, 1.0f)
    if (cam.cy < y0) face(0, 1, 2, 3, 0.4f)
    if (cam.cz > cz + hz) face(3, 2, 6, 7, 0.8f)
    if (cam.cz < cz - hz) face(0, 1, 5, 4, 0.5f)
    if (cam.cx < cx - hx) face(0, 3, 7, 4, 0.65f)
    if (cam.cx > cx + hx) face(1, 2, 6, 5, 0.6f)
}

// ───────────────────────── overview scene ─────────────────────────

private fun DrawScope.drawOverview(s: BvSim, cam: BvCam, base: Color, txt: BvText, gridColor: Color) {
    val tz = -((B_ROWS - 1) * B_ROW_SPACING) / 2f
    val path = s.path
    val e = s.energy

    // floor grid
    val half = B_ROW_WIDTH * 1.5f / 2f
    for (i in 0..16) {
        val c = -half + i * (half * 2f / 16f)
        if (cam.proj(-half, -1.3f, tz + c)) {
            val ax = cam.sx; val ay = cam.sy
            if (cam.proj(half, -1.3f, tz + c)) drawLine(gridColor, Offset(ax, ay), Offset(cam.sx, cam.sy), 1f)
        }
        if (cam.proj(c, -1.3f, tz - half)) {
            val ax = cam.sx; val ay = cam.sy
            if (cam.proj(c, -1.3f, tz + half)) drawLine(gridColor, Offset(ax, ay), Offset(cam.sx, cam.sy), 1f)
        }
    }

    // far-to-near: speaker, waterfall + rings, ear + brain
    val dSpeaker = cam.dist2(0f, 0.3f, SPEAKER_Z)
    val dRows = cam.dist2(0f, 0f, tz)
    val dEar = cam.dist2(0f, 0.3f, EAR_Z)
    val order = listOf(0 to dSpeaker, 1 to dRows, 2 to dEar).sortedByDescending { it.second }
    for ((id, _) in order) {
        when (id) {
            0 -> drawSpeaker(s, cam, base, path)
            1 -> drawWaterfall(s, cam, base, e, path)
            else -> drawEarAndBrain(s, cam, base, txt)
        }
    }
}

private fun DrawScope.drawSpeaker(s: BvSim, cam: BvCam, base: Color, path: Path) {
    val sy = 0.15f + sin(s.animT * 0.9f) * 0.05f
    box(cam, 0f, sy, SPEAKER_Z, 0.575f, 0.775f, 0.25f, SPEAKER_C, path)
    // driver cone, pulsing with the sound
    val pulse = 1f + s.energy * 0.5f
    val r = 0.42f * pulse
    path.reset()
    var started = false
    for (k in 0..32) {
        val a = k * B_TWO_PI / 32f
        if (!cam.proj(r * cos(a), sy + r * sin(a), SPEAKER_Z + 0.3f)) continue
        if (!started) { path.moveTo(cam.sx, cam.sy); started = true } else path.lineTo(cam.sx, cam.sy)
    }
    if (started) {
        path.close()
        drawPath(path, lerp(Color.Black, base, 0.55f + s.energy * 0.4f))
        drawPath(path, base, style = Stroke(2f))
    }
    if (cam.proj(0f, sy, SPEAKER_Z + 0.3f)) {
        val pr = 0.12f * cam.focal / cam.sd
        drawCircle(base.copy(alpha = 0.9f), pr, Offset(cam.sx, cam.sy))
        glow(cam.sx, cam.sy, 1.6f * cam.focal / cam.sd, base, 0.10f + s.energy * 0.25f)
    }
}

/** smooth curve through the first [n] points (quadratic segments between midpoints) */
private fun smoothPath(path: Path, xs: FloatArray, ys: FloatArray, n: Int) {
    path.reset()
    if (n < 2) return
    path.moveTo(xs[0], ys[0])
    for (i in 1 until n - 1) {
        path.quadraticBezierTo(xs[i], ys[i], (xs[i] + xs[i + 1]) * 0.5f, (ys[i] + ys[i + 1]) * 0.5f)
    }
    path.lineTo(xs[n - 1], ys[n - 1])
}

private fun DrawScope.drawWaterfall(s: BvSim, cam: BvCam, base: Color, e: Float, path: Path) {
    val line = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val glowS = Stroke(width = (5 + e * 6).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    for (r in B_ROWS - 1 downTo 0) {
        val row = s.rows[r]
        val z = -r * B_ROW_SPACING
        val amp = 1.1f * (1f - r.toFloat() / B_ROWS * 0.35f)
        var n = 0
        for (k in 0 until B_SAMPLES) {
            val x = (k / (B_SAMPLES - 1f) - 0.5f) * B_ROW_WIDTH
            if (!cam.proj(x, row[k] * amp, z)) continue
            s.wpx[n] = cam.sx; s.wpy[n] = cam.sy; n++
        }
        if (n < 2) continue
        smoothPath(path, s.wpx, s.wpy, n)
        val t = r / (B_ROWS - 1f)
        val c = lerp(base, INDIGO_B, t)
        val alpha = (1f - t) * (1f - t) * 0.85f + 0.15f
        // soft glow only on the nearest lines keeps the far ones crisp and uncluttered
        if (r < 10) drawPath(path, c.copy(alpha = alpha * (0.07f + e * 0.10f) * (1f - r / 10f)), style = glowS)
        drawPath(path, c.copy(alpha = alpha), style = line)
    }
    // sound rings travelling from the speaker to the ear
    val scale = 0.75f + e * 1.3f
    val rr = 0.89f * scale
    for (i in 0 until RING_COUNT) {
        val t = s.ringT[i]
        val z = SPEAKER_Z + (EAR_Z - SPEAKER_Z) * t
        val a = sin(t * B_PI) * (0.22f + e * 0.55f)
        if (a < 0.02f) continue
        var n = 0
        for (k in 0..48) {
            val ang = k * B_TWO_PI / 48f
            if (!cam.proj(rr * cos(ang), 0.15f + rr * sin(ang), z)) continue
            s.wpx[n] = cam.sx; s.wpy[n] = cam.sy; n++
        }
        if (n < 2) continue
        path.reset()
        path.moveTo(s.wpx[0], s.wpy[0])
        for (k in 1 until n) path.lineTo(s.wpx[k], s.wpy[k])
        drawPath(path, base.copy(alpha = a), style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawEarAndBrain(s: BvSim, cam: BvCam, base: Color, txt: BvText) {
    val bob = sin(s.animT * 0.9f + B_PI) * 0.05f
    val gy = 0.15f + bob                                  // ear group height
    // 👂
    if (cam.proj(0f, gy + 1.3f, EAR_Z)) emoji(txt.ear, cam.sx, cam.sy, 0.7f * cam.focal / cam.sd)

    // the little brain, just above the ear
    val by = gy + 1.05f
    if (!cam.proj(0f, by, EAR_Z)) return
    val cx = cam.sx; val cy = cam.sy
    val ppu = cam.focal / cam.sd
    s.hitX = cx; s.hitY = cy; s.hitR = max(1.25f * ppu, 44.dp.toPx())

    val bs = s.brainScale
    glow(cx, cy, 0.75f * ppu, base, 0.18f + 0.14f * sin(s.animT * 3f))

    // glowing core
    val coreR = 0.24f * bs * ppu
    drawCircle(
        Brush.radialGradient(
            listOf(lerp(base, Color.White, 0.55f), base, lerp(base, Color.Black, 0.45f)),
            center = Offset(cx - coreR * 0.3f, cy - coreR * 0.3f), radius = coreR * 1.5f,
        ),
        radius = coreR, center = Offset(cx, cy), alpha = 0.9f,
    )

    // wireframe shell (spinning icosahedron)
    val ico = s.ico
    val cy1 = cos(s.brainRotY); val sy1 = sin(s.brainRotY)
    val cx1 = cos(s.brainRotX); val sx1 = sin(s.brainRotX)
    val n = ico.vx.size
    val px = FloatArray(n); val py = FloatArray(n); val ok = BooleanArray(n)
    for (i in 0 until n) {
        var x = ico.vx[i]; var y = ico.vy[i]; var z = ico.vz[i]
        val x2 = x * cy1 + z * sy1; val z2 = -x * sy1 + z * cy1       // spin about y
        val y3 = y * cx1 - z2 * sx1; val z3 = y * sx1 + z2 * cx1      // tilt about x
        x = x2; y = y3; z = z3
        val k = 0.42f * bs
        ok[i] = cam.proj(x * k, by + y * k, EAR_Z + z * k)
        px[i] = cam.sx; py[i] = cam.sy
    }
    val wire = base.copy(alpha = 0.55f)
    var q = 0
    while (q < ico.edges.size) {
        val a = ico.edges[q]; val b = ico.edges[q + 1]
        if (ok[a] && ok[b]) drawLine(wire, Offset(px[a], py[a]), Offset(px[b], py[b]), 1.dp.toPx())
        q += 2
    }

    // neurons flicker on the surface
    for (i in 0 until NEURONS) {
        val nr = s.neuron[i]
        if (!cam.proj(nr[0], by + nr[1], EAR_Z + nr[2])) continue
        val a = 0.15f + s.flash[i] * 0.85f
        drawCircle(base.copy(alpha = a), max(1.5f, 0.045f * cam.focal / cam.sd), Offset(cam.sx, cam.sy))
    }

    // 🧠
    if (cam.proj(0f, by + 0.62f, EAR_Z)) emoji(txt.brain, cam.sx, cam.sy, 0.6f * cam.focal / cam.sd)
}

// ───────────────────────── hearing pathway ─────────────────────────

private fun DrawScope.drawHearing(s: BvSim, cam: BvCam, base: Color, txt: BvText, labelColor: Color) {
    // Scene is authored in "stage" coordinates (x = left->right along the pathway, y up, z toward
    // the viewer) and placed at the ear in the world exactly like the web app (rotated 90° about y).
    fun hp(x: Float, y: Float, z: Float): Boolean = cam.proj(z, y + 0.15f, EAR_Z - x)
    fun ppu(): Float = cam.focal / cam.sd
    val twoPi = B_TWO_PI
    val path = s.path

    fun seg(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, c: Color, wWorld: Float) {
        if (!hp(ax, ay, az)) return
        val x0 = cam.sx; val y0 = cam.sy; val d0 = cam.sd
        if (!hp(bx, by, bz)) return
        drawLine(c, Offset(x0, y0), Offset(cam.sx, cam.sy), max(1f, wWorld * cam.focal / ((d0 + cam.sd) / 2f)), StrokeCap.Round)
    }

    /** closed or open circle polyline in the plane perpendicular to the pathway (the tube cross-section) */
    fun ringYZ(x: Float, r: Float, n: Int): Boolean {
        path.reset()
        var started = false
        for (k in 0..n) {
            val a = k * twoPi / n
            if (!hp(x, r * sin(a), r * cos(a))) continue
            if (!started) { path.moveTo(cam.sx, cam.sy); started = true } else path.lineTo(cam.sx, cam.sy)
        }
        return started
    }

    fun polyOutline(poly: FloatArray, ox: Float, z: Float, fill: Color, edge: Color) {
        path.reset()
        var started = false
        var i = 0
        while (i < poly.size) {
            if (hp(ox + poly[i], poly[i + 1], z)) {
                if (!started) { path.moveTo(cam.sx, cam.sy); started = true } else path.lineTo(cam.sx, cam.sy)
            }
            i += 2
        }
        if (!started) return
        path.close()
        drawPath(path, fill)
        drawPath(path, edge, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
    }

    val frontSide = cam.cx > 0f   // camera looks at the pathway from its +z side

    fun drawPinna() {
        polyOutline(s.pinna, -3.38f, -0.42f, SKIN_C.copy(alpha = 0.9f), SKIN_D)
        polyOutline(s.ridge, -3.38f, -0.18f, SKIN_D.copy(alpha = 0.95f), Color(0xFFB57A66))
    }

    // ---- outer ear (behind the canal) ----
    if (frontSide) drawPinna()

    // ---- faint temporal bone ----
    run {
        val c = Color(0xFFAAB4C0).copy(alpha = 0.22f)
        val x0 = -0.55f - 2.3f; val x1 = -0.55f + 2.3f
        val y0 = -0.1f - 1.15f; val y1 = -0.1f + 1.15f
        val z0 = -0.7f; val z1 = 0.7f
        val xs = floatArrayOf(x0, x1); val ys = floatArrayOf(y0, y1); val zs = floatArrayOf(z0, z1)
        for (a in 0..1) for (b in 0..1) {
            seg(xs[0], ys[a], zs[b], xs[1], ys[a], zs[b], c, 0.012f)
            seg(xs[a], ys[0], zs[b], xs[a], ys[1], zs[b], c, 0.012f)
            seg(xs[a], ys[b], zs[0], xs[a], ys[b], zs[1], c, 0.012f)
        }
    }

    // ---- ear funnel + canal (open ghost tubes) ----
    run {
        val g = GHOST_C.copy(alpha = 0.28f)
        for (k in 0..4) {
            val x = -4.4f + k * 0.25f
            if (ringYZ(x, 0.72f + (0.38f - 0.72f) * (k / 4f), 20)) drawPath(path, g, style = Stroke(1.dp.toPx()))
        }
        for (k in 0..6) {
            if (ringYZ(-3.4f + k * (1.7f / 6f), 0.38f, 20)) drawPath(path, g, style = Stroke(1.dp.toPx()))
        }
        for (m in 0 until 8) {
            val a = m * twoPi / 8f
            val ca = cos(a); val sa = sin(a)
            seg(-4.4f, 0.72f * sa, 0.72f * ca, -3.4f, 0.38f * sa, 0.38f * ca, g, 0.012f)
            seg(-3.4f, 0.38f * sa, 0.38f * ca, -1.7f, 0.38f * sa, 0.38f * ca, g, 0.012f)
        }
    }

    // ---- air particles bunch up and spread out as the real waveform passes ----
    run {
        val pr = 0.055f
        for (i in 0 until AIR_N) {
            if (!hp(s.airX[i], s.airRestY[i], s.airRestZ[i])) continue
            drawCircle(base.copy(alpha = 0.9f), max(1.4f, pr * ppu()), Offset(cam.sx, cam.sy))
        }
    }

    // ---- pressure wavefronts ride on the compression peaks ----
    run {
        val ph0 = (s.phase / twoPi) % 1f
        for (i in 0 until 8) {
            val rp = (ph0 + 0.5f + i) / s.cycles
            if (rp > 1f) continue
            val rx = AIR_X0 + rp * (AIR_X1 - AIR_X0)
            val rs = airRadius(rx) * 0.95f
            val a = min(0.7f, s.envAt(rp) * 1.1f) * (0.3f + 0.7f * sin(rp * B_PI))
            if (a < 0.02f) continue
            if (ringYZ(rx, rs, 44)) drawPath(path, base.copy(alpha = a), style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
        }
    }

    // ---- eardrum (tympanic membrane): a disc whose surface really flexes ----
    drawEardrum(s, cam, txt, labelColor, ::hp)

    // ---- ossicles: malleus -> incus -> stapes ----
    run {
        fun rotX(px: Float, py: Float, ang: Float, vx: Float, vy: Float) = vx + (px - vx) * cos(ang) - (py - vy) * sin(ang)
        fun rotY(px: Float, py: Float, ang: Float, vx: Float, vy: Float) = vy + (px - vx) * sin(ang) + (py - vy) * cos(ang)
        fun ball(x: Float, y: Float, r: Float) {
            if (hp(x, y, 0f)) drawCircle(BONE_C, max(2f, r * ppu()), Offset(cam.sx, cam.sy))
        }
        fun limb(ax: Float, ay: Float, bx: Float, by: Float, r: Float) = seg(ax, ay, 0f, bx, by, 0f, BONE_C, r * 2f)

        // malleus: handle glued to the drum
        var pvx = -1.5f; var pvy = 0.32f
        var ang = s.drum * 1.1f
        limb(rotX(-1.68f, -0.2f, ang, pvx, pvy), rotY(-1.68f, -0.2f, ang, pvx, pvy), pvx, pvy, 0.04f)
        ball(pvx, pvy, 0.085f)

        // incus
        pvx = -1.05f; pvy = 0.38f; ang = -s.drum * 0.9f
        val ix = rotX(-1.3f, 0.36f, ang, pvx, pvy); val iy = rotY(-1.3f, 0.36f, ang, pvx, pvy)
        limb(ix, iy, pvx, pvy, 0.045f)
        ball(ix, iy, 0.08f)
        limb(pvx, pvy, rotX(-0.95f, -0.02f, ang, pvx, pvy), rotY(-0.95f, -0.02f, ang, pvx, pvy), 0.033f)

        // stapes: pushes the oval window
        val dx = s.drum * 0.7f
        limb(-0.95f + dx, -0.02f, -0.44f + dx, -0.06f, 0.03f)
        if (ringYZ(-0.4f + dx, 0.11f, 20)) {
            path.close()
            drawPath(path, BONE_C.copy(alpha = 0.9f))
        }
        path.reset()
        var started = false
        for (k in 0..18) {
            val a = k * twoPi / 18f
            if (!hp(-0.64f + dx + 0.07f * cos(a), -0.045f + 0.07f * sin(a), 0f)) continue
            if (!started) { path.moveTo(cam.sx, cam.sy); started = true } else path.lineTo(cam.sx, cam.sy)
        }
        if (started) drawPath(path, BONE_C, style = Stroke(max(1.5f, 0.028f * ppu())))
    }

    // ---- cochlea: snail-shell spiral with a hair cell for every pitch ----
    run {
        path.reset()
        var started = false
        for (k in 0..CN) {
            if (!hp(s.coX[k], s.coY[k], s.coZ[k])) continue
            if (!started) { path.moveTo(cam.sx, cam.sy); started = true } else path.lineTo(cam.sx, cam.sy)
        }
        if (started) {
            val wpx = 0.14f * ppu()
            drawPath(path, COCHLEA_C.copy(alpha = 0.55f), style = Stroke(wpx, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, Color.White.copy(alpha = 0.18f), style = Stroke(wpx * 0.35f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        for (k in 0 until HAIR_N) {
            val a = s.hairAct[k]
            if (!hp(s.hX[k], s.hY[k], s.hZ[k])) continue
            val col = lerp(base, AMBER, min(1f, a * 1.6f))
            drawCircle(col, max(1.8f, 0.034f * (1f + a * 1.9f) * ppu()), Offset(cam.sx, cam.sy))
        }
    }

    // ---- auditory nerve + travelling electrical pulses ----
    run {
        path.reset()
        var started = false
        for (k in 0..NV) {
            if (!hp(s.nvX[k], s.nvY[k], s.nvZ[k])) continue
            if (!started) { path.moveTo(cam.sx, cam.sy); started = true } else path.lineTo(cam.sx, cam.sy)
        }
        if (started) {
            val hot = min(1f, s.activePulses / 8f)
            drawPath(path, lerp(NERVE_C, NERVE_HOT, hot), style = Stroke(max(2f, 0.07f * ppu()), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        for (i in 0 until PULSES) {
            if (!s.pOn[i]) continue
            if (!hp(s.pX[i], s.pY[i], s.pZ[i])) continue
            val psz = 0.13f + 0.14f * s.pInten[i]
            val r = psz * ppu()
            glow(cam.sx, cam.sy, r * 1.1f, Color(0xFFFFA400), 0.9f)
            drawCircle(Color.White, max(1.5f, r * 0.28f), Offset(cam.sx, cam.sy))
        }
    }

    // ---- brain: folded cortex + a live neural network ----
    drawBrain(s, cam, base, ::hp)

    // ---- outer ear when seen from behind ----
    if (!frontSide) drawPinna()

    // ---- labels ----
    run {
        val spots = arrayOf(floatArrayOf(-3.7f, 1.6f, 0.3f), floatArrayOf(-4.0f, -1.5f, 0.3f), floatArrayOf(1.7f, 0.45f, 0.3f), floatArrayOf(2.25f, 3.65f, 0.34f))
        for (i in 0 until 4) {
            if (!hp(spots[i][0], spots[i][1], 0.5f)) continue
            labelAt(txt.labels[i], cam.sx, cam.sy, spots[i][2] * ppu(), labelColor)
        }
    }
}

private fun DrawScope.drawEardrum(s: BvSim, cam: BvCam, txt: BvText, labelColor: Color, hp: (Float, Float, Float) -> Boolean) {
    val path = s.path
    val twoPi = B_TWO_PI

    /** membrane displacement (along the pathway) at disc coordinates dx, dy */
    fun dz(dx: Float, dy: Float): Float {
        val rr = sqrt(dx * dx + dy * dy) / DRUM_R
        var d = (s.drum + s.jitter) * 1.4f * (1f - rr * rr)
        if (s.hp > 0f) {
            val hx = dx / DRUM_R - DRUM_CX; val hy = dy / DRUM_R - DRUM_CY
            val hth = atan2(hy, hx)
            val edge = sqrt(hx * hx + hy * hy) - s.holeF * drumJag(hth)
            if (edge < 0.3f) {
                val w = if (edge < 0f) 1f else 1f - edge / 0.3f
                d += w * w * s.curl * (0.6f + 0.4f * sin(hth * 3f + s.phase * 0.9f)) * (1f - rr.pow(4))
            }
        }
        return d
    }

    val nr = 7; val na = 32
    val vx = FloatArray((nr + 1) * (na + 1)); val vy = FloatArray(vx.size); val vo = BooleanArray(vx.size)
    for (i in 0..nr) {
        val r = DRUM_R * i / nr
        for (j in 0..na) {
            val a = j * twoPi / na
            val dx = r * cos(a); val dy = r * sin(a)
            val id = i * (na + 1) + j
            vo[id] = hp(DRUM_X + dz(dx, dy), dy, -dx)
            vx[id] = cam.sx; vy[id] = cam.sy
        }
    }
    val st = s.stS
    val col = Color(
        min(1f, 0.93f + s.drumAmp * 1.5f + st * 0.05f),
        (0.62f - st * 0.22f + s.drumAmp * 0.2f).coerceIn(0f, 1f),
        (0.69f - st * 0.26f).coerceIn(0f, 1f),
        0.92f,
    )
    for (i in 0 until nr) {
        for (j in 0 until na) {
            if (s.hp > 0f) {   // skip quads that fall inside the torn hole
                val rm = (i + 0.5f) / nr
                val am = (j + 0.5f) * twoPi / na
                val ux = rm * cos(am) - DRUM_CX; val uy = rm * sin(am) - DRUM_CY
                if (sqrt(ux * ux + uy * uy) < s.holeF * drumJag(atan2(uy, ux))) continue
            }
            val a = i * (na + 1) + j; val b = a + 1; val c = a + na + 1; val d = c + 1
            if (!(vo[a] && vo[b] && vo[c] && vo[d])) continue
            val mx = (vx[a] + vx[b] + vx[c] + vx[d]) / 4f; val my = (vy[a] + vy[b] + vy[c] + vy[d]) / 4f
            path.reset()
            path.moveTo(mx + (vx[a] - mx) * 1.06f, my + (vy[a] - my) * 1.06f)
            path.lineTo(mx + (vx[b] - mx) * 1.06f, my + (vy[b] - my) * 1.06f)
            path.lineTo(mx + (vx[d] - mx) * 1.06f, my + (vy[d] - my) * 1.06f)
            path.lineTo(mx + (vx[c] - mx) * 1.06f, my + (vy[c] - my) * 1.06f)
            path.close()
            drawPath(path, col)
        }
    }

    // rim
    path.reset()
    var started = false
    for (k in 0..40) {
        val a = k * twoPi / 40f
        if (!hp(DRUM_X, 0.43f * sin(a), -0.43f * cos(a))) continue
        if (!started) { path.moveTo(cam.sx, cam.sy); started = true } else path.lineTo(cam.sx, cam.sy)
    }
    if (started) drawPath(path, Color(0xFFE9C4B0), style = Stroke(max(1.5f, 0.044f * cam.focal / cam.sd)))

    // stress cracks, then the jagged torn hole with a bloody edge
    val cr = clamp01((s.rupt - 0.25f) / 0.25f)
    if (cr > 0f) {
        for (c in s.cracks) {
            val n = max(1, ceil(cr * 10f).toInt())
            path.reset()
            var st2 = false
            for (k in 0..n) {
                val ux = c[k * 2]; val uy = c[k * 2 + 1]
                val dx = ux * DRUM_R; val dy = uy * DRUM_R
                if (!hp(DRUM_X + dz(dx, dy) + 0.004f, dy, -dx)) continue
                if (!st2) { path.moveTo(cam.sx, cam.sy); st2 = true } else path.lineTo(cam.sx, cam.sy)
            }
            if (st2) drawPath(path, Color(0xF25F0519), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
    if (s.hp > 0f) {
        path.reset()
        var st3 = false
        for (k in 0..120) {
            val th = k / 120f * twoPi
            val rr = s.holeF * drumJag(th)
            val ux = DRUM_CX + cos(th) * rr; val uy = DRUM_CY + sin(th) * rr
            val dx = ux * DRUM_R; val dy = uy * DRUM_R
            if (!hp(DRUM_X + dz(dx, dy) + 0.004f, dy, -dx)) continue
            if (!st3) { path.moveTo(cam.sx, cam.sy); st3 = true } else path.lineTo(cam.sx, cam.sy)
        }
        if (st3) {
            path.close()
            drawPath(path, Color(0xF2960820), style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
            drawPath(path, Color(0xE6FFCDD2), style = Stroke(1.dp.toPx(), join = StrokeJoin.Round))
        }
    }

    // flying shreds, red flash and warning label
    if (s.shredOn) {
        val a = clamp01(s.shredLife * 1.6f)
        for (i in 0 until SHRED_N) {
            if (!hp(s.shX[i], s.shY[i], s.shZ[i])) continue
            drawCircle(Color(0xFFFF6A85).copy(alpha = a), max(1.5f, 0.042f * cam.focal / cam.sd), Offset(cam.sx, cam.sy))
        }
    }
    if (s.flashR > 0.01f && hp(DRUM_X, 0f, 0.05f)) {
        val size = 0.5f + (1f - s.flashR) * 2.4f
        glow(cam.sx, cam.sy, size * 0.5f * cam.focal / cam.sd, Color(0xFFFF2A3A), s.flashR * 0.9f)
    }
    if (s.labelA > 0.02f && hp(DRUM_X, 0.95f, 0.5f)) {
        labelAt(txt.torn, cam.sx, cam.sy, 0.3f * cam.focal / cam.sd, Color(0xFFFF4D5E), s.labelA)
    }
}

private fun DrawScope.drawBrain(s: BvSim, cam: BvCam, base: Color, hp: (Float, Float, Float) -> Boolean) {
    val path = s.path
    val bay = sin(s.animT * 0.5f) * 0.12f
    val cb = cos(bay); val sb = sin(bay)

    /** project a point given in the brain's own frame (after its slow sway) */
    fun bproj(x: Float, y: Float, z: Float): Boolean {
        val rx = x * cb + z * sb; val rz = -x * sb + z * cb
        return hp(BRAIN_X + rx, BRAIN_Y + y, rz)
    }

    // camera position in the brain's own frame (for culling + lighting)
    val lcx = EAR_Z - cam.cz; val lcy = cam.cy - 0.15f; val lcz = cam.cx
    val gx = lcx - BRAIN_X; val gy = lcy - BRAIN_Y; val gz = lcz
    val ccx = gx * cb - gz * sb; val ccy = gy; val ccz = gx * sb + gz * cb

    val em = min(0.5f, s.bMean * 1.6f)
    val lx = -0.3f; val ly = 0.7f; val lz = 0.65f
    val ll = sqrt(lx * lx + ly * ly + lz * lz)

    /** shade one mesh: per-quad lighting (diffuse + headlight + specular + rim), sulci darkened,
     *  quads of the same shade are batched into a single path so a frame is only ~100 draw calls */
    fun patch(m: BvMesh, ox: Float, oy: Float, oz: Float, groove: Color, crestC: Color) {
        for (v in 0 until m.n) {
            s.vok[v] = bproj(m.x[v] + ox, m.y[v] + oy, m.z[v] + oz)
            s.vsx[v] = cam.sx; s.vsy[v] = cam.sy
        }
        for (i in 0 until m.nl) {
            for (j in 0 until m.nm) {
                val id = i * m.nm + j
                val tx = ccx - (m.qcx[id] + ox); val ty = ccy - (m.qcy[id] + oy); val tz = ccz - (m.qcz[id] + oz)
                val tl = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(1e-4f)
                val facing = (m.qnx[id] * tx + m.qny[id] * ty + m.qnz[id] * tz) / tl
                if (facing <= 0f) continue
                val a = i * (m.nm + 1) + j; val b = a + 1; val c = a + m.nm + 1; val d = c + 1
                if (!(s.vok[a] && s.vok[b] && s.vok[c] && s.vok[d])) continue
                val nx = m.qnx[id]; val ny = m.qny[id]; val nz = m.qnz[id]
                val diff = max(0f, (nx * lx + ny * ly + nz * lz) / ll)
                // specular: half-vector between the light and the viewer
                var hx = lx / ll + tx / tl; var hy = ly / ll + ty / tl; var hz = lz / ll + tz / tl
                val hl = sqrt(hx * hx + hy * hy + hz * hz).coerceAtLeast(1e-4f)
                hx /= hl; hy /= hl; hz /= hl
                val nh = max(0f, nx * hx + ny * hy + nz * hz)
                val nh2 = nh * nh; val nh4 = nh2 * nh2; val nh8 = nh4 * nh4
                val spec = nh8 * nh8 * nh8 * nh8                        // ~ nh^32 (wet-looking highlight)
                val cr = m.qcrest[id]
                val rim = (1f - facing).let { it * it * it }
                val light = 0.24f + 0.62f * diff + 0.20f * facing + 0.55f * spec * (0.35f + 0.65f * cr) + 0.22f * rim * diff
                // sulci also lose some light (ambient occlusion)
                val lit = light * (0.62f + 0.38f * cr)
                val lq = (((lit - L_MIN) / (L_MAX - L_MIN)) * LV).toInt().coerceIn(0, LV - 1)
                val cq = (cr * CV).toInt().coerceIn(0, CV - 1)
                val bi = lq * CV + cq
                val mx = (s.vsx[a] + s.vsx[b] + s.vsx[c] + s.vsx[d]) / 4f
                val my = (s.vsy[a] + s.vsy[b] + s.vsy[c] + s.vsy[d]) / 4f
                val bp = s.shadePaths[bi]
                s.shadeUsed[bi] = true
                bp.moveTo(mx + (s.vsx[a] - mx) * 1.08f, my + (s.vsy[a] - my) * 1.08f)
                bp.lineTo(mx + (s.vsx[b] - mx) * 1.08f, my + (s.vsy[b] - my) * 1.08f)
                bp.lineTo(mx + (s.vsx[d] - mx) * 1.08f, my + (s.vsy[d] - my) * 1.08f)
                bp.lineTo(mx + (s.vsx[c] - mx) * 1.08f, my + (s.vsy[c] - my) * 1.08f)
                bp.close()
            }
        }
        for (bi in 0 until LV * CV) {
            if (!s.shadeUsed[bi]) continue
            val lv = L_MIN + ((bi / CV) + 0.5f) / LV * (L_MAX - L_MIN)
            val cf = ((bi % CV) + 0.5f) / CV
            var r = groove.red + (crestC.red - groove.red) * cf
            var g = groove.green + (crestC.green - groove.green) * cf
            var bl = groove.blue + (crestC.blue - groove.blue) * cf
            if (lv <= 1f) {
                r *= lv; g *= lv; bl *= lv
            } else {
                val k = min(1f, (lv - 1f) * 0.9f)                        // highlights wash toward white
                r += (1f - r) * k; g += (1f - g) * k; bl += (1f - bl) * k
            }
            r += AMBER.red * em * 0.4f; g += AMBER.green * em * 0.4f; bl += AMBER.blue * em * 0.4f
            drawPath(s.shadePaths[bi], Color(min(1f, r), min(1f, g), min(1f, bl), 1f))
            s.shadePaths[bi].reset()
            s.shadeUsed[bi] = false
        }
    }

    // brainstem + cerebellum sit behind / under the cerebrum
    if (bproj(-0.13f, -0.576f, 0f)) {
        val x0 = cam.sx; val y0 = cam.sy
        if (bproj(-0.07f, -1.324f, 0f)) {
            val x1 = cam.sx; val y1 = cam.sy
            val pw = cam.focal / cam.sd
            drawLine(lerp(STEM_C, Color.Black, 0.18f), Offset(x0, y0), Offset(x1, y1), max(2f, 0.23f * pw), StrokeCap.Round)
            drawLine(STEM_C, Offset(x0, y0), Offset(x1, y1), max(2f, 0.17f * pw), StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.12f), Offset(x0, y0), Offset(x1, y1), max(1f, 0.05f * pw), StrokeCap.Round)
        }
    }
    patch(s.cerebellum, -0.95f, -0.62f, 0f, Color(0xFFB8707F), CEREB_C)
    patch(s.cerebrum, 0f, 0f, 0f, GROOVE_C, CREST_C)

    // soft glows on the cortex that light up as the signal reaches them
    val gl = arrayOf(floatArrayOf(-0.2f, -0.45f, 0.72f, 1.0f), floatArrayOf(0.2f, 0.35f, 0.75f, 1.3f), floatArrayOf(0.95f, 0.1f, 0.6f, 1.1f))
    for (i in 0 until 3) {
        if (s.glowA[i] <= 0.02f) continue
        if (bproj(gl[i][0], gl[i][1], gl[i][2])) glow(cam.sx, cam.sy, gl[i][3] * 0.5f * cam.focal / cam.sd, AMBER, s.glowA[i])
    }

    // the neural network, lit as the signal spreads
    val e = s.edges
    var k = 0
    while (k < e.size) {
        val a = e[k]; val b = e[k + 1]
        k += 2
        if (!bproj(s.nodeX[a], s.nodeY[a], s.nodeZ[a])) continue
        val ax = cam.sx; val ay = cam.sy
        if (!bproj(s.nodeX[b], s.nodeY[b], s.nodeZ[b])) continue
        val act = max(s.nodeAct[a], s.nodeAct[b])
        val c = lerp(base, AMBER, min(1f, act * 1.5f))
        drawLine(c.copy(alpha = 0.22f + 0.7f * min(1f, act * 1.5f)), Offset(ax, ay), Offset(cam.sx, cam.sy), 1.dp.toPx())
    }
    for (i in 0 until s.nodeCount) {
        if (!bproj(s.nodeX[i], s.nodeY[i], s.nodeZ[i])) continue
        val act = s.nodeAct[i]
        val c = lerp(base, AMBER, min(1f, act * 1.4f))
        drawCircle(c.copy(alpha = 0.45f + 0.55f * min(1f, act * 1.4f)), max(1.6f, 0.04f * (1f + act * 1.4f) * cam.focal / cam.sd), Offset(cam.sx, cam.sy))
    }
}
