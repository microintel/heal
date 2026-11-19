package com.microintel.healplayer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.microintel.healplayer.R
import kotlinx.coroutines.delay

private val RedTop = Color(0xFFFB5A5D)
private val RedBottom = Color(0xFFB02A37)
private const val WAVE_PATH = "M150,256 C182,256 190,190 218,190 S250,322 284,322 S326,256 362,256"

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun easeOut(x: Float): Float { val k = 1f - clamp01(x); return 1f - k * k * k }
private fun easeInOut(x: Float): Float { val k = clamp01(x); return k * k * (3f - 2f * k) }

/**
 * Splash timeline (t = 0..1 over 1.8 s):
 *  0.00-0.30  the two bars of the H grow out from the middle
 *  0.25-0.75  the wave draws itself from left to right
 *  0.78-0.95  the drawn logo cross-fades into the final logo image
 *  0.70-1.00  "Healer" slides up, then Microintel fades in
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(1800, easing = LinearEasing))
        delay(700)
        onDone()
    }

    val wavePath = remember { PathParser().parsePathString(WAVE_PATH).toPath() }
    val measure = remember { PathMeasure().also { it.setPath(wavePath, false) } }
    val segment = remember { Path() }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(RedTop, RedBottom)))
    ) {
        Column(
            Modifier.fillMaxSize().graphicsLayer { alpha = clamp01(t.value / 0.06f) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // above / below the square logo the screen continues with the logo's own top / bottom colour
            Box(Modifier.weight(1f).fillMaxWidth().background(RedTop))
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                // 1) the logo drawing itself
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.verticalGradient(listOf(RedTop, RedBottom), startY = 0f, endY = size.height))
                    val sc = size.width / 512f
                    val bars = easeOut(t.value / 0.30f)
                    val wave = easeInOut((t.value - 0.25f) / 0.50f)
                    val pop = 0.86f + 0.14f * easeOut(t.value / 0.45f)
                    val ink = Brush.horizontalGradient(
                        listOf(Color(0xFF4A0F2A), Color(0xFF2B1240), Color(0xFF14152F)),
                        startX = 130f, endX = 382f,
                    )
                    withTransform({
                        scale(sc, sc, Offset.Zero)
                        scale(pop, pop, Offset(256f, 256f))
                    }) {
                        val half = 126f * bars
                        drawLine(ink, Offset(150f, 256f - half), Offset(150f, 256f + half), strokeWidth = 38f, cap = StrokeCap.Round)
                        drawLine(ink, Offset(362f, 256f - half), Offset(362f, 256f + half), strokeWidth = 38f, cap = StrokeCap.Round)
                        if (wave > 0f) {
                            segment.reset()
                            measure.getSegment(0f, measure.length * wave, segment, true)
                            drawPath(segment, ink, style = Stroke(width = 20f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                // 2) the final logo image fades in over it (same picture, with its soft shadow)
                Image(
                    painterResource(R.drawable.app_logo), "Healer",
                    Modifier.fillMaxSize().graphicsLayer { alpha = clamp01((t.value - 0.78f) / 0.17f) },
                    contentScale = ContentScale.FillWidth,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth().background(RedBottom), contentAlignment = Alignment.TopCenter) {
                Text(
                    "Healer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp).graphicsLayer {
                        val a = easeOut((t.value - 0.70f) / 0.30f)
                        alpha = a
                        translationY = (1f - a) * 18.dp.toPx()
                    },
                )
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp)
                .graphicsLayer { alpha = easeOut((t.value - 0.82f) / 0.18f) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painterResource(R.drawable.microintel_logo), "Microintel",
                Modifier.height(36.dp),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.85f)),
            )
        }
    }
}
