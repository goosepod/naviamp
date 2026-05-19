package app.naviamp.android.playback

import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.provider.navidrome.NavidromeTlsSettings

interface AndroidPlaybackEngine : PlaybackEngine {
    fun applyTlsSettings(tlsSettings: NavidromeTlsSettings)

    fun updateNotificationMetadata(
        title: String?,
        subtitle: String?,
        coverArtUrl: String?,
    )

    fun release()
}
