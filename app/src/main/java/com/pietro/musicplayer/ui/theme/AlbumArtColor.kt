package com.pietro.musicplayer.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import android.graphics.Color as AndroidColor

private val DefaultDominantColor = Color(0xFF1E3A2F)
private val dominantColorCache = LruCache<String, Color>(128)
private val missingArtworkUris = Collections.synchronizedSet(HashSet<String>())

@Composable
fun rememberDominantColor(
    artworkUri: Uri?,
    fallbackColor: Color = DefaultDominantColor
): Color {
    val context = LocalContext.current
    val cacheKey = artworkUri?.toString()
    val cachedColor = if (cacheKey != null) dominantColorCache.get(cacheKey) else null
    val initialColor = cachedColor ?: fallbackColor

    val targetColor by produceState(
        initialValue = initialColor,
        key1 = cacheKey,
        key2 = fallbackColor
    ) {
        if (cacheKey == null || cacheKey in missingArtworkUris) {
            value = fallbackColor
            return@produceState
        }
        val hit = dominantColorCache.get(cacheKey)
        if (hit != null) {
            value = hit
            return@produceState
        }

        val extracted = withContext(Dispatchers.IO) {
            extractDominantColor(context, artworkUri)
        }
        if (extracted != null) {
            dominantColorCache.put(cacheKey, extracted)
            value = extracted
        } else {
            missingArtworkUris.add(cacheKey)
            value = fallbackColor
        }
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 350),
        label = "DominantAlbumColor"
    )
    return animatedColor
}

private fun extractDominantColor(context: Context, uri: Uri): Color? {
    var bitmap: Bitmap? = null
    return try {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        val sampleSize = (minOf(boundsOptions.outWidth, boundsOptions.outHeight) / 32).coerceAtLeast(1)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val width = bitmap.width
        val height = bitmap.height
        if (width == 0 || height == 0) return null

        val stepX = (width / 16).coerceAtLeast(1)
        val stepY = (height / 16).coerceAtLeast(1)
        val hsv = FloatArray(3)

        var weightedR = 0f
        var weightedG = 0f
        var weightedB = 0f
        var totalWeight = 0f

        for (y in 0 until height step stepY) {
            for (x in 0 until width step stepX) {
                val pixel = bitmap.getPixel(x, y)
                AndroidColor.colorToHSV(pixel, hsv)
                val saturation = hsv[1]
                val brightness = hsv[2]

                if (brightness < 0.12f) continue
                if (brightness > 0.92f && saturation < 0.18f) continue
                if (saturation < 0.12f && brightness < 0.25f) continue

                val weight = (0.35f + saturation * 1.8f) * (1f - kotlin.math.abs(brightness - 0.55f))
                if (weight <= 0f) continue

                weightedR += AndroidColor.red(pixel) * weight
                weightedG += AndroidColor.green(pixel) * weight
                weightedB += AndroidColor.blue(pixel) * weight
                totalWeight += weight
            }
        }

        if (totalWeight <= 0f) return null

        val avgR = (weightedR / totalWeight).toInt().coerceIn(0, 255)
        val avgG = (weightedG / totalWeight).toInt().coerceIn(0, 255)
        val avgB = (weightedB / totalWeight).toInt().coerceIn(0, 255)

        val avgHsv = FloatArray(3)
        AndroidColor.RGBToHSV(avgR, avgG, avgB, avgHsv)
        avgHsv[1] = avgHsv[1].coerceAtLeast(0.28f).coerceAtMost(0.82f)
        avgHsv[2] = avgHsv[2].coerceIn(0.24f, 0.48f)

        Color(AndroidColor.HSVToColor(avgHsv))
    } catch (_: Exception) {
        null
    } finally {
        bitmap?.recycle()
    }
}
