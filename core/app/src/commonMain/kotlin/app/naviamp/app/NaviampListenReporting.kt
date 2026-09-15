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
    private var lastFailure: String? = null

    suspend fun observe(
        provider: MediaProvider,
        track: Track,
        state: PlaybackState,
        progress: PlaybackProgress,
        now: Long,
        presenceState: PlaybackReportState? = state.toPlaybackReportState(),
    ) {
        if (!provider.capabilities.supportsPlayReporting || track.isInternetRadioTrack()) return
        val prior = session
        val newSession = prior == null || prior.track.id != track.id || prior.provider.cacheNamespace != provider.cacheNamespace ||
            (prior.state.isTerminal() && !state.isTerminal() && state != PlaybackState.Idle)
        if (newSession) {
            prior?.let { close(it) }
            session = Session(provider, track, now, now, progress.positionSeconds ?: 0.0, state).also {
                if (provider.capabilities.supportsPlaybackTimeline && !state.isTerminal() && state != PlaybackState.Idle) {
                    it.timelineFailed = !attempt("Timeline starting") {
                        provider.reportPlaybackState(track.id, PlaybackReportState.Starting, progress.positionSeconds)
                    }
                } else if (state == PlaybackState.Playing) {
                    attempt("Legacy presence") { provider.reportLegacyNowPlaying(track.id) }
                }
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
        if (current.timeline && !current.timelineFailed && presenceState != null &&
            !(newSession && presenceState == PlaybackReportState.Starting)) {
            current.timelineFailed = !attempt("Timeline ${presenceState.providerValue}") {
                provider.reportPlaybackState(track.id, presenceState, position)
            }
        }
        // A failed timeline report must fall back in this same observation, even
        // when the provider still advertises the extension. Legacy sessions already
        // publish presence when created; retain that single initial report.
        val needsPresence = presenceState == PlaybackReportState.Playing &&
            (current.timelineFailed || (!current.timeline && !newSession))
        if (needsPresence) {
            attempt("Legacy presence") { provider.reportLegacyNowPlaying(track.id) }
        }
        qualify(current)
    }

    private suspend fun qualify(current: Session) {
        val duration = current.track.durationSeconds
        if (duration != null && duration < 30) return
        val threshold = minOf(duration?.div(2.0) ?: 240.0, 240.0)
        if (current.accounted || current.listened < threshold) return
        if (!current.provider.capabilities.supportsListenSubmission) return
        // Timeline requests are presence-only. The connected provider wrapper persists this
        // timestamped submission before the network effect so qualified offline listens survive.
        if (!attempt("Listen submission") {
            current.provider.submitListen(current.track.id, current.startedAt)
        }) return
        current.accounted = true
    }

    private suspend fun close(current: Session) {
        if (!current.state.isTerminal() && current.timeline && !current.timelineFailed) {
            current.timelineFailed = !attempt("Timeline stopped") {
                current.provider.reportPlaybackState(current.track.id, PlaybackReportState.Stopped, current.position)
            }
        }
        qualify(current)
    }

    fun diagnostics(): List<Pair<String, String>> = listOf(
        "Listen reporting mode" to when (val current = session) {
            null -> "Idle"
            else -> when {
                current.timeline && current.timelineFailed -> "Legacy fallback"
                current.timeline -> "Timeline presence"
                else -> "Legacy presence"
            }
        },
        "Last listen reporting failure" to (lastFailure ?: "None"),
        "External scrobbling" to "Server-managed; API acceptance does not confirm external delivery",
    )

    private suspend fun attempt(operation: String, block: suspend () -> Unit): Boolean = try {
        block()
        true
    }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            lastFailure = "$operation: ${failure.safeDiagnosticMessage()}"
            false
        }

    private fun PlaybackState.isTerminal() = this == PlaybackState.Finished || this == PlaybackState.Stopped || this is PlaybackState.Error
}

private fun Throwable.safeDiagnosticMessage(): String =
    (message ?: this::class.simpleName ?: "Unknown error")
        .replace(Regex("(?i)(^|[?&\\s])(u|p|t|s|token|password|api[_-]?key)=([^&\\s]+)")) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}=<redacted>"
        }
        .replace(Regex("://[^/@\\s]+:[^/@\\s]+@"), "://<redacted>@")
