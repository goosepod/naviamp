package app.naviamp.desktop

import app.naviamp.desktop.playback.PlaylistEngine
import app.naviamp.domain.Track
import app.naviamp.domain.radio.generatedRadioAppendTracksForSession
import app.naviamp.domain.radio.generatedRadioUpcomingAppendTracksForSession
import app.naviamp.domain.radio.generatedRadioUpcomingReplacementForSession

fun appendGeneratedRadioTracks(
    playlistEngine: PlaylistEngine,
    radioQueueActive: Boolean,
    radioSession: Int,
    currentRadioSession: Int,
    seedTrack: Track,
    fetchedTracks: List<Track>,
    maxHistory: Int,
) {
    val newTracks = generatedRadioAppendTracksForSession(
        radioQueueActive = radioQueueActive,
        radioSession = radioSession,
        currentRadioSession = currentRadioSession,
        seedTrack = seedTrack,
        fetchedTracks = fetchedTracks,
        queuedTracks = playlistEngine.queue.tracks,
    )
    if (newTracks.isNotEmpty()) {
        playlistEngine.appendTracks(
            tracks = newTracks,
            maxHistory = maxHistory,
        )
    }
}

fun replaceGeneratedRadioUpcomingTracks(
    playlistEngine: PlaylistEngine,
    radioQueueActive: Boolean,
    radioSession: Int,
    currentRadioSession: Int,
    currentTrack: Track,
    fetchedTracks: List<Track>,
    maxHistory: Int,
) {
    val upcomingTracks = generatedRadioUpcomingReplacementForSession(
        radioQueueActive = radioQueueActive,
        radioSession = radioSession,
        currentRadioSession = currentRadioSession,
        currentTrack = currentTrack,
        fetchedTracks = fetchedTracks,
    ) ?: return
    playlistEngine.replaceUpcomingTracks(
        currentTrack = currentTrack,
        upcomingTracks = upcomingTracks,
        maxHistory = maxHistory,
    )
}

fun appendGeneratedRadioUpcomingTracks(
    playlistEngine: PlaylistEngine,
    radioQueueActive: Boolean,
    radioSession: Int,
    currentRadioSession: Int,
    currentTrack: Track,
    fetchedTracks: List<Track>,
    maxHistory: Int,
) {
    val newTracks = generatedRadioUpcomingAppendTracksForSession(
        radioQueueActive = radioQueueActive,
        radioSession = radioSession,
        currentRadioSession = currentRadioSession,
        currentTrack = currentTrack,
        fetchedTracks = fetchedTracks,
        queuedTracks = playlistEngine.queue.tracks,
    )
    if (newTracks.isNotEmpty()) {
        playlistEngine.appendTracks(
            tracks = newTracks,
            maxHistory = maxHistory,
        )
    }
}
