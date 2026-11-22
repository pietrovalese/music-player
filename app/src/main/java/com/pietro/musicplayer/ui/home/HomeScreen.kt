package com.pietro.musicplayer.ui.home

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pietro.musicplayer.R
import com.pietro.musicplayer.domain.recommendation.SmartPlaylist
import com.pietro.musicplayer.domain.recommendation.WrappedAvailability
import com.pietro.musicplayer.ui.library.CollectionDestination
import com.pietro.musicplayer.ui.library.LibraryViewModel
import com.pietro.musicplayer.ui.theme.*
import com.pietro.musicplayer.update.UpdateUiState
import java.util.Calendar

private enum class HomeFilter(val label: String) {
    ALL("Tutto"),
    MUSIC("Musica"),
    MIXES("Mix per te"),
    FAVORITES("Preferiti")
}

private data class QuickTileData(
    val title: String,
    val artworkUri: Uri?,
    val isLikedTile: Boolean = false,
    val isRepeatTile: Boolean = false,
    val accentColor: Color = SpotifyMedGrey,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    viewModel: LibraryViewModel,
    onOpenCollection: (CollectionDestination) -> Unit,
    onOpenWrapped: (startStory: Boolean) -> Unit,
    onOpenUpdates: () -> Unit = {}
) {
    val allSongs by viewModel.allSongs.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val recentSongs by viewModel.recentSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val dailyMixes by viewModel.dailyMixes.collectAsState()
    val madeForYou by viewModel.madeForYou.collectAsState()
    val becauseYouListened by viewModel.becauseYouListened.collectAsState()
    val wrappedAvailability by viewModel.wrappedAvailability.collectAsState()
    val wrappedStats by viewModel.wrappedStats.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    var selectedFilter by remember { mutableStateOf(HomeFilter.ALL) }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11  -> "Buongiorno"
            in 12..17 -> "Buon pomeriggio"
            else      -> "Buonasera"
        }
    }

    val quickTiles = remember(madeForYou, dailyMixes, albums) {
        val list = mutableListOf<QuickTileData>()
        list.add(
            QuickTileData(
                title = "Brani che ti piacciono",
                artworkUri = null,
                isLikedTile = true,
                onClick = { onOpenCollection(CollectionDestination.LikedSongs) }
            )
        )
        madeForYou.firstOrNull { it.id == "on_repeat" }?.let { onRepeat ->
            list.add(
                QuickTileData(
                    title = onRepeat.title,
                    artworkUri = onRepeat.artworkUri,
                    isRepeatTile = onRepeat.artworkUri == null,
                    accentColor = Color(onRepeat.accentColor),
                    onClick = {
                        onOpenCollection(CollectionDestination.SmartPlaylistDetail(onRepeat.id))
                    }
                )
            )
        }
        dailyMixes.take(2).forEach { mix ->
            list.add(
                QuickTileData(
                    title = mix.title,
                    artworkUri = mix.artworkUri,
                    accentColor = Color(mix.accentColor),
                    onClick = {
                        onOpenCollection(CollectionDestination.SmartPlaylistDetail(mix.id))
                    }
                )
            )
        }
        albums.take(6 - list.size).forEach { album ->
            list.add(
                QuickTileData(
                    title = album.name,
                    artworkUri = album.albumArtUri,
                    onClick = { onOpenCollection(CollectionDestination.AlbumDetail(album.id)) }
                )
            )
        }
        list.take(6)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ─── Header + Filter Chips ────────────────────────────────────────────
        item(key = "home_header", contentType = "header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1F3B2D), SpotifyBlack),
                            startY = 0f,
                            endY = 520f
                        )
                    )
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
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
                        text = greeting,
                        style = MaterialTheme.typography.headlineMedium,
                        color = SpotifyWhite,
                        modifier = Modifier.weight(1f)
                    )
                    if (wrappedAvailability !is WrappedAvailability.Hidden) {
                        IconButton(onClick = { onOpenWrapped(true) }) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Wrapped",
                                tint = SpotifyGreen
                            )
                        }
                    }
                    val hasUpdateBadge = updateState is UpdateUiState.UpdateAvailable ||
                        updateState is UpdateUiState.ReadyToInstall
                    Box {
                        IconButton(onClick = onOpenUpdates) {
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = "Aggiornamenti OTA",
                                tint = if (hasUpdateBadge) SpotifyGreen else SpotifyWhite
                            )
                        }
                        if (hasUpdateBadge) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-8).dp, y = 8.dp)
                                    .clip(CircleShape)
                                    .background(SpotifyGreen)
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.syncLibrary() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Sincronizza libreria",
                            tint = SpotifyWhite
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(HomeFilter.entries, key = { it.name }, contentType = { "chip" }) { filter ->
                        val selected = selectedFilter == filter
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) SpotifyGreen else SpotifyMedGrey,
                            modifier = Modifier.clickable { selectedFilter = filter }
                        ) {
                            Text(
                                text = filter.label,
                                color = if (selected) SpotifyBlack else SpotifyWhite,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // ─── Griglia rapida 2x3 Spotify ───────────────────────────────────────
        if ((selectedFilter == HomeFilter.ALL || selectedFilter == HomeFilter.MUSIC) && quickTiles.isNotEmpty()) {
            item(key = "quick_grid", contentType = "quick_grid") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickTiles.chunked(2).forEach { rowTiles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowTiles.forEach { tile ->
                                QuickAccessTile(
                                    tile = tile,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowTiles.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // ─── Banner Wrapped (solo a fine mese / fine anno) ────────────────────
        if (wrappedAvailability !is WrappedAvailability.Hidden &&
            (selectedFilter == HomeFilter.ALL || selectedFilter == HomeFilter.MIXES)
        ) {
            item(key = "wrapped_banner", contentType = "wrapped_banner") {
                val (badgeLabel, bannerTitle) = when (val avail = wrappedAvailability) {
                    is WrappedAvailability.Monthly ->
                        "RECAP DEL MESE • ${avail.periodLabel.uppercase()}" to avail.title
                    is WrappedAvailability.Yearly ->
                        "SPOTIFY WRAPPED • ${avail.periodLabel.uppercase()}" to avail.title
                    WrappedAvailability.Hidden -> "" to ""
                }
                WrappedHomeBanner(
                    badgeLabel = badgeLabel,
                    title = bannerTitle,
                    totalMinutes = wrappedStats.totalMinutes,
                    personalityTitle = wrappedStats.personalityTitle,
                    onClick = { onOpenWrapped(true) }
                )
            }
        }

        // ─── Sezione "Preferiti" ──────────────────────────────────────────────
        if (selectedFilter == HomeFilter.FAVORITES) {
            item(key = "favorites_carousel", contentType = "carousel") {
                SectionTitle("I tuoi brani preferiti")
                if (favoriteSongs.isEmpty()) {
                    Text(
                        "Nessun brano nei preferiti. Tocca il cuore su un brano per aggiungerlo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpotifyLightGrey,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(favoriteSongs, key = { "fav_${it.id}" }, contentType = { "big_card" }) { song ->
                            BigCard(
                                title = song.title,
                                subtitle = song.artist,
                                artUri = song.albumArtUri,
                                onClick = {
                                    viewModel.playSong(song, favoriteSongs, "Brani che ti piacciono")
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // ─── Creato per te (Daily Mix 1..4) ───────────────────────────────────
        if ((selectedFilter == HomeFilter.ALL || selectedFilter == HomeFilter.MIXES) && dailyMixes.isNotEmpty()) {
            item(key = "daily_mixes_carousel", contentType = "carousel") {
                SectionTitle("Creato per te")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(dailyMixes, key = { it.id }, contentType = { "mix_card" }) { mix ->
                        SmartMixCard(
                            mix = mix,
                            onClick = {
                                onOpenCollection(CollectionDestination.SmartPlaylistDetail(mix.id))
                            }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // ─── I tuoi mix intelligenti ──────────────────────────────────────────
        if ((selectedFilter == HomeFilter.ALL || selectedFilter == HomeFilter.MIXES) && madeForYou.isNotEmpty()) {
            item(key = "made_for_you_carousel", contentType = "carousel") {
                SectionTitle("Mix intelligenti per te")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(madeForYou, key = { it.id }, contentType = { "mix_card" }) { mix ->
                        SmartMixCard(
                            mix = mix,
                            onClick = {
                                onOpenCollection(CollectionDestination.SmartPlaylistDetail(mix.id))
                            }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // ─── Perché hai ascoltato [Artista] ───────────────────────────────────
        if ((selectedFilter == HomeFilter.ALL || selectedFilter == HomeFilter.MIXES) && becauseYouListened != null) {
            val (artistName, recommendedSongs) = becauseYouListened!!
            item(key = "because_you_listened_carousel", contentType = "carousel") {
                SectionTitle("Perché hai ascoltato $artistName")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(recommendedSongs, key = { "rec_${it.id}" }, contentType = { "big_card" }) { song ->
                        BigCard(
                            title = song.title,
                            subtitle = song.artist,
                            artUri = song.albumArtUri,
                            onClick = {
                                viewModel.playSong(
                                    song,
                                    recommendedSongs,
                                    "Simili a $artistName"
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // ─── Ascoltati di recente ─────────────────────────────────────────────
        if ((selectedFilter == HomeFilter.ALL || selectedFilter == HomeFilter.MUSIC) && recentSongs.isNotEmpty()) {
            item(key = "recent_songs_carousel", contentType = "carousel") {
                SectionTitle("Ascoltati di recente")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(recentSongs, key = { "recent_${it.id}" }, contentType = { "big_card" }) { song ->
                        BigCard(
                            title = song.title,
                            subtitle = song.artist,
                            artUri = song.albumArtUri,
                            onClick = {
                                viewModel.playSong(song, recentSongs, "Ascoltati di recente")
                            }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // ─── I tuoi artisti ───────────────────────────────────────────────────
        if ((selectedFilter == HomeFilter.ALL || selectedFilter == HomeFilter.MUSIC) && artists.isNotEmpty()) {
            item(key = "artists_carousel", contentType = "carousel") {
                SectionTitle("I tuoi artisti preferiti")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(artists.take(12), key = { it.name }, contentType = { "artist_card" }) { artist ->
                        ArtistCard(
                            name = artist.name,
                            artworkUri = artist.artworkUri,
                            onClick = {
                                onOpenCollection(CollectionDestination.ArtistDetail(artist.name))
                            }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // ─── Album consigliati ────────────────────────────────────────────────
        if ((selectedFilter == HomeFilter.ALL || selectedFilter == HomeFilter.MUSIC) && albums.isNotEmpty()) {
            item(key = "albums_carousel", contentType = "carousel") {
                SectionTitle("Album consigliati")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(albums.take(12), key = { it.id }, contentType = { "big_card" }) { album ->
                        BigCard(
                            title = album.name,
                            subtitle = album.artist,
                            artUri = album.albumArtUri,
                            onClick = {
                                onOpenCollection(CollectionDestination.AlbumDetail(album.id))
                            }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // ─── Tutti i brani ────────────────────────────────────────────────────
        if (allSongs.isNotEmpty() && selectedFilter == HomeFilter.MUSIC) {
            item(key = "all_songs_carousel", contentType = "carousel") {
                SectionTitle("Brani nella tua libreria")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(allSongs.take(12), key = { "all_${it.id}" }, contentType = { "big_card" }) { song ->
                        BigCard(
                            title = song.title,
                            subtitle = song.artist,
                            artUri = song.albumArtUri,
                            onClick = { viewModel.playSong(song, allSongs, "Tutti i brani") }
                        )
                    }
                }
            }
        }
    }
}

// ─── Componenti Home ──────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = SpotifyWhite,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun QuickAccessTile(
    tile: QuickTileData,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = tile.onClick),
        color = SpotifyMedGrey,
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        when {
                            tile.isLikedTile -> Brush.linearGradient(
                                listOf(SpotifyLikedPurple, SpotifyLikedMint)
                            )
                            tile.isRepeatTile -> Brush.linearGradient(
                                listOf(tile.accentColor, SpotifyBlack)
                            )
                            else -> Brush.linearGradient(
                                listOf(SpotifyElevatedGrey, SpotifyElevatedGrey)
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    tile.isLikedTile -> Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = SpotifyWhite,
                        modifier = Modifier.size(22.dp)
                    )
                    tile.artworkUri != null -> AsyncImage(
                        model = tile.artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    tile.isRepeatTile -> Icon(
                        Icons.Default.Repeat,
                        contentDescription = null,
                        tint = SpotifyWhite,
                        modifier = Modifier.size(22.dp)
                    )
                    else -> Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = SpotifyLightGrey,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = tile.title,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 13.sp,
                color = SpotifyWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun WrappedHomeBanner(
    badgeLabel: String,
    title: String,
    totalMinutes: Long,
    personalityTitle: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF450AF5),
                        Color(0xFFE8115B),
                        Color(0xFFE13300)
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = SpotifyBlack.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyWhite,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = SpotifyWhite
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "$totalMinutes min ascoltati • $personalityTitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyWhite.copy(alpha = 0.9f)
                )
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                color = SpotifyWhite,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    "Apri",
                    color = SpotifyBlack,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SmartMixCard(
    mix: SmartPlaylist,
    onClick: () -> Unit
) {
    val accent = Color(mix.accentColor)
    Column(
        modifier = Modifier
            .width(152.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(152.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SpotifyMedGrey)
        ) {
            if (mix.artworkUri != null) {
                AsyncImage(
                    model = mix.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(accent, SpotifyBlack))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = SpotifyWhite,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = mix.badgeText,
                    color = SpotifyWhite,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter)
                    .background(accent)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = mix.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = SpotifyWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = mix.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = SpotifyLightGrey,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun BigCard(
    title: String,
    subtitle: String,
    artUri: Uri?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(152.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(152.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SpotifyMedGrey),
            contentAlignment = Alignment.Center
        ) {
            if (artUri != null) {
                AsyncImage(
                    model = artUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = SpotifyLightGrey,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            color = SpotifyWhite,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = SpotifyLightGrey,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ArtistCard(
    name: String,
    artworkUri: Uri?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(124.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(124.dp)
                .clip(CircleShape)
                .background(SpotifyMedGrey),
            contentAlignment = Alignment.Center
        ) {
            if (artworkUri != null) {
                AsyncImage(
                    model = artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyWhite
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = name,
            color = SpotifyWhite,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Artista",
            color = SpotifyLightGrey,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
