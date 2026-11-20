@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.apple.media

import app.naviamp.presentation.*
import app.naviamp.apple.media.lifecycle.*
import platform.Foundation.NSNotification

/** Native SDK availability guard, including weak-link safety on older operating systems. */
fun appleAudioSessionUsesLifecycleNotifications(): Boolean = NaviampAudioLifecycleAvailable()

object AppleAudioSessionLifecycleNotificationNames {
    val active: String? get() = NaviampAudioActiveNotification()
    val inactive: String? get() = NaviampAudioInactiveNotification()
    val resumption: String? get() = NaviampAudioResumptionNotification()
}

/** Typed public Objective-C context reads. Synthetic native-test payloads remain a separate effect. */
interface AppleAudioSessionLifecyclePayloads {
    fun deactivation(notification: NSNotification): NaviampExternalPlaybackAudioEvent.SessionBecameInactive?
    fun resumption(notification: NSNotification): NaviampAudioSessionResumptionRecommendation?
}

object AppleNativeAudioSessionLifecyclePayloads : AppleAudioSessionLifecyclePayloads {
    override fun deactivation(notification: NSNotification): NaviampExternalPlaybackAudioEvent.SessionBecameInactive? {
        val source = when (NaviampAudioReadSource(notification)) {
            NaviampAudioSourceMissing -> return null
            NaviampAudioSourceApplication -> NaviampAudioSessionDeactivationSource.Application
            NaviampAudioSourceSystem -> NaviampAudioSessionDeactivationSource.System
            else -> NaviampAudioSessionDeactivationSource.Unknown
        }
        val reason = when (NaviampAudioReadReason(notification)) {
            NaviampAudioReasonMissing -> null
            NaviampAudioReasonOutputDisconnected -> NaviampAudioSessionInterruptionReason.OutputDisconnected
            else -> NaviampAudioSessionInterruptionReason.Other
        }
        return NaviampExternalPlaybackAudioEvent.SessionBecameInactive(source, reason)
    }
    override fun resumption(notification: NSNotification): NaviampAudioSessionResumptionRecommendation? =
        when (NaviampAudioReadRecommendation(notification)) {
            NaviampAudioRecommendationMissing -> null
            NaviampAudioRecommendationResume -> NaviampAudioSessionResumptionRecommendation.Resume
            NaviampAudioRecommendationStayPaused -> NaviampAudioSessionResumptionRecommendation.StayPaused
            else -> NaviampAudioSessionResumptionRecommendation.Unknown
        }
}
