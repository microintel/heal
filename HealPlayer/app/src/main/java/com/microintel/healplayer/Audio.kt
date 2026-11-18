package com.microintel.healplayer

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val path: String,
) {
    val uri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    val artworkUri: Uri
        get() = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"), albumId
        )

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri)
                .build()
        )
        .build()
}

data class Folder(
    val path: String,
    val name: String,
    val songs: List<Song>,
)

object AudioRepository {

    /** Reads every audio file the system has indexed (MediaStore). */
    fun loadSongs(context: Context): List<Song> {
        val resolver: ContentResolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
        )
        // Skip ringtones / notification sounds / alarms and very short clips.
        val selection = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND " +
            "${MediaStore.Audio.Media.IS_ALARM} = 0 AND " +
            "${MediaStore.Audio.Media.DURATION} >= 10000"

        val result = ArrayList<Song>()
        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) {
                val artist = c.getString(artistCol).orEmpty()
                result.add(
                    Song(
                        id = c.getLong(idCol),
                        title = c.getString(titleCol).orEmpty().ifBlank { "Unknown" },
                        artist = if (artist == "<unknown>" || artist.isBlank()) "Unknown artist" else artist,
                        album = c.getString(albumCol).orEmpty(),
                        albumId = c.getLong(albumIdCol),
                        durationMs = c.getLong(durCol),
                        path = c.getString(dataCol).orEmpty(),
                    )
                )
            }
        }
        return result
    }

    /** Groups songs by the directory they live in. */
    fun groupByFolder(songs: List<Song>): List<Folder> =
        songs.groupBy { File(it.path).parent ?: "/" }
            .map { (dir, list) ->
                Folder(
                    path = dir,
                    name = File(dir).name.ifBlank { "Root" },
                    songs = list,
                )
            }
            .sortedBy { it.name.lowercase() }
}
