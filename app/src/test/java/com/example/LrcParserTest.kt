package com.example

import com.example.data.lyrics.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun parseSyncedLyrics_handlesTimestampsMetadataMultiTagsAndInstrumentalBreaks() {
        val rawLrc = """
            [ar:Arijit Singh]
            [ti:Kesariya]
            [al:Brahmastra]
            [by:Sonora]
            [length:04:28]
            [00:12.45]Mujhko itna bataye koi
            [00:18.125]Kaise tujhse dil na lagaye koi
            [00:25.00]
            [00:26.00]
            [00:30.50][01:15.20]Kesariya tera ishq hai piya
            [00:45.00]Rang jaaun jo main haath lagaaun
            [02:00.00]
        """.trimIndent()

        val parsed = LrcParser.parseSyncedLyrics(rawLrc)

        assertEquals(6, parsed.size)
        assertEquals(12_450L, parsed[0].timeMs)
        assertEquals("Mujhko itna bataye koi", parsed[0].text)

        assertEquals(18_125L, parsed[1].timeMs)
        assertEquals("Kaise tujhse dil na lagaye koi", parsed[1].text)

        // Consecutive empty instrumental lines collapse to a single ••• marker
        assertEquals(25_000L, parsed[2].timeMs)
        assertEquals(LrcParser.INSTRUMENTAL_BREAK_SYMBOL, parsed[2].text)

        // Multi-timestamp line splits and sorts ascending by timeMs
        assertEquals(30_500L, parsed[3].timeMs)
        assertEquals("Kesariya tera ishq hai piya", parsed[3].text)

        assertEquals(45_000L, parsed[4].timeMs)
        assertEquals("Rang jaaun jo main haath lagaaun", parsed[4].text)

        assertEquals(75_200L, parsed[5].timeMs)
        assertEquals("Kesariya tera ishq hai piya", parsed[5].text)
    }

    @Test
    fun parseSyncedLyrics_appliesOffsetTag() {
        val rawLrc = """
            [offset:500]
            [00:10.00]Line with offset
        """.trimIndent()

        val parsed = LrcParser.parseSyncedLyrics(rawLrc)
        assertEquals(1, parsed.size)
        assertEquals(10_500L, parsed[0].timeMs)
    }

    @Test
    fun cleanTrackTitle_stripsCatalogSuffixes() {
        assertEquals("Kesariya", LrcParser.cleanTrackTitle("Kesariya (From \"Brahmastra\")"))
        assertEquals("Blinding Lights", LrcParser.cleanTrackTitle("Blinding Lights [Official Video]"))
        assertEquals("Apna Bana Le", LrcParser.cleanTrackTitle("Apna Bana Le - Remix"))
        assertEquals("295", LrcParser.cleanTrackTitle("295 (Official Audio)"))
    }

    @Test
    fun extractPrimaryArtist_returnsLeadArtist() {
        assertEquals("Arijit Singh", LrcParser.extractPrimaryArtist("Arijit Singh, Pritam, Amitabh Bhattacharya"))
        assertEquals("The Weeknd", LrcParser.extractPrimaryArtist("The Weeknd & Ariana Grande"))
        assertEquals("Diljit Dosanjh", LrcParser.extractPrimaryArtist("Diljit Dosanjh feat. Sia"))
        assertTrue(LrcParser.extractPrimaryArtist("  AP Dhillon  ") == "AP Dhillon")
    }
}
