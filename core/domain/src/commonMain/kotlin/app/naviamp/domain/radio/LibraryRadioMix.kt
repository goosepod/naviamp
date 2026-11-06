package app.naviamp.domain.radio

import app.naviamp.domain.Track
import app.naviamp.domain.provider.isDiscoveryPreview

/** Library-only fallback: genre candidates rank first, with artists interleaved and capped. */
fun libraryRadioMixTracks(
    seed: Track,
    genreMatches: List<Track>,
    libraryCandidates: List<Track> = emptyList(),
    fallbackTracks: List<Track> = emptyList(),
    count: Int,
): List<Track> {
    val target = count.coerceIn(1, 300)
    val buckets = (genreMatches + libraryCandidates + fallbackTracks)
        .filterNot { it.id == seed.id || it.id.isDiscoveryPreview() }
        .distinctBy { it.id }
        .groupBy(::libraryRadioArtistKey)
        .entries.sortedBy { (_, tracks) -> tracks.first().sameLibraryRadioArtist(seed) }
        .map { it.value.toMutableList() }
    if (buckets.isEmpty()) return emptyList()
    // Small libraries can still mix their available artists. With four or more artists, no
    // artist takes more than a quarter (rounded up); return a shorter mix rather than flood it.
    val artistSlots = buckets.size.coerceAtMost(4)
    val perArtist = (target + artistSlots - 1) / artistSlots
    val result = mutableListOf<Track>()
    repeat(perArtist) {
        for (bucket in buckets) {
            if (bucket.isNotEmpty()) result += bucket.removeAt(0)
            if (result.size == target) return result
        }
    }
    return result
}

fun libraryRadioMixNeedsMoreCandidates(seed: Track, tracks: List<Track>, count: Int): Boolean =
    tracks.size < count.coerceIn(1, 300) ||
        tracks.distinctBy(::libraryRadioArtistKey).size < count.coerceIn(1, 4) ||
        tracks.none { !it.sameLibraryRadioArtist(seed) }

private fun libraryRadioArtistKey(track: Track): String =
    track.artistName.trim().lowercase().takeIf(String::isNotEmpty)?.let { "name:$it" }
        ?: track.artistId?.value?.let { "id:$it" } ?: "unknown"

private fun Track.sameLibraryRadioArtist(seed: Track): Boolean =
    artistId != null && artistId == seed.artistId ||
        artistName.trim().equals(seed.artistName.trim(), ignoreCase = true)
