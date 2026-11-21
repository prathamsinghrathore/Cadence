package com.example.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PaletteExtractor {

    private val paletteCache = object : LruCache<String, AmbientPaletteColors>(64) {}

    fun cacheKeyFor(song: Song): String {
        return song.artworkUrl.ifBlank { "res_${song.fallbackArtworkRes ?: song.id}" }
    }

    fun getCached(cacheKey: String): AmbientPaletteColors? {
        return paletteCache.get(cacheKey)
    }

    suspend fun extractFromSong(
        context: Context,
        song: Song
    ): AmbientPaletteColors {
        val cacheKey = cacheKeyFor(song)
        paletteCache.get(cacheKey)?.let { cached ->
            return cached
        }

        val extracted = withContext(Dispatchers.IO) {
            val loader = context.imageLoader
            val dataSource: Any = song.artworkUrl.ifBlank {
                song.fallbackArtworkRes ?: return@withContext AmbientPaletteColors()
            }
            val request = ImageRequest.Builder(context)
                .data(dataSource)
                .allowHardware(false)
                .size(160)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()

            val result = loader.execute(request)
            var bitmap: Bitmap? = (result as? SuccessResult)
                ?.drawable
                ?.let { it as? BitmapDrawable }
                ?.bitmap

            if (bitmap == null && song.fallbackArtworkRes != null) {
                val fallbackRequest = ImageRequest.Builder(context)
                    .data(song.fallbackArtworkRes)
                    .allowHardware(false)
                    .size(160)
                    .build()
                bitmap = (loader.execute(fallbackRequest) as? SuccessResult)
                    ?.drawable
                    ?.let { it as? BitmapDrawable }
                    ?.bitmap
            }

            if (bitmap == null) {
                return@withContext AmbientPaletteColors()
            }

            withContext(Dispatchers.Default) {
                val palette = Palette.from(bitmap)
                    .maximumColorCount(16)
                    .generate()

                val vibrantRgb = palette.vibrantSwatch?.rgb
                    ?: palette.dominantSwatch?.rgb
                    ?: palette.lightVibrantSwatch?.rgb
                val dominantRgb = palette.dominantSwatch?.rgb
                    ?: palette.darkVibrantSwatch?.rgb
                    ?: palette.mutedSwatch?.rgb
                    ?: vibrantRgb
                val lightVibrantRgb = palette.lightVibrantSwatch?.rgb
                    ?: palette.vibrantSwatch?.rgb
                    ?: palette.lightMutedSwatch?.rgb
                    ?: dominantRgb
                val deepRgb = palette.darkVibrantSwatch?.rgb
                    ?: palette.darkMutedSwatch?.rgb
                    ?: dominantRgb
                val accentRgb = palette.vibrantSwatch?.rgb
                    ?: palette.lightVibrantSwatch?.rgb
                    ?: palette.lightMutedSwatch?.rgb

                val vibrantColor = vibrantRgb?.let { Color(it) } ?: ElectricViolet
                val dominantColor = dominantRgb?.let { Color(it) } ?: PulseRose
                val lightVibrantColor = lightVibrantRgb?.let { Color(it) } ?: AuroraCyan

                AmbientPaletteColors(
                    primaryGlow = vibrantColor,
                    secondaryGlow = lightVibrantColor,
                    deepTint = deepRgb?.let { Color(it) } ?: ObsidianSurface,
                    accent = accentRgb?.let { Color(it) } ?: PulseRose,
                    vibrantColor = vibrantColor,
                    dominantColor = dominantColor,
                    lightVibrantColor = lightVibrantColor
                )
            }
        }

        paletteCache.put(cacheKey, extracted)
        return extracted
    }
}
