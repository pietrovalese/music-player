package com.pietro.musicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pietro.musicplayer.player.PlayerController
import com.pietro.musicplayer.ui.MainScreen
import com.pietro.musicplayer.ui.theme.MusicPlayerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var playerController: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        playerController.ensureConnected()
        setContent {
            MusicPlayerTheme {
                MainScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        playerController.ensureConnected()
    }
}
