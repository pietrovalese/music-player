package com.pietro.musicplayer.domain.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val albumArtUri: Uri?,
    val albumId: Long = 0L,
    val genre: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val isFavorite: Boolean = false,
    val dateAdded: Long = 0L
)
