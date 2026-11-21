package com.example.data.lyrics

import androidx.compose.runtime.Immutable

@Immutable
data class LyricLine(
    val timeMs: Long,
    val text: String
)

@Immutable
sealed interface LyricsUiState {
    @Immutable
    data object Idle : LyricsUiState

    @Immutable
    data class Loading(val songId: String) : LyricsUiState

    @Immutable
    data class Synced(
        val songId: String,
        val lines: List<LyricLine>,
        val isFromCache: Boolean = false
    ) : LyricsUiState

    @Immutable
    data class Plain(
        val songId: String,
        val lines: List<String>,
        val isFromCache: Boolean = false
    ) : LyricsUiState

    @Immutable
    data class Instrumental(
        val songId: String,
        val isFromCache: Boolean = false
    ) : LyricsUiState

    @Immutable
    data class NotFound(
        val songId: String,
        val message: String
    ) : LyricsUiState
}

/**
 * High-precision .lrc parser and track metadata cleaner optimized for LRCLIB lookups.
 */
object LrcParser {

    const val INSTRUMENTAL_BREAK_SYMBOL = "•••"

    // Matches [mm:ss.xx], [mm:ss.xxx], [m:ss.x], or [mm:ss]
    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    // Matches standard LRC metadata tags like [ar:Artist], [ti:Title], [al:Album], [by:Author], [length:03:45], [re:...], [ve:...]
    private val METADATA_TAG_REGEX = Regex("""^\[(?:ar|ti|al|au|by|length|re|ve|id|tool|lang):.*]$""", RegexOption.IGNORE_CASE)

    // Matches [offset:+/-ms]
    private val OFFSET_TAG_REGEX = Regex("""^\[offset:\s*([+-]?\d+)\s*]$""", RegexOption.IGNORE_CASE)

    // Common suffixes in Bollywood / Punjabi / English streaming catalogs that hurt exact lyrics matching
    private val PAREN_NOISE_REGEX = Regex(
        """\s*[\(\[](?:from\s+["“']?[^)\]]*["”']?|official\s*[^)\]]*|lyric\s*[^)\]]*|audio\s*[^)\]]*|video\s*[^)\]]*|full\s+song|feat\.?\s+[^)\]]*|ft\.?\s+[^)\]]*|remastered[^)\]]*|remix[^)\]]*|slowed[^)\]]*|reverb[^)\]]*|unplugged[^)\]]*|version[^)\]]*|bonus\s+track|explicit|clean|radio\s+edit|deluxe[^)\]]*)[\)\]]""",
        RegexOption.IGNORE_CASE
    )

    private val DASH_NOISE_REGEX = Regex(
        """\s*[-–—|]\s*(?:from\s+.*|remix.*|radio\s+edit.*|official.*|lyric.*|audio.*|video.*|single\s+version.*|remastered.*|feat\.?\s+.*|ft\.?\s+.*|unplugged.*|slowed.*|reverb.*|lofi.*|lo-fi.*)$""",
        RegexOption.IGNORE_CASE
    )

    private val MULTI_SPACE_REGEX = Regex("""\s+""")

    /**
     * Parses a standard .lrc string into an ascending time-sorted list of [LyricLine]s.
     * Supports multiple timestamps per line, ignores metadata headers, honors `[offset:ms]`,
     * and replaces blank instrumental pauses with `•••`.
     */
    fun parseSyncedLyrics(lrcText: String): List<LyricLine> {
        if (lrcText.isBlank()) return emptyList()

        var offsetMs = 0L
        val rawEntries = mutableListOf<LyricLine>()

        lrcText.lineSequence().forEach { rawLine ->
            val trimmedLine = rawLine.trim()
            if (trimmedLine.isEmpty()) return@forEach

            val offsetMatch = OFFSET_TAG_REGEX.matchEntire(trimmedLine)
            if (offsetMatch != null) {
                offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                return@forEach
            }

            if (METADATA_TAG_REGEX.matches(trimmedLine)) {
                return@forEach
            }

            val timestampMatches = TIMESTAMP_REGEX.findAll(trimmedLine).toList()
            if (timestampMatches.isEmpty()) return@forEach

            val lyricText = trimmedLine
                .replace(TIMESTAMP_REGEX, "")
                .trim()
                .let { if (it.isEmpty()) INSTRUMENTAL_BREAK_SYMBOL else it }

            for (match in timestampMatches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2].toLongOrNull() ?: continue
                val fractionRaw = match.groupValues[3]
                val fractionMs = when (fractionRaw.length) {
                    0 -> 0L
                    1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100L
                    2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10L
                    else -> fractionRaw.take(3).toLongOrNull() ?: 0L
                }
                val timeMs = (minutes * 60_000L + seconds * 1_000L + fractionMs + offsetMs).coerceAtLeast(0L)
                rawEntries.add(LyricLine(timeMs = timeMs, text = lyricText))
            }
        }

        if (rawEntries.isEmpty()) return emptyList()

        val sorted = rawEntries.sortedBy { it.timeMs }

        // Deduplicate consecutive instrumental break dots & remove trailing instrumental break
        val cleaned = ArrayList<LyricLine>(sorted.size)
        for (entry in sorted) {
            val prev = cleaned.lastOrNull()
            if (entry.text == INSTRUMENTAL_BREAK_SYMBOL && prev?.text == INSTRUMENTAL_BREAK_SYMBOL) {
                continue
            }
            if (prev != null && prev.timeMs == entry.timeMs && prev.text == entry.text) {
                continue
            }
            cleaned.add(entry)
        }
        while (cleaned.isNotEmpty() && cleaned.last().text == INSTRUMENTAL_BREAK_SYMBOL) {
            cleaned.removeAt(cleaned.lastIndex)
        }

        return cleaned
    }

    /**
     * Parses unsynced plain lyrics into clean display lines.
     */
    fun parsePlainLyrics(plainText: String): List<String> {
        if (plainText.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        var previousBlank = true
        plainText.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (!previousBlank) {
                    result.add("")
                    previousBlank = true
                }
            } else {
                result.add(trimmed)
                previousBlank = false
            }
        }
        while (result.isNotEmpty() && result.last().isEmpty()) {
            result.removeAt(result.lastIndex)
        }
        return result
    }

    /**
     * Strips extra suffixes like `(From "...")`, `[Official Video]`, `- Remix`, `(Feat. ...)`
     * so LRCLIB's exact and fuzzy search endpoints match much more reliably.
     */
    fun cleanTrackTitle(rawTitle: String): String {
        if (rawTitle.isBlank()) return ""
        var cleaned = rawTitle
            .replace(PAREN_NOISE_REGEX, "")
            .replace(DASH_NOISE_REGEX, "")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
            .trimEnd('-', '–', '—', '|', ':')
            .trim()
        if (cleaned.isBlank()) {
            cleaned = rawTitle.trim()
        }
        return cleaned
    }

    /**
     * Extracts the primary artist name from multi-artist credits like "Arijit Singh, Pritam, Amitabh Bhattacharya".
     */
    fun extractPrimaryArtist(rawArtist: String): String {
        if (rawArtist.isBlank()) return ""
        val separators = listOf(",", "&", " feat.", " feat ", " ft.", " ft ", " x ", " X ", "|", "/")
        var primary = rawArtist.trim()
        for (sep in separators) {
            val idx = primary.indexOf(sep, ignoreCase = true)
            if (idx > 0) {
                primary = primary.substring(0, idx).trim()
            }
        }
        return primary.ifBlank { rawArtist.trim() }
    }

    /**
     * Cleans album names by removing trailing "(Original Motion Picture Soundtrack)" or similar tags.
     */
    fun cleanAlbumName(rawAlbum: String): String {
        if (rawAlbum.isBlank()) return ""
        return rawAlbum
            .replace(Regex("""\s*[\(\[](?:original\s+motion\s+picture\s+soundtrack|soundtrack|deluxe[^)\]]*|expanded[^)\]]*)[\)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
            .ifBlank { rawAlbum.trim() }
    }
}
