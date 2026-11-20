package app.naviamp.ios.playback

import app.naviamp.apple.media.ApplePlaybackSurface
import app.naviamp.presentation.NaviampCoreExternalPlaybackBridge
import app.naviamp.presentation.NaviampExternalPlaybackSurfaceSession
import app.naviamp.presentation.attachSurface
import kotlinx.coroutines.CoroutineScope
import platform.UIKit.UIImage

/** Existing iOS entry point for the shared MediaPlayer/AVAudioSession/UIKit native effect. */
class IosNativePlaybackIntegration(
    scope: CoroutineScope,
    bridge: NaviampCoreExternalPlaybackBridge,
    artworkBytes: suspend (String) -> ByteArray?,
) {
    private val session: NaviampExternalPlaybackSurfaceSession<UIImage> = bridge.attachSurface(
        scope, ApplePlaybackSurface(), artworkBytes, onFailure = { throw it },
    )

    fun close() = session.close()
}
