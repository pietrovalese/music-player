package com.pietro.musicplayer.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pietro.musicplayer.ui.theme.*
import com.pietro.musicplayer.update.ReleaseInfo
import com.pietro.musicplayer.update.UpdateUiState
import java.io.File
import java.util.Locale

@Composable
fun OtaUpdateDialog(
    currentVersion: String,
    githubRepo: String,
    autoCheckUpdates: Boolean,
    updateState: UpdateUiState,
    onSaveSettings: (String, Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onStartDownload: (ReleaseInfo) -> Unit,
    onInstallReady: (File) -> Unit,
    onDismiss: () -> Unit
) {
    var repoInput by remember(githubRepo) { mutableStateOf(githubRepo) }
    var autoCheckInput by remember(autoCheckUpdates) { mutableStateOf(autoCheckUpdates) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpotifyMedGrey,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Aggiornamenti OTA",
                    color = SpotifyWhite,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = SpotifyDarkGrey,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Versione installata",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpotifyLightGrey
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "v$currentVersion",
                            style = MaterialTheme.typography.titleSmall,
                            color = SpotifyGreen
                        )
                    }
                }

                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text("Repository GitHub (owner/repo)", color = SpotifyLightGrey) },
                    placeholder = { Text("es. pietrovalese/music-player", color = SpotifyLightGrey.copy(alpha = 0.6f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpotifyGreen,
                        unfocusedBorderColor = SpotifySubtleGrey,
                        focusedTextColor = SpotifyWhite,
                        unfocusedTextColor = SpotifyWhite,
                        cursorColor = SpotifyGreen
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Controlla all'avvio",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpotifyWhite
                    )
                    Switch(
                        checked = autoCheckInput,
                        onCheckedChange = {
                            autoCheckInput = it
                            onSaveSettings(repoInput, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SpotifyBlack,
                            checkedTrackColor = SpotifyGreen,
                            uncheckedThumbColor = SpotifyLightGrey,
                            uncheckedTrackColor = SpotifyDarkGrey
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSaveSettings(repoInput, autoCheckInput) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Salva", color = SpotifyWhite)
                    }
                    Button(
                        onClick = {
                            onSaveSettings(repoInput, autoCheckInput)
                            onCheckNow()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                        shape = RoundedCornerShape(20.dp),
                        enabled = updateState !is UpdateUiState.Checking &&
                            updateState !is UpdateUiState.Downloading
                    ) {
                        Text("Controlla ora", color = SpotifyBlack, fontWeight = FontWeight.Bold)
                    }
                }

                UpdateStateStatusCard(
                    updateState = updateState,
                    onStartDownload = onStartDownload,
                    onInstallReady = onInstallReady
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Chiudi", color = SpotifyLightGrey)
            }
        }
    )
}

@Composable
fun UpdateAvailablePopup(
    updateState: UpdateUiState,
    onStartDownload: (ReleaseInfo) -> Unit,
    onInstallReady: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val titleText = when (updateState) {
        is UpdateUiState.UpdateAvailable -> "Aggiornamento disponibile"
        is UpdateUiState.Downloading -> "Download aggiornamento..."
        is UpdateUiState.ReadyToInstall -> "Pronto per l'installazione"
        else -> return
    }

    AlertDialog(
        onDismissRequest = {
            if (updateState !is UpdateUiState.Downloading) onDismiss()
        },
        containerColor = SpotifyMedGrey,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    titleText,
                    color = SpotifyWhite,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            UpdateStateStatusCard(
                updateState = updateState,
                onStartDownload = onStartDownload,
                onInstallReady = onInstallReady
            )
        },
        confirmButton = {
            when (updateState) {
                is UpdateUiState.UpdateAvailable -> {
                    Button(
                        onClick = { onStartDownload(updateState.release) },
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Aggiorna ora", color = SpotifyBlack, fontWeight = FontWeight.Bold)
                    }
                }
                is UpdateUiState.ReadyToInstall -> {
                    Button(
                        onClick = { onInstallReady(updateState.apkFile) },
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Installa ora", color = SpotifyBlack, fontWeight = FontWeight.Bold)
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            if (updateState !is UpdateUiState.Downloading) {
                TextButton(onClick = onDismiss) {
                    Text("Più tardi", color = SpotifyLightGrey)
                }
            }
        }
    )
}

@Composable
private fun UpdateStateStatusCard(
    updateState: UpdateUiState,
    onStartDownload: (ReleaseInfo) -> Unit,
    onInstallReady: (File) -> Unit
) {
    when (updateState) {
        UpdateUiState.Idle -> Unit

        UpdateUiState.Checking -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpotifyDarkGrey)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = SpotifyGreen,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Controllo nuove release su GitHub...",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyWhite
                )
            }
        }

        is UpdateUiState.UpToDate -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpotifyDarkGrey)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircleOutline, null, tint = SpotifyGreen)
                Spacer(Modifier.width(10.dp))
                Text(
                    "L'app è aggiornata (v${updateState.currentVersion}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyWhite
                )
            }
        }

        is UpdateUiState.UpdateAvailable -> {
            val release = updateState.release
            val sizeMb = if (release.sizeBytes > 0) {
                String.format(Locale.US, " • %.1f MB", release.sizeBytes / (1024f * 1024f))
            } else ""

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SpotifyDarkGrey)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = SpotifyGreen.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "${release.tagName}$sizeMb",
                        color = SpotifyGreen,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    release.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = SpotifyWhite
                )
                if (release.notes.isNotBlank()) {
                    Text(
                        release.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = SpotifyLightGrey,
                        maxLines = 6
                    )
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { onStartDownload(release) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Scarica e installa", color = SpotifyBlack, fontWeight = FontWeight.Bold)
                }
            }
        }

        is UpdateUiState.Downloading -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SpotifyDarkGrey)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Scaricamento ${updateState.release.tagName}...",
                    style = MaterialTheme.typography.titleSmall,
                    color = SpotifyWhite
                )
                LinearProgressIndicator(
                    progress = { (updateState.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = SpotifyGreen,
                    trackColor = SpotifySubtleGrey
                )
                val progressText = if (updateState.totalMb > 0f) {
                    String.format(
                        Locale.US,
                        "%d%% (%.1f / %.1f MB)",
                        updateState.progressPercent,
                        updateState.downloadedMb,
                        updateState.totalMb
                    )
                } else {
                    String.format(Locale.US, "%.1f MB scaricati", updateState.downloadedMb)
                }
                Text(
                    progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyLightGrey
                )
            }
        }

        is UpdateUiState.ReadyToInstall -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SpotifyDarkGrey)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "APK ${updateState.release.tagName} pronto!",
                    style = MaterialTheme.typography.titleSmall,
                    color = SpotifyGreen
                )
                Text(
                    "Se Android chiede il permesso per installare app da questa fonte, abilitalo e poi tocca il pulsante qui sotto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyLightGrey
                )
                Button(
                    onClick = { onInstallReady(updateState.apkFile) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Installa APK scaricato", color = SpotifyBlack, fontWeight = FontWeight.Bold)
                }
            }
        }

        is UpdateUiState.Error -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF3A1A1A))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFF6B6B))
                Spacer(Modifier.width(10.dp))
                Text(
                    updateState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyWhite
                )
            }
        }
    }
}
