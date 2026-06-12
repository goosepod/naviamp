package app.naviamp.domain.sonicmix

import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.SonicSimilarTrack

const val SonicMixDefaultTargetLength = 50
const val SonicMixMinTargetLength = 5
const val SonicMixMaxTargetLength = 100
const val SonicMixMinSeeds = 2
const val SonicMixMaxSeeds = 8

enum class SonicMixBias {
    Balanced,
    Favorites,
    Unplayed,
    Recent,
}

data class SonicMixRequest(
    val seedTracks: List<Track>,
    val targetLength: Int = SonicMixDefaultTargetLength,
    val bias: SonicMixBias = SonicMixBias.Balanced,
) {
    val normalizedSeeds: List<Track>
        get() = seedTracks.distinctBy { track -> track.id }.take(SonicMixMaxSeeds)

    val normalizedTargetLength: Int
        get() = targetLength.coerceIn(SonicMixMinTargetLength, SonicMixMaxTargetLength)
}

class SonicMixService(
    private val provider: MediaProvider,
) {
    suspend fun buildMix(request: SonicMixRequest): List<Track> {
        if (!provider.capabilities.supportsSonicSimilarity) return emptyList()
        val seeds = request.normalizedSeeds
        if (seeds.size < SonicMixMinSeeds) return emptyList()
        val perSeedCount = (request.normalizedTargetLength * 2)
            .coerceAtLeast(20)
            .coerceAtMost(SonicMixMaxTargetLength)
        val matchesBySeed = seeds.map { seed ->
            seed to provider.sonicSimilarTrackMatches(seed.id, count = perSeedCount)
        }
        return blendSonicMix(
            seeds = seeds,
            matchesBySeed = matchesBySeed,
            targetLength = request.normalizedTargetLength,
            bias = request.bias,
        )
    }
}

fun blendSonicMix(
    seeds: List<Track>,
    matchesBySeed: List<Pair<Track, List<SonicSimilarTrack>>>,
    targetLength: Int,
    bias: SonicMixBias = SonicMixBias.Balanced,
): List<Track> {
    val seedIds = seeds.map { track -> track.id }.toSet()
    val rankedBySeed = matchesBySeed
        .map { (_, matches) ->
            matches
                .filterNot { match -> match.track.id in seedIds }
                .groupBy { match -> match.track.id }
                .values
                .map { duplicates -> duplicates.maxBy { match -> match.score(bias) } }
                .sortedWith(compareByDescending<SonicSimilarTrack> { match -> match.score(bias) }
                    .thenBy { match -> match.track.artistName }
                    .thenBy { match -> match.track.title })
        }
        .filter { matches -> matches.isNotEmpty() }
    if (rankedBySeed.isEmpty()) return emptyList()

    val selected = mutableListOf<Track>()
    val seen = seedIds.toMutableSet()
    var cursor = 0
    var misses = 0
    val maxMisses = rankedBySeed.sumOf { matches -> matches.size } + rankedBySeed.size
    while (selected.size < targetLength && misses < maxMisses) {
        val seedMatches = rankedBySeed[cursor % rankedBySeed.size]
        val candidate = seedMatches.firstOrNull { match ->
            match.track.id !in seen && selected.canAddWithSpacing(match.track)
        } ?: seedMatches.firstOrNull { match -> match.track.id !in seen }
        if (candidate != null) {
            selected += candidate.track
            seen += candidate.track.id
            misses = 0
        } else {
            misses += 1
        }
        cursor += 1
    }
    return selected.take(targetLength)
}

private fun SonicSimilarTrack.score(bias: SonicMixBias): Double {
    val similarityScore = similarity ?: 0.5
    val biasScore = when (bias) {
        SonicMixBias.Balanced -> 0.0
        SonicMixBias.Favorites -> if (track.favoritedAtIso8601 != null) 0.25 else 0.0
        SonicMixBias.Unplayed -> if ((track.playCount ?: 0) == 0) 0.25 else 0.0
        SonicMixBias.Recent -> if (track.lastPlayedAtIso8601 != null) 0.25 else 0.0
    }
    return similarityScore + biasScore
}

private fun List<Track>.canAddWithSpacing(track: Track): Boolean {
    val recent = takeLast(2)
    val sameArtist = track.artistId?.let { artistId ->
        recent.count { it.artistId == artistId }
    } ?: recent.count { it.artistName == track.artistName }
    val sameAlbum = track.albumId?.let { albumId ->
        recent.count { it.albumId == albumId }
    } ?: recent.count { it.albumTitle == track.albumTitle && track.albumTitle != null }
    return sameArtist == 0 && sameAlbum == 0
}
