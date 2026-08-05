package app.naviamp.domain.lyrics

import app.naviamp.domain.LyricCue
import app.naviamp.domain.LyricCueLine
import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.Track
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.network.urlEncodedParameter
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Online rich-sync lookup with word timing preserved directly in Core models. */
open class MusixmatchLyricsProvider(
    private val httpClient: SharedHttpClient,
    private val nowMillis: () -> Long,
    private val baseUrl: String = RichSyncDesktopApiBaseUrl,
) : LyricsProvider {
    final override val id: String = "musixmatch"
    final override val capabilities: Set<LyricsTiming> = setOf(
        LyricsTiming.Plain,
        LyricsTiming.LineSynced,
        LyricsTiming.WordSynced,
    )

    private val tokenMutex = Mutex()
    private var userToken: String? = null

    final override suspend fun lyrics(track: Track): Lyrics? {
        val query = RichSyncLyricsQuery.fromTrack(track) ?: return null
        val initialToken = token() ?: return null
        val initialBody = responseBody(query, initialToken)
        if (initialBody != null && !initialBody.hasRichSyncUnauthorizedStatus()) {
            return parseRichSyncMacroResponse(initialBody, track)
        }

        invalidateToken(initialToken)
        val refreshedToken = token() ?: return null
        val refreshedBody = responseBody(query, refreshedToken) ?: return null
        return parseRichSyncMacroResponse(refreshedBody, track)
    }

    protected open suspend fun tokenBody(): String? =
        httpClient.get(
            url = "$baseUrl/token.get?" + listOf(
                "app_id" to RichSyncDesktopAppId,
                "user_language" to "en",
                "t" to nowMillis().toString(),
            ).encodedQuery(),
            headers = RichSyncHeaders,
        )

    protected open suspend fun responseBody(query: RichSyncLyricsQuery, token: String): String? =
        httpClient.get(
            url = "$baseUrl/macro.subtitles.get?" + (
                query.parameters + listOf(
                    "usertoken" to token,
                    "app_id" to RichSyncDesktopAppId,
                    "t" to nowMillis().toString(),
                )
            ).encodedQuery(),
            headers = RichSyncHeaders,
        )

    private suspend fun token(): String? = tokenMutex.withLock {
        userToken?.let { return@withLock it }
        val parsed = tokenBody()?.desktopUserToken()
        userToken = parsed
        parsed
    }

    private suspend fun invalidateToken(expected: String) = tokenMutex.withLock {
        if (userToken == expected) userToken = null
    }
}

data class RichSyncLyricsQuery(
    val artist: String,
    val title: String,
    val durationSeconds: Int,
) {
    val parameters: List<Pair<String, String>>
        get() = listOf(
            "format" to "json",
            "namespace" to "lyrics_richsynced",
            "optional_calls" to "track.richsync",
            "subtitle_format" to "lrc",
            "q_artist" to artist,
            "q_track" to title,
            "f_subtitle_length" to durationSeconds.toString(),
            "f_subtitle_length_max_deviation" to RichSyncDurationDeviationSeconds.toString(),
        )

    companion object {
        fun fromTrack(track: Track): RichSyncLyricsQuery? {
            val duration = track.durationSeconds?.takeIf { it > 0 } ?: return null
            if (track.title.isBlank() || track.artistName.isBlank()) return null
            return RichSyncLyricsQuery(
                artist = track.artistName,
                title = track.title,
                durationSeconds = duration,
            )
        }
    }
}

internal fun parseRichSyncMacroResponse(body: String, requestedTrack: Track): Lyrics? {
    val root = runCatching { RichSyncJson.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
    if (root.callStatusCode() != RichSyncSuccessStatus) return null
    val calls = root.objectAt("message", "body", "macro_calls") ?: return null
    val matchedTrack = calls.objectAt("matcher.track.get", "message", "body", "track")
    if (matchedTrack != null && !matchedTrack.matchesRequestedTrack(requestedTrack)) return null

    val richsyncCall = calls.objectAt("track.richsync.get")
    if (richsyncCall?.callStatusCode() == RichSyncSuccessStatus) {
        val richsync = richsyncCall.objectAt("message", "body", "richsync")
        val richsyncBody = richsync?.stringAt("richsync_body")
        if (!richsyncBody.isNullOrBlank()) {
            val subtitleLanguage = calls.arrayAt(
                "track.subtitles.get",
                "message",
                "body",
                "subtitle_list",
            )?.firstOrNull()?.jsonObject
                ?.objectAt("subtitle")
                ?.stringAt("subtitle_language")
            parseRichSyncBody(
                body = richsyncBody,
                language = subtitleLanguage ?: richsync.stringAt("richssync_language"),
                displayArtist = matchedTrack?.stringAt("artist_name") ?: requestedTrack.artistName,
                displayTitle = matchedTrack?.stringAt("track_name") ?: requestedTrack.title,
            )?.let { return it }
        }
    }

    val subtitle = calls.arrayAt("track.subtitles.get", "message", "body", "subtitle_list")
        ?.firstOrNull()
        ?.jsonObject
        ?.objectAt("subtitle")
    subtitle?.stringAt("subtitle_body")
        ?.takeIf { it.isNotBlank() }
        ?.let { text ->
            lyricsFromText(
                source = LyricsSource.Musixmatch,
                text = text,
                displayArtist = matchedTrack?.stringAt("artist_name") ?: requestedTrack.artistName,
                displayTitle = matchedTrack?.stringAt("track_name") ?: requestedTrack.title,
            )?.copy(language = subtitle.stringAt("subtitle_language"))?.let { return it }
        }

    val plain = calls.objectAt("track.lyrics.get", "message", "body", "lyrics")
    return plain?.stringAt("lyrics_body")
        ?.takeIf { it.isNotBlank() }
        ?.let { text ->
            lyricsFromText(
                source = LyricsSource.Musixmatch,
                text = text,
                displayArtist = matchedTrack?.stringAt("artist_name") ?: requestedTrack.artistName,
                displayTitle = matchedTrack?.stringAt("track_name") ?: requestedTrack.title,
            )?.copy(language = plain.stringAt("lyrics_language"))
        }
}

internal fun parseRichSyncBody(
    body: String,
    language: String? = null,
    displayArtist: String? = null,
    displayTitle: String? = null,
): Lyrics? {
    val richsyncLines = runCatching {
        RichSyncJson.decodeFromString<List<RichSyncLine>>(body)
    }.getOrNull() ?: return null

    val lines = mutableListOf<LyricLine>()
    val cueLines = mutableListOf<LyricCueLine>()
    richsyncLines.forEach { richLine ->
        val text = richLine.text.takeIf { it.isNotBlank() } ?: return@forEach
        val lineStart = richLine.startSeconds.toMillis()
        val lineEnd = richLine.endSeconds?.toMillis()?.coerceAtLeast(lineStart)
        val lineIndex = lines.size
        lines += LyricLine(startMillis = lineStart, text = text)

        val nonEmptyTokens = richLine.tokens.filter { it.content.isNotEmpty() }
        val tokenText = nonEmptyTokens.joinToString(separator = "") { it.content }
        if (nonEmptyTokens.isEmpty() || tokenText != text) return@forEach
        var byteCursor = 0
        var previousStart = lineStart
        val cues = nonEmptyTokens.map { token ->
            val calculatedStart = (richLine.startSeconds + token.offsetSeconds).toMillis()
            val start = calculatedStart.coerceAtLeast(previousStart)
            previousStart = start
            val byteLength = token.content.encodeToByteArray().size
            LyricCue(
                startMillis = start,
                endMillis = null,
                text = token.content,
                byteStart = byteCursor,
                byteEnd = byteCursor + byteLength - 1,
            ).also { byteCursor += byteLength }
        }.mapIndexed { index, cue ->
            val nextStart = nonEmptyTokens.getOrNull(index + 1)
                ?.let { token -> (richLine.startSeconds + token.offsetSeconds).toMillis() }
            cue.copy(
                endMillis = (nextStart ?: lineEnd)?.coerceAtLeast(cue.startMillis ?: lineStart),
            )
        }
        if (cues.isNotEmpty()) {
            cueLines += LyricCueLine(
                lineIndex = lineIndex,
                startMillis = lineStart,
                endMillis = lineEnd,
                text = text,
                cues = cues,
            )
        }
    }
    if (lines.isEmpty()) return null
    return Lyrics(
        source = LyricsSource.Musixmatch,
        synced = true,
        lines = lines,
        displayArtist = displayArtist,
        displayTitle = displayTitle,
        language = language,
        cueLines = cueLines,
    )
}

private fun JsonObject.matchesRequestedTrack(track: Track): Boolean {
    if (isEmpty()) return true
    val matchedTitle = stringAt("track_name")?.normalizedLyricsIdentity() ?: return false
    val matchedArtist = stringAt("artist_name")?.normalizedLyricsIdentity() ?: return false
    val requestedTitle = track.title.normalizedLyricsIdentity()
    val requestedArtist = track.artistName.normalizedLyricsIdentity()
    if (matchedTitle != requestedTitle || matchedArtist != requestedArtist) return false
    val matchedDuration = this["track_length"]?.jsonPrimitive?.intOrNull
    return matchedDuration == null || track.durationSeconds == null ||
        abs(matchedDuration - track.durationSeconds) <= RichSyncDurationDeviationSeconds
}

private fun String.normalizedLyricsIdentity(): String =
    lowercase().map { character -> if (character.isLetterOrDigit()) character else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ")

private fun String.desktopUserToken(): String? {
    val root = runCatching { RichSyncJson.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    if (root.callStatusCode() != RichSyncSuccessStatus) return null
    return root.stringAt("message", "body", "user_token")?.takeIf { it.isNotBlank() }
}

private fun String.hasRichSyncUnauthorizedStatus(): Boolean {
    val root = runCatching { RichSyncJson.parseToJsonElement(this).jsonObject }.getOrNull() ?: return false
    return root.callStatusCode() == RichSyncUnauthorizedStatus
}

private fun JsonObject.callStatusCode(): Int? =
    this["message"]?.jsonObject?.get("header")?.jsonObject?.get("status_code")?.jsonPrimitive?.intOrNull

private fun JsonObject.objectAt(vararg keys: String): JsonObject? {
    var current: JsonObject = this
    keys.forEachIndexed { index, key ->
        val element = current[key] ?: return null
        if (index == keys.lastIndex && element is JsonObject) return element
        current = runCatching { element.jsonObject }.getOrNull() ?: return null
    }
    return current
}

private fun JsonObject.arrayAt(vararg keys: String): JsonArray? {
    if (keys.isEmpty()) return null
    var current: JsonObject = this
    keys.dropLast(1).forEach { key ->
        current = current[key]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return null
    }
    return current[keys.last()]?.let { runCatching { it.jsonArray }.getOrNull() }
}

private fun JsonObject.stringAt(vararg keys: String): String? {
    if (keys.isEmpty()) return null
    var current: JsonObject = this
    keys.dropLast(1).forEach { key ->
        current = current[key]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return null
    }
    return current[keys.last()]?.jsonPrimitive?.contentOrNull
}

private fun List<Pair<String, String>>.encodedQuery(): String =
    joinToString("&") { (key, value) -> "$key=${value.urlEncodedParameter()}" }

private fun Double.toMillis(): Long = (this * 1_000.0).roundToLong()

@Serializable
private data class RichSyncLine(
    @kotlinx.serialization.SerialName("ts") val startSeconds: Double,
    @kotlinx.serialization.SerialName("te") val endSeconds: Double? = null,
    @kotlinx.serialization.SerialName("l") val tokens: List<RichSyncToken> = emptyList(),
    @kotlinx.serialization.SerialName("x") val text: String = "",
)

@Serializable
private data class RichSyncToken(
    @kotlinx.serialization.SerialName("c") val content: String,
    @kotlinx.serialization.SerialName("o") val offsetSeconds: Double,
)

private val RichSyncJson = Json { ignoreUnknownKeys = true }

private const val RichSyncDesktopApiBaseUrl = "https://apic-desktop.musixmatch.com/ws/1.1"
private const val RichSyncDesktopAppId = "web-desktop-app-v1.0"
private const val RichSyncSuccessStatus = 200
private const val RichSyncUnauthorizedStatus = 401
private const val RichSyncDurationDeviationSeconds = 3

private val RichSyncHeaders = mapOf(
    "Accept" to "application/json",
    "Accept-Language" to "en",
    "Cookie" to "AWSELBCORS=0; AWSELB=0",
    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.183 Safari/537.36",
)
