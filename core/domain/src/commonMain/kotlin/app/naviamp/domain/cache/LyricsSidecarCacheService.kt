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

    fun cachedLrclibLyrics(
        sourceId: String,
        trackId: String,
    ): CachedLyricsRow?

    fun touchCachedLrclibLyrics(
        sourceId: String,
        trackId: String,
        lastAccessedEpochMillis: Long,
    )

    fun upsertCachedLrclibLyrics(
        sourceId: String,
        trackId: String,
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
    ): Lyrics? =
        withContext(Dispatchers.Default) {
            cachedLyrics(sourceId, trackId)?.let { return@withContext it }
            val lyrics = provider.lyrics(trackId) ?: return@withContext null
            storeLyrics(sourceId, trackId, lyrics)
            lyrics
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

    suspend fun lrclibLyrics(
        sourceId: String,
        track: Track,
        provider: LyricsProvider,
    ): Lyrics? =
        withContext(Dispatchers.Default) {
            cachedLrclibLyrics(sourceId, track.id)?.let { return@withContext it }
            val lyrics = provider.lyrics(track) ?: return@withContext null
            storeLrclibLyrics(sourceId, track.id, lyrics)
            lyrics
        }

    suspend fun cachedLyrics(
        sourceId: String,
        trackId: TrackId,
    ): Lyrics? =
        withContext(Dispatchers.Default) {
            cachedLyricsRow(sourceId, trackId)
        }

    private fun cachedLyricsRow(
        sourceId: String,
        trackId: TrackId,
    ): Lyrics? {
        val row = store.cachedLyrics(sourceId, trackId.value) ?: return null
        store.touchCachedLyrics(sourceId, trackId.value, nowMillis())
        return row.toLyrics()
    }

    private fun cachedLrclibLyrics(
        sourceId: String,
        trackId: TrackId,
    ): Lyrics? {
        val row = store.cachedLrclibLyrics(sourceId, trackId.value) ?: return null
        store.touchCachedLrclibLyrics(sourceId, trackId.value, nowMillis())
        return row.toLyrics(source = LyricsSource.Lrclib)
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
            sizeBytes = linesJson.toByteArray(Charsets.UTF_8).size.toLong(),
            createdAtEpochMillis = now,
            lastAccessedEpochMillis = now,
        )
    }

    private fun storeLrclibLyrics(
        sourceId: String,
        trackId: TrackId,
        lyrics: Lyrics,
    ) {
        val linesJson = lyrics.linesJson()
        val now = nowMillis()
        store.upsertCachedLrclibLyrics(
            sourceId = sourceId,
            trackId = trackId.value,
            synced = lyrics.synced,
            linesJson = linesJson,
            displayArtist = lyrics.displayArtist,
            displayTitle = lyrics.displayTitle,
            language = lyrics.language,
            offsetMillis = lyrics.offsetMillis.toLong(),
            sizeBytes = linesJson.toByteArray(Charsets.UTF_8).size.toLong(),
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
                lines = lyrics.lines.map { LyricLineDto.fromLyricLine(it) },
                kind = lyrics.kind,
                agents = lyrics.agents.map { LyricAgentDto.fromLyricAgent(it) },
                cueLines = lyrics.cueLines.map { LyricCueLineDto.fromLyricCueLine(it) },
            )
    }
}

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
