package com.pietro.musicplayer.ui.wrapped

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pietro.musicplayer.R
import com.pietro.musicplayer.domain.recommendation.WrappedAvailability
import com.pietro.musicplayer.domain.recommendation.WrappedStats
import com.pietro.musicplayer.ui.library.CollectionDestination
import com.pietro.musicplayer.ui.library.LibraryViewModel
import com.pietro.musicplayer.ui.theme.*

@Composable
fun WrappedScreen(
    viewModel: LibraryViewModel,
    availability: WrappedAvailability,
    isActive: Boolean = true,
    startInStoryMode: Boolean,
    onStoryModeConsumed: () -> Unit,
    onOpenCollection: (CollectionDestination) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val stats by viewModel.wrappedStats.collectAsState()

    var showStory by remember { mutableStateOf(false) }
    var playlistSavedFeedback by remember { mutableStateOf(false) }

    val (headerTitle, periodLabel, periodSubtitle) = when (availability) {
        is WrappedAvailability.Monthly -> Triple(
            availability.title,
            availability.periodLabel,
            "Il riepilogo degli ascolti di ${availability.periodLabel}"
        )
        is WrappedAvailability.Yearly -> Triple(
            availability.title,
            availability.periodLabel,
            "Il tuo intero anno musicale in sintesi (${availability.periodLabel})"
        )
        WrappedAvailability.Hidden -> Triple("Wrapped", "", "")
    }

    BackHandler(enabled = isActive && !showStory, onBack = onDismiss)

    LaunchedEffect(startInStoryMode) {
        if (startInStoryMode) {
            showStory = true
            onStoryModeConsumed()
        }
    }

    val touchBlockerModifier = if (isActive) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {}
        )
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(touchBlockerModifier)
            .background(SpotifyBlack)
            .systemBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ─── Header ───────────────────────────────────────────────────────
            item(key = "wrapped_header", contentType = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF450AF5), SpotifyBlack)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Indietro",
                                tint = SpotifyWhite
                            )
                        }
                        Image(
                            painter = painterResource(id = R.drawable.spotify_logo),
                            contentDescription = "Spotify",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            headerTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = SpotifyWhite,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        periodSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = SpotifyLightGrey,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            // ─── Hero Card Avvio Storia Wrapped ───────────────────────────────
            item(key = "wrapped_hero", contentType = "hero") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF7358FF),
                                    Color(0xFFE8115B),
                                    Color(0xFFE13300)
                                )
                            )
                        )
                        .clickable { showStory = true }
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            "STORIA INTERATTIVA (${periodLabel.uppercase()})",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpotifyWhite.copy(alpha = 0.85f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Rivivi la tua musica",
                            style = MaterialTheme.typography.headlineMedium,
                            color = SpotifyWhite
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Scopri minuti totali, Top Artisti, Top Brani, generi e la tua personalità d'ascolto.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SpotifyWhite.copy(alpha = 0.9f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { showStory = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyWhite),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = SpotifyBlack)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Guarda la Storia Wrapped",
                                color = SpotifyBlack,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ─── Griglia Statistiche Chiave ───────────────────────────────────
            item(key = "wrapped_stats_grid", contentType = "stats_grid") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatTile(
                            value = "${stats.totalMinutes}",
                            label = "Minuti ascoltati",
                            accent = SpotifyGreen,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            value = "${stats.totalPlays}",
                            label = "Riproduzioni",
                            accent = Color(0xFF7358FF),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatTile(
                            value = "${stats.uniqueArtistsExplored}",
                            label = "Artisti ascoltati",
                            accent = Color(0xFFE8115B),
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            value = "${stats.favoriteSongsCount}",
                            label = "Brani preferiti",
                            accent = Color(0xFF0D73EC),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ─── Personalità e Fascia Oraria ──────────────────────────────────
            item(key = "wrapped_personality", contentType = "personality") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = SpotifyDarkGrey,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "PERSONALITÀ D'ASCOLTO",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpotifyGreen
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stats.personalityTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = SpotifyWhite
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stats.personalityDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SpotifyLightGrey
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            thickness = 0.5.dp,
                            color = SpotifyElevatedGrey
                        )
                        Text(
                            "ORARIO DI PUNTA",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE8115B)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stats.peakPeriodTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = SpotifyWhite
                        )
                        Text(
                            stats.peakPeriodSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyLightGrey
                        )
                    }
                }
            }

            // ─── Top Artisti ──────────────────────────────────────────────────
            if (stats.topArtists.isNotEmpty()) {
                item(key = "sec_top_artists", contentType = "section") {
                    Text(
                        "Top Artisti",
                        style = MaterialTheme.typography.titleLarge,
                        color = SpotifyWhite,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                items(
                    count = stats.topArtists.size,
                    key = { idx -> "ta_${stats.topArtists[idx].name}" },
                    contentType = { "top_artist_row" }
                ) { index ->
                    val artist = stats.topArtists[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenCollection(CollectionDestination.ArtistDetail(artist.name))
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "#${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (index == 0) SpotifyGreen else SpotifyLightGrey,
                            modifier = Modifier.width(34.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(50.dp)
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
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                artist.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = SpotifyWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (artist.playCount > 0) {
                                    "${artist.playCount} ascolti • ${artist.minutesListened} min"
                                } else {
                                    "${artist.songCount} brani in libreria"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SpotifyLightGrey
                            )
                        }
                    }
                }
            }

            // ─── Top Brani + Salva Playlist ───────────────────────────────────
            if (stats.topSongs.isNotEmpty()) {
                item(key = "sec_top_songs", contentType = "section") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Top Brani",
                            style = MaterialTheme.typography.titleLarge,
                            color = SpotifyWhite
                        )
                        TextButton(
                            onClick = {
                                viewModel.saveWrappedTopSongsAsPlaylist()
                                playlistSavedFeedback = true
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (playlistSavedFeedback) "Playlist salvata!" else "Salva playlist",
                                color = SpotifyGreen,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                val topSongList = stats.topSongs.map { it.song }
                items(
                    count = stats.topSongs.size,
                    key = { idx -> "ts_${stats.topSongs[idx].song.id}" },
                    contentType = { "top_song_row" }
                ) { index ->
                    val stat = stats.topSongs[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.playSong(stat.song, topSongList, headerTitle)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "#${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (index == 0) SpotifyGreen else SpotifyLightGrey,
                            modifier = Modifier.width(34.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SpotifyMedGrey),
                            contentAlignment = Alignment.Center
                        ) {
                            if (stat.song.albumArtUri != null) {
                                AsyncImage(
                                    model = stat.song.albumArtUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.MusicNote, null, tint = SpotifyLightGrey)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stat.song.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = SpotifyWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (stat.playCount > 0) {
                                    "${stat.song.artist} • ${stat.playCount} ascolti"
                                } else {
                                    stat.song.artist
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SpotifyLightGrey,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // ─── Top Generi ───────────────────────────────────────────────────
            if (stats.topGenres.isNotEmpty()) {
                item(key = "sec_top_genres", contentType = "section") {
                    Text(
                        "Top Generi",
                        style = MaterialTheme.typography.titleLarge,
                        color = SpotifyWhite,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                items(
                    count = stats.topGenres.size,
                    key = { idx -> "tg_${stats.topGenres[idx].name}" },
                    contentType = { "top_genre_row" }
                ) { index ->
                    val genre = stats.topGenres[index]
                    val barColor = SpotifyCardColors[index % SpotifyCardColors.size]
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenCollection(CollectionDestination.GenreDetail(genre.name))
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(genre.name, style = MaterialTheme.typography.bodyMedium, color = SpotifyWhite)
                            Text(
                                "${(genre.percentage * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = SpotifyLightGrey
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { genre.percentage.coerceIn(0.05f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = barColor,
                            trackColor = SpotifyMedGrey
                        )
                    }
                }
            }

            // ─── Bottone Condividi ────────────────────────────────────────────
            item(key = "wrapped_share_btn", contentType = "button") {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { shareWrappedSummary(context, stats, periodLabel) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Share, null, tint = SpotifyBlack)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Condividi il tuo Wrapped",
                        color = SpotifyBlack,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (showStory) {
            WrappedStoryOverlay(
                stats = stats,
                periodLabel = periodLabel,
                playlistSaved = playlistSavedFeedback,
                onSavePlaylist = {
                    viewModel.saveWrappedTopSongsAsPlaylist()
                    playlistSavedFeedback = true
                },
                onPlayTopSongs = {
                    val songs = stats.topSongs.map { it.song }
                    if (songs.isNotEmpty()) {
                        viewModel.playSong(songs.first(), songs, headerTitle)
                        showStory = false
                    }
                },
                onShare = { shareWrappedSummary(context, stats, periodLabel) },
                onClose = { showStory = false }
            )
        }
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = SpotifyDarkGrey,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = accent
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyLightGrey
            )
        }
    }
}

@Composable
private fun WrappedStoryOverlay(
    stats: WrappedStats,
    periodLabel: String,
    playlistSaved: Boolean,
    onSavePlaylist: () -> Unit,
    onPlayTopSongs: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    val slideCount = 5
    var currentSlide by remember { mutableIntStateOf(0) }
    val slideProgress = remember { Animatable(0f) }

    LaunchedEffect(currentSlide) {
        slideProgress.snapTo(0f)
        slideProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 7000, easing = LinearEasing)
        )
        if (currentSlide < slideCount - 1) {
            currentSlide += 1
        }
    }

    val slideGradients = listOf(
        listOf(Color(0xFF450AF5), Color(0xFF121212)),
        listOf(Color(0xFFE8115B), Color(0xFF181818)),
        listOf(Color(0xFF148A08), Color(0xFF121212)),
        listOf(Color(0xFFBC5900), Color(0xFF121212)),
        listOf(Color(0xFF7358FF), Color(0xFFE8115B), Color(0xFF121212))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(slideGradients[currentSlide]))
            .pointerInput(currentSlide) {
                detectTapGestures { offset ->
                    if (offset.x < size.width * 0.35f) {
                        if (currentSlide > 0) currentSlide -= 1
                    } else {
                        if (currentSlide < slideCount - 1) currentSlide += 1
                    }
                }
            }
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ─── Indicatori Storie ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 0 until slideCount) {
                    val progress = when {
                        i < currentSlide -> 1f
                        i == currentSlide -> slideProgress.value
                        else -> 0f
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = SpotifyWhite,
                        trackColor = SpotifyWhite.copy(alpha = 0.28f)
                    )
                }
            }

            // ─── Header ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "WRAPPED • ${periodLabel.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SpotifyWhite.copy(alpha = 0.9f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = SpotifyWhite)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ─── Contenuto Slide ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (currentSlide) {
                    0 -> SlideMinutesAndPersonality(stats)
                    1 -> SlideTopArtists(stats)
                    2 -> SlideTopSongs(
                        stats = stats,
                        playlistSaved = playlistSaved,
                        onSavePlaylist = onSavePlaylist
                    )
                    3 -> SlideGenresAndPeak(stats)
                    else -> SlideSummaryCard(
                        stats = stats,
                        periodLabel = periodLabel,
                        onShare = onShare,
                        onPlayTopSongs = onPlayTopSongs
                    )
                }
            }

            Text(
                text = if (currentSlide < slideCount - 1) {
                    "Tocca a destra per continuare • ${currentSlide + 1}/$slideCount"
                } else {
                    "Tocca a sinistra per tornare indietro • 5/5"
                },
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyWhite.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun SlideMinutesAndPersonality(stats: WrappedStats) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Hai ascoltato",
            style = MaterialTheme.typography.titleLarge,
            color = SpotifyWhite.copy(alpha = 0.9f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${stats.totalMinutes}",
            fontSize = 68.sp,
            fontWeight = FontWeight.Black,
            color = SpotifyGreen
        )
        Text(
            "minuti di musica",
            style = MaterialTheme.typography.headlineMedium,
            color = SpotifyWhite
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "${stats.totalPlays} riproduzioni • ${stats.uniqueSongsExplored} brani • ${stats.uniqueArtistsExplored} artisti",
            style = MaterialTheme.typography.bodyMedium,
            color = SpotifyWhite.copy(alpha = 0.85f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Surface(
            color = SpotifyBlack.copy(alpha = 0.45f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "LA TUA PERSONALITÀ SONORA",
                    style = MaterialTheme.typography.labelSmall,
                    color = SpotifyGreen
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stats.personalityTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = SpotifyWhite,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stats.personalityDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotifyLightGrey,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SlideTopArtists(stats: WrappedStats) {
    val top = stats.topArtists.firstOrNull()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Il tuo artista #1",
            style = MaterialTheme.typography.titleLarge,
            color = SpotifyWhite.copy(alpha = 0.9f)
        )
        Spacer(Modifier.height(16.dp))
        if (top != null) {
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .clip(CircleShape)
                    .background(SpotifyMedGrey),
                contentAlignment = Alignment.Center
            ) {
                if (top.artworkUri != null) {
                    AsyncImage(
                        model = top.artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        top.name.firstOrNull()?.uppercase() ?: "?",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        color = SpotifyWhite
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                top.name,
                style = MaterialTheme.typography.headlineMedium,
                color = SpotifyWhite,
                textAlign = TextAlign.Center
            )
            Text(
                if (top.playCount > 0) "${top.playCount} ascolti • ${top.minutesListened} min"
                else "${top.songCount} brani in libreria",
                style = MaterialTheme.typography.bodyMedium,
                color = SpotifyWhite.copy(alpha = 0.85f)
            )
        }

        Spacer(Modifier.height(24.dp))
        Surface(
            color = SpotifyBlack.copy(alpha = 0.4f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "CLASSIFICA TOP 5 ARTISTI",
                    style = MaterialTheme.typography.labelSmall,
                    color = SpotifyWhite.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(10.dp))
                stats.topArtists.forEachIndexed { idx, artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${idx + 1}. ${artist.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Medium,
                            color = SpotifyWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (artist.playCount > 0) "${artist.playCount} play" else "${artist.songCount} brani",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyLightGrey
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlideTopSongs(
    stats: WrappedStats,
    playlistSaved: Boolean,
    onSavePlaylist: () -> Unit
) {
    val top = stats.topSongs.firstOrNull()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Il tuo brano #1",
            style = MaterialTheme.typography.titleLarge,
            color = SpotifyWhite.copy(alpha = 0.9f)
        )
        Spacer(Modifier.height(14.dp))
        if (top != null) {
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SpotifyMedGrey),
                contentAlignment = Alignment.Center
            ) {
                if (top.song.albumArtUri != null) {
                    AsyncImage(
                        model = top.song.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.MusicNote, null, tint = SpotifyLightGrey, modifier = Modifier.size(48.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                top.song.title,
                style = MaterialTheme.typography.titleLarge,
                color = SpotifyWhite,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                top.song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = SpotifyWhite.copy(alpha = 0.85f)
            )
        }

        Spacer(Modifier.height(18.dp))
        Surface(
            color = SpotifyBlack.copy(alpha = 0.4f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "CLASSIFICA TOP 5 BRANI",
                    style = MaterialTheme.typography.labelSmall,
                    color = SpotifyWhite.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(8.dp))
                stats.topSongs.forEachIndexed { idx, stat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${idx + 1}. ${stat.song.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SpotifyWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            stat.song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyLightGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        if (stats.topSongs.isNotEmpty()) {
            Button(
                onClick = onSavePlaylist,
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = SpotifyBlack)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (playlistSaved) "Playlist salvata!" else "Salva Playlist Top Brani",
                    color = SpotifyBlack,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SlideGenresAndPeak(stats: WrappedStats) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "La tua Aura Musicale",
            style = MaterialTheme.typography.headlineMedium,
            color = SpotifyWhite
        )
        Spacer(Modifier.height(18.dp))

        if (stats.topGenres.isEmpty()) {
            Surface(
                color = SpotifyBlack.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "I file audio della tua libreria non contengono ancora tag genere sufficienti, ma il tuo ritmo d'ascolto è già tracciato!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotifyWhite,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Surface(
                color = SpotifyBlack.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "TOP GENERI",
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyWhite.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(12.dp))
                    stats.topGenres.forEachIndexed { idx, genre ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(genre.name, color = SpotifyWhite, style = MaterialTheme.typography.bodyMedium)
                            Text("${(genre.percentage * 100).toInt()}%", color = SpotifyLightGrey)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { genre.percentage.coerceIn(0.06f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = SpotifyCardColors[idx % SpotifyCardColors.size],
                            trackColor = SpotifyWhite.copy(alpha = 0.15f)
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Surface(
            color = SpotifyBlack.copy(alpha = 0.45f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "FASCIA ORARIA PREFERITA",
                    style = MaterialTheme.typography.labelSmall,
                    color = SpotifyGreen
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stats.peakPeriodTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = SpotifyWhite
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stats.peakPeriodSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotifyLightGrey
                )
            }
        }
    }
}

@Composable
private fun SlideSummaryCard(
    stats: WrappedStats,
    periodLabel: String,
    onShare: () -> Unit,
    onPlayTopSongs: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = SpotifyBlack.copy(alpha = 0.82f),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, SpotifyGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.spotify_logo),
                            contentDescription = "Spotify",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "WRAPPED ${periodLabel.uppercase()}",
                            style = MaterialTheme.typography.labelLarge,
                            color = SpotifyGreen
                        )
                    }
                    Text(
                        stats.personalityTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = SpotifyLightGrey
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "${stats.totalMinutes} MINUTI ASCOLTATI",
                    style = MaterialTheme.typography.headlineMedium,
                    color = SpotifyWhite
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TOP ARTISTI", style = MaterialTheme.typography.labelSmall, color = SpotifyGreen)
                        Spacer(Modifier.height(6.dp))
                        stats.topArtists.forEachIndexed { idx, a ->
                            Text(
                                "${idx + 1}. ${a.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SpotifyWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TOP BRANI", style = MaterialTheme.typography.labelSmall, color = SpotifyGreen)
                        Spacer(Modifier.height(6.dp))
                        stats.topSongs.forEachIndexed { idx, s ->
                            Text(
                                "${idx + 1}. ${s.song.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SpotifyWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onShare,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(Icons.Default.Share, null, tint = SpotifyBlack)
            Spacer(Modifier.width(8.dp))
            Text("Condividi Wrapped", color = SpotifyBlack, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onPlayTopSongs,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SpotifyWhite.copy(alpha = 0.6f))
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = SpotifyWhite)
            Spacer(Modifier.width(8.dp))
            Text("Ascolta i tuoi Top Brani", color = SpotifyWhite, fontWeight = FontWeight.Bold)
        }
    }
}

private fun shareWrappedSummary(
    context: Context,
    stats: WrappedStats,
    periodLabel: String
) {
    val topArtist = stats.topArtists.firstOrNull()?.name ?: "N/D"
    val topSong = stats.topSongs.firstOrNull()?.song?.let { "${it.title} - ${it.artist}" } ?: "N/D"
    val topGenre = stats.topGenres.firstOrNull()?.name ?: "Vari"

    val summary = buildString {
        appendLine("Il mio Spotify Wrapped ($periodLabel)")
        appendLine("Minuti ascoltati: ${stats.totalMinutes} (${stats.totalPlays} riproduzioni)")
        appendLine("Personalità: ${stats.personalityTitle}")
        appendLine("Artista #1: $topArtist")
        appendLine("Brano #1: $topSong")
        appendLine("Genere #1: $topGenre")
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, summary)
    }
    context.startActivity(Intent.createChooser(intent, "Condividi il tuo Wrapped"))
}
