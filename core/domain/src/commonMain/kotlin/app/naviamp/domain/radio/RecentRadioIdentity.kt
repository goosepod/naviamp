package app.naviamp.domain.radio

import app.naviamp.domain.settings.RecentRadioKind
import app.naviamp.domain.settings.RecentRadioStream
import kotlinx.serialization.Serializable

/** Options that define a generated station, rather than its particular random queue. */
@Serializable
data class RecentRadioGenerationOptions(
    val minimumTracks: Int = DefaultRadioMinimumTracks,
    val tuning: RadioTuningSettings = RadioTuningSettings(),
    val sonicSimilarity: Boolean = false,
) {
    fun normalized() = copy(
        minimumTracks = normalizedRadioMinimumTracks(minimumTracks),
        tuning = tuning.normalized(),
    )
}

data class RecentRadioIdentity(
    val sourceId: String?,
    val mode: String,
    val seeds: List<String>,
    val options: RecentRadioGenerationOptions?,
)

/** Unknown legacy options remain a separate variant; never guess an account or merge by label. */
fun RecentRadioStream.stationIdentity(): RecentRadioIdentity {
    val base = if (startedAtEpochMillis != null) id.replace(SessionSuffix, "") else id
    val mode = when {
        base.startsWith("artist-mix:") -> "artist-mix"
        base.startsWith("album-mix:") -> "album-mix"
        base.startsWith("genre-mix:") -> "genre-mix"
        base.startsWith("popular:") -> "popular"
        else -> kind.name
    }
    val seeds = when {
        stationSeedIds.isNotEmpty() -> stationSeedIds.distinct().sorted()
        mode.endsWith("-mix") -> listOf("legacy:$base") // Old composite IDs cannot be split safely.
        mode == "popular" -> listOf(base)
        kind == RecentRadioKind.Library -> emptyList()
        kind == RecentRadioKind.Genre -> genre?.takeIf { it.isNotBlank() }?.let(::listOf)
        kind == RecentRadioKind.Decade -> if (fromYear != null && toYear != null) listOf("$fromYear", "$toYear") else null
        kind == RecentRadioKind.Artist -> artist?.id?.takeIf { it.isNotBlank() }?.let(::listOf)
        kind == RecentRadioKind.Album || kind == RecentRadioKind.RandomAlbum -> album?.id?.takeIf { it.isNotBlank() }?.let(::listOf)
        kind == RecentRadioKind.Track -> track?.id?.takeIf { it.isNotBlank() }?.let(::listOf)
        else -> null
    } ?: listOf("unresolved:$base")
    return RecentRadioIdentity(sourceId, "${kind.name}:$mode", seeds, generationOptions?.normalized())
}

/** Keep newest recency/identity, borrowing retained content only when the newer record lacks it. */
fun reconciledRecentRadioStreams(
    streams: List<RecentRadioStream>,
    limit: Int = MaxRecentRadioStreams,
): List<RecentRadioStream> {
    val stations = linkedMapOf<RecentRadioIdentity, RecentRadioStream>()
    for (stream in streams) {
        val identity = stream.stationIdentity()
        val newest = stations[identity]
        stations[identity] = if (newest == null) stream.copy(generationOptions = stream.generationOptions?.normalized()) else newest.copy(
            sessionTracks = newest.sessionTracks.ifEmpty { stream.sessionTracks },
            coverArtIds = newest.coverArtIds.ifEmpty { stream.coverArtIds },
        )
    }
    return stations.values.take(limit.coerceAtLeast(0))
}

private val SessionSuffix = Regex(":session:-?\\d+(?::\\d+)?$")
