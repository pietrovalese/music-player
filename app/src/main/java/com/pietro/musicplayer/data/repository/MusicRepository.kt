package com.pietro.musicplayer.data.repository

import com.pietro.musicplayer.data.db.PlayHistoryDao
import com.pietro.musicplayer.data.db.PlayHistoryEntity
import com.pietro.musicplayer.data.db.PlaylistDao
import com.pietro.musicplayer.data.db.PlaylistEntity
import com.pietro.musicplayer.data.db.PlaylistSongCrossRef
import com.pietro.musicplayer.data.db.PlaylistWithSongs
import com.pietro.musicplayer.data.db.SongDao
import com.pietro.musicplayer.data.db.SongWithPlayCount
import com.pietro.musicplayer.data.mapper.toDomain
import com.pietro.musicplayer.data.mapper.toEntity
import com.pietro.musicplayer.data.scanner.MediaStoreScanner
import com.pietro.musicplayer.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val playHistoryDao: PlayHistoryDao,
    private val scanner: MediaStoreScanner
) {
    // --- Canzoni ---
    val allSongs: Flow<List<Song>> =
        songDao.getAllSongs().map { list -> list.map { it.toDomain() } }

    fun searchSongs(query: String): Flow<List<Song>> =
        songDao.searchSongs(query).map { list -> list.map { it.toDomain() } }

    suspend fun syncLibrary() = withContext(Dispatchers.IO) {
        val deviceSongs = scanner.getAllSongs()
        val existingEntities = songDao.getAllSongsOnce()
        val existingById = existingEntities.associateBy { it.id }
        val activeIds = deviceSongs.mapTo(mutableSetOf()) { it.id }
        val now = System.currentTimeMillis()

        val toUpsert = deviceSongs.mapNotNull { song ->
            val existing = existingById[song.id]
            val effectiveDateAdded = if (song.dateAdded > 0L) song.dateAdded else (existing?.dateAdded ?: now)
            val candidate = song.copy(
                isFavorite = existing?.isFavorite ?: false,
                dateAdded = effectiveDateAdded
            ).toEntity()
            if (existing == null || candidate != existing) candidate else null
        }

        if (toUpsert.isNotEmpty()) {
            songDao.upsertSongs(toUpsert)
        }

        val deletedIds = existingEntities
            .asSequence()
            .map { it.id }
            .filter { it !in activeIds }
            .toList()

        if (deletedIds.isNotEmpty()) {
            deletedIds.chunked(500).forEach { chunk ->
                songDao.deleteSongsByIds(chunk)
            }
        }
    }

    suspend fun setFavorite(songId: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        songDao.setFavorite(songId, isFavorite)
    }

    // --- Playlist ---
    val allPlaylists: Flow<List<PlaylistWithSongs>> =
        playlistDao.getAllPlaylistsWithSongs()

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun deletePlaylist(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId))
    }

    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) = withContext(Dispatchers.IO) {
        if (songIds.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        playlistDao.addSongsToPlaylist(
            songIds.map { songId ->
                PlaylistSongCrossRef(playlistId = playlistId, songId = songId, addedAt = now)
            }
        )
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        playlistDao.removeSongFromPlaylist(PlaylistSongCrossRef(playlistId, songId))
    }

    // --- Storico ---
    val allPlayHistory: Flow<List<PlayHistoryEntity>> =
        playHistoryDao.getAllPlayHistory()

    fun getRecentSongIds(limit: Int = 30): Flow<List<Long>> =
        playHistoryDao.getRecentSongIds(limit)

    fun getTopSongsWithCount(
        from: Long = 0L,
        to: Long = Long.MAX_VALUE,
        limit: Int = 20
    ): Flow<List<SongWithPlayCount>> =
        playHistoryDao.getTopSongs(from, to, limit)

    val topSongs: Flow<List<Song>> =
        playHistoryDao.getTopSongs(from = 0L, to = Long.MAX_VALUE, limit = 10)
            .map { list -> list.map { it.song.toDomain() } }

    suspend fun recordPlay(songId: Long, completionPercent: Int = 100) = withContext(Dispatchers.IO) {
        playHistoryDao.recordPlay(
            PlayHistoryEntity(songId = songId, completionPercent = completionPercent)
        )
    }
}
