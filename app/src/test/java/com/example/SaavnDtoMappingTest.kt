package com.example

import com.example.data.remote.AudiusTracksResponseDto
import com.example.data.remote.JioSaavnAuthTokenResponseDto
import com.example.data.remote.JioSaavnSearchResponseDto
import com.example.data.remote.NetworkModule
import com.example.data.remote.StreamUrlResolver
import com.example.data.remote.decodeHtmlEntities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SaavnDtoMappingTest {

    @Test
    fun `decodeHtmlEntities decodes quotes ampersands and apostrophes`() {
        val raw = "Kesariya (From &quot;Brahmastra&quot;) &amp; Arijit&#039;s Mix"
        assertEquals(
            "Kesariya (From \"Brahmastra\") & Arijit's Mix",
            decodeHtmlEntities(raw)
        )
    }

    @Test
    fun `official jiosaavn api response maps full-length song fields and 500x500 art`() {
        val searchJson = """
            {
              "total": 1,
              "start": 1,
              "results": [
                {
                  "id": "5WXAlMNt",
                  "title": "Kesariya (From &quot;Brahmastra&quot;)",
                  "image": "https://c.saavncdn.com/191/Kesariya-150x150.jpg",
                  "language": "hindi",
                  "more_info": {
                    "album": "Brahmastra &amp; Beyond",
                    "duration": "268",
                    "encrypted_media_url": "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyTest",
                    "artistMap": {
                      "primary_artists": [
                        { "id": "1", "name": "Pritam" },
                        { "id": "2", "name": "Arijit Singh" }
                      ]
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val tokenJson = """
            {
              "auth_url": "https://aac.saavncdn.com/191/track_320.mp4?Expires=1700000000",
              "type": "mp4",
              "status": "success"
            }
        """.trimIndent()

        val parsedSearch = NetworkModule.json.decodeFromString<JioSaavnSearchResponseDto>(searchJson)
        val parsedToken = NetworkModule.json.decodeFromString<JioSaavnAuthTokenResponseDto>(tokenJson)
        val dto = parsedSearch.results.first()
        val song = dto.toSong(streamUrl = parsedToken.authUrl.orEmpty())

        assertNotNull(song)
        song!!
        assertEquals("5WXAlMNt", song.id)
        assertEquals("Kesariya (From \"Brahmastra\")", song.title)
        assertEquals("Pritam, Arijit Singh", song.artist)
        assertEquals("Brahmastra & Beyond", song.album)
        assertEquals(268_000L, song.duration)
        assertEquals("https://c.saavncdn.com/191/Kesariya-500x500.jpg", song.artworkUrl)
        assertEquals("https://aac.saavncdn.com/191/track_320.mp4?Expires=1700000000", song.streamUrl)
        assertEquals("Bollywood", song.genre)
        assertEquals("320kbps AAC", song.audioQualityLabel)
    }

    @Test
    fun `extractEncryptedMediaUrlFromDetails supports songs array and id-keyed map`() {
        val songsArrayJson = """
            {
              "songs": [
                {
                  "id": "5WXAlMNt",
                  "more_info": { "encrypted_media_url": "encValueArray+123==" }
                }
              ]
            }
        """.trimIndent()
        val idKeyedJson = """
            {
              "5WXAlMNt": {
                "id": "5WXAlMNt",
                "more_info": { "encrypted_media_url": "encValueMap/456==" }
              }
            }
        """.trimIndent()

        assertEquals(
            "encValueArray+123==",
            StreamUrlResolver.extractEncryptedMediaUrlFromDetails(songsArrayJson, "5WXAlMNt")
        )
        assertEquals(
            "encValueMap/456==",
            StreamUrlResolver.extractEncryptedMediaUrlFromDetails(idKeyedJson, "5WXAlMNt")
        )
    }

    @Test
    fun `audius fallback response maps stream url and high-res artwork`() {
        val audiusJson = """
            {
              "data": [
                {
                  "id": "D7KyD",
                  "title": "Midnight Horizon",
                  "duration": 215,
                  "genre": "Electronic",
                  "is_streamable": true,
                  "user": {
                    "name": "Nova Pulse",
                    "handle": "novapulse"
                  },
                  "artwork": {
                    "150x150": "https://creator.audius.co/150.jpg",
                    "480x480": "https://creator.audius.co/480.jpg",
                    "1000x1000": "https://creator.audius.co/1000.jpg"
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = NetworkModule.json.decodeFromString<AudiusTracksResponseDto>(audiusJson)
        val song = parsed.data.first().toSong()

        assertNotNull(song)
        song!!
        assertEquals("audius_D7KyD", song.id)
        assertEquals("Midnight Horizon", song.title)
        assertEquals("Nova Pulse", song.artist)
        assertEquals(215_000L, song.duration)
        assertEquals("https://creator.audius.co/1000.jpg", song.artworkUrl)
        assertEquals("https://api.audius.co/v1/tracks/D7KyD/stream?app_name=Cadence", song.streamUrl)
    }
}
