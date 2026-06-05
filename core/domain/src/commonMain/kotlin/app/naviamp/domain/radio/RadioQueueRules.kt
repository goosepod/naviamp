package app.naviamp.domain.radio

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode

fun radioTracksNotAlreadyQueued(
    candidateTracks: List<Track>,
    queuedTracks: List<Track>,
): List<Track> {
    val queuedTrackIds = queuedTracks.map { it.id }.toSet()
    return candidateTracks.filterNot { track -> track.id in queuedTrackIds }
}

fun generatedRadioTracksToAppend(
    seedTrack: Track,
    fetchedTracks: List<Track>,
    queuedTracks: List<Track>,
): List<Track> =
    radioTracksNotAlreadyQueued(
        candidateTracks = generatedRadioQueue(seedTrack, fetchedTracks),
        queuedTracks = queuedTracks,
    )

fun generatedRadioUpcomingTracks(
    currentTrack: Track,
    fetchedTracks: List<Track>,
): List<Track> =
    generatedRadioQueue(currentTrack, fetchedTracks).drop(1)

fun generatedRadioUpcomingTracksToAppend(
    currentTrack: Track,
    fetchedTracks: List<Track>,
    queuedTracks: List<Track>,
): List<Track> =
    radioTracksNotAlreadyQueued(
        candidateTracks = generatedRadioUpcomingTracks(currentTrack, fetchedTracks),
        queuedTracks = queuedTracks,
    )

fun radioRefillSeedTrack(
    queue: PlaybackQueue,
    refillThreshold: Int,
    repeatMode: RepeatMode,
    isActive: Boolean,
    isRefilling: Boolean,
    lastRefillSeedTrackId: TrackId?,
): Track? {
    if (!isActive || isRefilling) return null
    if (repeatMode != RepeatMode.Off) return null
    val seedTrack = queue.tracks.lastOrNull() ?: return null
    if (queue.upNext().size > refillThreshold) return null
    if (lastRefillSeedTrackId == seedTrack.id) return null
    return seedTrack
}

fun isCurrentRadioSession(
    radioQueueActive: Boolean,
    radioSession: Int,
    currentRadioSession: Int,
): Boolean =
    radioQueueActive && radioSession == currentRadioSession

fun generatedRadioAppendTracksForSession(
    radioQueueActive: Boolean,
    radioSession: Int,
    currentRadioSession: Int,
    seedTrack: Track,
    fetchedTracks: List<Track>,
    queuedTracks: List<Track>,
): List<Track> {
    if (!isCurrentRadioSession(radioQueueActive, radioSession, currentRadioSession)) return emptyList()
    return generatedRadioTracksToAppend(seedTrack, fetchedTracks, queuedTracks)
}

fun generatedRadioUpcomingReplacementForSession(
    radioQueueActive: Boolean,
    radioSession: Int,
    currentRadioSession: Int,
    currentTrack: Track,
    fetchedTracks: List<Track>,
): List<Track>? {
    if (!isCurrentRadioSession(radioQueueActive, radioSession, currentRadioSession)) return null
    return generatedRadioUpcomingTracks(currentTrack, fetchedTracks)
}

fun generatedRadioUpcomingAppendTracksForSession(
    radioQueueActive: Boolean,
    radioSession: Int,
    currentRadioSession: Int,
    currentTrack: Track,
    fetchedTracks: List<Track>,
    queuedTracks: List<Track>,
): List<Track> {
    if (!isCurrentRadioSession(radioQueueActive, radioSession, currentRadioSession)) return emptyList()
    return generatedRadioUpcomingTracksToAppend(currentTrack, fetchedTracks, queuedTracks)
}

fun shouldFinishRadioRefillForSession(
    radioSession: Int,
    currentRadioSession: Int,
): Boolean =
    radioSession == currentRadioSession
