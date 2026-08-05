package app.naviamp.domain.cache

import app.naviamp.domain.LyricAgent
import app.naviamp.domain.LyricCue
import app.naviamp.domain.LyricCueLine
import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.lyrics.LyricsProvider
import app.naviamp.domain.lyrics.LyricsTiming
import app.naviamp.domain.lyrics.timing
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

interface LyricsSidecarStore {
    fun cachedLyrics(
        sourceId: String,
        trackId: String,
    ): CachedLyricsRow?

    fun touchCachedLyrics(
        sourceId: String,
        trackId: String,
        lastAccessedEpochMillis: Long,
    )

    fun upsertCachedLyrics(
        sourceId: String,
        trackId: String,
        lyricSource: String,
        synced: Boolean,
        linesJson: String,
        displayArtist: String?,
        displayTitle: String?,
        language: String?,
        offsetMillis: Long,
        sizeBytes: Long,
        createdAtEpochMillis: Long,
        lastAccessedEpochMillis: Long,
    )

    fun cachedOnlineLyrics(
        sourceId: String,
        trackId: String,
        onlineProviderId: String,
    ): CachedLyricsRow?

    fun touchCachedOnlineLyrics(
        sourceId: String,
        trackId: String,
        onlineProviderId: String,
        lastAccessedEpochMillis: Long,
    )

    fun upsertCachedOnlineLyrics(
        sourceId: String,
        trackId: String,
        onlineProviderId: String,
        lyricSource: String,
        synced: Boolean,
        linesJson: String,
        displayArtist: String?,
        displayTitle: String?,
        language: String?,
        offsetMillis: Long,
        sizeBytes: Long,
        createdAtEpochMillis: Long,
        lastAccessedEpochMillis: Long,
    )
}

data class CachedLyricsRow(
    val lyricSource: String,
    val synced: Boolean,
    val linesJson: String,
    val displayArtist: String?,
    val displayTitle: String?,
    val language: String?,
    val offsetMillis: Long,
)

class LyricsSidecarCacheService(
    private val store: LyricsSidecarStore,
    private val nowMillis: () -> Long,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    suspend fun providerLyrics(
        sourceId: String,
        provider: MediaProvider,
        trackId: TrackId,
        acceptedTimings: Set<LyricsTiming> = LyricsTiming.entries.toSet(),
    ): Lyrics? =
        withContext(Dispatchers.Default) {
            val cached = cachedLyricsEntry(sourceId, trackId)
            if (
                cached != null &&
                cached.lyrics.source == LyricsSource.Provider &&
                cached.lyrics.timing in acceptedTimings &&
                cached.payloadVersion >= CurrentLyricsPayloadVersion
            ) {
                return@withContext cached.lyrics
            }
            val refreshed = provider.lyrics(trackId)
            if (refreshed != null) {
                storeLyrics(sourceId, trackId, refreshed)
                return@withContext refreshed
            }
            cached?.lyrics?.also { lyrics ->
                // Mark a legacy line-only payload as checked without discarding lyrics when the
                // provider has no replacement. This prevents a network retry on every view.
                storeLyrics(sourceId, trackId, lyrics)
            }
        }

    suspend fun cacheEmbeddedLyrics(
        sourceId: String,
        trackId: TrackId,
        lyrics: Lyrics,
    ): Lyrics =
        withContext(Dispatchers.Default) {
            storeLyrics(sourceId, trackId, lyrics)
            lyrics
        }

    suspend fun onlineLyrics(
        sourceId: String,
        track: Track,
        provider: LyricsProvider,
        acceptedTimings: Set<LyricsTiming> = LyricsTiming.entries.toSet(),
    ): Lyrics? =
        withContext(Dispatchers.Default) {
            readCachedOnlineLyrics(sourceId, track.id, provider.id)
                ?.takeIf { lyrics -> lyrics.timing in acceptedTimings }
                ?.let { return@withContext it }
            val lyrics = provider.lyrics(track) ?: return@withContext null
            storeOnlineLyrics(sourceId, track.id, provider.id, lyrics)
            lyrics
        }

    suspend fun cachedLyrics(
        sourceId: String,
        trackId: TrackId,
    ): Lyrics? =
        withContext(Dispatchers.Default) {
            cachedLyricsEntry(sourceId, trackId)?.lyrics
        }

    suspend fun cachedOnlineLyrics(
        sourceId: String,
        trackId: TrackId,
        providerId: String,
    ): Lyrics? =
        withContext(Dispatchers.Default) {
            readCachedOnlineLyrics(sourceId, trackId, providerId)
        }

    private fun cachedLyricsEntry(
        sourceId: String,
        trackId: TrackId,
    ): DecodedLyricsCache? {
        val row = store.cachedLyrics(sourceId, trackId.value) ?: return null
        store.touchCachedLyrics(sourceId, trackId.value, nowMillis())
        val payload = row.lyricsPayload()
        return DecodedLyricsCache(
            lyrics = payload.toLyrics(
                source = row.lyricSource.toLyricsSource(),
                synced = row.synced,
                displayArtist = row.displayArtist,
                displayTitle = row.displayTitle,
                language = row.language,
                offsetMillis = row.offsetMillis.toInt(),
            ),
            payloadVersion = payload.payloadVersion,
        )
    }

    private fun readCachedOnlineLyrics(
        sourceId: String,
        trackId: TrackId,
        onlineProviderId: String,
    ): Lyrics? {
        val row = store.cachedOnlineLyrics(sourceId, trackId.value, onlineProviderId) ?: return null
        store.touchCachedOnlineLyrics(sourceId, trackId.value, onlineProviderId, nowMillis())
        return row.toLyrics()
    }

    private fun storeLyrics(
        sourceId: String,
        trackId: TrackId,
        lyrics: Lyrics,
    ) {
        val linesJson = lyrics.linesJson()
        val now = nowMillis()
        store.upsertCachedLyrics(
            sourceId = sourceId,
            trackId = trackId.value,
            lyricSource = lyrics.source.name,
            synced = lyrics.synced,
            linesJson = linesJson,
            displayArtist = lyrics.displayArtist,
            displayTitle = lyrics.displayTitle,
            language = lyrics.language,
            offsetMillis = lyrics.offsetMillis.toLong(),
            sizeBytes = linesJson.encodeToByteArray().size.toLong(),
            createdAtEpochMillis = now,
            lastAccessedEpochMillis = now,
        )
    }

    private fun storeOnlineLyrics(
        sourceId: String,
        trackId: TrackId,
        onlineProviderId: String,
        lyrics: Lyrics,
    ) {
        val linesJson = lyrics.linesJson()
        val now = nowMillis()
        store.upsertCachedOnlineLyrics(
            sourceId = sourceId,
            trackId = trackId.value,
            onlineProviderId = onlineProviderId,
            lyricSource = lyrics.source.name,
            synced = lyrics.synced,
            linesJson = linesJson,
            displayArtist = lyrics.displayArtist,
            displayTitle = lyrics.displayTitle,
            language = lyrics.language,
            offsetMillis = lyrics.offsetMillis.toLong(),
            sizeBytes = linesJson.encodeToByteArray().size.toLong(),
            createdAtEpochMillis = now,
            lastAccessedEpochMillis = now,
        )
    }

    private fun Lyrics.linesJson(): String =
        json.encodeToString(LyricsDto.fromLyrics(this))

    private fun CachedLyricsRow.toLyrics(source: LyricsSource = lyricSource.toLyricsSource()): Lyrics =
        lyricsPayload().toLyrics(
            source = source,
            synced = synced,
            displayArtist = displayArtist,
            displayTitle = displayTitle,
            language = language,
            offsetMillis = offsetMillis.toInt(),
        )

    private fun CachedLyricsRow.lyricsPayload(): LyricsDto {
        val element = json.parseToJsonElement(linesJson)
        return if (element is JsonArray) {
            LyricsDto(lines = json.decodeFromString<List<LyricLineDto>>(linesJson))
        } else {
            json.decodeFromString<LyricsDto>(linesJson)
        }
    }
}

private fun String.toLyricsSource(): LyricsSource =
    runCatching { LyricsSource.valueOf(this) }.getOrDefault(LyricsSource.Provider)

@Serializable
private data class LyricLineDto(
    val startMillis: Long? = null,
    val text: String,
) {
    fun toLyricLine(): LyricLine =
        LyricLine(
            startMillis = startMillis,
            text = text,
        )

    companion object {
        fun fromLyricLine(line: LyricLine): LyricLineDto =
            LyricLineDto(
                startMillis = line.startMillis,
                text = line.text,
            )
    }
}

@Serializable
private data class LyricsDto(
    val payloadVersion: Int = 1,
    val lines: List<LyricLineDto>,
    val kind: String? = null,
    val agents: List<LyricAgentDto> = emptyList(),
    val cueLines: List<LyricCueLineDto> = emptyList(),
) {
    fun toLyrics(
        source: LyricsSource,
        synced: Boolean,
        displayArtist: String?,
        displayTitle: String?,
        language: String?,
        offsetMillis: Int,
    ): Lyrics =
        Lyrics(
            source = source,
            synced = synced,
            lines = lines.map { it.toLyricLine() },
            displayArtist = displayArtist,
            displayTitle = displayTitle,
            language = language,
            offsetMillis = offsetMillis,
            kind = kind,
            agents = agents.map { it.toLyricAgent() },
            cueLines = cueLines.map { it.toLyricCueLine() },
        )

    companion object {
        fun fromLyrics(lyrics: Lyrics): LyricsDto =
            LyricsDto(
                payloadVersion = CurrentLyricsPayloadVersion,
                lines = lyrics.lines.map { LyricLineDto.fromLyricLine(it) },
                kind = lyrics.kind,
                agents = lyrics.agents.map { LyricAgentDto.fromLyricAgent(it) },
                cueLines = lyrics.cueLines.map { LyricCueLineDto.fromLyricCueLine(it) },
            )
    }
}

private data class DecodedLyricsCache(
    val lyrics: Lyrics,
    val payloadVersion: Int,
)

private const val CurrentLyricsPayloadVersion = 2

@Serializable
private data class LyricAgentDto(
    val id: String,
    val name: String? = null,
    val role: String? = null,
) {
    fun toLyricAgent(): LyricAgent =
        LyricAgent(
            id = id,
            name = name,
            role = role,
        )

    companion object {
        fun fromLyricAgent(agent: LyricAgent): LyricAgentDto =
            LyricAgentDto(
                id = agent.id,
                name = agent.name,
                role = agent.role,
            )
    }
}

@Serializable
private data class LyricCueLineDto(
    val lineIndex: Int,
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val text: String,
    val agentId: String? = null,
    val cues: List<LyricCueDto> = emptyList(),
) {
    fun toLyricCueLine(): LyricCueLine =
        LyricCueLine(
            lineIndex = lineIndex,
            startMillis = startMillis,
            endMillis = endMillis,
            text = text,
            agentId = agentId,
            cues = cues.map { it.toLyricCue() },
        )

    companion object {
        fun fromLyricCueLine(cueLine: LyricCueLine): LyricCueLineDto =
            LyricCueLineDto(
                lineIndex = cueLine.lineIndex,
                startMillis = cueLine.startMillis,
                endMillis = cueLine.endMillis,
                text = cueLine.text,
                agentId = cueLine.agentId,
                cues = cueLine.cues.map { LyricCueDto.fromLyricCue(it) },
            )
    }
}

@Serializable
private data class LyricCueDto(
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val text: String,
    val byteStart: Int? = null,
    val byteEnd: Int? = null,
) {
    fun toLyricCue(): LyricCue =
        LyricCue(
            startMillis = startMillis,
            endMillis = endMillis,
            text = text,
            byteStart = byteStart,
            byteEnd = byteEnd,
        )

    companion object {
        fun fromLyricCue(cue: LyricCue): LyricCueDto =
            LyricCueDto(
                startMillis = cue.startMillis,
                endMillis = cue.endMillis,
                text = cue.text,
                byteStart = cue.byteStart,
                byteEnd = cue.byteEnd,
            )
    }
}
