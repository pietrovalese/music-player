package com.pietro.musicplayer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val uri: String,
    val genre: String?,
    val year: Int?,
    val trackNumber: Int?,
    val isFavorite: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis()
)
