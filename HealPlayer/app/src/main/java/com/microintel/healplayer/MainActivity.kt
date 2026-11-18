package com.microintel.healplayer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.microintel.healplayer.ui.Accent
import com.microintel.healplayer.ui.HealApp
import com.microintel.healplayer.ui.Palette
import com.microintel.healplayer.ui.PaletteStore
import com.microintel.healplayer.ui.HealTheme
import com.microintel.healplayer.ui.SplashScreen
import com.microintel.healplayer.ui.ThemeMode
import com.microintel.healplayer.ui.ThemeStore

class MainActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Accent.color = PaletteStore.load(this).color
        // fresh open (not a rotation): show a healing quote notification
        if (savedInstanceState == null) QuoteNotifier.show(this)
        setContent {
            var mode by remember { mutableStateOf(ThemeStore.load(this@MainActivity)) }
            var palette by remember { mutableStateOf(PaletteStore.load(this@MainActivity)) }
            val dark = when (mode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // keep status/navigation bar icons readable in whichever theme is active
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            HealTheme(dark) {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                Box {
                    HealApp(
                        vm = vm,
                        themeMode = mode,
                        onThemeChange = {
                            mode = it
                            ThemeStore.save(this@MainActivity, it)
                        },
                        palette = palette,
                        onPaletteChange = {
                            palette = it
                            Accent.color = it.color
                            PaletteStore.save(this@MainActivity, it)
                        },
                    )
                    AnimatedVisibility(visible = showSplash, exit = fadeOut(tween(400))) {
                        SplashScreen { showSplash = false }
                    }
                }
            }
        }
    }
}
