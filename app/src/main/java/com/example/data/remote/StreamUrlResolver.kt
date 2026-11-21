package com.example.data.remote

import android.util.Log
import com.example.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder

object StreamUrlResolver {

    private const val TAG = "StreamUrlResolver"

    /**
     * Resolves a fresh, playable direct HTTPS CDN audio stream URL right before
     * playback or offline download.
     */
    suspend fun resolveStreamUrl(
        song: Song,
        preferredBitrate: Int = 320,
        jioSaavnApi: MusicApiService = NetworkModule.musicApiService,
        saavnMirrorApi: SaavnMirrorApiService = NetworkModule.saavnMirrorApiService,
        saavnVercelMirrorApi: SaavnVercelMirrorApiService = NetworkModule.saavnVercelMirrorApiService
    ): String? = withContext(Dispatchers.IO) {
        // Method C: Audius Open API stream
        if (isAudiusSong(song)) {
            val rawId = song.id.removePrefix("audius_").trim()
            if (rawId.isNotEmpty()) {
                return@withContext "${AudiusApiService.BASE_URL}v1/tracks/$rawId/stream?app_name=${AudiusApiService.APP_NAME}"
            }
        }

        // Method A: Official JioSaavn song.getDetails -> URLEncoder -> song.generateAuthToken
        val primaryUrl = resolveViaOfficialJioSaavn(
            songId = song.id,
            preferredBitrate = preferredBitrate,
            jioSaavnApi = jioSaavnApi
        )
        if (!primaryUrl.isNullOrBlank()) {
            return@withContext primaryUrl
        }

        // Method B: Automatic Fallback Mirrors (saavn.dev -> vercel mirror)
        val mirrorUrl = resolveViaMirrors(
            songId = song.id,
            preferredBitrate = preferredBitrate,
            saavnMirrorApi = saavnMirrorApi,
            saavnVercelMirrorApi = saavnVercelMirrorApi
        )
        if (!mirrorUrl.isNullOrBlank()) {
            return@withContext mirrorUrl
        }

        // Last resort: use existing http(s) streamUrl if it wasn't a placeholder
        song.streamUrl
            .takeIf { it.startsWith("http", ignoreCase = true) }
            ?.let(::forceHttps)
    }

    fun isAudiusSong(song: Song): Boolean {
        return song.id.startsWith("audius_") ||
            song.streamUrl.contains("audius.co", ignoreCase = true)
    }

    private suspend fun resolveViaOfficialJioSaavn(
        songId: String,
        preferredBitrate: Int,
        jioSaavnApi: MusicApiService
    ): String? {
        val cleanId = songId.trim()
        if (cleanId.isEmpty()) return null

        val detailsBody = runCatching {
            jioSaavnApi.getSongDetails(songId = cleanId).use { it.string() }
        }.onFailure { err ->
            Log.w(TAG, "song.getDetails failed for $cleanId: ${err.localizedMessage}")
        }.getOrNull() ?: return null

        val encryptedMediaUrl = extractEncryptedMediaUrlFromDetails(detailsBody, cleanId)
        if (encryptedMediaUrl.isNullOrBlank()) {
            Log.w(TAG, "encrypted_media_url missing in song.getDetails for $cleanId")
            return null
        }

        val encodedUrl = URLEncoder.encode(encryptedMediaUrl, "UTF-8")
        val tokenResponse = runCatching {
            jioSaavnApi.generateAuthToken(
                encodedEncryptedMediaUrl = encodedUrl,
                bitrate = preferredBitrate
            )
        }.onFailure { err ->
            Log.w(TAG, "song.generateAuthToken failed for $cleanId: ${err.localizedMessage}")
        }.getOrNull() ?: return null

        val authUrl = tokenResponse.authUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return forceHttps(authUrl)
    }

    internal fun extractEncryptedMediaUrlFromDetails(
        rawJson: String,
        songId: String
    ): String? {
        val element = runCatching {
            NetworkModule.json.parseToJsonElement(rawJson)
        }.getOrNull() ?: return null

        if (element is JsonObject) {
            // Shape 1: { "songs": [ { "more_info": { "encrypted_media_url": "..." } } ] }
            val songsArray = element["songs"] as? JsonArray
            songsArray?.firstOrNull()?.let { firstSong ->
                findEncryptedMediaUrlRecursive(firstSong)?.let { return it }
            }
            // Shape 2: { "<songId>": { "more_info": { "encrypted_media_url": "..." } } }
            element[songId]?.let { keyedSong ->
                findEncryptedMediaUrlRecursive(keyedSong)?.let { return it }
            }
        }
        return findEncryptedMediaUrlRecursive(element)
    }

    private fun findEncryptedMediaUrlRecursive(element: JsonElement): String? {
        return when (element) {
            is JsonObject -> {
                val direct = (element["encrypted_media_url"] as? JsonPrimitive)
                    ?.contentOrNull
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                if (direct != null) return direct

                val moreInfo = element["more_info"]
                if (moreInfo != null) {
                    findEncryptedMediaUrlRecursive(moreInfo)?.let { return it }
                }
                element.values.firstNotNullOfOrNull(::findEncryptedMediaUrlRecursive)
            }
            is JsonArray -> element.firstNotNullOfOrNull(::findEncryptedMediaUrlRecursive)
            else -> null
        }
    }

    private suspend fun resolveViaMirrors(
        songId: String,
        preferredBitrate: Int,
        saavnMirrorApi: SaavnMirrorApiService,
        saavnVercelMirrorApi: SaavnVercelMirrorApiService
    ): String? {
        val saavnDevLinks = runCatching {
            saavnMirrorApi.getSongById(songId).data.firstOrNull()?.downloadUrl.orEmpty()
        }.getOrDefault(emptyList())
        pickDownloadUrlForBitrate(saavnDevLinks, preferredBitrate)?.let { return forceHttps(it) }

        val vercelLinks = runCatching {
            saavnVercelMirrorApi.getSongById(songId).data.firstOrNull()?.downloadUrl.orEmpty()
        }.getOrDefault(emptyList())
        pickDownloadUrlForBitrate(vercelLinks, preferredBitrate)?.let { return forceHttps(it) }

        return null
    }

    private fun pickDownloadUrlForBitrate(
        links: List<SaavnMediaLinkDto>,
        preferredBitrate: Int
    ): String? {
        val valid = links.filter { !it.url.isNullOrBlank() }
        if (valid.isEmpty()) return null

        val targetTag = "${preferredBitrate}kbps"
        return valid.firstOrNull { it.quality.equals(targetTag, ignoreCase = true) }?.url
            ?: valid.firstOrNull { it.quality.equals("160kbps", ignoreCase = true) }?.url
            ?: valid.lastOrNull()?.url
    }

    private fun forceHttps(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://", ignoreCase = true)) {
            "https://${trimmed.removePrefix("http://")}"
        } else {
            trimmed
        }
    }
}
