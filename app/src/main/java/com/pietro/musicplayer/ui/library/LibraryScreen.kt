package com.pietro.musicplayer.ui.library

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pietro.musicplayer.R
import com.pietro.musicplayer.data.db.PlaylistWithSongs
import com.pietro.musicplayer.data.mapper.toDomain
import com.pietro.musicplayer.domain.model.Song
import com.pietro.musicplayer.domain.recommendation.SmartPlaylist
import com.pietro.musicplayer.player.PlayerState
import com.pietro.musicplayer.ui.theme.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed interface CollectionDestination {
    data object LikedSongs : CollectionDestination
    data object TopSongs : CollectionDestination
    data class SmartPlaylistDetail(val id: String) : CollectionDestination
    data class AlbumDetail(val albumId: String) : CollectionDestination
    data class ArtistDetail(val artistName: String) : CollectionDestination
    data class GenreDetail(val genreName: String) : CollectionDestination
    data class PlaylistDetail(val playlistId: Long) : CollectionDestination
}

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenCollection: (CollectionDestination) -> Unit
) {
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val favoriteSongIds by viewModel.favoriteSongIds.collectAsState()
    val sortedSongs by viewModel.sortedLibrarySongs.collectAsState()
    val sortedFavorites by viewModel.sortedLibraryFavorites.collectAsState()
    val sortedPlaylists by viewModel.sortedLibraryPlaylists.collectAsState()
    val sortedArtists by viewModel.sortedLibraryArtists.collectAsState()
    val sortedAlbums by viewModel.sortedLibraryAlbums.collectAsState()
    val dailyMixes by viewModel.dailyMixes.collectAsState()
    val madeForYou by viewModel.madeForYou.collectAsState()
    val playerState by viewModel.playerState.collectAsState()

    val selectedFilter by viewModel.libraryFilter.collectAsState()
    val sortOrder by viewModel.librarySortOrder.collectAsState()
    val isGridView by viewModel.isLibraryGridView.collectAsState()
    val localFilterQuery by viewModel.librarySearchQuery.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showInlineSearch by remember { mutableStateOf(false) }

    val playingSongId = if (playerState.isPlaying) {
        playerState.currentMediaItem?.mediaId?.toLongOrNull()
    } else null

    val smartMixes = remember(dailyMixes, madeForYou, localFilterQuery) {
        val allMixes = dailyMixes + madeForYou
        if (localFilterQuery.isBlank()) allMixes
        else allMixes.filter {
            it.title.contains(localFilterQuery, ignoreCase = true) ||
                it.subtitle.contains(localFilterQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
    ) {
        // ─── Top Header Spotify ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.spotify_logo),
                contentDescription = "Spotify",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "La tua libreria",
                style = MaterialTheme.typography.headlineMedium,
                color = SpotifyWhite,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                showInlineSearch = !showInlineSearch
                if (!showInlineSearch) viewModel.onLibrarySearchQueryChange("")
            }) {
                Icon(Icons.Default.Search, contentDescription = "Filtra libreria", tint = SpotifyWhite)
            }
            IconButton(onClick = { viewModel.syncLibrary() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Aggiorna libreria", tint = SpotifyWhite)
            }
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nuova playlist", tint = SpotifyWhite)
            }
        }

        if (showInlineSearch) {
            OutlinedTextField(
                value = localFilterQuery,
                onValueChange = { viewModel.onLibrarySearchQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Filtra nella libreria...", color = SpotifyLightGrey) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = SpotifyLightGrey) },
                trailingIcon = {
                    if (localFilterQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onLibrarySearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, null, tint = SpotifyLightGrey)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SpotifyMedGrey,
                    unfocusedContainerColor = SpotifyMedGrey,
                    focusedBorderColor = SpotifyGreen,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = SpotifyWhite,
                    unfocusedTextColor = SpotifyWhite
                )
            )
        }

        // ─── Filter Chips ─────────────────────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LibraryFilter.entries, key = { it.name }, contentType = { "chip" }) { filter ->
                val isSelected = selectedFilter == filter
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) SpotifyGreen else SpotifyMedGrey,
                    modifier = Modifier.clickable { viewModel.setLibraryFilter(filter) }
                ) {
                    Text(
                        text = filter.label,
                        color = if (isSelected) SpotifyBlack else SpotifyWhite,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // ─── Sort & Layout Bar ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showSortMenu = true }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.SwapVert,
                        contentDescription = null,
                        tint = SpotifyWhite,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        sortOrder.label,
                        color = SpotifyWhite,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                if (showSortMenu) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(SpotifyMedGrey)
                    ) {
                        LibrarySortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        order.label,
                                        color = if (order == sortOrder) SpotifyGreen else SpotifyWhite
                                    )
                                },
                                trailingIcon = {
                                    if (order == sortOrder) {
                                        Icon(Icons.Default.Check, null, tint = SpotifyGreen)
                                    }
                                },
                                onClick = {
                                    viewModel.setLibrarySortOrder(order)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            if (selectedFilter != LibraryFilter.SONGS && selectedFilter != LibraryFilter.FAVORITES) {
                IconButton(
                    onClick = { viewModel.toggleLibraryViewMode() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (isGridView) Icons.AutoMirrored.Filled.FormatListBulleted else Icons.Default.GridView,
                        contentDescription = "Cambia layout",
                        tint = SpotifyWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ─── Contenuto Libreria ───────────────────────────────────────────────
        when (selectedFilter) {
            LibraryFilter.ALL -> {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "liked_grid", contentType = "grid_card") {
                            LibraryGridCard(
                                title = "Brani che ti piacciono",
                                subtitle = "${favoriteSongs.size} brani",
                                artworkUri = null,
                                isLikedCard = true,
                                isCircle = false,
                                onClick = { onOpenCollection(CollectionDestination.LikedSongs) }
                            )
                        }
                        items(smartMixes, key = { "mix_${it.id}" }, contentType = { "grid_card" }) { mix ->
                            LibraryGridCard(
                                title = mix.title,
                                subtitle = "${mix.badgeText} • ${mix.songs.size} brani",
                                artworkUri = mix.artworkUri,
                                isLikedCard = false,
                                isCircle = false,
                                onClick = {
                                    onOpenCollection(CollectionDestination.SmartPlaylistDetail(mix.id))
                                }
                            )
                        }
                        items(sortedPlaylists, key = { "pl_${it.playlist.id}" }, contentType = { "grid_card" }) { p ->
                            LibraryGridCard(
                                title = p.playlist.name,
                                subtitle = "Playlist • ${p.songs.size} brani",
                                artworkUri = p.songs.firstOrNull()?.toDomain()?.albumArtUri,
                                isLikedCard = false,
                                isCircle = false,
                                onClick = {
                                    onOpenCollection(CollectionDestination.PlaylistDetail(p.playlist.id))
                                }
                            )
                        }
                        items(sortedFavorites, key = { "fav_grid_${it.id}" }, contentType = { "grid_card" }) { song ->
                            LibraryGridCard(
                                title = song.title,
                                subtitle = "Preferito • ${song.artist}",
                                artworkUri = song.albumArtUri,
                                isLikedCard = false,
                                isCircle = false,
                                onClick = {
                                    viewModel.playSong(song, sortedFavorites, "Brani che ti piacciono")
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        item(key = "liked_list", contentType = "liked_row") {
                            PinnedLikedSongsRow(
                                count = favoriteSongs.size,
                                onClick = { onOpenCollection(CollectionDestination.LikedSongs) }
                            )
                        }

                        if (smartMixes.isNotEmpty()) {
                            item(key = "sec_mixes", contentType = "section_header") {
                                LibrarySectionHeader("I tuoi Mix")
                            }
                            items(smartMixes, key = { "mix_${it.id}" }, contentType = { "mix_row" }) { mix ->
                                SmartPlaylistLibraryRow(
                                    mix = mix,
                                    onClick = {
                                        onOpenCollection(CollectionDestination.SmartPlaylistDetail(mix.id))
                                    },
                                    onPlay = { viewModel.playSmartPlaylist(mix) }
                                )
                            }
                        }

                        if (sortedPlaylists.isNotEmpty()) {
                            item(key = "sec_playlists", contentType = "section_header") {
                                LibrarySectionHeader("Le tue Playlist")
                            }
                            items(sortedPlaylists, key = { "pl_${it.playlist.id}" }, contentType = { "playlist_row" }) { playlistWithSongs ->
                                PlaylistRow(
                                    playlistWithSongs = playlistWithSongs,
                                    onClick = {
                                        onOpenCollection(
                                            CollectionDestination.PlaylistDetail(playlistWithSongs.playlist.id)
                                        )
                                    },
                                    onPlay = { viewModel.playSongFromPlaylist(playlistWithSongs) },
                                    onDelete = { viewModel.deletePlaylist(playlistWithSongs.playlist) }
                                )
                            }
                        }

                        if (sortedFavorites.isNotEmpty()) {
                            item(key = "sec_favorites", contentType = "section_header") {
                                LibrarySectionHeader("Brani preferiti")
                            }
                            items(sortedFavorites, key = { "fav_all_${it.id}" }, contentType = { "song_row" }) { song ->
                                SongRow(
                                    song = song,
                                    isPlaying = song.id == playingSongId,
                                    isFavorite = true,
                                    onClick = {
                                        viewModel.playSong(song, sortedFavorites, "Brani che ti piacciono")
                                    },
                                    playlists = sortedPlaylists,
                                    onAddToPlaylist = { playlistId -> viewModel.addSongToPlaylist(playlistId, song.id) },
                                    onToggleFavorite = { viewModel.toggleFavorite(song.id) },
                                    onAddNextInQueue = { viewModel.addNextInQueue(song) },
                                    onAddToQueue = { viewModel.addToQueue(song) },
                                    onStartRadio = { viewModel.startSongRadio(song) }
                                )
                            }
                        }
                    }
                }
            }

            LibraryFilter.SONGS -> {
                val songsListState = rememberLazyListState()
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = songsListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(end = 14.dp, bottom = 16.dp)
                    ) {
                        items(sortedSongs, key = { it.id }, contentType = { "song_row" }) { song ->
                            SongRow(
                                song = song,
                                isPlaying = song.id == playingSongId,
                                isFavorite = song.id in favoriteSongIds,
                                onClick = { viewModel.playSong(song, sortedSongs, "Tutti i brani") },
                                playlists = sortedPlaylists,
                                onAddToPlaylist = { playlistId -> viewModel.addSongToPlaylist(playlistId, song.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(song.id) },
                                onAddNextInQueue = { viewModel.addNextInQueue(song) },
                                onAddToQueue = { viewModel.addToQueue(song) },
                                onStartRadio = { viewModel.startSongRadio(song) }
                            )
                        }
                    }

                    if (sortedSongs.size > 15) {
                        LazyColumnFastScrollbar(
                            listState = songsListState,
                            songs = sortedSongs,
                            sortOrder = sortOrder,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                        )
                    }
                }
            }

            LibraryFilter.FAVORITES -> {
                if (sortedFavorites.isEmpty()) {
                    EmptyFavoritesState()
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item(key = "liked_pin", contentType = "liked_row") {
                            PinnedLikedSongsRow(
                                count = favoriteSongs.size,
                                onClick = { onOpenCollection(CollectionDestination.LikedSongs) }
                            )
                        }
                        items(sortedFavorites, key = { it.id }, contentType = { "song_row" }) { song ->
                            SongRow(
                                song = song,
                                isPlaying = song.id == playingSongId,
                                isFavorite = true,
                                onClick = {
                                    viewModel.playSong(song, sortedFavorites, "Brani che ti piacciono")
                                },
                                playlists = sortedPlaylists,
                                onAddToPlaylist = { playlistId -> viewModel.addSongToPlaylist(playlistId, song.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(song.id) },
                                onAddNextInQueue = { viewModel.addNextInQueue(song) },
                                onAddToQueue = { viewModel.addToQueue(song) },
                                onStartRadio = { viewModel.startSongRadio(song) }
                            )
                        }
                    }
                }
            }

            LibraryFilter.PLAYLISTS -> {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "liked_grid", contentType = "grid_card") {
                            LibraryGridCard(
                                title = "Brani che ti piacciono",
                                subtitle = "${favoriteSongs.size} brani",
                                artworkUri = null,
                                isLikedCard = true,
                                isCircle = false,
                                onClick = { onOpenCollection(CollectionDestination.LikedSongs) }
                            )
                        }
                        items(sortedPlaylists, key = { "pl_${it.playlist.id}" }, contentType = { "grid_card" }) { p ->
                            LibraryGridCard(
                                title = p.playlist.name,
                                subtitle = "Playlist • ${p.songs.size} brani",
                                artworkUri = p.songs.firstOrNull()?.toDomain()?.albumArtUri,
                                isLikedCard = false,
                                isCircle = false,
                                onClick = {
                                    onOpenCollection(CollectionDestination.PlaylistDetail(p.playlist.id))
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item(key = "liked_list", contentType = "liked_row") {
                            PinnedLikedSongsRow(
                                count = favoriteSongs.size,
                                onClick = { onOpenCollection(CollectionDestination.LikedSongs) }
                            )
                        }
                        items(sortedPlaylists, key = { "pl_${it.playlist.id}" }, contentType = { "playlist_row" }) { playlistWithSongs ->
                            PlaylistRow(
                                playlistWithSongs = playlistWithSongs,
                                onClick = {
                                    onOpenCollection(
                                        CollectionDestination.PlaylistDetail(playlistWithSongs.playlist.id)
                                    )
                                },
                                onPlay = { viewModel.playSongFromPlaylist(playlistWithSongs) },
                                onDelete = { viewModel.deletePlaylist(playlistWithSongs.playlist) }
                            )
                        }
                    }
                }
            }

            LibraryFilter.ARTISTS -> {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(sortedArtists, key = { "ar_${it.name}" }, contentType = { "grid_card" }) { artist ->
                            LibraryGridCard(
                                title = artist.name,
                                subtitle = "Artista • ${artist.songCount} brani",
                                artworkUri = artist.artworkUri,
                                isLikedCard = false,
                                isCircle = true,
                                onClick = {
                                    onOpenCollection(CollectionDestination.ArtistDetail(artist.name))
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(sortedArtists, key = { "ar_${it.name}" }, contentType = { "artist_row" }) { artist ->
                            ArtistRow(
                                artist = artist,
                                onClick = {
                                    onOpenCollection(CollectionDestination.ArtistDetail(artist.name))
                                }
                            )
                        }
                    }
                }
            }

            LibraryFilter.ALBUMS -> {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(sortedAlbums, key = { "al_${it.id}" }, contentType = { "grid_card" }) { album ->
                            LibraryGridCard(
                                title = album.name,
                                subtitle = "Album • ${album.artist}",
                                artworkUri = album.albumArtUri,
                                isLikedCard = false,
                                isCircle = false,
                                onClick = {
                                    onOpenCollection(CollectionDestination.AlbumDetail(album.id))
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(sortedAlbums, key = { "al_${it.id}" }, contentType = { "album_row" }) { album ->
                            AlbumRow(
                                album = album,
                                onClick = {
                                    onOpenCollection(CollectionDestination.AlbumDetail(album.id))
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
private fun LazyColumnFastScrollbar(
    listState: LazyListState,
    songs: List<Song>,
    sortOrder: LibrarySortOrder,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var trackHeightPx by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val thumbHeightDp = 42.dp
    val thumbHeightPx = with(density) { thumbHeightDp.toPx() }

    val scrollFraction by remember(songs.size) {
        derivedStateOf {
            val total = songs.size
            if (total <= 1) 0f
            else {
                val visible = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                val maxStart = (total - visible).coerceAtLeast(1)
                (listState.firstVisibleItemIndex.toFloat() / maxStart.toFloat()).coerceIn(0f, 1f)
            }
        }
    }

    val activeFraction = if (isDragging) dragFraction else scrollFraction
    val usableHeightPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
    val thumbOffsetY = (activeFraction * usableHeightPx).roundToInt()

    val currentSongIndex = if (songs.isEmpty()) 0
    else (activeFraction * (songs.size - 1)).roundToInt().coerceIn(0, songs.lastIndex)

    val badgeLabel = remember(currentSongIndex, songs, sortOrder) {
        val song = songs.getOrNull(currentSongIndex)
        when {
            song == null -> ""
            sortOrder == LibrarySortOrder.ARTIST ->
                song.artist.firstOrNull()?.uppercase() ?: "#"
            sortOrder == LibrarySortOrder.DURATION ->
                song.duration.toMinutesSeconds()
            else ->
                song.title.firstOrNull()?.uppercase() ?: "#"
        }
    }

    Box(
        modifier = modifier
            .padding(vertical = 8.dp)
            .width(88.dp)
            .onSizeChanged { trackHeightPx = it.height }
    ) {
        AnimatedVisibility(
            visible = isDragging && badgeLabel.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(x = -with(density) { 30.dp.roundToPx() }, y = thumbOffsetY) }
        ) {
            Surface(
                color = SpotifyGreen,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = badgeLabel,
                    color = SpotifyBlack,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(24.dp)
                .pointerInput(songs.size, trackHeightPx) {
                    detectTapGestures { offset ->
                        if (trackHeightPx <= 0 || songs.isEmpty()) return@detectTapGestures
                        val fraction = ((offset.y - thumbHeightPx / 2f) / usableHeightPx).coerceIn(0f, 1f)
                        val targetIdx = (fraction * (songs.size - 1)).roundToInt().coerceIn(0, songs.lastIndex)
                        coroutineScope.launch { listState.scrollToItem(targetIdx) }
                    }
                }
                .pointerInput(songs.size, trackHeightPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val fraction = ((offset.y - thumbHeightPx / 2f) / usableHeightPx).coerceIn(0f, 1f)
                            dragFraction = fraction
                            if (songs.isNotEmpty()) {
                                val targetIdx = (fraction * (songs.size - 1)).roundToInt().coerceIn(0, songs.lastIndex)
                                coroutineScope.launch { listState.scrollToItem(targetIdx) }
                            }
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            if (trackHeightPx <= 0 || songs.isEmpty()) return@detectVerticalDragGestures
                            val fraction = ((change.position.y - thumbHeightPx / 2f) / usableHeightPx).coerceIn(0f, 1f)
                            dragFraction = fraction
                            val targetIdx = (fraction * (songs.size - 1)).roundToInt().coerceIn(0, songs.lastIndex)
                            coroutineScope.launch { listState.scrollToItem(targetIdx) }
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 3.dp)
                    .offset { IntOffset(x = 0, y = thumbOffsetY) }
                    .width(if (isDragging) 6.dp else 4.dp)
                    .height(thumbHeightDp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isDragging || listState.isScrollInProgress) {
                            SpotifyGreen
                        } else {
                            SpotifyLightGrey.copy(alpha = 0.45f)
                        }
                    )
            )
        }
    }
}

@Composable
private fun LibrarySectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = SpotifyLightGrey,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun SmartPlaylistLibraryRow(
    mix: SmartPlaylist,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    val accent = Color(mix.accentColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.linearGradient(listOf(accent, SpotifyDarkGrey))),
            contentAlignment = Alignment.Center
        ) {
            if (mix.artworkUri != null) {
                AsyncImage(
                    model = mix.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SpotifyWhite)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                mix.title,
                style = MaterialTheme.typography.titleSmall,
                color = SpotifyWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Mix per te • ${mix.songs.size} brani",
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyLightGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (mix.songs.isNotEmpty()) {
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Riproduci mix",
                    tint = SpotifyGreen
                )
            }
        }
    }
}

@Composable
private fun PinnedLikedSongsRow(
    count: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(SpotifyLikedPurple, SpotifyLikedMint)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = SpotifyWhite,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Brani che ti piacciono",
                style = MaterialTheme.typography.titleSmall,
                color = SpotifyWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Playlist • $count brani",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyLightGrey
                )
            }
        }
    }
}

@Composable
private fun EmptyFavoritesState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = SpotifyLightGrey,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "I tuoi brani preferiti appariranno qui",
                style = MaterialTheme.typography.titleSmall,
                color = SpotifyWhite,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tocca l'icona del cuore sul player o dal menu di qualsiasi brano.",
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyLightGrey,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ArtistRow(
    artist: Artist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(SpotifyMedGrey),
            contentAlignment = Alignment.Center
        ) {
            if (artist.artworkUri != null) {
                AsyncImage(
                    model = artist.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    artist.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = SpotifyWhite
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                artist.name,
                style = MaterialTheme.typography.titleSmall,
                color = SpotifyWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Artista • ${artist.albumCount} album • ${artist.songCount} brani",
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyLightGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AlbumRow(
    album: Album,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SpotifyMedGrey),
            contentAlignment = Alignment.Center
        ) {
            if (album.albumArtUri != null) {
                AsyncImage(
                    model = album.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Album, contentDescription = null, tint = SpotifyLightGrey)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                album.name,
                style = MaterialTheme.typography.titleSmall,
                color = SpotifyWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Album • ${album.artist} • ${album.songCount} brani",
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyLightGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LibraryGridCard(
    title: String,
    subtitle: String,
    artworkUri: Uri?,
    isLikedCard: Boolean,
    isCircle: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = if (isCircle) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(if (isCircle) CircleShape else RoundedCornerShape(8.dp))
                .background(
                    if (isLikedCard) {
                        Brush.linearGradient(listOf(SpotifyLikedPurple, SpotifyLikedMint))
                    } else {
                        Brush.linearGradient(listOf(SpotifyMedGrey, SpotifyMedGrey))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLikedCard -> Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = SpotifyWhite,
                    modifier = Modifier.size(52.dp)
                )
                artworkUri != null -> AsyncImage(
                    model = artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                else -> Icon(
                    if (isCircle) Icons.Default.AccountCircle else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = SpotifyLightGrey,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = SpotifyWhite,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = SpotifyLightGrey,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Dettaglio Collezione in Stile Spotify ───────────────────────────────────

@Composable
fun SpotifyCollectionDetailScreen(
    title: String,
    subtitle: String,
    description: String?,
    artworkUri: Uri?,
    accentColor: Color? = null,
    isLikedCollection: Boolean = false,
    isArtistCircle: Boolean = false,
    songs: List<Song>,
    favoriteSongIds: Set<Long>,
    playerState: PlayerState,
    playlists: List<PlaylistWithSongs>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onAddToPlaylist: (Long, Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddNextInQueue: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onStartRadio: (Song) -> Unit,
    onRemoveFromPlaylist: ((Long) -> Unit)? = null
) {
    val dominantColor = rememberDominantColor(
        artworkUri = if (isLikedCollection) null else artworkUri,
        fallbackColor = if (isLikedCollection) SpotifyLikedPurple else (accentColor ?: Color(0xFF1E3A2F))
    )
    val totalMinutes = remember(songs) {
        (songs.sumOf { it.duration } / 60_000L).coerceAtLeast(if (songs.isNotEmpty()) 1L else 0L)
    }
    val playingSongId = if (playerState.isPlaying) {
        playerState.currentMediaItem?.mediaId?.toLongOrNull()
    } else null

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "detail_header", contentType = "header") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(dominantColor, SpotifyBlack)
                        )
                    )
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Indietro",
                                tint = SpotifyWhite
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(196.dp)
                                .clip(if (isArtistCircle) CircleShape else RoundedCornerShape(8.dp))
                                .background(
                                    if (isLikedCollection) {
                                        Brush.linearGradient(
                                            listOf(SpotifyLikedPurple, SpotifyLikedMint)
                                        )
                                    } else {
                                        Brush.linearGradient(listOf(SpotifyMedGrey, SpotifyDarkGrey))
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isLikedCollection -> Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = SpotifyWhite,
                                    modifier = Modifier.size(72.dp)
                                )
                                artworkUri != null -> AsyncImage(
                                    model = artworkUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                else -> Icon(
                                    Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = SpotifyLightGrey,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = SpotifyWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!description.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyLightGrey
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "$subtitle • $totalMinutes min",
                        style = MaterialTheme.typography.labelMedium,
                        color = SpotifyLightGrey
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (songs.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = onShufflePlay,
                                    shape = RoundedCornerShape(24.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        SpotifyLightGrey.copy(alpha = 0.5f)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Shuffle,
                                        contentDescription = null,
                                        tint = SpotifyGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Casuale", color = SpotifyWhite, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        if (songs.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(SpotifyGreen)
                                    .clickable(onClick = onPlayAll),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Riproduci",
                                    tint = SpotifyBlack,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (songs.isEmpty()) {
            item(key = "detail_empty", contentType = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nessun brano in questa raccolta",
                        color = SpotifyLightGrey,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(songs, key = { it.id }, contentType = { "song_row" }) { song ->
                SongRow(
                    song = song,
                    isPlaying = song.id == playingSongId,
                    isFavorite = song.id in favoriteSongIds,
                    onClick = { onPlaySong(song) },
                    playlists = playlists,
                    onAddToPlaylist = { playlistId -> onAddToPlaylist(playlistId, song.id) },
                    onToggleFavorite = { onToggleFavorite(song.id) },
                    onAddNextInQueue = { onAddNextInQueue(song) },
                    onAddToQueue = { onAddToQueue(song) },
                    onStartRadio = { onStartRadio(song) },
                    onRemoveFromPlaylist = onRemoveFromPlaylist?.let { remover ->
                        { remover(song.id) }
                    }
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlistWithSongs: PlaylistWithSongs,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SpotifyMedGrey),
            contentAlignment = Alignment.Center
        ) {
            if (playlistWithSongs.songs.isNotEmpty()) {
                val firstSong = playlistWithSongs.songs.first().toDomain()
                AsyncImage(
                    model = firstSong.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = SpotifyLightGrey
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlistWithSongs.playlist.name,
                style = MaterialTheme.typography.titleSmall,
                color = SpotifyWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Playlist • ${playlistWithSongs.songs.size} brani",
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyLightGrey
            )
        }
        if (playlistWithSongs.songs.isNotEmpty()) {
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Riproduci playlist",
                    tint = SpotifyGreen
                )
            }
        }
        IconButton(onClick = { showDeleteDialog = true }) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Elimina", tint = SpotifyLightGrey)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Elimina playlist", color = SpotifyWhite) },
            text = {
                Text(
                    "Vuoi eliminare \"${playlistWithSongs.playlist.name}\"?",
                    color = SpotifyLightGrey
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Elimina", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annulla", color = SpotifyGreen)
                }
            },
            containerColor = SpotifyMedGrey
        )
    }
}

@Composable
private fun CreatePlaylistDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dai un nome alla playlist", color = SpotifyWhite, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("La mia playlist", color = SpotifyLightGrey) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SpotifyGreen,
                    unfocusedBorderColor = SpotifyLightGrey,
                    focusedTextColor = SpotifyWhite,
                    unfocusedTextColor = SpotifyWhite,
                    cursorColor = SpotifyGreen
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) {
                Text("Crea", color = SpotifyGreen, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla", color = SpotifyLightGrey)
            }
        },
        containerColor = SpotifyMedGrey
    )
}

// ─── Componenti condivisi ─────────────────────────────────────────────────────

@Composable
fun SongRow(
    song: Song,
    isPlaying: Boolean,
    isFavorite: Boolean = song.isFavorite,
    onClick: () -> Unit,
    playlists: List<PlaylistWithSongs> = emptyList(),
    onAddToPlaylist: ((Long) -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onAddNextInQueue: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) SpotifyGreen else SpotifyWhite,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = {
            Text(
                "${song.artist} • ${song.album}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = SpotifyLightGrey,
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SpotifyMedGrey),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (song.albumArtUri == null) {
                    Icon(Icons.Default.MusicNote, null, tint = SpotifyLightGrey)
                }
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            null,
                            tint = SpotifyGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onToggleFavorite != null && isFavorite) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Rimuovi dai preferiti",
                            tint = SpotifyGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = SpotifyLightGrey)
                    }
                    if (showMenu) {
                        DropdownMenu(
                            expanded = true,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(SpotifyMedGrey)
                        ) {
                            if (onToggleFavorite != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                                            color = SpotifyWhite
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            null,
                                            tint = SpotifyGreen
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onToggleFavorite()
                                    }
                                )
                            }
                            if (onAddNextInQueue != null) {
                                DropdownMenuItem(
                                    text = { Text("Riproduci successivo", color = SpotifyWhite) },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = SpotifyWhite)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onAddNextInQueue()
                                    }
                                )
                            }
                            if (onAddToQueue != null) {
                                DropdownMenuItem(
                                    text = { Text("Aggiungi alla coda", color = SpotifyWhite) },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = SpotifyWhite)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onAddToQueue()
                                    }
                                )
                            }
                            if (onStartRadio != null) {
                                DropdownMenuItem(
                                    text = { Text("Vai alla radio del brano", color = SpotifyWhite) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Radio, null, tint = SpotifyGreen)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onStartRadio()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Aggiungi a playlist", color = SpotifyWhite) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = SpotifyGreen)
                                },
                                onClick = {
                                    showMenu = false
                                    showPlaylistPicker = true
                                }
                            )
                            if (onRemoveFromPlaylist != null) {
                                DropdownMenuItem(
                                    text = { Text("Rimuovi dalla playlist", color = Color.Red) },
                                    leadingIcon = {
                                        Icon(Icons.Default.DeleteOutline, null, tint = Color.Red)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onRemoveFromPlaylist()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    if (showPlaylistPicker) {
        PlaylistPickerDialog(
            playlists = playlists,
            onSelect = { playlistId ->
                onAddToPlaylist?.invoke(playlistId)
                showPlaylistPicker = false
            },
            onDismiss = { showPlaylistPicker = false }
        )
    }
}

@Composable
private fun PlaylistPickerDialog(
    playlists: List<PlaylistWithSongs>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Aggiungi a playlist", color = SpotifyWhite, fontWeight = FontWeight.Bold)
        },
        text = {
            if (playlists.isEmpty()) {
                Text("Nessuna playlist disponibile.\nCreane una nella scheda Libreria!", color = SpotifyLightGrey)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(playlists, key = { it.playlist.id }, contentType = { "picker_row" }) { playlistWithSongs ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(playlistWithSongs.playlist.id) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    playlistWithSongs.playlist.name,
                                    color = SpotifyWhite,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "${playlistWithSongs.songs.size} brani",
                                    color = SpotifyLightGrey,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF333333))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla", color = SpotifyLightGrey)
            }
        },
        containerColor = SpotifyMedGrey
    )
}

@Composable
fun MiniPlayer(
    playerState: PlayerState,
    playbackPositionFlow: StateFlow<Long>,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit
) {
    val metadata = playerState.currentMediaItem?.mediaMetadata
    val dominantColor = rememberDominantColor(
        artworkUri = metadata?.artworkUri,
        fallbackColor = SpotifyMedGrey
    )
    val cardBackground = remember(dominantColor) {
        lerp(dominantColor, SpotifyMedGrey, 0.35f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onExpand),
        color = cardBackground,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SpotifyDarkGrey),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = metadata?.artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (metadata?.artworkUri == null) {
                        Icon(
                            Icons.Default.MusicNote,
                            null,
                            tint = SpotifyLightGrey,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        metadata?.title?.toString() ?: "Sconosciuto",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        metadata?.artist?.toString() ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpotifyWhite.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Preferiti",
                        tint = if (isFavorite) SpotifyGreen else SpotifyWhite.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pausa",
                        tint = SpotifyWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            MiniPlayerProgressBar(
                playbackPositionFlow = playbackPositionFlow,
                duration = playerState.duration
            )
        }
    }
}

@Composable
private fun MiniPlayerProgressBar(
    playbackPositionFlow: StateFlow<Long>,
    duration: Long
) {
    val position by playbackPositionFlow.collectAsState()
    val progress = if (duration > 0) {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .padding(horizontal = 8.dp),
        color = SpotifyWhite,
        trackColor = SpotifyWhite.copy(alpha = 0.25f)
    )
}

fun Long.toMinutesSeconds(): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
