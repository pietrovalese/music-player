package com.pietro.musicplayer.data.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.pietro.musicplayer.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val hasGenreColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val legacyGenreMap = if (!hasGenreColumn) loadLegacyGenresMap() else emptyMap()

        val baseProjection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED
        )
        if (hasGenreColumn) {
            baseProjection.add(MediaStore.Audio.Media.GENRE)
        }

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                baseProjection.toTypedArray(),
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val genreCol = if (hasGenreColumn) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                } else -1

                val fallbackNow = System.currentTimeMillis()
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val albumId = cursor.getLong(albumIdCol)

                    val songUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val albumArtUri = if (albumId > 0L) {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"), albumId
                        )
                    } else null

                    val rawTrack = cursor.getInt(trackCol)
                    val normalizedTrack = (rawTrack % 1000).takeIf { it > 0 }
                    val rawDateAdded = cursor.getLong(dateAddedCol)
                    val dateAddedMs = if (rawDateAdded > 0L) rawDateAdded * 1000L else fallbackNow

                    val genre = if (genreCol >= 0) {
                        cursor.getString(genreCol)?.takeIf { it.isNotBlank() }
                    } else {
                        legacyGenreMap[id]
                    }

                    songs.add(
                        Song(
                            id = id,
                            title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Sconosciuto",
                            artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                                ?: "Artista sconosciuto",
                            album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                                ?: "Album sconosciuto",
                            duration = cursor.getLong(durationCol).coerceAtLeast(0L),
                            uri = songUri,
                            albumArtUri = albumArtUri,
                            albumId = albumId,
                            genre = genre,
                            trackNumber = normalizedTrack,
                            year = cursor.getInt(yearCol).takeIf { it > 0 },
                            dateAdded = dateAddedMs
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permesso non ancora concesso
        }
        songs
    }

    private fun loadLegacyGenresMap(): Map<Long, String> {
        val audioToGenre = mutableMapOf<Long, String>()
        try {
            val genreProjection = arrayOf(
                MediaStore.Audio.Genres._ID,
                MediaStore.Audio.Genres.NAME
            )
            context.contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                genreProjection,
                null,
                null,
                null
            )?.use { genreCursor ->
                val genreIdCol = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
                val genreNameCol = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
                while (genreCursor.moveToNext()) {
                    val genreId = genreCursor.getLong(genreIdCol)
                    val genreName = genreCursor.getString(genreNameCol)?.takeIf { it.isNotBlank() } ?: continue
                    val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                    context.contentResolver.query(
                        membersUri,
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null,
                        null,
                        null
                    )?.use { membersCursor ->
                        val audioIdCol = membersCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID)
                        while (membersCursor.moveToNext()) {
                            audioToGenre[membersCursor.getLong(audioIdCol)] = genreName
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignora errori sui dispositivi con MediaStore non standard
        }
        return audioToGenre
    }
}
