package com.pietro.musicplayer.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class ReleaseInfo(
    val tagName: String,
    val title: String,
    val notes: String,
    val downloadUrl: String,
    val sizeBytes: Long
)

@Immutable
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateUiState
    data class Downloading(
        val release: ReleaseInfo,
        val progressPercent: Int,
        val downloadedMb: Float,
        val totalMb: Float
    ) : UpdateUiState
    data class ReadyToInstall(val apkFile: File, val release: ReleaseInfo) : UpdateUiState
    data class UpToDate(val currentVersion: String) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("spotify_ota_prefs", Context.MODE_PRIVATE)

    private val _githubRepo = MutableStateFlow(
        prefs.getString(KEY_GITHUB_REPO, DEFAULT_GITHUB_REPO)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_GITHUB_REPO
    )
    val githubRepo: StateFlow<String> = _githubRepo.asStateFlow()

    private val _autoCheckUpdates = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CHECK, true))
    val autoCheckUpdates: StateFlow<Boolean> = _autoCheckUpdates.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private var hasCheckedAtStartup = false

    fun getCurrentVersionInfo(): Pair<String, Long> {
        return try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val versionName = pkgInfo.versionName ?: "1.0.0"
            val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
            versionName to versionCode
        } catch (_: Exception) {
            "1.0.0" to 100L
        }
    }

    fun saveSettings(repo: String, autoCheck: Boolean) {
        val cleaned = repo.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .trim('/')
        prefs.edit()
            .putString(KEY_GITHUB_REPO, cleaned)
            .putBoolean(KEY_AUTO_CHECK, autoCheck)
            .apply()
        _githubRepo.value = cleaned
        _autoCheckUpdates.value = autoCheck
    }

    fun dismissNotice() {
        _updateState.value = UpdateUiState.Idle
    }

    suspend fun checkForUpdatesOnStartup() {
        if (hasCheckedAtStartup) return
        hasCheckedAtStartup = true
        if (!_autoCheckUpdates.value) return
        if (_githubRepo.value.isBlank()) return
        checkForUpdates(manual = false)
    }

    suspend fun checkForUpdates(manual: Boolean = false) {
        val repo = _githubRepo.value.trim()
        if (repo.isBlank() || !repo.contains("/")) {
            if (manual) {
                _updateState.value = UpdateUiState.Error(
                    "Inserisci un repository GitHub valido nel formato owner/repo (es. pietro/MusicPlayer)."
                )
            }
            return
        }

        _updateState.value = UpdateUiState.Checking
        val (currentVersion, _) = getCurrentVersionInfo()

        val result = withContext(Dispatchers.IO) {
            fetchLatestRelease(repo)
        }

        result.fold(
            onSuccess = { release ->
                val installedTag = prefs.getString(KEY_INSTALLED_TAG, null)
                if (isReleaseNewer(release.tagName, currentVersion, installedTag)) {
                    _updateState.value = UpdateUiState.UpdateAvailable(release)
                } else {
                    _updateState.value = if (manual) {
                        UpdateUiState.UpToDate(currentVersion)
                    } else {
                        UpdateUiState.Idle
                    }
                }
            },
            onFailure = { error ->
                _updateState.value = if (manual) {
                    UpdateUiState.Error(error.message ?: "Errore durante il controllo aggiornamenti.")
                } else {
                    UpdateUiState.Idle
                }
            }
        )
    }

    suspend fun downloadAndInstall(release: ReleaseInfo) {
        _updateState.value = UpdateUiState.Downloading(
            release = release,
            progressPercent = 0,
            downloadedMb = 0f,
            totalMb = (release.sizeBytes / (1024f * 1024f)).coerceAtLeast(0f)
        )

        val downloadResult = withContext(Dispatchers.IO) {
            downloadApkFile(release)
        }

        downloadResult.fold(
            onSuccess = { apkFile ->
                prefs.edit().putString(KEY_INSTALLED_TAG, release.tagName).apply()
                _updateState.value = UpdateUiState.ReadyToInstall(apkFile, release)
                triggerInstall(apkFile)
            },
            onFailure = { error ->
                _updateState.value = UpdateUiState.Error(
                    error.message ?: "Errore durante il download dell'aggiornamento."
                )
            }
        )
    }

    fun triggerInstall(apkFile: File) {
        if (!apkFile.exists()) {
            _updateState.value = UpdateUiState.Error("File APK non trovato in cache.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    private fun fetchLatestRelease(repo: String): Result<ReleaseInfo> {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("https://api.github.com/repos/$repo/releases/latest")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "SpotifyLocalAndroidApp")
            }

            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                return Result.failure(
                    IllegalStateException("Nessuna release trovata su GitHub per '$repo'.")
                )
            }
            if (code !in 200..299) {
                return Result.failure(
                    IllegalStateException("Risposta GitHub HTTP $code.")
                )
            }

            val bodyText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(bodyText)
            val tagName = json.optString("tag_name").trim()
            val title = json.optString("name").takeIf { it.isNotBlank() } ?: tagName
            val notes = json.optString("body").trim()
            val assets = json.optJSONArray("assets")

            var apkUrl = ""
            var apkSize = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            if (tagName.isBlank() || apkUrl.isBlank()) {
                return Result.failure(
                    IllegalStateException("Nessun file .apk allegato all'ultima release ($tagName).")
                )
            }

            Result.success(
                ReleaseInfo(
                    tagName = tagName,
                    title = title,
                    notes = notes,
                    downloadUrl = apkUrl,
                    sizeBytes = apkSize
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun downloadApkFile(release: ReleaseInfo): Result<File> {
        var connection: HttpURLConnection? = null
        return try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val tempFile = File(updatesDir, "Spotify-update.apk.tmp")
            val finalFile = File(updatesDir, "Spotify-update.apk")
            if (tempFile.exists()) tempFile.delete()

            val url = URL(release.downloadUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "SpotifyLocalAndroidApp")
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                return Result.failure(IllegalStateException("Download fallito (HTTP $code)."))
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            val totalMb = (totalBytes / (1024f * 1024f)).coerceAtLeast(0f)

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var downloaded = 0L
                    var lastReportTime = 0L
                    var lastPercent = -1

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        val percent = if (totalBytes > 0) {
                            ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        } else 0

                        if (percent != lastPercent && (now - lastReportTime >= 150L || percent == 100)) {
                            lastPercent = percent
                            lastReportTime = now
                            _updateState.value = UpdateUiState.Downloading(
                                release = release,
                                progressPercent = percent,
                                downloadedMb = downloaded / (1024f * 1024f),
                                totalMb = totalMb
                            )
                        }
                    }
                    output.flush()
                }
            }

            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            Result.success(finalFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun isReleaseNewer(
        remoteTag: String,
        currentVersionName: String,
        installedTag: String?
    ): Boolean {
        val remoteParts = parseNumericVersion(remoteTag)
        val currentParts = parseNumericVersion(currentVersionName)

        if (remoteParts != null && currentParts != null) {
            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }

        val normRemote = remoteTag.trim().removePrefix("v").removePrefix("V")
        val normCurrent = currentVersionName.trim().removePrefix("v").removePrefix("V")
        return normRemote != normCurrent && remoteTag != installedTag
    }

    private fun parseNumericVersion(raw: String): List<Int>? {
        val clean = raw.trim().removePrefix("v").removePrefix("V").substringBefore('-')
        if (clean.isEmpty()) return null
        val parts = clean.split('.')
        val nums = parts.map { it.toIntOrNull() ?: return null }
        return nums
    }

    private companion object {
        const val DEFAULT_GITHUB_REPO = "pietrovalese/music-player"
        const val KEY_GITHUB_REPO = "github_repo"
        const val KEY_AUTO_CHECK = "auto_check"
        const val KEY_INSTALLED_TAG = "installed_tag"
    }
}
