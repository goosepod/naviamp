package app.naviamp.domain.lyrics

import app.naviamp.domain.LyricCue
import app.naviamp.domain.LyricCueLine
import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.Track
import app.naviamp.domain.network.NaviampUserAgent
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.network.urlEncodedParameter
import kotlin.math.abs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Public, keyless LRCMUse lookup with millisecond line and word timing preserved in Core models. */
open class LrcmuxLyricsProvider(
    private val httpClient: SharedHttpClient,
    baseUrl: String = LrcmuxPublicApiBaseUrl,
) : LyricsProvider {
    final override val id: String = "lrcmux"
    final override val capabilities: Set<LyricsTiming> = setOf(
        LyricsTiming.Plain,
        LyricsTiming.LineSynced,
        LyricsTiming.WordSynced,
    )

    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    final override suspend fun lyrics(track: Track): Lyrics? {
        val query = LrcmuxLyricsQuery.fromTrack(track) ?: return null
        val body = responseBody(query) ?: return null
        return parseLrcmuxResponse(body, track)
    }

    protected open suspend fun responseBody(query: LrcmuxLyricsQuery): String? =
        httpClient.get(
            url = requestUrl(query),
            headers = LrcmuxJsonHeaders,
        )

    protected fun requestUrl(query: LrcmuxLyricsQuery): String =
        "$normalizedBaseUrl/get?" + query.parameters.joinToString("&") { (key, value) ->
            "$key=${value.urlEncodedParameter()}"
        }
}

data class LrcmuxLyricsQuery(
    val artist: String,
    val title: String,
    val album: String,
    val durationSeconds: Int,
) {
    val parameters: List<Pair<String, String>>
        get() = listOf(
            "artist" to artist,
            "title" to title,
            "album" to album,
            "duration" to durationSeconds.toString(),
            "level" to "word",
            "format" to "json",
        )

    companion object {
        fun fromTrack(track: Track): LrcmuxLyricsQuery? {
            val duration = track.durationSeconds?.takeIf { it > 0 } ?: return null
            if (track.title.isBlank() || track.artistName.isBlank()) return null
            return LrcmuxLyricsQuery(
                artist = track.artistName,
                title = track.title,
                album = track.albumTitle.orEmpty(),
                durationSeconds = duration,
            )
        }
    }
}

internal fun parseLrcmuxResponse(body: String, requestedTrack: Track): Lyrics? {
    val response = runCatching {
        LrcmuxJson.decodeFromString<LrcmuxLyricsResponse>(body)
    }.getOrNull() ?: return null
    if (response.meta.instrumental == true || !response.track.matches(requestedTrack)) return null

    val lines = mutableListOf<LyricLine>()
    val cueLines = mutableListOf<LyricCueLine>()
    response.lines.orEmpty().forEach { responseLine ->
        val text = responseLine.text.takeIf { it.isNotBlank() } ?: return@forEach
        val lineStart = responseLine.start?.coerceAtLeast(0)
        val lineEnd = responseLine.end
            ?.coerceAtLeast(lineStart ?: 0)
        val lineIndex = lines.size
        lines += LyricLine(startMillis = lineStart, text = text)

        responseLine.words
            ?.toCueLine(
                lineIndex = lineIndex,
                lineStart = lineStart,
                lineEnd = lineEnd,
                lineText = text,
            )
            ?.let(cueLines::add)
    }
    if (lines.isEmpty()) return null

    val hasTimedContent = lines.any { it.startMillis != null } || cueLines.isNotEmpty()
    return Lyrics(
        source = LyricsSource.Lrcmux,
        synced = response.meta.level != LrcmuxSyncLevelNone && hasTimedContent,
        lines = lines,
        displayArtist = response.track.artist.takeIf { it.isNotBlank() } ?: requestedTrack.artistName,
        displayTitle = response.track.title.takeIf { it.isNotBlank() } ?: requestedTrack.title,
        kind = response.meta.source?.id?.takeIf { it.isNotBlank() }?.let { "lrcmux:$it" } ?: "lrcmux",
        cueLines = cueLines,
    )
}

private fun List<LrcmuxWord>.toCueLine(
    lineIndex: Int,
    lineStart: Long?,
    lineEnd: Long?,
    lineText: String,
): LyricCueLine? {
    val words = filter { it.text.isNotEmpty() }
    if (words.isEmpty() || words.joinToString(separator = "") { it.text } != lineText) return null

    var byteCursor = 0
    var previousStart = lineStart ?: 0
    val cues = words.map { word ->
        val start = word.start.coerceAtLeast(previousStart)
        val end = word.end.coerceAtLeast(start)
        val byteLength = word.text.encodeToByteArray().size
        previousStart = start
        LyricCue(
            startMillis = start,
            endMillis = end,
            text = word.text,
            byteStart = byteCursor,
            byteEnd = byteCursor + byteLength - 1,
        ).also { byteCursor += byteLength }
    }
    return LyricCueLine(
        lineIndex = lineIndex,
        startMillis = lineStart ?: cues.first().startMillis,
        endMillis = lineEnd ?: cues.last().endMillis,
        text = lineText,
        cues = cues,
    )
}

private fun LrcmuxTrack.matches(requestedTrack: Track): Boolean {
    val matchedTitle = title.normalizedLyricsIdentity()
    val matchedArtist = artist.normalizedLyricsIdentity()
    if (matchedTitle.isEmpty() || matchedArtist.isEmpty()) return false
    if (matchedTitle != requestedTrack.title.normalizedLyricsIdentity()) return false
    if (matchedArtist != requestedTrack.artistName.normalizedLyricsIdentity()) return false
    return requestedTrack.durationSeconds == null || duration <= 0 ||
        abs(duration - requestedTrack.durationSeconds) <= LrcmuxDurationDeviationSeconds
}

private fun String.normalizedLyricsIdentity(): String =
    lowercase().map { character -> if (character.isLetterOrDigit()) character else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ")

@Serializable
private data class LrcmuxLyricsResponse(
    val lines: List<LrcmuxLine>? = null,
    val meta: LrcmuxMeta,
    val track: LrcmuxTrack,
)

@Serializable
private data class LrcmuxLine(
    val text: String,
    val start: Long? = null,
    val end: Long? = null,
    val words: List<LrcmuxWord>? = null,
)

@Serializable
private data class LrcmuxWord(
    val text: String,
    val start: Long,
    val end: Long,
)

@Serializable
private data class LrcmuxMeta(
    val level: String,
    val instrumental: Boolean? = null,
    val source: LrcmuxSource? = null,
)

@Serializable
private data class LrcmuxSource(
    val id: String,
    val name: String,
    val url: String? = null,
)

@Serializable
private data class LrcmuxTrack(
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
)

private val LrcmuxJson = Json { ignoreUnknownKeys = true }

private const val LrcmuxPublicApiBaseUrl = "https://api.lrcmux.dev"
private const val LrcmuxSyncLevelNone = "none"
private const val LrcmuxDurationDeviationSeconds = 5

private val LrcmuxJsonHeaders = mapOf(
    "Accept" to "application/json",
    "User-Agent" to NaviampUserAgent,
)
