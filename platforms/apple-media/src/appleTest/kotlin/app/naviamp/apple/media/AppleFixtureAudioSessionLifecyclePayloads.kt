@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.apple.media

import app.naviamp.presentation.*
import platform.Foundation.NSNotification

/** Synthetic edge cases use a private test tag; real contexts always go through the production reader. */
object AppleFixtureAudioSessionLifecyclePayloads : AppleAudioSessionLifecyclePayloads {
    const val fixtureSource = "naviamp-test-source"
    const val fixtureRecommendation = "naviamp-test-recommendation"
    override fun deactivation(notification: NSNotification): NaviampExternalPlaybackAudioEvent.SessionBecameInactive? {
        AppleNativeAudioSessionLifecyclePayloads.deactivation(notification)?.let {
            println("OS27 typed deactivation received: ${it.source}")
            return it
        }
        val source = NaviampAudioSessionDeactivationSource.entries.firstOrNull { it.name == notification.userInfo?.get(fixtureSource) } ?: return null
        return NaviampExternalPlaybackAudioEvent.SessionBecameInactive(source)
    }
    override fun resumption(notification: NSNotification): NaviampAudioSessionResumptionRecommendation? {
        AppleNativeAudioSessionLifecyclePayloads.resumption(notification)?.let {
            println("OS27 typed resumption received: $it")
            return it
        }
        return NaviampAudioSessionResumptionRecommendation.entries.firstOrNull { it.name == notification.userInfo?.get(fixtureRecommendation) }
    }
}
