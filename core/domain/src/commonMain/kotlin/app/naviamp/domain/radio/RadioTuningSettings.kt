package app.naviamp.domain.radio

import app.naviamp.domain.Track
import kotlinx.serialization.Serializable

@Serializable
data class RadioTuningSettings(
    val familiarity: RadioFamiliarity = RadioFamiliarity.Balanced,
    val artistSpread: RadioArtistSpread = RadioArtistSpread.Balanced,
    val sameDecadeOnly: Boolean = false,
    val artistRunMode: RadioArtistRunMode = RadioArtistRunMode.Mixed,
    val sameArtistRunLength: Int = 2,
    val otherArtistRunLength: Int = 3,
) {
    fun normalized(): RadioTuningSettings =
        copy(
            familiarity = familiarity,
            artistSpread = artistSpread,
            sameArtistRunLength = sameArtistRunLength.coerceIn(MinArtistRunLength, MaxArtistRunLength),
            otherArtistRunLength = otherArtistRunLength.coerceIn(MinArtistRunLength, MaxArtistRunLength),
        )
}

@Serializable
data class RadioDjPreset(
    val id: String,
    val name: String,
    val tuning: RadioTuningSettings = RadioTuningSettings(),
) {
    fun normalized(): RadioDjPreset =
        copy(
            name = name.trim().ifBlank { "Untitled DJ" },
            tuning = tuning.normalized(),
        )
}

interface RadioDjPresetRepository {
    fun radioDjPresets(): List<RadioDjPreset>
    fun replaceRadioDjPresets(presets: List<RadioDjPreset>)
    fun upsertRadioDjPreset(preset: RadioDjPreset)
    fun deleteRadioDjPreset(id: String)
}

@Serializable
enum class RadioFamiliarity(val label: String) {
    Balanced("Balanced"),
    Favorites("Favorites"),
    DeepCuts("Deep cuts"),
}

@Serializable
enum class RadioArtistSpread(val label: String) {
    Narrow("Narrow"),
    Balanced("Balanced"),
    Broad("Broad"),
}

@Serializable
enum class RadioArtistRunMode(val label: String) {
    Mixed("Mixed"),
    SingleArtist("Single artist"),
    ArtistBlocks("Artist blocks"),
}

fun tunedRadioTracks(
    seedTrack: Track?,
    tracks: List<Track>,
    tuning: RadioTuningSettings = RadioTuningSettings(),
    targetCount: Int = tracks.size,
): List<Track> {
    val distinctTracks = tracks.distinctBy { it.id }
    val decadeFiltered = seedTrack
        ?.albumReleaseYear
        ?.takeIf { tuning.sameDecadeOnly }
        ?.let { year ->
            val decadeStart = (year / 10) * 10
            val sameDecade = distinctTracks.filter { track ->
                track.albumReleaseYear?.let { it in decadeStart..(decadeStart + 9) } == true
            }
            sameDecade.ifEmpty { distinctTracks }
        }
        ?: distinctTracks

    val familiarRanked = when (tuning.familiarity) {
        RadioFamiliarity.Balanced -> decadeFiltered
        RadioFamiliarity.Favorites -> decadeFiltered.sortedWith(
            compareByDescending<Track> { it.favoritedAtIso8601 != null }
                .thenByDescending { it.userRating ?: 0 }
                .thenByDescending { it.playCount ?: 0 },
        )
        RadioFamiliarity.DeepCuts -> decadeFiltered.sortedWith(
            compareBy<Track> { it.favoritedAtIso8601 != null }
                .thenBy { it.userRating ?: 0 }
                .thenBy { it.playCount ?: 0 },
        )
    }

    val spreadRanked = when (tuning.artistSpread) {
        RadioArtistSpread.Balanced -> familiarRanked
        RadioArtistSpread.Narrow -> seedTrack?.let { seed ->
            familiarRanked.sortedByDescending { track -> track.sameArtistAs(seed) }
        } ?: familiarRanked
        RadioArtistSpread.Broad -> interleaveByArtist(familiarRanked)
    }

    val artistRunRanked = when (tuning.artistRunMode) {
        RadioArtistRunMode.Mixed -> spreadRanked
        RadioArtistRunMode.SingleArtist -> sameArtistOnly(seedTrack, spreadRanked)
        RadioArtistRunMode.ArtistBlocks -> artistBlocks(seedTrack, spreadRanked, tuning.normalized())
    }

    return artistRunRanked.take(targetCount.coerceAtLeast(0))
}

private fun Track.sameArtistAs(seedTrack: Track): Boolean =
    if (artistId != null && seedTrack.artistId != null) {
        artistId == seedTrack.artistId
    } else {
        artistName.equals(seedTrack.artistName, ignoreCase = true)
    }

private fun sameArtistOnly(seedTrack: Track?, tracks: List<Track>): List<Track> =
    seedTrack
        ?.let { seed -> tracks.filter { track -> track.sameArtistAs(seed) }.ifEmpty { tracks } }
        ?: tracks

private fun artistBlocks(seedTrack: Track?, tracks: List<Track>, tuning: RadioTuningSettings): List<Track> {
    val seed = seedTrack ?: return tracks
    val sameArtist = tracks.filter { it.sameArtistAs(seed) }.toMutableList()
    val otherArtists = tracks.filterNot { it.sameArtistAs(seed) }.toMutableList()
    if (sameArtist.isEmpty() || otherArtists.isEmpty()) return tracks

    val result = mutableListOf<Track>()
    while (sameArtist.isNotEmpty() || otherArtists.isNotEmpty()) {
        repeat(tuning.sameArtistRunLength) {
            if (sameArtist.isNotEmpty()) result += sameArtist.removeAt(0)
        }
        repeat(tuning.otherArtistRunLength) {
            if (otherArtists.isNotEmpty()) result += otherArtists.removeAt(0)
        }
    }
    return result
}

private fun interleaveByArtist(tracks: List<Track>): List<Track> {
    val artistBuckets = tracks.groupBy { track -> track.artistId?.value ?: track.artistName.lowercase() }
        .values
        .map { it.toMutableList() }
        .toMutableList()
    val result = mutableListOf<Track>()
    while (artistBuckets.isNotEmpty()) {
        val iterator = artistBuckets.iterator()
        while (iterator.hasNext()) {
            val bucket = iterator.next()
            result += bucket.removeAt(0)
            if (bucket.isEmpty()) iterator.remove()
        }
    }
    return result
}

const val MinArtistRunLength = 1
const val MaxArtistRunLength = 8
