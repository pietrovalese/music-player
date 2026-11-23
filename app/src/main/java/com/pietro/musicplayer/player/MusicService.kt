package com.pietro.musicplayer.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import com.pietro.musicplayer.MainActivity

import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class MusicService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private val retriedMediaIds = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val mediaSourceFactory = DefaultMediaSourceFactory(this, TolerantAudioExtractorsFactory())

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true) // gestisce audio focus automaticamente
            .setHandleAudioBecomingNoisy(true)          // pausa quando stacchi le cuffie
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    retriedMediaIds.clear()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val currentItem = player.currentMediaItem
                val mediaId = currentItem?.mediaId ?: return
                val currentUri = currentItem.localConfiguration?.uri ?: currentItem.requestMetadata.mediaUri
                Log.w(
                    TAG,
                    "Playback error (${error.errorCodeName}) for mediaId=$mediaId uri=$currentUri",
                    error
                )

                if (currentUri?.scheme == "content" && retriedMediaIds.add(mediaId)) {
                    val fileUri = resolveDirectFileUri(currentUri)
                    if (fileUri != null) {
                        Log.i(TAG, "Retrying mediaId=$mediaId via direct file URI: $fileUri")
                        val fallbackItem = currentItem.buildUpon()
                            .setUri(fileUri)
                            .setRequestMetadata(
                                currentItem.requestMetadata.buildUpon()
                                    .setMediaUri(fileUri)
                                    .build()
                            )
                            .build()
                        val index = player.currentMediaItemIndex
                        if (index in 0 until player.mediaItemCount) {
                            player.replaceMediaItem(index, fallbackItem)
                            player.prepare()
                            player.play()
                        }
                    }
                }
            }
        })

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(MusicSessionCallback())
            .build()
    }

    private fun resolveDirectFileUri(contentUri: Uri): Uri? {
        return try {
            contentResolver.query(
                contentUri,
                arrayOf(MediaStore.Audio.Media.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val path = cursor.getString(0)
                    if (!path.isNullOrBlank()) {
                        val file = File(path)
                        if (file.exists() && file.canRead()) {
                            return Uri.fromFile(file)
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve direct file URI for $contentUri", e)
            null
        }
    }

    companion object {
        private const val TAG = "MusicService"
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    private inner class MusicSessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<androidx.media3.common.MediaItem>
        ): ListenableFuture<List<androidx.media3.common.MediaItem>> {
            val resolvedItems = mediaItems.map { item ->
                val resolvedUri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
                item.buildUpon()
                    .setUri(resolvedUri)
                    .build()
            }
            return Futures.immediateFuture(resolvedItems)
        }
    }
}