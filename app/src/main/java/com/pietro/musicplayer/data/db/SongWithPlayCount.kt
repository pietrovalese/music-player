package com.pietro.musicplayer.data.db

import androidx.room.Embedded

data class SongWithPlayCount(
    @Embedded val song: SongEntity,
    val playCount: Int
)