package com.pietro.musicplayer.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SpotifyColorScheme = darkColorScheme(
    primary              = SpotifyGreen,
    onPrimary            = SpotifyBlack,
    primaryContainer     = SpotifyGreenDark,
    onPrimaryContainer   = SpotifyWhite,
    secondary            = SpotifyLightGrey,
    onSecondary          = SpotifyBlack,
    background           = SpotifyBlack,
    onBackground         = SpotifyWhite,
    surface              = SpotifyDarkGrey,
    onSurface            = SpotifyWhite,
    surfaceVariant       = SpotifyMedGrey,
    onSurfaceVariant     = SpotifyLightGrey,
    surfaceContainer     = SpotifyMedGrey,
    outline              = SpotifyMedGrey,
    outlineVariant       = Color(0xFF333333),
)

@Composable
fun MusicPlayerTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = SpotifyColorScheme,
        typography = Typography,
        content = content
    )
}
