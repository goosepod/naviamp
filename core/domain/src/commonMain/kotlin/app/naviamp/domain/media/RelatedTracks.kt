package app.naviamp.domain.media

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.provider.MediaProvider

enum class RelatedTracksSource {
    None,
    SonicSimilarity,
    LocalLibrary,
    ProviderRadio,
}

data class RelatedTrackMatch(
    val track: Track,
    val similarity: Double? = null,
)

data class RelatedTracksResult(
    val source: RelatedTracksSource,
    val matches: List<RelatedTrackMatch>,
) {
    val tracks: List<Track>
        get() = matches.map { it.track }

    val similarityByTrackId: Map<TrackId, Double>
        get() = matches.mapNotNull { match ->
            match.similarity?.let { similarity -> match.track.id to similarity }
        }.toMap()

    companion object {
        val Empty = RelatedTracksResult(RelatedTracksSource.None, emptyList())
    }
}

suspend fun relatedTracksResult(
    seedTrack: Track,
    activeSourceId: String?,
    provider: MediaProvider?,
    localLibraryIndexRepository: LocalLibraryIndexRepository?,
    preferSonicSimilarity: Boolean,
    limit: Int,
    fallbackSources: List<RelatedTracksSource>,
): RelatedTracksResult {
    val normalizedLimit = limit.coerceAtLeast(1)
    if (preferSonicSimilarity && provider?.capabilities?.supportsSonicSimilarity == true) {
        val matches = provider.sonicSimilarTrackMatches(seedTrack.id, count = normalizedLimit)
            .filterNot { match -> match.track.id == seedTrack.id }
            .take(normalizedLimit)
        if (matches.isNotEmpty()) {
            return RelatedTracksResult(
                source = RelatedTracksSource.SonicSimilarity,
                matches = matches.map { match ->
                    RelatedTrackMatch(track = match.track, similarity = match.similarity)
                },
            )
        }
    }

    fallbackSources.forEach { source ->
        val result = when (source) {
            RelatedTracksSource.LocalLibrary -> localLibraryResult(
                seedTrack = seedTrack,
                activeSourceId = activeSourceId,
                localLibraryIndexRepository = localLibraryIndexRepository,
                limit = normalizedLimit,
            )
            RelatedTracksSource.ProviderRadio -> providerRadioResult(
                seedTrack = seedTrack,
                provider = provider,
                limit = normalizedLimit,
            )
            RelatedTracksSource.None,
            RelatedTracksSource.SonicSimilarity,
            -> RelatedTracksResult.Empty
        }
        if (result.matches.isNotEmpty()) return result
    }

    return RelatedTracksResult.Empty
}

private fun localLibraryResult(
    seedTrack: Track,
    activeSourceId: String?,
    localLibraryIndexRepository: LocalLibraryIndexRepository?,
    limit: Int,
): RelatedTracksResult {
    val sourceId = activeSourceId ?: return RelatedTracksResult.Empty
    val repository = localLibraryIndexRepository ?: return RelatedTracksResult.Empty
    val tracks = repository.relatedLibraryTracks(sourceId, seedTrack, limit = limit.toLong())
        .filterNot { track -> track.id == seedTrack.id }
    return RelatedTracksResult(
        source = RelatedTracksSource.LocalLibrary,
        matches = tracks.map { track -> RelatedTrackMatch(track = track) },
    )
}

private suspend fun providerRadioResult(
    seedTrack: Track,
    provider: MediaProvider?,
    limit: Int,
): RelatedTracksResult {
    if (provider?.capabilities?.supportsTrackRadio != true) return RelatedTracksResult.Empty
    val tracks = provider.trackRadio(seedTrack.id, count = limit)
        .filterNot { track -> track.id == seedTrack.id }
    return RelatedTracksResult(
        source = RelatedTracksSource.ProviderRadio,
        matches = tracks.map { track -> RelatedTrackMatch(track = track) },
    )
}
