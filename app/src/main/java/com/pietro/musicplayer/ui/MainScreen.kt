package com.pietro.musicplayer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pietro.musicplayer.data.mapper.toDomain
import com.pietro.musicplayer.domain.recommendation.WrappedAvailability
import com.pietro.musicplayer.ui.home.HomeScreen
import com.pietro.musicplayer.ui.library.CollectionDestination
import com.pietro.musicplayer.ui.library.LibraryScreen
import com.pietro.musicplayer.ui.library.LibraryViewModel
import com.pietro.musicplayer.ui.library.MiniPlayer
import com.pietro.musicplayer.ui.library.SpotifyCollectionDetailScreen
import com.pietro.musicplayer.ui.player.PlayerScreen
import com.pietro.musicplayer.ui.search.SearchScreen
import com.pietro.musicplayer.ui.theme.*
import com.pietro.musicplayer.ui.update.OtaUpdateDialog
import com.pietro.musicplayer.ui.update.UpdateAvailablePopup
import com.pietro.musicplayer.ui.wrapped.WrappedScreen
import com.pietro.musicplayer.update.UpdateUiState

sealed class Tab(val label: String, val icon: ImageVector) {
    object Home    : Tab("Home",     Icons.Default.Home)
    object Search  : Tab("Cerca",    Icons.Default.Search)
    object Library : Tab("Libreria", Icons.Default.LibraryMusic)
}

@Composable
fun MainScreen(viewModel: LibraryViewModel = hiltViewModel()) {
    val tabs = listOf(Tab.Home, Tab.Search, Tab.Library)
    var selectedTab by remember { mutableStateOf<Tab>(Tab.Home) }
    var activeDestination by remember { mutableStateOf<CollectionDestination?>(null) }
    var showWrappedModal by remember { mutableStateOf(false) }
    var openWrappedStory by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }
    var showUpdateSettingsDialog by remember { mutableStateOf(false) }

    val playerState by viewModel.playerState.collectAsState()
    val favoriteSongIds by viewModel.favoriteSongIds.collectAsState()
    val wrappedAvailability by viewModel.wrappedAvailability.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val githubRepo by viewModel.githubRepo.collectAsState()
    val autoCheckUpdates by viewModel.autoCheckUpdates.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()

    val currentSongId = playerState.currentMediaItem?.mediaId?.toLongOrNull()
    val isCurrentFavorite = currentSongId != null && currentSongId in favoriteSongIds

    val context = LocalContext.current
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.syncLibraryIfNeeded()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshWrappedAvailability()
        viewModel.checkForUpdatesOnStart()
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            audioPermission
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.syncLibraryIfNeeded()
        } else {
            permissionLauncher.launch(audioPermission)
        }
    }

    LaunchedEffect(wrappedAvailability) {
        if (wrappedAvailability is WrappedAvailability.Hidden && showWrappedModal) {
            showWrappedModal = false
        }
    }

    BackHandler(enabled = activeDestination != null && !showPlayer && !showWrappedModal) {
        activeDestination = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = SpotifyBlack,
            bottomBar = {
                Column {
                    if (playerState.currentMediaItem != null) {
                        MiniPlayer(
                            playerState = playerState,
                            playbackPositionFlow = viewModel.playbackPosition,
                            isFavorite = isCurrentFavorite,
                            onToggleFavorite = {
                                if (currentSongId != null) viewModel.toggleFavorite(currentSongId)
                            },
                            onPlayPause = { viewModel.togglePlayPause() },
                            onExpand = { showPlayer = true }
                        )
                    }
                    NavigationBar(
                        containerColor = SpotifyDarkGrey.copy(alpha = 0.96f),
                        tonalElevation = 0.dp
                    ) {
                        tabs.forEach { tab ->
                            val isSelected = selectedTab == tab
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    activeDestination = null
                                    selectedTab = tab
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = {
                                    Text(
                                        tab.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor   = SpotifyWhite,
                                    selectedTextColor   = SpotifyWhite,
                                    unselectedIconColor = SpotifyLightGrey,
                                    unselectedTextColor = SpotifyLightGrey,
                                    indicatorColor      = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                val destination = activeDestination
                if (destination != null) {
                    DestinationDetailHost(
                        destination = destination,
                        viewModel = viewModel,
                        onBack = { activeDestination = null }
                    )
                } else {
                    when (selectedTab) {
                        Tab.Home -> HomeScreen(
                            viewModel = viewModel,
                            onOpenCollection = { dest -> activeDestination = dest },
                            onOpenWrapped = { startStory ->
                                openWrappedStory = startStory
                                showWrappedModal = true
                            },
                            onOpenUpdates = { showUpdateSettingsDialog = true }
                        )
                        Tab.Search -> SearchScreen(
                            viewModel = viewModel,
                            onOpenCollection = { dest -> activeDestination = dest },
                            onOpenWrapped = { startStory ->
                                openWrappedStory = startStory
                                showWrappedModal = true
                            }
                        )
                        Tab.Library -> LibraryScreen(
                            viewModel = viewModel,
                            onOpenCollection = { dest -> activeDestination = dest }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showWrappedModal && wrappedAvailability !is WrappedAvailability.Hidden,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 280),
                initialOffsetY = { it }
            ),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 240),
                targetOffsetY = { it }
            )
        ) {
            WrappedScreen(
                viewModel = viewModel,
                availability = wrappedAvailability,
                isActive = showWrappedModal,
                startInStoryMode = openWrappedStory,
                onStoryModeConsumed = { openWrappedStory = false },
                onOpenCollection = { dest ->
                    showWrappedModal = false
                    activeDestination = dest
                },
                onDismiss = { showWrappedModal = false }
            )
        }

        AnimatedVisibility(
            visible = showPlayer,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 280),
                initialOffsetY = { it }
            ),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 240),
                targetOffsetY = { it }
            )
        ) {
            PlayerScreen(
                viewModel = viewModel,
                isActive = showPlayer,
                onDismiss = { showPlayer = false },
                onNavigateToAlbum = { albumName ->
                    showPlayer = false
                    showWrappedModal = false
                    val match = albums.find { it.name.equals(albumName, ignoreCase = true) }
                    if (match != null) {
                        activeDestination = CollectionDestination.AlbumDetail(match.id)
                    } else {
                        selectedTab = Tab.Library
                    }
                },
                onNavigateToArtist = { artistName ->
                    showPlayer = false
                    showWrappedModal = false
                    val match = artists.find { it.name.equals(artistName, ignoreCase = true) }
                    if (match != null) {
                        activeDestination = CollectionDestination.ArtistDetail(match.name)
                    } else {
                        selectedTab = Tab.Library
                    }
                }
            )
        }

        if (showUpdateSettingsDialog) {
            OtaUpdateDialog(
                currentVersion = viewModel.currentAppVersion,
                githubRepo = githubRepo,
                autoCheckUpdates = autoCheckUpdates,
                updateState = updateState,
                onSaveSettings = { repo, autoCheck -> viewModel.saveUpdateSettings(repo, autoCheck) },
                onCheckNow = { viewModel.checkForUpdatesManual() },
                onStartDownload = { release -> viewModel.startUpdateDownload(release) },
                onInstallReady = { file -> viewModel.installReadyApk(file) },
                onDismiss = { showUpdateSettingsDialog = false }
            )
        } else if (
            updateState is UpdateUiState.UpdateAvailable ||
            updateState is UpdateUiState.Downloading ||
            updateState is UpdateUiState.ReadyToInstall
        ) {
            UpdateAvailablePopup(
                updateState = updateState,
                onStartDownload = { release -> viewModel.startUpdateDownload(release) },
                onInstallReady = { file -> viewModel.installReadyApk(file) },
                onDismiss = { viewModel.dismissUpdateNotice() }
            )
        }
    }
}

@Composable
private fun DestinationDetailHost(
    destination: CollectionDestination,
    viewModel: LibraryViewModel,
    onBack: () -> Unit
) {
    val playerState by viewModel.playerState.collectAsState()
    val favoriteSongIds by viewModel.favoriteSongIds.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val topSongs by viewModel.topSongs.collectAsState()
    val dailyMixes by viewModel.dailyMixes.collectAsState()
    val madeForYou by viewModel.madeForYou.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val genres by viewModel.genres.collectAsState()

    when (destination) {
        CollectionDestination.LikedSongs -> {
            SpotifyCollectionDetailScreen(
                title = "Brani che ti piacciono",
                subtitle = "I tuoi preferiti",
                description = "Tutti i brani contrassegnati con il cuore nella tua libreria locale.",
                artworkUri = null,
                isLikedCollection = true,
                songs = favoriteSongs,
                favoriteSongIds = favoriteSongIds,
                playerState = playerState,
                playlists = playlists,
                onBack = onBack,
                onPlayAll = {
                    if (favoriteSongs.isNotEmpty()) {
                        viewModel.playSong(favoriteSongs.first(), favoriteSongs, "Brani che ti piacciono")
                    }
                },
                onShufflePlay = { viewModel.playShuffled(favoriteSongs, "Brani che ti piacciono") },
                onPlaySong = { song -> viewModel.playSong(song, favoriteSongs, "Brani che ti piacciono") },
                onAddToPlaylist = { pId, sId -> viewModel.addSongToPlaylist(pId, sId) },
                onToggleFavorite = { sId -> viewModel.toggleFavorite(sId) },
                onAddNextInQueue = { viewModel.addNextInQueue(it) },
                onAddToQueue = { viewModel.addToQueue(it) },
                onStartRadio = { viewModel.startSongRadio(it) }
            )
        }

        CollectionDestination.TopSongs -> {
            SpotifyCollectionDetailScreen(
                title = "Più ascoltati",
                subtitle = "La tua classifica personale",
                description = "I brani che hai riprodotto più spesso su questo dispositivo.",
                artworkUri = topSongs.firstNotNullOfOrNull { it.albumArtUri },
                accentColor = Color(0xFFE13300),
                songs = topSongs,
                favoriteSongIds = favoriteSongIds,
                playerState = playerState,
                playlists = playlists,
                onBack = onBack,
                onPlayAll = {
                    if (topSongs.isNotEmpty()) {
                        viewModel.playSong(topSongs.first(), topSongs, "Più ascoltati")
                    }
                },
                onShufflePlay = { viewModel.playShuffled(topSongs, "Più ascoltati") },
                onPlaySong = { song -> viewModel.playSong(song, topSongs, "Più ascoltati") },
                onAddToPlaylist = { pId, sId -> viewModel.addSongToPlaylist(pId, sId) },
                onToggleFavorite = { sId -> viewModel.toggleFavorite(sId) },
                onAddNextInQueue = { viewModel.addNextInQueue(it) },
                onAddToQueue = { viewModel.addToQueue(it) },
                onStartRadio = { viewModel.startSongRadio(it) }
            )
        }

        is CollectionDestination.SmartPlaylistDetail -> {
            val mix = (dailyMixes + madeForYou).find { it.id == destination.id }
            if (mix == null) {
                LaunchedEffect(Unit) { onBack() }
                return
            }
            SpotifyCollectionDetailScreen(
                title = mix.title,
                subtitle = mix.badgeText,
                description = mix.description,
                artworkUri = mix.artworkUri,
                accentColor = Color(mix.accentColor),
                songs = mix.songs,
                favoriteSongIds = favoriteSongIds,
                playerState = playerState,
                playlists = playlists,
                onBack = onBack,
                onPlayAll = { viewModel.playSmartPlaylist(mix) },
                onShufflePlay = { viewModel.playShuffled(mix.songs, mix.title) },
                onPlaySong = { song -> viewModel.playSmartPlaylist(mix, song) },
                onAddToPlaylist = { pId, sId -> viewModel.addSongToPlaylist(pId, sId) },
                onToggleFavorite = { sId -> viewModel.toggleFavorite(sId) },
                onAddNextInQueue = { viewModel.addNextInQueue(it) },
                onAddToQueue = { viewModel.addToQueue(it) },
                onStartRadio = { viewModel.startSongRadio(it) }
            )
        }

        is CollectionDestination.AlbumDetail -> {
            val album = albums.find { it.id == destination.albumId }
            if (album == null) {
                LaunchedEffect(Unit) { onBack() }
                return
            }
            SpotifyCollectionDetailScreen(
                title = album.name,
                subtitle = "Album • ${album.artist}",
                description = null,
                artworkUri = album.albumArtUri,
                songs = album.songs,
                favoriteSongIds = favoriteSongIds,
                playerState = playerState,
                playlists = playlists,
                onBack = onBack,
                onPlayAll = {
                    if (album.songs.isNotEmpty()) {
                        viewModel.playSong(album.songs.first(), album.songs, album.name)
                    }
                },
                onShufflePlay = { viewModel.playShuffled(album.songs, album.name) },
                onPlaySong = { song -> viewModel.playSong(song, album.songs, album.name) },
                onAddToPlaylist = { pId, sId -> viewModel.addSongToPlaylist(pId, sId) },
                onToggleFavorite = { sId -> viewModel.toggleFavorite(sId) },
                onAddNextInQueue = { viewModel.addNextInQueue(it) },
                onAddToQueue = { viewModel.addToQueue(it) },
                onStartRadio = { viewModel.startSongRadio(it) }
            )
        }

        is CollectionDestination.ArtistDetail -> {
            val artist = artists.find { it.name == destination.artistName }
            if (artist == null) {
                LaunchedEffect(Unit) { onBack() }
                return
            }
            SpotifyCollectionDetailScreen(
                title = artist.name,
                subtitle = "Artista • ${artist.albumCount} album",
                description = null,
                artworkUri = artist.artworkUri,
                isArtistCircle = true,
                songs = artist.songs,
                favoriteSongIds = favoriteSongIds,
                playerState = playerState,
                playlists = playlists,
                onBack = onBack,
                onPlayAll = {
                    if (artist.songs.isNotEmpty()) {
                        viewModel.playSong(artist.songs.first(), artist.songs, artist.name)
                    }
                },
                onShufflePlay = { viewModel.playShuffled(artist.songs, artist.name) },
                onPlaySong = { song -> viewModel.playSong(song, artist.songs, artist.name) },
                onAddToPlaylist = { pId, sId -> viewModel.addSongToPlaylist(pId, sId) },
                onToggleFavorite = { sId -> viewModel.toggleFavorite(sId) },
                onAddNextInQueue = { viewModel.addNextInQueue(it) },
                onAddToQueue = { viewModel.addToQueue(it) },
                onStartRadio = { viewModel.startSongRadio(it) }
            )
        }

        is CollectionDestination.GenreDetail -> {
            val genre = genres.find { it.name == destination.genreName }
            if (genre == null) {
                LaunchedEffect(Unit) { onBack() }
                return
            }
            SpotifyCollectionDetailScreen(
                title = genre.name,
                subtitle = "Genere musicale",
                description = "Tutti i brani del genere ${genre.name} nella tua libreria.",
                artworkUri = genre.artworkUri,
                songs = genre.songs,
                favoriteSongIds = favoriteSongIds,
                playerState = playerState,
                playlists = playlists,
                onBack = onBack,
                onPlayAll = {
                    if (genre.songs.isNotEmpty()) {
                        viewModel.playSong(genre.songs.first(), genre.songs, genre.name)
                    }
                },
                onShufflePlay = { viewModel.playShuffled(genre.songs, genre.name) },
                onPlaySong = { song -> viewModel.playSong(song, genre.songs, genre.name) },
                onAddToPlaylist = { pId, sId -> viewModel.addSongToPlaylist(pId, sId) },
                onToggleFavorite = { sId -> viewModel.toggleFavorite(sId) },
                onAddNextInQueue = { viewModel.addNextInQueue(it) },
                onAddToQueue = { viewModel.addToQueue(it) },
                onStartRadio = { viewModel.startSongRadio(it) }
            )
        }

        is CollectionDestination.PlaylistDetail -> {
            val playlist = playlists.find { it.playlist.id == destination.playlistId }
            if (playlist == null) {
                LaunchedEffect(Unit) { onBack() }
                return
            }
            val songs = remember(playlist.songs) { playlist.songs.map { it.toDomain() } }
            SpotifyCollectionDetailScreen(
                title = playlist.playlist.name,
                subtitle = "Playlist personale",
                description = null,
                artworkUri = songs.firstNotNullOfOrNull { it.albumArtUri },
                songs = songs,
                favoriteSongIds = favoriteSongIds,
                playerState = playerState,
                playlists = playlists,
                onBack = onBack,
                onPlayAll = { viewModel.playSongFromPlaylist(playlist) },
                onShufflePlay = { viewModel.playShuffled(songs, playlist.playlist.name) },
                onPlaySong = { song -> viewModel.playSongFromPlaylist(playlist, song) },
                onAddToPlaylist = { pId, sId -> viewModel.addSongToPlaylist(pId, sId) },
                onToggleFavorite = { sId -> viewModel.toggleFavorite(sId) },
                onAddNextInQueue = { viewModel.addNextInQueue(it) },
                onAddToQueue = { viewModel.addToQueue(it) },
                onStartRadio = { viewModel.startSongRadio(it) },
                onRemoveFromPlaylist = { songId ->
                    viewModel.removeSongFromPlaylist(playlist.playlist.id, songId)
                }
            )
        }
    }
}
