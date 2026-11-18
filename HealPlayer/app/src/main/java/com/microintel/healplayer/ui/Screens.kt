package com.microintel.healplayer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import com.microintel.healplayer.R
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.microintel.healplayer.ToneGenerator
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.microintel.healplayer.VizMode
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import com.microintel.healplayer.Folder
import com.microintel.healplayer.PlayerViewModel
import com.microintel.healplayer.QuoteNotifier
import com.microintel.healplayer.Song
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ───────────────────────── Root ─────────────────────────

@Composable
fun HealApp(
    vm: PlayerViewModel,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    palette: Palette,
    onPaletteChange: (Palette) -> Unit,
) {
    val ctx = LocalContext.current
    val audioPerm = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, audioPerm) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        granted = r[audioPerm] == true
        // first launch: notification permission was just granted, so show today's quote now
        if (r[Manifest.permission.POST_NOTIFICATIONS] == true) QuoteNotifier.show(ctx)
    }
    val permsToAsk = remember {
        if (Build.VERSION.SDK_INT >= 33) arrayOf(audioPerm, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(audioPerm)
    }

    LaunchedEffect(Unit) { if (!granted) launcher.launch(permsToAsk) }
    LaunchedEffect(granted) { if (granted) vm.loadLibrary() }

    var tab by remember { mutableIntStateOf(0) }
    var openFolderPath by remember { mutableStateOf<String?>(null) }
    var openAlbumId by remember { mutableStateOf<Long?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showTune by remember { mutableStateOf(false) }

    val openFolder: Folder? = openFolderPath?.let { p -> vm.folders.firstOrNull { it.path == p } }
    val albums = remember(vm.songs) { buildAlbums(vm.songs) }
    val openAlbum: AlbumGroup? = openAlbumId?.let { id -> albums.firstOrNull { it.id == id } }
    BackHandler(enabled = openAlbum != null && openFolder == null) { openAlbumId = null }
    BackHandler(enabled = openFolder != null) { openFolderPath = null }
    BackHandler(enabled = showTune) { showTune = false }
    BackHandler(enabled = showSleep) { showSleep = false }
    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = showAbout) { showAbout = false }
    BackHandler(enabled = searching) { searching = false; vm.onQueryChange("") }

    val pagerState = rememberPagerState { 4 }
    val pagerVisible = granted && !searching && !showAbout && !showSettings && !showSleep && !showTune &&
        openFolder == null && !(tab == 2 && openAlbum != null)
    // pager -> tab (user swiped); runs only while the pager is on screen
    LaunchedEffect(pagerVisible) {
        if (!pagerVisible) return@LaunchedEffect
        if (pagerState.currentPage != tab) pagerState.scrollToPage(tab)
        snapshotFlow { pagerState.settledPage }.collect { tab = it }
    }
    // tab -> pager (bottom bar tapped): glide to the page
    LaunchedEffect(tab) {
        if (pagerVisible && pagerState.currentPage != tab) pagerState.animateScrollToPage(tab)
    }

    val showNav = granted && !searching && !showAbout && !showSettings && !showSleep && !showTune

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when {
                    !granted -> PermissionScreen { launcher.launch(permsToAsk) }
                    showAbout -> AboutScreen { showAbout = false }
                    showTune -> TuneScreen(vm) { showTune = false }
                    showSleep -> SleepTimerScreen(vm) { showSleep = false }
                    showSettings -> SettingsScreen(
                        vm = vm,
                        themeMode = themeMode,
                        onThemeChange = onThemeChange,
                        palette = palette,
                        onPaletteChange = onPaletteChange,
                        onAbout = { showAbout = true },
                        onSleep = { showSleep = true },
                        onBack = { showSettings = false },
                    )
                    searching -> SearchScreen(
                        vm = vm,
                        onClose = { searching = false; vm.onQueryChange("") },
                        onOpenFolder = { f ->
                            searching = false
                            vm.onQueryChange("")
                            tab = 0
                            openFolderPath = f.path
                        },
                    )
                    openFolder != null -> SongList(
                        vm = vm,
                        songs = openFolder.songs,
                        title = openFolder.name,
                        subtitle = null,
                        onBack = { openFolderPath = null },
                    )
                    tab == 2 && openAlbum != null -> SongList(
                        vm = vm,
                        songs = openAlbum.songs,
                        title = openAlbum.name,
                        subtitle = openAlbum.artist,
                        onBack = { openAlbumId = null },
                    )
                    else -> HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        when (page) {
                            3 -> VisualizerScreen(vm)
                            else -> Column(Modifier.fillMaxSize()) {
                                HomeHeader(
                                    title = when (page) { 0 -> "Folders"; 1 -> "Songs"; else -> "Albums" },
                                    onSearch = { searching = true },
                                    onSettings = { showSettings = true },
                                    onTune = { showTune = true },
                                )
                                if (page == 0) FolderList(vm) { openFolderPath = it.path }
                                else if (page == 2) AlbumGrid(vm, albums) { openAlbumId = it.id }
                                else SongList(vm = vm, songs = vm.songs, title = null, subtitle = null, onBack = null)
                            }
                        }
                    }
                }
            }
            if (vm.currentSong != null) {
                MiniPlayer(vm) { showPlayer = true }
            }
            if (showNav) {
                HealNavBar(tab) { tab = it; openFolderPath = null; openAlbumId = null }
            } else {
                Spacer(Modifier.navigationBarsPadding())
            }
        }

        AnimatedVisibility(
            visible = showPlayer && vm.currentSong != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            NowPlaying(vm) { showPlayer = false }
        }

    }
}

@Composable
private fun HomeHeader(
    title: String,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onTune: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search", Modifier.size(26.dp)) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Menu") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Tone generator") },
                    leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                    onClick = { menu = false; onTune() },
                )
                DropdownMenuItem(
                    text = { Text("Settings") },
                    leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                    onClick = { menu = false; onSettings() },
                )
            }
        }
    }
}

// ───────────────────────── Settings ─────────────────────────

@Composable
private fun SettingsScreen(
    vm: PlayerViewModel,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    palette: Palette,
    onPaletteChange: (Palette) -> Unit,
    onAbout: () -> Unit,
    onSleep: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            SectionLabel("Theme")
            SettingsChoice("Light", Icons.Rounded.LightMode, themeMode == ThemeMode.LIGHT) { onThemeChange(ThemeMode.LIGHT) }
            SettingsChoice("Dark", Icons.Rounded.DarkMode, themeMode == ThemeMode.DARK) { onThemeChange(ThemeMode.DARK) }
            SettingsChoice("System default", Icons.Rounded.SettingsBrightness, themeMode == ThemeMode.SYSTEM) { onThemeChange(ThemeMode.SYSTEM) }

            SectionLabel("Palette")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                for (p in Palette.values()) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(p.color).clickable { onPaletteChange(p) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (p == palette) Icon(Icons.Rounded.Check, p.label, tint = Color.White)
                    }
                }
            }

            SectionLabel("Visualizer mode")
            for (m in VizMode.values()) {
                val icon = when (m) {
                    VizMode.WAVE3D -> Icons.Rounded.ViewInAr
                    VizMode.WAVE2D -> Icons.Rounded.ShowChart
                    VizMode.FREQ -> Icons.Rounded.BarChart
                }
                SettingsChoice(m.label, icon, vm.vizMode == m) { vm.selectViz(m) }
            }

            SectionLabel("Sleep timer")
            Row(
                Modifier.fillMaxWidth().clickable { onSleep() }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Bedtime, null, tint = if (vm.sleepMinutes > 0) HealTeal else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sleep timer")
                    Text(
                        if (vm.sleepMinutes > 0) "Stops in ${fmtRemaining(vm.sleepRemainingMs)}" else "Off",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (vm.sleepMinutes > 0) HealTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionLabel("About")
            Row(
                Modifier.fillMaxWidth().clickable { onAbout() }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text("About app", Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun fmtRemaining(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val sec = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
private fun SleepTimerScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val presets = listOf(0 to "Off", 30 to "30 minutes", 60 to "1 hour", 90 to "1 hour 30 minutes", 120 to "2 hours")
    val isPreset = presets.any { it.first == vm.sleepMinutes }
    var showCustom by remember { mutableStateOf(false) }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (vm.sleepMinutes > 0) {
            Text(
                "Music will stop in ${fmtRemaining(vm.sleepRemainingMs)}",
                color = HealTeal, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }
        Column(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            for ((min, label) in presets) {
                SleepRow(label, vm.sleepMinutes == min) { vm.setSleepTimer(min) }
                HorizontalDivider(Modifier.padding(start = 56.dp, end = 16.dp), color = MaterialTheme.colorScheme.outline)
            }
            SleepRow(
                if (!isPreset) "Custom (${vm.sleepMinutes} min)" else "Custom",
                !isPreset,
            ) { showCustom = true }
        }
    }

    if (showCustom) {
        var text by remember { mutableStateOf(if (!isPreset) vm.sleepMinutes.toString() else "") }
        val minutes = text.toIntOrNull()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text("Custom timer") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(3) },
                    singleLine = true,
                    label = { Text("Minutes (1–720)") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = minutes != null && minutes in 1..720,
                    onClick = { vm.setSleepTimer(minutes!!); showCustom = false },
                ) { Text("Start", color = HealTeal) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showCustom = false }) { Text("Cancel", color = dim) }
            },
        )
    }
}

@Composable
private fun SleepRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(
            selected = selected, onClick = onClick,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = HealTeal),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SettingsChoice(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) HealTeal else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, Modifier.weight(1f), color = if (selected) HealTeal else MaterialTheme.colorScheme.onSurface)
        if (selected) Icon(Icons.Rounded.Check, null, tint = HealTeal)
    }
}

@Composable
private fun HealNavBar(selected: Int, onSelect: (Int) -> Unit) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val items = listOf(
        "Folders" to Icons.Rounded.Folder,
        "Songs" to Icons.Rounded.LibraryMusic,
        "Albums" to Icons.Rounded.Album,
        "Visualizer" to Icons.Rounded.GraphicEq,
    )
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .animateContentSize()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            run {
                items.forEachIndexed { i, (label, icon) ->
                    val sel = i == selected
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(if (sel) HealTeal.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable { onSelect(i) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, label, tint = if (sel) HealTeal else dim)
                        AnimatedVisibility(
                            visible = sel,
                            enter = expandHorizontally() + fadeIn(),
                            exit = shrinkHorizontally() + fadeOut(),
                        ) {
                            Text(
                                label,
                                Modifier.padding(start = 8.dp),
                                color = HealTeal, maxLines = 1, softWrap = false,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualizerScreen(vm: PlayerViewModel) {
    var resetKey by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Visualizer",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            VizModeIcons(vm.vizMode, vm::selectViz)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            VizPanel(
                vm.vizMode, vm.isPlaying,
                Modifier.fillMaxSize(),
                resetKey = resetKey,
                freq3D = vm.freq3D,
            )
            Row(
                Modifier.align(Alignment.BottomEnd).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (vm.vizMode == VizMode.FREQ) {
                    // flip the frequency bars between 2D and 3D
                    Text(
                        if (vm.freq3D) "3D" else "2D",
                        color = HealTeal, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                            .clickable { vm.toggleFreq3D() }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
                // back to the default, perfect view
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                        .clickable { resetKey++ },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.CenterFocusStrong, "Reset view", Modifier.size(22.dp), tint = HealTeal) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.MusicNote, null, Modifier.size(64.dp), tint = HealTeal)
        Spacer(Modifier.height(16.dp))
        Text("Allow access to your music", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Healer needs permission to read the audio files on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("Grant access") }
    }
}

// ───────────────────────── Lists ─────────────────────────

@Composable
private fun FolderList(vm: PlayerViewModel, onOpen: (Folder) -> Unit) {
    when {
        vm.loading -> Centered { CircularProgressIndicator() }
        vm.folders.isEmpty() -> Centered { Text("No audio files found", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(vm.folders, key = { it.path }) { f ->
                FolderRow(f) { onOpen(f) }
            }
        }
    }
}

@Composable
private fun SongList(
    vm: PlayerViewModel,
    songs: List<Song>,
    title: String?,
    subtitle: String?,
    onBack: (() -> Unit)?,
) {
    Column(Modifier.fillMaxSize()) {
        if (title != null && onBack != null) {
            Row(
                Modifier.statusBarsPadding().padding(top = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(
                        subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (vm.loading) {
            Centered { CircularProgressIndicator() }
            return@Column
        }
        if (songs.isEmpty()) {
            Centered { Text("No audio files found", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.play(songs, 0) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play all")
            }
            OutlinedButton(onClick = { vm.shufflePlay(songs) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Shuffle")
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(songs, key = { it.id }) { s ->
                SongRow(s, vm.currentSong?.id == s.id) { vm.play(songs, songs.indexOf(s)) }
            }
        }
    }
}

@Composable
private fun FolderRow(f: Folder, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Folder, null, tint = HealTeal) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(
                "${f.songs.size} song${if (f.songs.size == 1) "" else "s"}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SongRow(s: Song, playing: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(s, 144, Modifier.size(48.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
                color = if (playing) HealTeal else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(fmt(s.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ───────────────────────── Tone generator ─────────────────────────

@Composable
private fun TuneScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    var freq by remember { mutableIntStateOf(ToneGenerator.frequency.toInt().coerceIn(20, 20000)) }
    var wave by remember { mutableStateOf(ToneGenerator.waveform) }
    var volume by remember { mutableFloatStateOf(ToneGenerator.volume) }
    var playing by remember { mutableStateOf(ToneGenerator.isRunning) }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    // never leave a tone running when the screen is closed or the app goes to the background
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) { ToneGenerator.stop(); playing = false }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); ToneGenerator.stop() }
    }

    fun setFreq(f: Int) {
        freq = f.coerceIn(20, 20000)
        ToneGenerator.frequency = freq.toFloat()
    }

    val logSpan = kotlin.math.ln(1000f)   // 20 Hz -> 20000 Hz
    val pos = (kotlin.math.ln(freq / 20f) / logSpan).coerceIn(0f, 1f)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Tune", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Frequency · 20–20000 Hz", style = MaterialTheme.typography.bodySmall, color = dim)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$freq", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = HealTeal)
                Spacer(Modifier.width(8.dp))
                Text("Hz", style = MaterialTheme.typography.titleLarge, color = dim, modifier = Modifier.padding(bottom = 8.dp))
            }

            // preview of the selected waveform
            Canvas(Modifier.fillMaxWidth().height(90.dp).padding(vertical = 8.dp)) {
                val w = size.width
                val h = size.height
                val path = Path()
                val n = 240
                for (i in 0..n) {
                    val t = i / n.toFloat()
                    val p = (t * 3f) % 1f
                    val v = when (wave) {
                        ToneGenerator.Waveform.SINE -> kotlin.math.sin(p * 6.2831855f)
                        ToneGenerator.Waveform.SQUARE -> if (p < 0.5f) 1f else -1f
                        ToneGenerator.Waveform.TRI -> 4f * kotlin.math.abs(p - 0.5f) - 1f
                        ToneGenerator.Waveform.SAW -> 2f * p - 1f
                    }
                    val x = t * w
                    val y = h / 2f - v * h * 0.42f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, HealTeal, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }

            Slider(
                value = pos,
                onValueChange = { setFreq(Math.round(20f * kotlin.math.exp(it * logSpan))) },
                colors = SliderDefaults.colors(
                    thumbColor = HealTeal,
                    activeTrackColor = HealTeal,
                    inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f),
                ),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { setFreq(freq - 1) }) { Icon(Icons.Rounded.Remove, "−1 Hz") }
                for (preset in listOf(100, 440, 1000, 10000)) {
                    Text(
                        if (preset >= 1000) "${preset / 1000}k" else "$preset",
                        color = if (freq == preset) HealTeal else dim,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape)
                            .background(if (freq == preset) HealTeal.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { setFreq(preset) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
                IconButton(onClick = { setFreq(freq + 1) }) { Icon(Icons.Rounded.Add, "+1 Hz") }
            }

            Spacer(Modifier.height(20.dp))
            Text("Waveform", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (wf in ToneGenerator.Waveform.values()) {
                    val sel = wf == wave
                    Text(
                        wf.label,
                        color = if (sel) HealTeal else dim,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.clip(CircleShape)
                            .background(if (sel) HealTeal.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { wave = wf; ToneGenerator.waveform = wf }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Volume", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Slider(
                value = volume,
                onValueChange = { volume = it; ToneGenerator.volume = it },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = HealTeal,
                    activeTrackColor = HealTeal,
                    inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f),
                ),
            )
            Text(
                "Start with a low volume. High or loud tones can be uncomfortable and may harm your hearing.",
                style = MaterialTheme.typography.bodySmall, color = dim,
            )

            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(HealTeal).clickable {
                        if (playing) {
                            ToneGenerator.stop(); playing = false
                        } else {
                            vm.pausePlayback()            // don't mix with the music
                            ToneGenerator.start(); playing = true
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        if (playing) "Stop" else "Play", Modifier.size(40.dp), tint = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ───────────────────────── Albums ─────────────────────────

private data class AlbumGroup(val id: Long, val name: String, val artist: String, val songs: List<Song>)

private fun buildAlbums(songs: List<Song>): List<AlbumGroup> =
    songs.groupBy { it.albumId }
        .map { (id, list) ->
            val first = list.first()
            val artists = list.map { it.artist }.distinct()
            AlbumGroup(
                id = id,
                name = first.album.ifBlank { "Unknown album" },
                artist = if (artists.size == 1) artists[0] else "Various artists",
                songs = list,
            )
        }
        .sortedBy { it.name.lowercase() }

@Composable
private fun AlbumGrid(vm: PlayerViewModel, albums: List<AlbumGroup>, onOpen: (AlbumGroup) -> Unit) {
    when {
        vm.loading -> Centered { CircularProgressIndicator() }
        albums.isEmpty() -> Centered { Text("No albums found", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            gridItems(albums, key = { it.id }) { a ->
                Column(Modifier.clickable { onOpen(a) }) {
                    Artwork(a.songs.first(), 400, Modifier.fillMaxWidth().aspectRatio(1f), corner = 16.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${a.artist} · ${a.songs.size} song${if (a.songs.size == 1) "" else "s"}",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ───────────────────────── About ─────────────────────────

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty() } catch (e: Exception) { "" }
    }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("About", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(width = 120.dp, height = 56.dp)) {
                val mid = size.height / 2f
                drawLine(
                    Color(0xFF4F55B8), Offset(0f, mid), Offset(size.width, mid),
                    strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round,
                )
                val path = Path()
                val steps = 60
                for (i in 0..steps) {
                    val x = size.width * i / steps
                    val y = mid - kotlin.math.sin(i / steps.toFloat() * 6.2831855f * 2f) * mid * 0.85f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, HealTeal, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
            }
            Spacer(Modifier.height(24.dp))
            Text("Healer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (version.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Version $version", color = dim)
            }
            Spacer(Modifier.height(32.dp))
            Text("Developed by", style = MaterialTheme.typography.bodySmall, color = dim)
            Spacer(Modifier.height(2.dp))
            Image(
                painterResource(R.drawable.microintel_logo), "Microintel",
                Modifier.height(44.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

// ───────────────────────── Search ─────────────────────────

@Composable
private fun SearchScreen(vm: PlayerViewModel, onClose: () -> Unit, onOpenFolder: (Folder) -> Unit) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.requestFocus() }

    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val q = vm.query

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            BasicTextField(
                value = q,
                onValueChange = { vm.onQueryChange(it) },
                modifier = Modifier.weight(1f).focusRequester(focus),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(HealTeal),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (q.isEmpty()) Text("Search songs, artists, folders", style = MaterialTheme.typography.titleMedium, color = dim)
                        inner()
                    }
                },
            )
            if (q.isNotEmpty()) {
                IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Rounded.Close, "Clear") }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))

        val songs = vm.foundSongs
        val folders = vm.foundFolders
        when {
            q.isBlank() -> Centered { Text("Type to search", color = dim) }
            songs.isEmpty() && folders.isEmpty() -> Centered { Text("No matches for “$q”", color = dim) }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (folders.isNotEmpty()) {
                    item { SectionLabel("Folders") }
                    items(folders, key = { "f" + it.path }) { f -> FolderRow(f) { onOpenFolder(f) } }
                }
                if (songs.isNotEmpty()) {
                    item { SectionLabel("Songs") }
                    items(songs, key = { "s" + it.id }) { s ->
                        SongRow(s, vm.currentSong?.id == s.id) {
                            focusManager.clearFocus()
                            vm.play(songs, songs.indexOf(s))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

// ───────────────────────── Mini player ─────────────────────────

@Composable
private fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val song = vm.currentSong ?: return
    val progress = if (vm.durationMs > 0) (vm.positionMs.toFloat() / vm.durationMs).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = HealTeal,
            trackColor = Color.Transparent,
        )
        Row(
            Modifier.fillMaxWidth().clickable { onOpen() }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song, 144, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.togglePlay() }) {
                Icon(if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", Modifier.size(30.dp))
            }
            IconButton(onClick = { vm.next() }) { Icon(Icons.Rounded.SkipNext, "Next") }
        }
    }
}

// ───────────────────────── Now playing ─────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlaying(vm: PlayerViewModel, onClose: () -> Unit) {
    val song = vm.currentSong ?: return
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current

    val bg = MaterialTheme.colorScheme.background
    val dark = bg.luminance() < 0.5f
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    // background gradient tinted with the artwork's average colour
    val tint by produceState<Color?>(null, song.id) {
        value = withContext(Dispatchers.IO) {
            loadArt(ctx, song, 144)?.let { b -> Color(Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)) }
        }
    }
    val target = lerp(bg, tint ?: HealTeal, if (dark) 0.5f else 0.35f)
    val bottomColor by animateColorAsState(target, tween(700), label = "tint")

    var menu by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = vm.durationMs.coerceAtLeast(1L).toFloat()
    val shown = (if (dragging) dragValue else vm.positionMs.toFloat()).coerceIn(0f, duration)

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(0f to bg, 0.35f to bg, 1f to bottomColor))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close", Modifier.size(32.dp)) }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Menu") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Restart song") },
                        leadingIcon = { Icon(Icons.Rounded.SkipPrevious, null) },
                        onClick = { vm.seekTo(0); menu = false },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SpinningDisc(song, vm.isPlaying, Modifier.fillMaxWidth(0.8f).aspectRatio(1f))
        Spacer(Modifier.height(18.dp))
        Text(
            song.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
        )
        Spacer(Modifier.height(2.dp))
        Text(song.artist, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)

        Spacer(Modifier.weight(1f))

        Spacer(Modifier.height(8.dp))
        Slider(
            value = shown,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { vm.seekTo(dragValue.toLong()); dragging = false },
            valueRange = 0f..duration,
            colors = SliderDefaults.colors(
                thumbColor = HealTeal,
                activeTrackColor = HealTeal,
                inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f),
            ),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(shown.toLong()), style = MaterialTheme.typography.bodySmall, color = dim)
            Text(fmt(vm.durationMs), style = MaterialTheme.typography.bodySmall, color = dim)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.toggleShuffle() }) {
                Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (vm.shuffle) HealTeal else dim)
            }
            IconButton(onClick = { vm.previous() }, Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(36.dp))
            }
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(HealTeal).clickable { vm.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    "Play/Pause", Modifier.size(40.dp), tint = Color.White,
                )
            }
            IconButton(onClick = { vm.next() }, Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(36.dp))
            }
            IconButton(onClick = { vm.cycleRepeat() }) {
                val on = vm.repeatMode != Player.REPEAT_MODE_OFF
                Icon(
                    if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Repeat", tint = if (on) HealTeal else dim,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ───────────────────────── CD disc ─────────────────────────

@Composable
private fun SpinningDisc(song: Song, isPlaying: Boolean, modifier: Modifier = Modifier) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var last = 0L
            while (true) {
                androidx.compose.runtime.withFrameNanos { now ->
                    if (last != 0L) angle = (angle + (now - last) / 1_000_000_000f * 40f) % 360f
                    last = now
                }
            }
        }
    }
    val hole = MaterialTheme.colorScheme.background
    Box(
        modifier.graphicsLayer { rotationZ = angle }.clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Artwork(song, 600, Modifier.fillMaxSize(), corner = 500.dp)
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f
            // fine grooves like a CD / vinyl
            for (i in 1..5) {
                drawCircle(Color.Black.copy(alpha = 0.10f), r * (0.40f + i * 0.10f), c, style = Stroke(1.dp.toPx()))
            }
            drawCircle(Color.White.copy(alpha = 0.25f), r * 0.2f, c, style = Stroke(6.dp.toPx()))  // inner hub ring
            drawCircle(hole, r * 0.1f, c)                                                          // centre hole
            drawCircle(Color.Black.copy(alpha = 0.35f), r * 0.1f, c, style = Stroke(1.5f.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.35f), r - 1.dp.toPx(), c, style = Stroke(2.dp.toPx())) // rim
        }
    }
}

// ───────────────────────── Artwork ─────────────────────────

private val artCache = LruCache<String, Bitmap>(80)
private val artMissing: MutableSet<String> = ConcurrentHashMap.newKeySet()

private fun loadArt(ctx: Context, song: Song, px: Int): Bitmap? {
    val key = "${song.id}_$px"
    artCache.get(key)?.let { return it }
    if (key in artMissing) return null
    val bmp = try {
        if (Build.VERSION.SDK_INT >= 29) {
            ctx.contentResolver.loadThumbnail(song.uri, Size(px, px), null)
        } else {
            ctx.contentResolver.openInputStream(song.artworkUri)?.use { BitmapFactory.decodeStream(it) }
        }
    } catch (e: Exception) {
        null
    }
    if (bmp != null) artCache.put(key, bmp) else artMissing.add(key)
    return bmp
}

@Composable
private fun Artwork(song: Song?, px: Int, modifier: Modifier, corner: Dp = 10.dp) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(initialValue = null, song?.id, px) {
        value = if (song == null) null else withContext(Dispatchers.IO) { loadArt(ctx, song, px) }
    }
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = HealTeal)
        }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
