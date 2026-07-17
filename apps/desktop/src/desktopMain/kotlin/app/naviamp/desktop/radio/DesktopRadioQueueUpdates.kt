package app.naviamp.desktop

import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.desktop.playback.DesktopPlaylistEngine
import app.naviamp.domain.Track

fun appendGeneratedRadioTracks(
    queueCoordinator: NaviampPlaybackQueueCoordinator,
    playlistEngine: DesktopPlaylistEngine,
    radioQueueActive: Boolean,
    radioSession: Int,
    currentRadioSession: Int,
    seedTrack: Track,
    fetchedTracks: List<Track>,
    maxHistory: Int,
) {
    val update = queueCoordinator.appendGeneratedRadioTracks(
        seedTrack = seedTrack,
        fetchedTracks = fetchedTracks,
        requestIsCurrent = radioQueueActive && radioSession == currentRadioSession,
        maxHistory = maxHistory,
    )
    playlistEngine.applyQueueUpdate(update)
}

fun replaceGeneratedRadioUpcomingTracks(
    queueCoordinator: NaviampPlaybackQueueCoordinator,
    playlistEngine: DesktopPlaylistEngine,
    radioQueueActive: Boolean,
    radioSession: Int,
    currentRadioSession: Int,
    currentTrack: Track,
    fetchedTracks: List<Track>,
    maxHistory: Int,
) {
    val update = queueCoordinator.replaceGeneratedRadioUpcomingTracks(
        currentTrack = currentTrack,
        fetchedTracks = fetchedTracks,
        requestIsCurrent = radioQueueActive && radioSession == currentRadioSession,
        maxHistory = maxHistory,
    )
    playlistEngine.applyQueueMutation(update)
}

fun appendGeneratedRadioUpcomingTracks(
    queueCoordinator: NaviampPlaybackQueueCoordinator,
    playlistEngine: DesktopPlaylistEngine,
    radioQueueActive: Boolean,
    radioSession: Int,
    currentRadioSession: Int,
    currentTrack: Track,
    fetchedTracks: List<Track>,
    maxHistory: Int,
) {
    val update = queueCoordinator.appendGeneratedRadioUpcomingTracks(
        currentTrack = currentTrack,
        fetchedTracks = fetchedTracks,
        requestIsCurrent = radioQueueActive && radioSession == currentRadioSession,
        maxHistory = maxHistory,
    )
    playlistEngine.applyQueueUpdate(update)
}
