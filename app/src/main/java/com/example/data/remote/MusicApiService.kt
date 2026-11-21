package com.example.data.remote

import com.example.data.Song
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ---------------------------------------------------------------------------
// 1. Primary API: Official JioSaavn Web API (https://www.jiosaavn.com/)
// ---------------------------------------------------------------------------

@Serializable
data class JioSaavnSearchResponseDto(
    @SerialName("total") val total: Int = 0,
    @SerialName("start") val start: Int = 0,
    @SerialName("results") val results: List<JioSaavnSongDto> = emptyList()
)

@Serializable
data class JioSaavnSongDto(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String? = null,
    @SerialName("song") val song: String? = null,
    @SerialName("subtitle") val subtitle: String? = null,
    @SerialName("image") val image: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("more_info") val moreInfo: JioSaavnMoreInfoDto? = null
) {
    val encryptedMediaUrl: String?
        get() = moreInfo?.encryptedMediaUrl?.trim()?.takeIf { it.isNotEmpty() }

    fun toSong(
        streamUrl: String = "jiosaavn://$id",
        isDownloaded: Boolean = false,
        genreOverride: String? = null,
        isTrending: Boolean = false
    ): Song? {
        val songId = id.trim()
        if (songId.isEmpty() || streamUrl.isBlank()) return null

        val rawTitle = (title ?: song)?.trim().orEmpty()
        if (rawTitle.isEmpty()) return null

        val primaryNames = moreInfo?.artistMap?.primaryArtists
            ?.mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }
            .orEmpty()
        val fallbackNames = moreInfo?.artistMap?.artists
            ?.mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }
            .orEmpty()
        val artistText = when {
            primaryNames.isNotEmpty() -> primaryNames.joinToString(", ") { decodeHtmlEntities(it) }
            fallbackNames.isNotEmpty() -> fallbackNames.joinToString(", ") { decodeHtmlEntities(it) }
            !subtitle.isNullOrBlank() -> decodeHtmlEntities(subtitle.trim())
            else -> "Unknown Artist"
        }

        val albumTitle = moreInfo?.album
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::decodeHtmlEntities)
            ?: "Single"

        val durationSeconds = moreInfo?.duration?.intOrNull
            ?: moreInfo?.duration?.content?.toDoubleOrNull()?.toInt()
            ?: 0
        val durationMs = (durationSeconds.coerceAtLeast(1)) * 1000L

        val highResArt = normalizeJioSaavnArtwork(image)
        val resolvedGenre = genreOverride ?: mapLanguageToGenre(language)

        return Song(
            id = songId,
            title = decodeHtmlEntities(rawTitle),
            artist = artistText,
            album = albumTitle,
            duration = durationMs,
            artworkUrl = highResArt,
            streamUrl = streamUrl,
            isDownloaded = isDownloaded,
            genre = resolvedGenre,
            isTrending = isTrending,
            audioQualityLabel = "320kbps AAC"
        )
    }

    private fun normalizeJioSaavnArtwork(rawUrl: String?): String {
        val trimmed = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return ""
        val httpsUrl = if (trimmed.startsWith("http://", ignoreCase = true)) {
            "https://${trimmed.removePrefix("http://")}"
        } else {
            trimmed
        }
        return httpsUrl
            .replace("150x150", "500x500")
            .replace("50x50", "500x500")
    }

    private fun mapLanguageToGenre(language: String?): String {
        return when (language?.trim()?.lowercase()) {
            "hindi" -> "Bollywood"
            "punjabi" -> "Punjabi"
            "english" -> "English"
            null, "" -> "Music"
            else -> language.trim().replaceFirstChar { it.uppercase() }
        }
    }
}

@Serializable
data class JioSaavnMoreInfoDto(
    @SerialName("album") val album: String? = null,
    @SerialName("duration") val duration: JsonPrimitive? = null,
    @SerialName("encrypted_media_url") val encryptedMediaUrl: String? = null,
    @SerialName("artistMap") val artistMap: JioSaavnArtistMapDto? = null
)

@Serializable
data class JioSaavnArtistMapDto(
    @SerialName("primary_artists") val primaryArtists: List<JioSaavnArtistDto> = emptyList(),
    @SerialName("artists") val artists: List<JioSaavnArtistDto> = emptyList()
)

@Serializable
data class JioSaavnArtistDto(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null
)

@Serializable
data class JioSaavnAuthTokenResponseDto(
    @SerialName("auth_url") val authUrl: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("status") val status: String? = null
)

interface MusicApiService {

    @GET("api.php")
    suspend fun searchSongs(
        @Query("q") query: String,
        @Query("n") limit: Int = 25,
        @Query("p") page: Int = 1,
        @Query("__call") call: String = "search.getResults",
        @Query("_format") format: String = "json",
        @Query("_marker") marker: String = "0",
        @Query("api_version") apiVersion: String = "4",
        @Query("ctx") ctx: String = "web6dot0"
    ): JioSaavnSearchResponseDto

    @GET("api.php")
    suspend fun getSongDetails(
        @Query("pids") songId: String,
        @Query("__call") call: String = "song.getDetails",
        @Query("api_version") apiVersion: String = "4",
        @Query("_format") format: String = "json",
        @Query("ctx") ctx: String = "web6dot0",
        @Query("_marker") marker: String = "0"
    ): ResponseBody

    @GET("api.php")
    suspend fun generateAuthToken(
        @Query(value = "url", encoded = true) encodedEncryptedMediaUrl: String,
        @Query("bitrate") bitrate: Int = 320,
        @Query("__call") call: String = "song.generateAuthToken",
        @Query("api_version") apiVersion: String = "4",
        @Query("_format") format: String = "json",
        @Query("ctx") ctx: String = "web6dot0",
        @Query("_marker") marker: String = "0"
    ): JioSaavnAuthTokenResponseDto

    companion object {
        const val BASE_URL = "https://www.jiosaavn.com/"
    }
}

// ---------------------------------------------------------------------------
// 2. Backup Mirrors for Song Stream Lookup
// ---------------------------------------------------------------------------

@Serializable
data class SaavnMirrorSongsResponseDto(
    @SerialName("success") val success: Boolean = false,
    @SerialName("data") val data: List<SaavnMirrorSongDto> = emptyList()
)

@Serializable
data class SaavnMirrorSongDto(
    @SerialName("id") val id: String = "",
    @SerialName("downloadUrl") val downloadUrl: List<SaavnMediaLinkDto> = emptyList()
)

@Serializable
data class SaavnMediaLinkDto(
    @SerialName("quality") val quality: String? = null,
    @SerialName("url") val url: String? = null
)

interface SaavnMirrorApiService {

    @GET("songs/{id}")
    suspend fun getSongById(@Path("id") id: String): SaavnMirrorSongsResponseDto

    companion object {
        const val BASE_URL = "https://saavn.dev/api/"
    }
}

interface SaavnVercelMirrorApiService {

    @GET("songs")
    suspend fun getSongById(@Query("id") id: String): SaavnMirrorSongsResponseDto

    companion object {
        const val BASE_URL = "https://jiosaavn-api-privatecvc2.vercel.app/"
    }
}

// ---------------------------------------------------------------------------
// 3. Automatic Fallback: Audius Open Music API (https://api.audius.co/)
// ---------------------------------------------------------------------------

@Serializable
data class AudiusTracksResponseDto(
    @SerialName("data") val data: List<AudiusTrackDto> = emptyList()
)

@Serializable
data class AudiusTrackDto(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String? = null,
    @SerialName("duration") val duration: JsonPrimitive? = null,
    @SerialName("genre") val genre: String? = null,
    @SerialName("is_streamable") val isStreamable: Boolean = true,
    @SerialName("user") val user: AudiusUserDto? = null,
    @SerialName("artwork") val artwork: AudiusArtworkDto? = null
) {
    fun toSong(
        isDownloaded: Boolean = false,
        genreOverride: String? = null,
        isTrending: Boolean = false
    ): Song? {
        val trackId = id.trim()
        if (trackId.isEmpty() || !isStreamable) return null
        val trackTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        val durationSeconds = duration?.intOrNull
            ?: duration?.content?.toDoubleOrNull()?.toInt()
            ?: 0
        val durationMs = (durationSeconds.coerceAtLeast(1)) * 1000L

        val artworkUrl = artwork?.bestUrl.orEmpty()
        val artistName = user?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: user?.handle?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Independent Artist"

        return Song(
            id = "audius_$trackId",
            title = decodeHtmlEntities(trackTitle),
            artist = decodeHtmlEntities(artistName),
            album = genre?.takeIf { it.isNotBlank() } ?: "Audius Stream",
            duration = durationMs,
            artworkUrl = artworkUrl,
            streamUrl = "${AudiusApiService.BASE_URL}v1/tracks/$trackId/stream?app_name=${AudiusApiService.APP_NAME}",
            isDownloaded = isDownloaded,
            genre = genreOverride ?: genre?.takeIf { it.isNotBlank() } ?: "English",
            isTrending = isTrending,
            audioQualityLabel = "Full Stream"
        )
    }
}

@Serializable
data class AudiusUserDto(
    @SerialName("name") val name: String? = null,
    @SerialName("handle") val handle: String? = null
)

@Serializable
data class AudiusArtworkDto(
    @SerialName("1000x1000") val x1000: String? = null,
    @SerialName("480x480") val x480: String? = null,
    @SerialName("150x150") val x150: String? = null
) {
    val bestUrl: String?
        get() = listOfNotNull(x1000, x480, x150).firstOrNull { it.isNotBlank() }
}

interface AudiusApiService {

    @GET("v1/tracks/trending")
    suspend fun getTrendingTracks(
        @Query("app_name") appName: String = APP_NAME,
        @Query("limit") limit: Int = 25
    ): AudiusTracksResponseDto

    @GET("v1/tracks/search")
    suspend fun searchTracks(
        @Query("query") query: String,
        @Query("app_name") appName: String = APP_NAME,
        @Query("limit") limit: Int = 25
    ): AudiusTracksResponseDto

    companion object {
        const val BASE_URL = "https://api.audius.co/"
        const val APP_NAME = "Cadence"
    }
}

// ---------------------------------------------------------------------------
// HTML Entity Decoding Helper
// ---------------------------------------------------------------------------

private val HTML_ENTITY_REGEX = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")

fun decodeHtmlEntities(input: String): String {
    if (!input.contains('&')) return input
    var current = input
    repeat(2) {
        val decoded = HTML_ENTITY_REGEX.replace(current) { match ->
            val token = match.groupValues[1]
            when {
                token.startsWith("#x", ignoreCase = true) -> {
                    val code = token.substring(2).toIntOrNull(16)
                    if (code != null) code.toChar().toString() else match.value
                }
                token.startsWith("#") -> {
                    val code = token.substring(1).toIntOrNull()
                    if (code != null) code.toChar().toString() else match.value
                }
                else -> when (token) {
                    "quot" -> "\""
                    "amp" -> "&"
                    "apos" -> "'"
                    "lt" -> "<"
                    "gt" -> ">"
                    "nbsp" -> " "
                    "rsquo", "lsquo" -> "'"
                    "rdquo", "ldquo" -> "\""
                    "ndash" -> "–"
                    "mdash" -> "—"
                    "hellip" -> "…"
                    else -> match.value
                }
            }
        }
        if (decoded == current) return decoded
        current = decoded
    }
    return current
}
