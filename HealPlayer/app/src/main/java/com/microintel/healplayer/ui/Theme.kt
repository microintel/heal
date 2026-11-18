package com.microintel.healplayer.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class Palette(val label: String, val color: Color) {
    TEAL("Teal", Color(0xFF0D9488)),
    BLUE("Blue", Color(0xFF3B82F6)),
    INDIGO("Indigo", Color(0xFF6366F1)),
    PURPLE("Purple", Color(0xFFA855F7)),
    ROSE("Rose", Color(0xFFE11D48)),
    ORANGE("Orange", Color(0xFFF97316)),
    GREEN("Green", Color(0xFF22C55E)),
}

/** Current accent colour; reading it inside a composable recomposes when the palette changes. */
object Accent {
    var color by mutableStateOf(Palette.TEAL.color)
}

val HealTeal: Color get() = Accent.color
val HealIndigo = Color(0xFF6366F1)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

object PaletteStore {
    private const val KEY = "palette"

    fun load(ctx: Context): Palette = try {
        Palette.valueOf(ctx.getSharedPreferences("heal_prefs", Context.MODE_PRIVATE).getString(KEY, null) ?: "TEAL")
    } catch (e: Exception) {
        Palette.TEAL
    }

    fun save(ctx: Context, p: Palette) {
        ctx.getSharedPreferences("heal_prefs", Context.MODE_PRIVATE).edit().putString(KEY, p.name).apply()
    }
}

object ThemeStore {
    private const val PREFS = "heal_prefs"
    private const val KEY = "theme_mode"

    fun load(ctx: Context): ThemeMode = try {
        ThemeMode.valueOf(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: "SYSTEM")
    } catch (e: Exception) {
        ThemeMode.SYSTEM
    }

    fun save(ctx: Context, mode: ThemeMode) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
    }
}

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,
    secondary = HealIndigo,
    background = Color(0xFF000000),
    onBackground = Color(0xFFF1F2F5),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF1F2F5),
    surfaceVariant = Color(0xFF111111),
    onSurfaceVariant = Color(0xFF9AA1AC),
    outline = Color(0xFF232323),
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    secondary = HealIndigo,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1F27),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1F27),
    surfaceVariant = Color(0xFFF1F2F5),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE1E4E9),
)

@Composable
fun HealTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) darkScheme(Accent.color) else lightScheme(Accent.color)
    MaterialTheme(colorScheme = scheme) {
        // Surface sets the default text/icon colour (otherwise it stays black on black)
        Surface(color = scheme.background, contentColor = scheme.onBackground, content = content)
    }
}
