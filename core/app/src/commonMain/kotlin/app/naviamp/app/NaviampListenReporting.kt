package app.naviamp.app

import app.naviamp.domain.Track
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.PlaybackReportState
import kotlinx.coroutines.CancellationException

/** Receives ordered observations; all lifecycle, eligibility, and fallback decisions are shared. */
class NaviampListenReporting {
    private data class Session(
        var provider: MediaProvider, val track: Track, val startedAt: Long,
        var lastAt: Long, var position: Double, var state: PlaybackState,
        val timeline: Boolean = provider.capabilities.supportsPlaybackTimeline,
        var listened: Double = 0.0, var accounted: Boolean = false, var timelineFailed: Boolean = false,
    )
    private var session: Session? = null

    suspend fun observe(provider: MediaProvider, track: Track, state: PlaybackState, progress: PlaybackProgress, now: Long) {
        if (!provider.capabilities.supportsPlayReporting || track.isInternetRadioTrack()) return
        val prior = session
        val newSession = prior == null || prior.track.id != track.id || prior.provider.cacheNamespace != provider.cacheNamespace ||
            (prior.state.isTerminal() && !state.isTerminal() && state != PlaybackState.Idle)
        if (newSession) {
            prior?.let { close(it) }
            session = Session(provider, track, now, now, progress.positionSeconds ?: 0.0, state).also {
                if (provider.capabilities.supportsPlaybackTimeline) {
                    it.timelineFailed = !attempt { provider.reportPlaybackState(track.id, PlaybackReportState.Starting, progress.positionSeconds) }
                } else attempt { provider.reportNowPlaying(track.id) }
            }
        }
        val current = session ?: return
        current.provider = provider
        if (current.timeline && !provider.capabilities.supportsPlaybackTimeline) current.timelineFailed = true
        val position = progress.positionSeconds?.takeIf { it.isFinite() && it >= 0.0 } ?: current.position
        if (current.state == PlaybackState.Playing) {
            current.listened += minOf((now - current.lastAt).coerceAtLeast(0) / 1000.0,
                (position - current.position).coerceAtLeast(0.0))
        }
        current.lastAt = now
        current.position = position
        current.state = state
        if (current.timeline && !current.timelineFailed && state != PlaybackState.Loading) {
            state.toPlaybackReportState()?.let { mapped ->
                current.timelineFailed = !attempt { provider.reportPlaybackState(track.id, mapped, position) }
            }
        } else if (!current.timeline && !provider.capabilities.supportsPlaybackTimeline && state == PlaybackState.Playing && !newSession) {
            attempt { provider.reportNowPlaying(track.id) }
        }
        qualify(current)
    }

    private suspend fun qualify(current: Session) {
        val duration = current.track.durationSeconds
        if (duration != null && duration < 30) return
        val threshold = minOf(duration?.div(2.0) ?: 240.0, 240.0)
        if (current.accounted || current.listened < threshold) return
        if (!current.timeline || current.timelineFailed) {
            if (!current.provider.capabilities.supportsListenSubmission) return
            // The connected provider wrapper durably queues this when offline.
            if (!attempt { current.provider.submitListen(current.track.id, current.startedAt) }) return
        }
        current.accounted = true
    }

    private suspend fun close(current: Session) {
        if (!current.state.isTerminal() && current.timeline && !current.timelineFailed) {
            current.timelineFailed = !attempt {
                current.provider.reportPlaybackState(current.track.id, PlaybackReportState.Stopped, current.position)
            }
        }
        qualify(current)
    }

    private suspend fun attempt(block: suspend () -> Unit): Boolean = try { block(); true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }

    private fun PlaybackState.isTerminal() = this == PlaybackState.Finished || this == PlaybackState.Stopped || this is PlaybackState.Error
}
