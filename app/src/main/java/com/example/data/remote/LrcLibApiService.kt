package com.example.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Free, keyless LRCLIB REST API for fetching time-synced (.lrc) and plain lyrics.
 * Docs: https://lrclib.net/docs
 */
interface LrcLibApiService {

    companion object {
        const val BASE_URL = "https://lrclib.net/"
    }

    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String? = null,
        @Query("duration") durationSeconds: Int? = null
    ): Response<LrcLibTrackDto>

    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String
    ): Response<List<LrcLibTrackDto>>
}

@Serializable
data class LrcLibTrackDto(
    @SerialName("id") val id: Long = 0L,
    @SerialName("trackName") val trackName: String? = null,
    @SerialName("artistName") val artistName: String? = null,
    @SerialName("albumName") val albumName: String? = null,
    @SerialName("duration") val duration: Double? = null,
    @SerialName("instrumental") val instrumental: Boolean = false,
    @SerialName("plainLyrics") val plainLyrics: String? = null,
    @SerialName("syncedLyrics") val syncedLyrics: String? = null
)
