package com.pietro.musicplayer.ui.player

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.pietro.musicplayer.data.db.PlaylistWithSongs
import com.pietro.musicplayer.domain.model.Song
import com.pietro.musicplayer.ui.library.LibraryViewModel
import com.pietro.musicplayer.ui.library.toMinutesSeconds
import com.pietro.musicplayer.ui.theme.*
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: LibraryViewModel,
    isActive: Boolean = true,
    onDismiss: () -> Unit,
    onNavigateToAlbum: (String?) -> Unit = {},
    onNavigateToArtist: (String?) -> Unit = {}
) {
    BackHandler(enabled = isActive, onBack = onDismiss)

    val context = LocalContext.current
    val playerState by viewModel.playerState.collectAsState()
    val songById by viewModel.songById.collectAsState()
    val favoriteSongIds by viewModel.favoriteSongIds.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    val metadata = playerState.currentMediaItem?.mediaMetadata
    val currentSongId = playerState.currentMediaItem?.mediaId?.toLongOrNull()
    val currentSong = currentSongId?.let { songById[it] }
    val isFavorite = currentSongId != null && currentSongId in favoriteSongIds
    val dominantColor = rememberDominantColor(metadata?.artworkUri)

    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

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
            .background(
                Brush.verticalGradient(
                    colors = listOf(dominantColor, Color(0xFF161616), SpotifyBlack)
                )
            )
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ─── Top bar ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Chiudi",
                        tint = SpotifyWhite,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "IN RIPRODUZIONE DA",
                        color = SpotifyLightGrey,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = playerState.contextTitle,
                        color = SpotifyWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                Box {
                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Opzioni",
                            tint = SpotifyWhite
                        )
                    }
                    if (showOptionsMenu) {
                        DropdownMenu(
                            expanded = true,
                            onDismissRequest = { showOptionsMenu = false },
                            modifier = Modifier.background(SpotifyMedGrey)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Aggiungi a playlist", color = SpotifyWhite) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = SpotifyGreen) },
                                onClick = {
                                    showOptionsMenu = false
                                    showPlaylistPicker = true
                                }
                            )
                            if (currentSong != null) {
                                DropdownMenuItem(
                                    text = { Text("Vai alla radio del brano", color = SpotifyWhite) },
                                    leadingIcon = { Icon(Icons.Default.Radio, null, tint = SpotifyGreen) },
                                    onClick = {
                                        showOptionsMenu = false
                                        viewModel.startSongRadio(currentSong)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Vai all'album", color = SpotifyWhite) },
                                leadingIcon = { Icon(Icons.Default.Album, null, tint = SpotifyWhite) },
                                onClick = {
                                    showOptionsMenu = false
                                    onNavigateToAlbum(currentSong?.album ?: metadata?.albumTitle?.toString())
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Vai all'artista", color = SpotifyWhite) },
                                leadingIcon = { Icon(Icons.Default.AccountCircle, null, tint = SpotifyWhite) },
                                onClick = {
                                    showOptionsMenu = false
                                    onNavigateToArtist(currentSong?.artist ?: metadata?.artist?.toString())
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Timer di spegnimento", color = SpotifyWhite) },
                                leadingIcon = { Icon(Icons.Default.Bedtime, null, tint = SpotifyWhite) },
                                onClick = {
                                    showOptionsMenu = false
                                    showSleepTimerDialog = true
                                }
                            )
                        }
                    }
                }
            }

            // ─── Album Art ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .widthIn(max = 330.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SpotifyMedGrey),
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
                        contentDescription = null,
                        tint = SpotifyLightGrey,
                        modifier = Modifier.size(80.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ─── Titolo + Preferiti ───────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metadata?.title?.toString() ?: "Sconosciuto",
                        color = SpotifyWhite,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    val artistText = metadata?.artist?.toString() ?: ""
                    Text(
                        text = artistText,
                        color = SpotifyLightGrey,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = artistText.isNotBlank()) {
                            onNavigateToArtist(currentSong?.artist ?: artistText)
                        }
                    )
                }
                IconButton(
                    onClick = {
                        if (currentSongId != null) {
                            viewModel.toggleFavorite(currentSongId)
                        }
                    }
                ) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Preferiti",
                        tint = if (isFavorite) SpotifyGreen else SpotifyWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            playerState.playbackError?.let { errorMessage ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFB00020).copy(alpha = 0.25f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFF8A80),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ─── Seekbar ──────────────────────────────────────────────────
            PlayerSeekBarSection(
                playbackPositionFlow = viewModel.playbackPosition,
                duration = playerState.duration,
                songKey = currentSongId,
                onSeekTo = { position -> viewModel.seekTo(position) }
            )

            Spacer(Modifier.height(12.dp))

            // ─── Controlli principali ─────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playerState.shuffleEnabled) SpotifyGreen else SpotifyLightGrey,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (playerState.shuffleEnabled) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(SpotifyGreen)
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                    }
                }

                IconButton(onClick = { viewModel.skipPrevious() }) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Precedente",
                        tint = SpotifyWhite,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(SpotifyWhite)
                        .clickable { viewModel.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pausa",
                        tint = SpotifyBlack,
                        modifier = Modifier.size(38.dp)
                    )
                }

                IconButton(onClick = { viewModel.skipNext() }) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Successivo",
                        tint = SpotifyWhite,
                        modifier = Modifier.size(38.dp)
                    )
                }

                val repeatActive = playerState.repeatMode != Player.REPEAT_MODE_OFF
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { viewModel.toggleRepeat() }) {
                        Icon(
                            when (playerState.repeatMode) {
                                Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = "Ripeti",
                            tint = if (repeatActive) SpotifyGreen else SpotifyLightGrey,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (repeatActive) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(SpotifyGreen)
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ─── Azioni inferiori Spotify ─────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionIconLabel(
                    icon = Icons.Default.Radio,
                    label = "Radio",
                    tint = SpotifyLightGrey,
                    onClick = {
                        if (currentSong != null) viewModel.startSongRadio(currentSong)
                    }
                )
                ActionIconLabel(
                    icon = Icons.Default.Bedtime,
                    label = playerState.sleepTimerRemainingMs?.toMinutesSeconds() ?: "Timer",
                    tint = if (playerState.sleepTimerRemainingMs != null) SpotifyGreen else SpotifyLightGrey,
                    onClick = { showSleepTimerDialog = true }
                )
                ActionIconLabel(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    label = "Coda",
                    tint = SpotifyLightGrey,
                    onClick = { showQueueSheet = true }
                )
                ActionIconLabel(
                    icon = Icons.Default.Share,
                    label = "Condividi",
                    tint = SpotifyLightGrey,
                    onClick = {
                        val title = metadata?.title?.toString() ?: "Sconosciuto"
                        val artist = metadata?.artist?.toString().orEmpty()
                        val shareText = if (artist.isNotBlank()) {
                            "Sto ascoltando \"$title\" di $artist"
                        } else {
                            "Sto ascoltando \"$title\""
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Condividi brano"))
                    }
                )
            }
        }
    }

    if (showPlaylistPicker) {
        PlaylistPickerFromPlayer(
            playlists = playlists,
            onSelect = { playlistId ->
                if (currentSongId != null) viewModel.addSongToPlaylist(playlistId, currentSongId)
                showPlaylistPicker = false
            },
            onDismiss = { showPlaylistPicker = false }
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            remainingMs = playerState.sleepTimerRemainingMs,
            onSelectMinutes = { mins ->
                viewModel.setSleepTimer(mins)
                showSleepTimerDialog = false
            },
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = SpotifyDarkGrey,
            contentColor = SpotifyWhite
        ) {
            QueueBottomSheetContent(
                queue = playerState.queue,
                currentIndex = playerState.currentQueueIndex,
                contextTitle = playerState.contextTitle,
                onPlayIndex = { index -> viewModel.playQueueIndex(index) },
                onRemoveIndex = { index -> viewModel.removeFromQueue(index) }
            )
        }
    }
}

@Composable
private fun PlayerSeekBarSection(
    playbackPositionFlow: StateFlow<Long>,
    duration: Long,
    songKey: Long?,
    onSeekTo: (Long) -> Unit
) {
    val position by playbackPositionFlow.collectAsState()
    var dragProgress by remember(songKey) { mutableStateOf<Float?>(null) }

    val playbackProgress = if (duration > 0) {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val displayedProgress = dragProgress ?: playbackProgress
    val displayedPosition = dragProgress?.let { (it * duration).toLong() } ?: position

    Slider(
        value = displayedProgress,
        onValueChange = { newProgress ->
            dragProgress = newProgress.coerceIn(0f, 1f)
        },
        onValueChangeFinished = {
            dragProgress?.let { finalProgress ->
                onSeekTo((finalProgress * duration).toLong())
            }
            dragProgress = null
        },
        modifier = Modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
            thumbColor = SpotifyWhite,
            activeTrackColor = SpotifyWhite,
            inactiveTrackColor = SpotifyWhite.copy(alpha = 0.25f)
        )
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            displayedPosition.toMinutesSeconds(),
            color = SpotifyLightGrey,
            fontSize = 12.sp
        )
        Text(
            duration.toMinutesSeconds(),
            color = SpotifyLightGrey,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ActionIconLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun QueueBottomSheetContent(
    queue: List<Song>,
    currentIndex: Int,
    contextTitle: String,
    onPlayIndex: (Int) -> Unit,
    onRemoveIndex: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            "Coda di riproduzione",
            style = MaterialTheme.typography.titleLarge,
            color = SpotifyWhite
        )
        Text(
            "In riproduzione da: $contextTitle",
            style = MaterialTheme.typography.bodySmall,
            color = SpotifyLightGrey
        )
        Spacer(Modifier.height(12.dp))

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("La coda è vuota", color = SpotifyLightGrey)
            }
        } else {
            LazyColumn {
                itemsIndexed(
                    items = queue,
                    key = { index, song -> "${index}_${song.id}" },
                    contentType = { _, _ -> "queue_song" }
                ) { index, song ->
                    val isCurrent = index == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isCurrent) SpotifyMedGrey else Color.Transparent)
                            .clickable { onPlayIndex(index) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SpotifyMedGrey),
                            contentAlignment = Alignment.Center
                        ) {
                            if (song.albumArtUri != null) {
                                AsyncImage(
                                    model = song.albumArtUri,
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
                                song.title,
                                color = if (isCurrent) SpotifyGreen else SpotifyWhite,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist,
                                color = SpotifyLightGrey,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!isCurrent) {
                            IconButton(onClick = { onRemoveIndex(index) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Rimuovi dalla coda",
                                    tint = SpotifyLightGrey,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepTimerDialog(
    remainingMs: Long?,
    onSelectMinutes: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(5, 15, 30, 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timer di spegnimento", color = SpotifyWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (remainingMs != null) {
                    Text(
                        "Attivo: ${remainingMs.toMinutesSeconds()} rimanenti",
                        color = SpotifyGreen,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                }
                options.forEach { mins ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectMinutes(mins) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Timer, null, tint = SpotifyLightGrey, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("$mins minuti", color = SpotifyWhite, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (remainingMs != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectMinutes(null) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Disattiva timer", color = Color.Red, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Chiudi", color = SpotifyLightGrey)
            }
        },
        containerColor = SpotifyMedGrey
    )
}

@Composable
private fun PlaylistPickerFromPlayer(
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
                Text("Nessuna playlist.\nCreane una dalla scheda Libreria!", color = SpotifyLightGrey)
            } else {
                Column {
                    playlists.forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(p.playlist.id) }
                                .padding(vertical = 12.dp),
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
                                Text(p.playlist.name, color = SpotifyWhite, fontWeight = FontWeight.Medium)
                                Text("${p.songs.size} brani", color = SpotifyLightGrey, fontSize = 12.sp)
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF333333))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla", color = SpotifyLightGrey) }
        },
        containerColor = SpotifyMedGrey
    )
}
