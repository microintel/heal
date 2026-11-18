package com.microintel.healplayer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Posts a short "heal by music" quote notification every time the app is opened. */
object QuoteNotifier {

    private const val CHANNEL = "heal_quotes"
    private const val NOTIFICATION_ID = 1001
    private const val PREFS = "heal_prefs"
    private const val KEY_LAST = "last_quote"
    private const val KEY_CACHE = "quotes_cache"
    private const val QUOTES_URL = "https://microintel.github.io/heal/quotes.json"

    private val fallbackQuotes = listOf(
        "Let the music carry what words can't.",
        "A quiet song can quiet a restless mind.",
        "Breathe in, press play, let go.",
        "Healing often starts with a single note.",
        "Some feelings only a melody can reach.",
        "Slow down. Let the rhythm settle you.",
        "Music turns heavy hours into gentle ones.",
        "Your heartbeat was the first rhythm you ever knew.",
        "Sit with a song. Let it sit with you.",
        "Every tune is a small place to rest.",
        "Silence rests the ears, music rests the heart.",
        "Let each beat loosen what you've been holding.",
        "Play something kind to yourself today.",
        "Calm is only a playlist away.",
        "Melody mends what hurry breaks.",
        "Listen deeply. Feel gently. Heal slowly.",
        "A good song is a short vacation for the mind.",
        "Let the music be your pause button.",
        "Tension fades when the tempo slows.",
        "Hum along. Your body knows how to unwind.",
        "Soft sounds, softer thoughts.",
        "When the day is loud, choose a gentler sound.",
        "Rhythm reminds us that everything flows.",
        "Close your eyes. Let the music do the rest.",
    )

    /** Fetches the latest quotes online (cached for offline use) and shows a random one. */
    fun show(context: Context) {
        val ctx = context.applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        Thread {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            var list = fetchQuotes()
            if (list.isNotEmpty()) {
                prefs.edit().putString(KEY_CACHE, JSONArray(list).toString()).apply()
            } else {
                list = parse(prefs.getString(KEY_CACHE, null))   // offline: last downloaded copy
            }
            if (list.isEmpty()) list = fallbackQuotes             // never fetched: built-in quotes
            post(ctx, list)
        }.start()
    }

    private fun fetchQuotes(): List<String> = try {
        val conn = URL(QUOTES_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        try {
            if (conn.responseCode == 200) {
                parse(conn.inputStream.bufferedReader().use { it.readText() })
            } else emptyList()
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** Accepts ["quote", ...] (also [{"quote"|"text": "..."}]). */
    private fun parse(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val item = arr.get(i)
                val text = when (item) {
                    is String -> item
                    is JSONObject -> item.optString("quote", item.optString("text", ""))
                    else -> ""
                }.trim()
                if (text.isNotEmpty()) out.add(text.take(220))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun post(ctx: Context, quotes: List<String>) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Heal quotes", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "A short healing-through-music thought when you open the app"
                }
            )
        }

        // random quote, never the same one twice in a row
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getInt(KEY_LAST, -1)
        var i = quotes.indices.random()
        if (quotes.size > 1 && i == last) i = (i + 1) % quotes.size
        prefs.edit().putInt(KEY_LAST, i).apply()

        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_heal)
            .setColor(0xFF0D9488.toInt())
            .setContentTitle("Heal")
            .setContentText(quotes[i])
            .setStyle(NotificationCompat.BigTextStyle().bigText(quotes[i]))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(10_000)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            nm.notify(NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
            // permission revoked – ignore
        }
    }
}
