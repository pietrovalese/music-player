package com.pietro.musicplayer.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {
    @Insert
    suspend fun recordPlay(entry: PlayHistoryEntity)

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC")
    fun getAllPlayHistory(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT songId FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentSongIds(limit: Int = 30): Flow<List<Long>>

    @Query("""
        SELECT songs.*, COUNT(play_history.id) as playCount
        FROM songs JOIN play_history ON songs.id = play_history.songId
        WHERE play_history.playedAt BETWEEN :from AND :to
        GROUP BY songs.id ORDER BY playCount DESC LIMIT :limit
    """)
    fun getTopSongs(from: Long, to: Long, limit: Int = 10): Flow<List<SongWithPlayCount>>

    @Query("SELECT COUNT(*) FROM play_history WHERE playedAt BETWEEN :from AND :to")
    fun getTotalPlays(from: Long, to: Long): Flow<Int>

    @Query("SELECT SUM(s.duration) FROM play_history ph JOIN songs s ON ph.songId = s.id WHERE ph.playedAt BETWEEN :from AND :to")
    fun getTotalListeningTime(from: Long, to: Long): Flow<Long?>
}
