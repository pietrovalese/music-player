package com.pietro.musicplayer.ui.search

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pietro.musicplayer.data.mapper.toDomain
import com.pietro.musicplayer.domain.recommendation.WrappedAvailability
import com.pietro.musicplayer.ui.library.CollectionDestination
import com.pietro.musicplayer.ui.library.LibraryViewModel
import com.pietro.musicplayer.ui.library.SongRow
import com.pietro.musicplayer.ui.theme.*

private enum class SearchResultFilter(val label: String) {
    ALL("Tutto"),
    SONGS("Brani"),
    ARTISTS("Artisti"),
    ALBUMS("Album"),
    PLAYLISTS("Playlist")
}

private data class BrowseCategoryTile(
    val id: String,
    val title: String,
    val color: Color,
    val icon: ImageVector,
    val artworkUri: Uri? = null,
    val onClick: () -> Unit
)

@Composable
fun SearchScreen(
    viewModel: LibraryViewModel,
    onOpenCollection: (CollectionDestination) -> Unit,
    onOpenWrapped: (startStory: Boolean) -> Unit
) {
    val query by viewModel.searchQuery.collectAsState()
    val filteredSongs by viewModel.songs.collectAsState()
    val favoriteSongIds by viewModel.favoriteSongIds.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val madeForYou by viewModel.madeForYou.collectAsState()
    val wrappedAvailability by viewModel.wrappedAvailability.collectAsState()

    var activeFilter by remember { mutableStateOf(SearchResultFilter.ALL) }

    val playingSongId = if (playerState.isPlaying) {
        playerState.currentMediaItem?.mediaId?.toLongOrNull()
    } else null

    val matchingArtists = remember(artists, query) {
        if (query.isBlank()) emptyList()
        else artists.filter { it.name.contains(query, ignoreCase = true) }
    }

    val matchingAlbums = remember(albums, query) {
        if (query.isBlank()) emptyList()
        else albums.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
        }
    }

    val matchingPlaylists = remember(playlists, query) {
        if (query.isBlank()) emptyList()
        else playlists.filter { it.playlist.name.contains(query, ignoreCase = true) }
    }

    val browseTiles = remember(genres, madeForYou, albums, wrappedAvailability) {
        val tiles = mutableListOf(
            BrowseCategoryTile(
                id = "liked",
                title = "Brani che ti piacciono",
                color = SpotifyLikedPurple,
                icon = Icons.Default.Favorite,
                onClick = { onOpenCollection(CollectionDestination.LikedSongs) }
            )
        )
        if (wrappedAvailability !is WrappedAvailability.Hidden) {
            val wrappedTitle = when (val avail = wrappedAvailability) {
                is WrappedAvailability.Monthly -> avail.title
                is WrappedAvailability.Yearly -> avail.title
                WrappedAvailability.Hidden -> "Il tuo Wrapped"
            }
            tiles.add(
                BrowseCategoryTile(
                    id = "wrapped",
                    title = wrappedTitle,
                    color = Color(0xFFE8115B),
                    icon = Icons.Default.AutoAwesome,
                    onClick = { onOpenWrapped(true) }
                )
            )
        }
        tiles.add(
            BrowseCategoryTile(
                id = "on_repeat",
                title = "In ripetizione",
                color = Color(0xFF148A08),
                icon = Icons.Default.Repeat,
                artworkUri = madeForYou.firstOrNull { it.id == "on_repeat" }?.artworkUri,
                onClick = { onOpenCollection(CollectionDestination.SmartPlaylistDetail("on_repeat")) }
            )
        )
        tiles.add(
            BrowseCategoryTile(
                id = "hidden_gems",
                title = "Gemme nascoste",
                color = Color(0xFF7358FF),
                icon = Icons.Default.MusicNote,
                artworkUri = madeForYou.firstOrNull { it.id == "hidden_gems" }?.artworkUri,
                onClick = { onOpenCollection(CollectionDestination.SmartPlaylistDetail("hidden_gems")) }
            )
        )
        tiles.add(
            BrowseCategoryTile(
                id = "top_songs",
                title = "Più ascoltati",
                color = Color(0xFFE13300),
                icon = Icons.Default.AutoAwesome,
                artworkUri = albums.firstOrNull()?.albumArtUri,
                onClick = { onOpenCollection(CollectionDestination.TopSongs) }
            )
        )
        genres.forEachIndexed { index, genre ->
            tiles.add(
                BrowseCategoryTile(
                    id = "genre_${genre.name}",
                    title = genre.name,
                    color = SpotifyCardColors[index % SpotifyCardColors.size],
                    icon = Icons.Default.LibraryMusic,
                    artworkUri = genre.artworkUri,
                    onClick = { onOpenCollection(CollectionDestination.GenreDetail(genre.name)) }
                )
            )
        }
        tiles
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
    ) {
        // ─── Header + Barra di ricerca Spotify ────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "Cerca",
                style = MaterialTheme.typography.headlineMedium,
                color = SpotifyWhite
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Cosa vuoi ascoltare?",
                        color = SpotifyDarkGrey,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = SpotifyBlack)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Pulisci", tint = SpotifyBlack)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SpotifyWhite,
                    unfocusedContainerColor = SpotifyWhite,
                    focusedBorderColor = SpotifyWhite,
                    unfocusedBorderColor = SpotifyWhite,
                    focusedTextColor = SpotifyBlack,
                    unfocusedTextColor = SpotifyBlack,
                    cursorColor = SpotifyBlack
                )
            )
        }

        if (query.isBlank()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(2) }, key = "browse_title", contentType = "header") {
                    Text(
                        "Sfoglia tutto",
                        style = MaterialTheme.typography.titleMedium,
                        color = SpotifyWhite,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }

                itemsIndexed(
                    items = browseTiles,
                    key = { _, tile -> tile.id },
                    contentType = { _, _ -> "browse_tile" }
                ) { _, tile ->
                    BrowseCard(tile = tile)
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SearchResultFilter.entries, key = { it.name }, contentType = { "chip" }) { filter ->
                    val isSelected = activeFilter == filter
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) SpotifyGreen else SpotifyMedGrey,
                        modifier = Modifier.clickable { activeFilter = filter }
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

            val showSongs = activeFilter == SearchResultFilter.ALL || activeFilter == SearchResultFilter.SONGS
            val showArtists = activeFilter == SearchResultFilter.ALL || activeFilter == SearchResultFilter.ARTISTS
            val showAlbums = activeFilter == SearchResultFilter.ALL || activeFilter == SearchResultFilter.ALBUMS
            val showPlaylists = activeFilter == SearchResultFilter.ALL || activeFilter == SearchResultFilter.PLAYLISTS

            val noResults = (!showSongs || filteredSongs.isEmpty()) &&
                (!showArtists || matchingArtists.isEmpty()) &&
                (!showAlbums || matchingAlbums.isEmpty()) &&
                (!showPlaylists || matchingPlaylists.isEmpty())

            if (noResults) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Nessun risultato per \"$query\"",
                            style = MaterialTheme.typography.titleMedium,
                            color = SpotifyWhite
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Controlla l'ortografia o prova un'altra parola chiave.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyLightGrey
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    if (showArtists && matchingArtists.isNotEmpty()) {
                        item(key = "sec_artists", contentType = "section") { SearchSectionLabel("Artisti") }
                        items(
                            items = matchingArtists.take(8),
                            key = { "sa_${it.name}" },
                            contentType = { "artist_row" }
                        ) { artist ->
                            ListItem(
                                modifier = Modifier.clickable {
                                    onOpenCollection(CollectionDestination.ArtistDetail(artist.name))
                                },
                                headlineContent = {
                                    Text(artist.name, color = SpotifyWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text("Artista • ${artist.songCount} brani", color = SpotifyLightGrey)
                                },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
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
                                                color = SpotifyWhite,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }

                    if (showAlbums && matchingAlbums.isNotEmpty()) {
                        item(key = "sec_albums", contentType = "section") { SearchSectionLabel("Album") }
                        items(
                            items = matchingAlbums.take(8),
                            key = { "sal_${it.id}" },
                            contentType = { "album_row" }
                        ) { album ->
                            ListItem(
                                modifier = Modifier.clickable {
                                    onOpenCollection(CollectionDestination.AlbumDetail(album.id))
                                },
                                headlineContent = {
                                    Text(album.name, color = SpotifyWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text("Album • ${album.artist}", color = SpotifyLightGrey, maxLines = 1)
                                },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
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
                                            Icon(Icons.Default.Album, null, tint = SpotifyLightGrey)
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }

                    if (showPlaylists && matchingPlaylists.isNotEmpty()) {
                        item(key = "sec_playlists", contentType = "section") { SearchSectionLabel("Playlist") }
                        items(
                            items = matchingPlaylists,
                            key = { "sp_${it.playlist.id}" },
                            contentType = { "playlist_row" }
                        ) { p ->
                            ListItem(
                                modifier = Modifier.clickable {
                                    onOpenCollection(CollectionDestination.PlaylistDetail(p.playlist.id))
                                },
                                headlineContent = {
                                    Text(p.playlist.name, color = SpotifyWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text("Playlist • ${p.songs.size} brani", color = SpotifyLightGrey)
                                },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(SpotifyMedGrey),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val artUri = p.songs.firstOrNull()?.toDomain()?.albumArtUri
                                        if (artUri != null) {
                                            AsyncImage(
                                                model = artUri,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = SpotifyLightGrey)
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }

                    if (showSongs && filteredSongs.isNotEmpty()) {
                        item(key = "sec_songs", contentType = "section") { SearchSectionLabel("Brani") }
                        items(
                            items = filteredSongs,
                            key = { "ss_${it.id}" },
                            contentType = { "song_row" }
                        ) { song ->
                            SongRow(
                                song = song,
                                isPlaying = song.id == playingSongId,
                                isFavorite = song.id in favoriteSongIds,
                                onClick = { viewModel.playSong(song, filteredSongs, "Risultati per \"$query\"") },
                                playlists = playlists,
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
    }
}

@Composable
private fun SearchSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = SpotifyWhite,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun BrowseCard(tile: BrowseCategoryTile) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(98.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tile.color)
            .clickable(onClick = tile.onClick)
            .padding(12.dp)
    ) {
        Text(
            text = tile.title,
            style = MaterialTheme.typography.titleSmall,
            color = SpotifyWhite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .align(Alignment.TopStart)
        )

        Box(
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 8.dp)
                .graphicsLayer { rotationZ = 22f }
                .clip(RoundedCornerShape(6.dp))
                .background(SpotifyBlack.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            if (tile.artworkUri != null) {
                AsyncImage(
                    model = tile.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    tile.icon,
                    contentDescription = null,
                    tint = SpotifyWhite.copy(alpha = 0.9f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
