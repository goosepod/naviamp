package app.naviamp.domain.sonichome

import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.SonicSimilarTrack
import app.naviamp.domain.sonicmix.SonicMixBias
import app.naviamp.domain.sonicmix.blendSonicMix

const val SonicHomeDiscoveryRowLimit = 12

enum class SonicHomeDiscoveryRowId(val value: String) {
    MoreLikeRecentPlays("more-like-recent-plays"),
    SonicDeepCuts("sonic-deep-cuts"),
    SimilarToStarredTracks("similar-to-starred-tracks"),
}

data class SonicHomeDiscoveryRow(
    val id: SonicHomeDiscoveryRowId,
    val title: String,
    val tracks: List<Track>,
)

data class SonicHomeDiscoveryRows(
    val rows: List<SonicHomeDiscoveryRow> = emptyList(),
) {
    fun row(id: SonicHomeDiscoveryRowId): SonicHomeDiscoveryRow? =
        rows.firstOrNull { row -> row.id == id }
}

class SonicHomeDiscoveryService(
    private val provider: MediaProvider,
) {
    suspend fun loadRows(
        libraryTracks: List<Track>,
        recentTracks: List<Track> = emptyList(),
    ): SonicHomeDiscoveryRows {
        if (!provider.capabilities.supportsSonicSimilarity) return SonicHomeDiscoveryRows()
        val candidates = libraryTracks.distinctBy { track -> track.id }
        val explicitRecentSeeds = recentTracks.distinctBy { track -> track.id }
        if (candidates.isEmpty() && explicitRecentSeeds.isEmpty()) return SonicHomeDiscoveryRows()

        val recentSeeds = explicitRecentSeeds
            .take(SonicHomeDiscoverySeedLimit)
            .ifEmpty {
                candidates
                    .filter { track -> track.lastPlayedAtIso8601 != null }
                    .sortedByDescending { track -> track.lastPlayedAtIso8601 }
                    .take(SonicHomeDiscoverySeedLimit)
            }
        val starredSeeds = candidates
            .filter { track -> track.favoritedAtIso8601 != null }
            .sortedByDescending { track -> track.favoritedAtIso8601 }
            .take(SonicHomeDiscoverySeedLimit)
        val deepCutSeeds = (recentSeeds + starredSeeds)
            .distinctBy { track -> track.id }
            .ifEmpty {
                candidates
                    .sortedWith(compareBy<Track> { track -> track.playCount ?: Int.MAX_VALUE }
                        .thenBy { track -> track.artistName }
                        .thenBy { track -> track.title })
                    .take(SonicHomeDiscoverySeedLimit)
            }

        val rows = listOfNotNull(
            buildRow(
                id = SonicHomeDiscoveryRowId.MoreLikeRecentPlays,
                title = "More Like Recent Plays",
                seeds = recentSeeds,
                bias = SonicMixBias.Recent,
            ),
            buildRow(
                id = SonicHomeDiscoveryRowId.SonicDeepCuts,
                title = "Sonic Deep Cuts",
                seeds = deepCutSeeds,
                bias = SonicMixBias.Unplayed,
                postProcess = { tracks -> tracks.deepCutSorted() },
            ),
            buildRow(
                id = SonicHomeDiscoveryRowId.SimilarToStarredTracks,
                title = "Similar To Starred Tracks",
                seeds = starredSeeds,
                bias = SonicMixBias.Favorites,
            ),
        )
        return SonicHomeDiscoveryRows(rows)
    }

    private suspend fun buildRow(
        id: SonicHomeDiscoveryRowId,
        title: String,
        seeds: List<Track>,
        bias: SonicMixBias,
        postProcess: (List<Track>) -> List<Track> = { tracks -> tracks },
    ): SonicHomeDiscoveryRow? {
        if (seeds.isEmpty()) return null
        val tracks = runCatching {
            val matchesBySeed = seeds.map { seed ->
                seed to provider.sonicSimilarTrackMatches(seed.id, count = SonicHomeDiscoveryMatchCount)
            }
            postProcess(
                blendSonicMix(
                    seeds = seeds,
                    matchesBySeed = matchesBySeed,
                    targetLength = SonicHomeDiscoveryRowLimit * 2,
                    bias = bias,
                ),
            ).take(SonicHomeDiscoveryRowLimit)
        }.getOrDefault(emptyList())
        return tracks.takeIf { it.isNotEmpty() }?.let { SonicHomeDiscoveryRow(id, title, it) }
    }
}

private fun List<Track>.deepCutSorted(): List<Track> =
    sortedWith(compareBy<Track> { track -> track.playCount ?: 0 }
        .thenBy { track -> track.favoritedAtIso8601 != null }
        .thenByDescending { track -> track.lastPlayedAtIso8601 }
        .thenBy { track -> track.artistName }
        .thenBy { track -> track.title })

private const val SonicHomeDiscoverySeedLimit = 4
private const val SonicHomeDiscoveryMatchCount = 35
