package app.naviamp.presentation

import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.settings.PlaybackSessionSettings

internal fun emptyPlaybackSessions() = NaviampPlaybackSessionController(
    object : PlaybackSessionRepository {
        override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? = null
        override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) = Unit
    },
)
