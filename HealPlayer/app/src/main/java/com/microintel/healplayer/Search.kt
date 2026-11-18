package com.microintel.healplayer

import java.io.File
import java.text.Normalizer

/**
 * In-memory search index.
 *
 * - Text is normalised once (lower-case, accents stripped, punctuation -> space).
 * - Query is split into tokens; every token must match (AND).
 * - Each token is scored per field: exact word 100 > word prefix 80 > substring 50
 *   > typo-tolerant fuzzy (Damerau/OSA edit distance on words) 40..16.
 * - Fields are weighted: title 3.0, artist 2.0, album 1.5, folder 1.0.
 * - Bonus when the whole query is a prefix of the title; ties -> shorter title first.
 */
class SearchIndex(songs: List<Song>, folders: List<Folder>) {

    private class Field(text: String) {
        val full: String = normalize(text)
        val words: List<String> = if (full.isEmpty()) emptyList() else full.split(' ')
    }

    private class SongDoc(val song: Song) {
        val title = Field(song.title)
        val artist = Field(song.artist)
        val album = Field(song.album)
        val folder = Field(File(song.path).parentFile?.name.orEmpty())
    }

    private class FolderDoc(val folder: Folder) {
        val name = Field(folder.name)
    }

    private val songDocs = songs.map { SongDoc(it) }
    private val folderDocs = folders.map { FolderDoc(it) }

    fun searchSongs(query: String, limit: Int = 200): List<Song> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        val q = tokens.joinToString(" ")
        val scored = ArrayList<Pair<Song, Double>>()
        for (d in songDocs) {
            var total = 0.0
            var ok = true
            for (t in tokens) {
                val s = maxOf(
                    fieldScore(t, d.title) * 3.0,
                    fieldScore(t, d.artist) * 2.0,
                    fieldScore(t, d.album) * 1.5,
                    fieldScore(t, d.folder) * 1.0,
                )
                if (s <= 0.0) { ok = false; break }
                total += s
            }
            if (!ok) continue
            if (d.title.full.startsWith(q)) total += 150.0
            else if (d.title.full.contains(q)) total += 60.0
            total -= d.title.full.length * 0.05 // prefer shorter (closer) titles
            scored.add(d.song to total)
        }
        return scored.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    fun searchFolders(query: String, limit: Int = 5): List<Folder> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<Folder, Double>>()
        for (d in folderDocs) {
            var total = 0.0
            var ok = true
            for (t in tokens) {
                val s = fieldScore(t, d.name)
                if (s <= 0) { ok = false; break }
                total += s
            }
            if (ok) scored.add(d.folder to total)
        }
        return scored.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    // ---- scoring ----

    private fun fieldScore(token: String, f: Field): Int {
        if (f.words.isEmpty()) return 0
        var best = 0
        for ((i, w) in f.words.withIndex()) {
            val s = when {
                w == token -> 100
                w.startsWith(token) -> 80
                else -> 0
            }
            if (s > 0) {
                best = maxOf(best, if (i == 0) s + 10 else s)
                if (best >= 110) return best
            }
        }
        if (best > 0) return best
        if (f.full.contains(token)) return 50
        return fuzzy(token, f.words)
    }

    private fun fuzzy(token: String, words: List<String>): Int {
        val len = token.length
        val maxEdit = when {
            len <= 3 -> 0
            len <= 6 -> 1
            else -> 2
        }
        if (maxEdit == 0) return 0
        var best = Int.MAX_VALUE
        for (w in words) {
            if (w.length + maxEdit < len) continue
            val d = minOf(osa(token, w), osa(token, w.take(len))) // 2nd: typo while still typing
            if (d < best) best = d
            if (best == 0) break
        }
        return if (best <= maxEdit) 40 - 12 * best else 0
    }

    /** Optimal string alignment distance (Levenshtein + adjacent transposition). */
    private fun osa(a: String, b: String): Int {
        if (a == b) return 0
        val n = a.length
        val m = b.length
        if (n == 0) return m
        if (m == 0) return n
        var prev2 = IntArray(m + 1)
        var prev = IntArray(m + 1) { it }
        var cur = IntArray(m + 1)
        for (i in 1..n) {
            cur[0] = i
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = minOf(v, prev2[j - 2] + 1)
                }
                cur[j] = v
            }
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[m]
    }

    companion object {
        fun normalize(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase()
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                .trim()

        fun tokenize(q: String): List<String> =
            normalize(q).split(' ').filter { it.isNotEmpty() }.distinct()
    }
}
