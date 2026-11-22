package com.pietro.musicplayer.ui.library

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pietro.musicplayer.data.db.PlayHistoryEntity
import com.pietro.musicplayer.data.db.PlaylistEntity
import com.pietro.musicplayer.data.db.PlaylistWithSongs
import com.pietro.musicplayer.data.mapper.toDomain
import com.pietro.musicplayer.data.repository.MusicRepository
import com.pietro.musicplayer.domain.model.Song
import com.pietro.musicplayer.domain.recommendation.RecommendationEngine
import com.pietro.musicplayer.domain.recommendation.RecommendationSnapshot
import com.pietro.musicplayer.domain.recommendation.SmartPlaylist
import com.pietro.musicplayer.domain.recommendation.WrappedAvailability
import com.pietro.musicplayer.domain.recommendation.WrappedStats
import com.pietro.musicplayer.player.PlayerController
import com.pietro.musicplayer.update.AppUpdateManager
import com.pietro.musicplayer.update.ReleaseInfo
import com.pietro.musicplayer.update.UpdateUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@Immutable
data class Album(
    val id: String,
    val albumId: Long,
    val name: String,
    val artist: String,
    val albumArtUri: Uri?,
    val songCount: Int,
    val totalDuration: Long,
    val songs: List<Song>
)

@Immutable
data class Artist(
    val name: String,
    val albumCount: Int,
    val songCount: Int,
    val totalDuration: Long,
    val artworkUri: Uri?,
    val songs: List<Song>
)

@Immutable
data class Genre(
    val name: String,
    val songCount: Int,
    val totalDuration: Long,
    val artworkUri: Uri?,
    val songs: List<Song>
)

@Immutable
private data class LibraryCatalog(
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val genres: List<Genre> = emptyList()
)

enum class LibraryFilter(val label: String) {
    ALL("Tutto"),
    PLAYLISTS("Playlist"),
    ARTISTS("Artisti"),
    ALBUMS("Album"),
    SONGS("Brani"),
    FAVORITES("Preferiti")
}

enum class LibrarySortOrder(val label: String) {
    RECENT("Recenti"),
    LAST_ADDED("Ultime aggiunte"),
    ALPHABETICAL("Ordine alfabetico"),
    ARTIST("Artista"),
    DURATION("Durata")
}

@OptIn(FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val playerController: PlayerController,
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _librarySearchQuery = MutableStateFlow("")
    val librarySearchQuery: StateFlow<String> = _librarySearchQuery.asStateFlow()

    private val _libraryFilter = MutableStateFlow(LibraryFilter.ALL)
    val libraryFilter: StateFlow<LibraryFilter> = _libraryFilter.asStateFlow()

    private val _librarySortOrder = MutableStateFlow(LibrarySortOrder.ALPHABETICAL)
    val librarySortOrder: StateFlow<LibrarySortOrder> = _librarySortOrder.asStateFlow()

    private val _isLibraryGridView = MutableStateFlow(false)
    val isLibraryGridView: StateFlow<Boolean> = _isLibraryGridView.asStateFlow()

    private val _wrappedAvailability = MutableStateFlow(RecommendationEngine.evaluateWrappedAvailability())
    val wrappedAvailability: StateFlow<WrappedAvailability> = _wrappedAvailability.asStateFlow()

    private val _hasSyncedOnce = MutableStateFlow(false)

    // Flussi condivisi tenuti attivi (Eagerly) per evitare stop/ricalcoli su navigazione
    val allSongs: StateFlow<List<Song>> =
        repository.allSongs
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allPlayHistory: StateFlow<List<PlayHistoryEntity>> =
        repository.allPlayHistory
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val playlists: StateFlow<List<PlaylistWithSongs>> =
        repository.allPlaylists
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val songById: StateFlow<Map<Long, Song>> =
        allSongs
            .map { list -> list.associateBy { it.id } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val favoriteSongIds: StateFlow<Set<Long>> =
        allSongs
            .map { list ->
                list.asSequence()
                    .filter { it.isFavorite }
                    .map { it.id }
                    .toSet()
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val songs: StateFlow<List<Song>> =
        combine(allSongs, _searchQuery) { list, query ->
            if (query.isBlank()) list
            else list.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true) ||
                    (it.genre?.contains(query, ignoreCase = true) == true)
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteSongs: StateFlow<List<Song>> =
        allSongs
            .map { list -> list.filter { it.isFavorite } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val libraryCatalog: StateFlow<LibraryCatalog> =
        allSongs
            .distinctUntilChanged { old, new ->
                old.size == new.size && old.indices.all { i ->
                    val a = old[i]
                    val b = new[i]
                    a.id == b.id &&
                        a.title == b.title &&
                        a.artist == b.artist &&
                        a.album == b.album &&
                        a.albumId == b.albumId &&
                        a.genre == b.genre &&
                        a.trackNumber == b.trackNumber &&
                        a.duration == b.duration &&
                        a.dateAdded == b.dateAdded
                }
            }
            .map { list -> buildLibraryCatalog(list) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, LibraryCatalog())

    val albums: StateFlow<List<Album>> =
        libraryCatalog
            .map { it.albums }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<Artist>> =
        libraryCatalog
            .map { it.artists }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val genres: StateFlow<List<Genre>> =
        libraryCatalog
            .map { it.genres }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val recommendationSnapshot: StateFlow<RecommendationSnapshot> =
        combine(
            allSongs,
            allPlayHistory.debounce(500L)
        ) { songsList, history ->
            RecommendationEngine.computeSnapshot(songsList, history)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, RecommendationSnapshot())

    val recentSongs: StateFlow<List<Song>> =
        recommendationSnapshot
            .map { it.recentSongs }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val topSongs: StateFlow<List<Song>> =
        recommendationSnapshot
            .map { it.topSongs }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dailyMixes: StateFlow<List<SmartPlaylist>> =
        recommendationSnapshot
            .map { it.dailyMixes }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val madeForYou: StateFlow<List<SmartPlaylist>> =
        recommendationSnapshot
            .map { it.madeForYou }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val becauseYouListened: StateFlow<Pair<String, List<Song>>?> =
        recommendationSnapshot
            .map { it.becauseYouListened }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ─── Flussi Libreria filtrati e ordinati su Dispatchers.Default ──────────

    private val recentOrderMapFlow: StateFlow<Map<Long, Int>> =
        recentSongs
            .map { list -> list.mapIndexed { index, song -> song.id to index }.toMap() }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val sortedLibrarySongs: StateFlow<List<Song>> =
        combine(allSongs, _librarySortOrder, recentOrderMapFlow, _librarySearchQuery) { list, order, recentMap, query ->
            val filtered = if (query.isBlank()) list
            else list.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true)
            }
            sortSongs(filtered, order, recentMap)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sortedLibraryFavorites: StateFlow<List<Song>> =
        combine(favoriteSongs, _librarySortOrder, recentOrderMapFlow, _librarySearchQuery) { list, order, recentMap, query ->
            val filtered = if (query.isBlank()) list
            else list.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true)
            }
            sortSongs(filtered, order, recentMap)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sortedLibraryPlaylists: StateFlow<List<PlaylistWithSongs>> =
        combine(playlists, _librarySortOrder, _librarySearchQuery) { list, order, query ->
            val filtered = if (query.isBlank()) list
            else list.filter { it.playlist.name.contains(query, ignoreCase = true) }
            when (order) {
                LibrarySortOrder.RECENT, LibrarySortOrder.LAST_ADDED ->
                    filtered.sortedByDescending { it.playlist.createdAt }
                LibrarySortOrder.ALPHABETICAL, LibrarySortOrder.ARTIST ->
                    filtered.sortedBy { it.playlist.name.lowercase() }
                LibrarySortOrder.DURATION ->
                    filtered.sortedByDescending { p -> p.songs.sumOf { it.duration } }
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sortedLibraryArtists: StateFlow<List<Artist>> =
        combine(artists, _librarySortOrder, recentOrderMapFlow, _librarySearchQuery) { list, order, recentMap, query ->
            val filtered = if (query.isBlank()) list
            else list.filter { it.name.contains(query, ignoreCase = true) }
            when (order) {
                LibrarySortOrder.RECENT -> filtered.sortedBy { artist ->
                    artist.songs.minOfOrNull { recentMap[it.id] ?: Int.MAX_VALUE } ?: Int.MAX_VALUE
                }
                LibrarySortOrder.LAST_ADDED -> filtered.sortedByDescending { artist ->
                    artist.songs.maxOfOrNull { it.dateAdded } ?: 0L
                }
                LibrarySortOrder.ALPHABETICAL, LibrarySortOrder.ARTIST ->
                    filtered.sortedBy { it.name.lowercase() }
                LibrarySortOrder.DURATION ->
                    filtered.sortedByDescending { it.totalDuration }
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sortedLibraryAlbums: StateFlow<List<Album>> =
        combine(albums, _librarySortOrder, recentOrderMapFlow, _librarySearchQuery) { list, order, recentMap, query ->
            val filtered = if (query.isBlank()) list
            else list.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true)
            }
            when (order) {
                LibrarySortOrder.RECENT -> filtered.sortedBy { album ->
                    album.songs.minOfOrNull { recentMap[it.id] ?: Int.MAX_VALUE } ?: Int.MAX_VALUE
                }
                LibrarySortOrder.LAST_ADDED -> filtered.sortedByDescending { album ->
                    album.songs.maxOfOrNull { it.dateAdded } ?: 0L
                }
                LibrarySortOrder.ALPHABETICAL -> filtered.sortedBy { it.name.lowercase() }
                LibrarySortOrder.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
                LibrarySortOrder.DURATION -> filtered.sortedByDescending { it.totalDuration }
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val wrappedStats: StateFlow<WrappedStats> =
        combine(allSongs, allPlayHistory, _wrappedAvailability) { songsList, history, availability ->
            when (availability) {
                WrappedAvailability.Hidden -> RecommendationEngine.emptyWrappedStats()
                is WrappedAvailability.Monthly -> RecommendationEngine.computeWrappedStats(
                    allSongs = songsList,
                    history = history,
                    fromTimestamp = availability.fromTimestamp,
                    toTimestamp = availability.toTimestamp
                )
                is WrappedAvailability.Yearly -> RecommendationEngine.computeWrappedStats(
                    allSongs = songsList,
                    history = history,
                    fromTimestamp = availability.fromTimestamp,
                    toTimestamp = availability.toTimestamp
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                RecommendationEngine.emptyWrappedStats()
            )

    val playerState = playerController.playerState
    val playbackPosition: StateFlow<Long> = playerController.playbackPosition

    val updateState: StateFlow<UpdateUiState> = appUpdateManager.updateState
    val githubRepo: StateFlow<String> = appUpdateManager.githubRepo
    val autoCheckUpdates: StateFlow<Boolean> = appUpdateManager.autoCheckUpdates
    val currentAppVersion: String = appUpdateManager.getCurrentVersionInfo().first

    init {
        viewModelScope.launch {
            playerController.playedSongEvents.collect { songId ->
                repository.recordPlay(songId)
            }
        }
    }

    private fun sortSongs(
        songs: List<Song>,
        order: LibrarySortOrder,
        recentMap: Map<Long, Int>
    ): List<Song> = when (order) {
        LibrarySortOrder.RECENT -> songs.sortedBy { recentMap[it.id] ?: Int.MAX_VALUE }
        LibrarySortOrder.LAST_ADDED -> songs.sortedByDescending { it.dateAdded }
        LibrarySortOrder.ALPHABETICAL -> songs.sortedBy { it.title.lowercase() }
        LibrarySortOrder.ARTIST -> songs.sortedBy { it.artist.lowercase() }
        LibrarySortOrder.DURATION -> songs.sortedByDescending { it.duration }
    }

    private fun buildLibraryCatalog(allSongs: List<Song>): LibraryCatalog {
        if (allSongs.isEmpty()) return LibraryCatalog()

        val albumsMap = HashMap<String, MutableList<Song>>()
        val artistsMap = HashMap<String, MutableList<Song>>()
        val genresMap = HashMap<String, MutableList<Song>>()

        for (song in allSongs) {
            val albumKey = if (song.albumId > 0L) {
                "id_${song.albumId}"
            } else {
                "name_${song.album.lowercase()}_${song.artist.lowercase()}"
            }
            albumsMap.getOrPut(albumKey) { mutableListOf() }.add(song)
            artistsMap.getOrPut(song.artist) { mutableListOf() }.add(song)
            val genre = song.genre?.trim()
            if (!genre.isNullOrBlank()) {
                genresMap.getOrPut(genre) { mutableListOf() }.add(song)
            }
        }

        val albums = albumsMap.entries
            .map { (groupKey, albumSongs) ->
                val first = albumSongs.first()
                val sortedSongs = albumSongs.sortedWith(
                    compareBy<Song> { it.trackNumber ?: Int.MAX_VALUE }
                        .thenBy { it.title }
                )
                var totalDuration = 0L
                var artUri: Uri? = null
                for (s in sortedSongs) {
                    totalDuration += s.duration
                    if (artUri == null && s.albumArtUri != null) {
                        artUri = s.albumArtUri
                    }
                }
                Album(
                    id = groupKey,
                    albumId = first.albumId,
                    name = first.album,
                    artist = first.artist,
                    albumArtUri = artUri,
                    songCount = sortedSongs.size,
                    totalDuration = totalDuration,
                    songs = sortedSongs
                )
            }
            .sortedBy { it.name.lowercase() }

        val artists = artistsMap.entries
            .map { (artistName, artistSongs) ->
                val sortedSongs = artistSongs.sortedBy { it.title.lowercase() }
                val albumKeys = HashSet<String>(sortedSongs.size)
                var totalDuration = 0L
                var artUri: Uri? = null
                for (s in sortedSongs) {
                    albumKeys.add(if (s.albumId > 0L) s.albumId.toString() else s.album)
                    totalDuration += s.duration
                    if (artUri == null && s.albumArtUri != null) {
                        artUri = s.albumArtUri
                    }
                }
                Artist(
                    name = artistName,
                    albumCount = albumKeys.size,
                    songCount = sortedSongs.size,
                    totalDuration = totalDuration,
                    artworkUri = artUri,
                    songs = sortedSongs
                )
            }
            .sortedBy { it.name.lowercase() }

        val genres = genresMap.entries
            .map { (genreName, genreSongs) ->
                val sortedSongs = genreSongs.sortedBy { it.title.lowercase() }
                var totalDuration = 0L
                var artUri: Uri? = null
                for (s in sortedSongs) {
                    totalDuration += s.duration
                    if (artUri == null && s.albumArtUri != null) {
                        artUri = s.albumArtUri
                    }
                }
                Genre(
                    name = genreName,
                    songCount = sortedSongs.size,
                    totalDuration = totalDuration,
                    artworkUri = artUri,
                    songs = sortedSongs
                )
            }
            .sortedBy { it.name.lowercase() }

        return LibraryCatalog(albums = albums, artists = artists, genres = genres)
    }

    fun refreshWrappedAvailability() {
        _wrappedAvailability.value = RecommendationEngine.evaluateWrappedAvailability()
    }

    fun setLibraryFilter(filter: LibraryFilter) {
        _libraryFilter.value = filter
    }

    fun setLibrarySortOrder(order: LibrarySortOrder) {
        _librarySortOrder.value = order
    }

    fun toggleLibraryViewMode() {
        _isLibraryGridView.value = !_isLibraryGridView.value
    }

    fun onLibrarySearchQueryChange(query: String) {
        _librarySearchQuery.value = query
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { repository.createPlaylist(name) }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch { repository.deletePlaylist(playlist) }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { repository.addSongToPlaylist(playlistId, songId) }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { repository.removeSongFromPlaylist(playlistId, songId) }
    }

    fun playSongFromPlaylist(playlistWithSongs: PlaylistWithSongs, startSong: Song? = null) {
        if (playlistWithSongs.songs.isEmpty()) return
        val playlistSongs = playlistWithSongs.songs.map { it.toDomain() }
        val songToPlay = startSong ?: playlistSongs.first()
        playerController.playSong(
            song = songToPlay,
            queue = playlistSongs,
            contextTitle = playlistWithSongs.playlist.name
        )
    }

    fun playSmartPlaylist(smartPlaylist: SmartPlaylist, startSong: Song? = null) {
        if (smartPlaylist.songs.isEmpty()) return
        val songToPlay = startSong ?: smartPlaylist.songs.first()
        playerController.playSong(
            song = songToPlay,
            queue = smartPlaylist.songs,
            contextTitle = smartPlaylist.title
        )
    }

    fun playShuffled(songs: List<Song>, contextTitle: String) {
        if (songs.isEmpty()) return
        val shuffled = songs.shuffled()
        playerController.playSong(
            song = shuffled.first(),
            queue = shuffled,
            contextTitle = contextTitle
        )
    }

    fun startSongRadio(seed: Song) {
        val radioSongs = RecommendationEngine.buildSongRadio(
            seed = seed,
            allSongs = allSongs.value,
            history = allPlayHistory.value
        )
        if (radioSongs.isEmpty()) return
        playerController.playSong(
            song = radioSongs.first(),
            queue = radioSongs,
            contextTitle = "Radio di ${seed.title}"
        )
    }

    fun saveWrappedTopSongsAsPlaylist() {
        val songIds = wrappedStats.value.topSongs.map { it.song.id }
        if (songIds.isEmpty()) return
        val periodLabel = when (val avail = _wrappedAvailability.value) {
            is WrappedAvailability.Monthly -> avail.periodLabel
            is WrappedAvailability.Yearly -> avail.periodLabel
            WrappedAvailability.Hidden -> "Wrapped"
        }
        viewModelScope.launch {
            val playlistName = "Top $periodLabel"
            val playlistId = repository.createPlaylist(playlistName)
            if (playlistId > 0L) {
                repository.addSongsToPlaylist(playlistId, songIds)
            }
        }
    }

    fun syncLibraryIfNeeded(force: Boolean = false) {
        if (!force && _hasSyncedOnce.value) return
        _hasSyncedOnce.value = true
        viewModelScope.launch { repository.syncLibrary() }
    }

    fun syncLibrary() {
        syncLibraryIfNeeded(force = true)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(songId: Long) {
        val isFav = songId in favoriteSongIds.value
        viewModelScope.launch {
            repository.setFavorite(songId, !isFav)
        }
    }

    fun playSong(
        song: Song,
        queue: List<Song> = songs.value,
        contextTitle: String = "Brani"
    ) {
        playerController.playSong(song, queue, contextTitle)
    }

    fun addNextInQueue(song: Song) = playerController.addNextInQueue(song)
    fun addToQueue(song: Song) = playerController.addToQueue(song)
    fun playQueueIndex(index: Int) = playerController.playQueueIndex(index)
    fun removeFromQueue(index: Int) = playerController.removeFromQueue(index)
    fun setSleepTimer(minutes: Int?) = playerController.setSleepTimer(minutes)

    fun togglePlayPause() = playerController.togglePlayPause()
    fun skipNext() = playerController.skipNext()
    fun skipPrevious() = playerController.skipPrevious()
    fun seekTo(position: Long) = playerController.seekTo(position)
    fun toggleShuffle() = playerController.toggleShuffle()
    fun toggleRepeat() = playerController.toggleRepeat()

    // ─── OTA Updates ──────────────────────────────────────────────────────────

    fun checkForUpdatesOnStart() {
        viewModelScope.launch { appUpdateManager.checkForUpdatesOnStartup() }
    }

    fun checkForUpdatesManual() {
        viewModelScope.launch { appUpdateManager.checkForUpdates(manual = true) }
    }

    fun startUpdateDownload(release: ReleaseInfo) {
        viewModelScope.launch { appUpdateManager.downloadAndInstall(release) }
    }

    fun installReadyApk(apkFile: File) = appUpdateManager.triggerInstall(apkFile)

    fun saveUpdateSettings(repo: String, autoCheck: Boolean) =
        appUpdateManager.saveSettings(repo, autoCheck)

    fun dismissUpdateNotice() = appUpdateManager.dismissNotice()
}
