package com.pietro.musicplayer.data.mapper

import android.content.ContentUris
import android.net.Uri
import androidx.core.net.toUri
import com.pietro.musicplayer.data.db.SongEntity
import com.pietro.musicplayer.domain.model.Song

fun SongEntity.toDomain(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    uri = uri.toUri(),
    albumArtUri = if (albumId > 0L) ContentUris.withAppendedId(
        Uri.parse("content://media/external/audio/albumart"), albumId
    ) else null,
    albumId = albumId,
    genre = genre,
    year = year,
    trackNumber = trackNumber,
    isFavorite = isFavorite,
    dateAdded = dateAdded
)

fun Song.toEntity(): SongEntity = SongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    duration = duration,
    uri = uri.toString(),
    genre = genre,
    year = year,
    trackNumber = trackNumber,
    isFavorite = isFavorite,
    dateAdded = if (dateAdded > 0L) dateAdded else System.currentTimeMillis()
)
