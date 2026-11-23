package com.pietro.musicplayer.player

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.pietro.musicplayer.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingCommand: ((MediaController) -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val mediaItemCache = ConcurrentHashMap<Long, MediaItem>()

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _playedSongEvents = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    val playedSongEvents: SharedFlow<Long> = _playedSongEvents.asSharedFlow()

    init {
        ensureConnected()
    }

    private fun getMediaItem(song: Song): MediaItem =
        mediaItemCache.getOrPut(song.id) { song.toMediaItem() }

    // ─── Connessione ──────────────────────────────────────────────────────────

    fun connect() = ensureConnected()

    fun ensureConnected() {
        val currentCtrl = controller
        if (currentCtrl != null && currentCtrl.isConnected) return
        if (currentCtrl != null && !currentCtrl.isConnected) {
            currentCtrl.removeListener(playerListener)
            controller = null
        }

        val existingFuture = controllerFuture
        if (existingFuture != null) {
            if (existingFuture.isDone) {
                val doneCtrl = runCatching { existingFuture.get() }.getOrNull()
                if (doneCtrl != null && doneCtrl.isConnected) {
                    controller = doneCtrl
                    doneCtrl.addListener(playerListener)
                    syncStateFromController(doneCtrl)
                    if (doneCtrl.isPlaying) startPositionUpdates()
                    val action = pendingCommand
                    pendingCommand = null
                    action?.invoke(doneCtrl)
                    return
                } else {
                    MediaController.releaseFuture(existingFuture)
                    controllerFuture = null
                }
            } else {
                return
            }
        }

        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            val ctrl = runCatching { future.get() }.getOrNull()
            if (ctrl == null || !ctrl.isConnected) {
                controllerFuture = null
                return@addListener
            }
            controller = ctrl
            ctrl.addListener(playerListener)
            syncStateFromController(ctrl)
            if (ctrl.isPlaying) {
                startPositionUpdates()
            }
            val action = pendingCommand
            pendingCommand = null
            action?.invoke(ctrl)
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        stopPositionUpdates()
        pendingCommand = null
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private inline fun withController(crossinline action: (MediaController) -> Unit) {
        val ctrl = controller
        if (ctrl != null && ctrl.isConnected) {
            action(ctrl)
        } else {
            if (ctrl != null && !ctrl.isConnected) {
                ctrl.removeListener(playerListener)
                controller = null
                controllerFuture?.let { MediaController.releaseFuture(it) }
                controllerFuture = null
            }
            pendingCommand = { connectedCtrl -> action(connectedCtrl) }
            ensureConnected()
        }
    }

    private fun syncStateFromController(ctrl: MediaController) {
        val currentIdx = ctrl.currentMediaItemIndex.coerceAtLeast(0)
        _playbackPosition.value = ctrl.currentPosition.coerceAtLeast(0L)
        _playerState.value = _playerState.value.copy(
            isPlaying = ctrl.isPlaying,
            currentMediaItem = ctrl.currentMediaItem,
            shuffleEnabled = ctrl.shuffleModeEnabled,
            repeatMode = ctrl.repeatMode,
            duration = ctrl.duration.takeIf { it > 0 } ?: 0L,
            currentQueueIndex = currentIdx
        )
    }

    // ─── Comandi ──────────────────────────────────────────────────────────────

    fun playSong(
        song: Song,
        queue: List<Song> = emptyList(),
        contextTitle: String = "La tua libreria"
    ) {
        val fullQueue = if (queue.isEmpty()) listOf(song) else queue
        val clickedIndex = fullQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

        val maxPlayerWindow = 50
        val effectiveQueue = if (fullQueue.size <= maxPlayerWindow) {
            fullQueue
        } else {
            val half = maxPlayerWindow / 2
            val start = (clickedIndex - half).coerceIn(0, fullQueue.size - maxPlayerWindow)
            fullQueue.subList(start, start + maxPlayerWindow).toList()
        }

        val items = effectiveQueue.map { getMediaItem(it) }
        val startIndex = effectiveQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

        _playbackPosition.value = 0L
        _playerState.value = _playerState.value.copy(
            queue = effectiveQueue,
            currentQueueIndex = startIndex,
            currentMediaItem = items[startIndex],
            contextTitle = contextTitle,
            duration = song.duration.coerceAtLeast(0L),
            playbackError = null
        )

        withController { ctrl ->
            ctrl.setMediaItems(items, startIndex, 0L)
            ctrl.prepare()
            ctrl.play()
            _playedSongEvents.tryEmit(song.id)
        }
    }

    fun playQueueIndex(index: Int) {
        val currentQueue = _playerState.value.queue
        if (index !in currentQueue.indices) return
        _playbackPosition.value = 0L
        _playerState.value = _playerState.value.copy(
            currentQueueIndex = index,
            playbackError = null
        )
        withController { ctrl ->
            ctrl.seekTo(index, 0L)
            ctrl.play()
        }
    }

    fun addNextInQueue(song: Song) {
        val ctrl = controller
        val currentQueue = _playerState.value.queue
        if (ctrl == null || !ctrl.isConnected || currentQueue.isEmpty()) {
            playSong(song)
            return
        }
        val insertIndex = (ctrl.currentMediaItemIndex + 1).coerceIn(0, currentQueue.size)
        val updatedQueue = currentQueue.toMutableList().apply { add(insertIndex, song) }
        ctrl.addMediaItem(insertIndex, getMediaItem(song))
        _playerState.value = _playerState.value.copy(queue = updatedQueue)
    }

    fun addToQueue(song: Song) {
        val ctrl = controller
        val currentQueue = _playerState.value.queue
        if (ctrl == null || !ctrl.isConnected || currentQueue.isEmpty()) {
            playSong(song)
            return
        }
        val updatedQueue = currentQueue + song
        ctrl.addMediaItem(getMediaItem(song))
        _playerState.value = _playerState.value.copy(queue = updatedQueue)
    }

    fun removeFromQueue(index: Int) {
        val currentQueue = _playerState.value.queue
        if (index !in currentQueue.indices) return
        withController { ctrl ->
            ctrl.removeMediaItem(index)
            val updatedQueue = currentQueue.toMutableList().apply { removeAt(index) }
            val newIndex = ctrl.currentMediaItemIndex.coerceIn(0, (updatedQueue.size - 1).coerceAtLeast(0))
            _playerState.value = _playerState.value.copy(
                queue = updatedQueue,
                currentQueueIndex = newIndex
            )
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (minutes == null || minutes <= 0) {
            _playerState.value = _playerState.value.copy(sleepTimerRemainingMs = null)
            return
        }

        val totalMs = minutes * 60_000L
        _playerState.value = _playerState.value.copy(sleepTimerRemainingMs = totalMs)
        sleepTimerJob = scope.launch {
            var remaining = totalMs
            while (isActive && remaining > 0L) {
                delay(1_000L)
                remaining = (remaining - 1_000L).coerceAtLeast(0L)
                _playerState.value = _playerState.value.copy(
                    sleepTimerRemainingMs = remaining.takeIf { it > 0L }
                )
            }
            if (isActive) {
                controller?.pause()
                _playerState.value = _playerState.value.copy(sleepTimerRemainingMs = null)
            }
        }
    }

    fun togglePlayPause() {
        withController { ctrl ->
            if (ctrl.isPlaying) {
                ctrl.pause()
            } else {
                when (ctrl.playbackState) {
                    Player.STATE_IDLE -> {
                        ctrl.prepare()
                        ctrl.play()
                    }
                    Player.STATE_ENDED -> {
                        ctrl.seekTo(0L)
                        ctrl.play()
                    }
                    else -> ctrl.play()
                }
            }
        }
    }

    fun skipNext() {
        withController { ctrl -> ctrl.seekToNextMediaItem() }
    }

    fun skipPrevious() {
        withController { ctrl -> ctrl.seekToPreviousMediaItem() }
    }

    fun seekTo(position: Long) {
        val clamped = position.coerceAtLeast(0L)
        _playbackPosition.value = clamped
        withController { ctrl -> ctrl.seekTo(clamped) }
    }

    fun toggleShuffle() {
        withController { ctrl -> ctrl.shuffleModeEnabled = !ctrl.shuffleModeEnabled }
    }

    fun toggleRepeat() {
        withController { ctrl ->
            ctrl.repeatMode = when (ctrl.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // ─── Position tracking ────────────────────────────────────────────────────

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                val ctrl = controller
                if (ctrl != null && ctrl.isConnected) {
                    _playbackPosition.value = ctrl.currentPosition.coerceAtLeast(0L)
                    val newDuration = ctrl.duration.takeIf { it > 0 }
                    if (newDuration != null && newDuration != _playerState.value.duration) {
                        _playerState.value = _playerState.value.copy(duration = newDuration)
                    }
                }
                delay(200L)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    // ─── Listener ─────────────────────────────────────────────────────────────

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _playerState.value = _playerState.value.copy(
                isPlaying = false,
                playbackError = "Impossibile riprodurre il brano (${error.errorCodeName})"
            )
            stopPositionUpdates()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val ctrl = controller
            _playbackPosition.value = ctrl?.currentPosition?.coerceAtLeast(0L) ?: _playbackPosition.value
            _playerState.value = _playerState.value.copy(
                isPlaying = isPlaying,
                duration = ctrl?.duration?.takeIf { it > 0 } ?: _playerState.value.duration,
                playbackError = if (isPlaying) null else _playerState.value.playbackError
            )
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val ctrl = controller ?: return
            _playbackPosition.value = ctrl.currentPosition.coerceAtLeast(0L)
            val newDuration = ctrl.duration.takeIf { it > 0 } ?: _playerState.value.duration
            if (newDuration != _playerState.value.duration) {
                _playerState.value = _playerState.value.copy(duration = newDuration)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val ctrl = controller
            _playbackPosition.value = ctrl?.currentPosition?.coerceAtLeast(0L) ?: 0L
            _playerState.value = _playerState.value.copy(
                currentMediaItem = mediaItem,
                currentQueueIndex = ctrl?.currentMediaItemIndex?.coerceAtLeast(0)
                    ?: _playerState.value.currentQueueIndex,
                duration = ctrl?.duration?.takeIf { it > 0 } ?: 0L,
                playbackError = null
            )
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                mediaItem?.mediaId?.toLongOrNull()?.let { songId ->
                    _playedSongEvents.tryEmit(songId)
                }
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _playerState.value = _playerState.value.copy(shuffleEnabled = shuffleModeEnabled)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _playerState.value = _playerState.value.copy(repeatMode = repeatMode)
        }
    }
}

// ─── Modelli ──────────────────────────────────────────────────────────────────

@Immutable
data class PlayerState(
    val isPlaying: Boolean = false,
    val currentMediaItem: MediaItem? = null,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val duration: Long = 0L,
    val queue: List<Song> = emptyList(),
    val currentQueueIndex: Int = 0,
    val contextTitle: String = "La tua libreria",
    val sleepTimerRemainingMs: Long? = null,
    val playbackError: String? = null
)

fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setRequestMetadata(
            MediaItem.RequestMetadata.Builder()
                .setMediaUri(uri)
                .build()
        )
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArtUri)
                .build()
        )
        .build()
