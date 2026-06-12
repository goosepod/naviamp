package app.naviamp.domain.sonicpath

import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider

const val SonicPathDefaultCount = 25
const val SonicPathMinCount = 2
const val SonicPathMaxCount = 100

data class SonicPathRequest(
    val startTrack: Track,
    val endTrack: Track,
    val count: Int = SonicPathDefaultCount,
) {
    val normalizedCount: Int
        get() = count.coerceIn(SonicPathMinCount, SonicPathMaxCount)
}

class SonicPathService(
    private val provider: MediaProvider,
) {
    suspend fun findPath(request: SonicPathRequest): List<Track> {
        if (!provider.capabilities.supportsSonicSimilarity) return emptyList()
        val matches = provider.findSonicPath(
            startTrackId = request.startTrack.id,
            endTrackId = request.endTrack.id,
            count = request.normalizedCount,
        )
        if (matches.isEmpty()) return emptyList()
        return normalizeSonicPath(
            startTrack = request.startTrack,
            endTrack = request.endTrack,
            tracks = matches.map { match -> match.track },
        )
    }
}

fun normalizeSonicPath(
    startTrack: Track,
    endTrack: Track,
    tracks: List<Track>,
): List<Track> {
    val middleTracks = tracks
        .filterNot { track -> track.id == startTrack.id || track.id == endTrack.id }
        .distinctBy { track -> track.id }
    return (listOf(startTrack) + middleTracks + listOf(endTrack))
        .distinctByKeepingEndpoints(startTrack, endTrack)
}

private fun List<Track>.distinctByKeepingEndpoints(
    startTrack: Track,
    endTrack: Track,
): List<Track> {
    val seen = mutableSetOf(startTrack.id, endTrack.id)
    val middle = drop(1)
        .dropLast(1)
        .filter { track -> seen.add(track.id) }
    return listOf(startTrack) + middle + listOf(endTrack)
}
