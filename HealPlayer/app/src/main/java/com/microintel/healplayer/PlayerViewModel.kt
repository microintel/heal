package com.microintel.healplayer

import android.app.Application
import android.content.ComponentName
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    // Library
    var songs by mutableStateOf<List<Song>>(emptyList()); private set
    var folders by mutableStateOf<List<Folder>>(emptyList()); private set
    var loading by mutableStateOf(false); private set

    // Playback state
    var currentSong by mutableStateOf<Song?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var positionMs by mutableStateOf(0L); private set
    var durationMs by mutableStateOf(0L); private set
    var shuffle by mutableStateOf(false); private set
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF); private set

    // Visualizer
    var vizMode by mutableStateOf(
        try {
            VizMode.valueOf(app.getSharedPreferences("heal_prefs", 0).getString("viz_mode", null) ?: "WAVE3D")
        } catch (e: Exception) { VizMode.WAVE3D }
    ); private set
    fun selectViz(m: VizMode) {
        vizMode = m
        getApplication<Application>().getSharedPreferences("heal_prefs", 0).edit().putString("viz_mode", m.name).apply()
    }

    fun pausePlayback() { controller?.pause() }

    // Frequencies: 3D bars (true) or flat 2D bars (false)
    var freq3D by mutableStateOf(app.getSharedPreferences("heal_prefs", 0).getBoolean("freq_3d", true)); private set
    fun toggleFreq3D() {
        freq3D = !freq3D
        getApplication<Application>().getSharedPreferences("heal_prefs", 0).edit().putBoolean("freq_3d", freq3D).apply()
    }

    // Sleep timer
    var sleepMinutes by mutableIntStateOf(0); private set          // 0 = off
    var sleepRemainingMs by mutableLongStateOf(0L); private set
    private var sleepJob: Job? = null

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepMinutes = minutes.coerceAtLeast(0)
        if (minutes <= 0) { sleepRemainingMs = 0L; return }
        val end = SystemClock.elapsedRealtime() + minutes * 60_000L
        sleepRemainingMs = minutes * 60_000L
        sleepJob = viewModelScope.launch {
            while (true) {
                val rem = end - SystemClock.elapsedRealtime()
                if (rem <= 0L) {
                    controller?.pause()
                    sleepMinutes = 0
                    sleepRemainingMs = 0L
                    break
                }
                sleepRemainingMs = rem
                delay(minOf(1000L, rem))
            }
        }
    }

    // Search
    var query by mutableStateOf(""); private set
    var foundSongs by mutableStateOf<List<Song>>(emptyList()); private set
    var foundFolders by mutableStateOf<List<Folder>>(emptyList()); private set
    private var index: SearchIndex? = null
    private var searchJob: Job? = null

    private var controller: MediaController? = null
    private var currentId: String? = null

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = sync()
            })
            sync()
        }, ContextCompat.getMainExecutor(app))

        // Progress ticker for the seek bar.
        viewModelScope.launch {
            while (true) {
                delay(300)
                controller?.let { positionMs = it.currentPosition.coerceAtLeast(0L) }
            }
        }
    }

    fun loadLibrary() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { AudioRepository.loadSongs(getApplication()) }
            val grouped = withContext(Dispatchers.Default) { AudioRepository.groupByFolder(list) }
            index = withContext(Dispatchers.Default) { SearchIndex(list, grouped) }
            songs = list
            folders = grouped
            loading = false
            sync()
        }
    }

    fun onQueryChange(q: String) {
        query = q
        searchJob?.cancel()
        if (q.isBlank()) {
            foundSongs = emptyList()
            foundFolders = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(100) // debounce while typing
            val idx = index ?: return@launch
            val (s, f) = withContext(Dispatchers.Default) { idx.searchSongs(q) to idx.searchFolders(q) }
            foundSongs = s
            foundFolders = f
        }
    }

    private fun sync() {
        val c = controller ?: return
        isPlaying = c.isPlaying
        shuffle = c.shuffleModeEnabled
        repeatMode = c.repeatMode
        durationMs = c.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
        positionMs = c.currentPosition.coerceAtLeast(0L)
        currentId = c.currentMediaItem?.mediaId
        currentSong = currentId?.let { id -> songs.firstOrNull { it.id.toString() == id } }
    }

    // ---- Actions ----

    /** Start playing [list] from [index]. */
    fun play(list: List<Song>, index: Int, shuffled: Boolean = false) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.setMediaItems(list.map { it.toMediaItem() }, index.coerceIn(0, list.lastIndex), 0L)
        c.shuffleModeEnabled = shuffled
        c.prepare()
        c.play()
    }

    fun shufflePlay(list: List<Song>) {
        if (list.isEmpty()) return
        play(list, list.indices.random(), shuffled = true)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        positionMs = ms
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun onCleared() {
        controller?.release()
        controller = null
        super.onCleared()
    }
}
