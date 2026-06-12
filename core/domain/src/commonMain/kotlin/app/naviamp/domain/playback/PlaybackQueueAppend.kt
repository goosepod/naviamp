package app.naviamp.domain.playback

import app.naviamp.domain.Track

fun appendableTracks(
    tracksToAdd: List<Track>,
    existingTracks: List<Track> = emptyList(),
    deduplicateExisting: Boolean = false,
): List<Track> {
    if (!deduplicateExisting) return tracksToAdd
    val existingIds = existingTracks.map { it.id }.toSet()
    return tracksToAdd.filterNot { track -> track.id in existingIds }
}

fun queueAppendStatus(
    originalTracks: List<Track>,
    tracksToAdd: List<Track>,
    label: String = "tracks",
    deduplicateExisting: Boolean = false,
): String =
    if (tracksToAdd.isEmpty()) {
        if (deduplicateExisting && originalTracks.isNotEmpty()) {
            "${label.replaceFirstChar { it.uppercase() }} are already in the queue."
        } else {
            "No tracks found."
        }
    } else {
        val displayLabel = if (tracksToAdd.size == 1 && label == "tracks") "track" else label
        "Added ${tracksToAdd.size} $displayLabel to queue."
    }

fun queuePlayNextStatus(
    originalTracks: List<Track>,
    tracksToAdd: List<Track>,
    label: String = "tracks",
    deduplicateExisting: Boolean = false,
): String =
    if (tracksToAdd.isEmpty()) {
        if (deduplicateExisting && originalTracks.isNotEmpty()) {
            "${label.replaceFirstChar { it.uppercase() }} are already in the queue."
        } else {
            "No tracks found."
        }
    } else {
        val displayLabel = if (tracksToAdd.size == 1 && label == "tracks") "track" else label
        "Added ${tracksToAdd.size} $displayLabel to play next."
    }
