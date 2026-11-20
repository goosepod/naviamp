@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package app.naviamp.tvos.playback

import app.naviamp.apple.media.*
import app.naviamp.presentation.*
import app.naviamp.apple.playback.AppleBassPlaybackEngineRuntime
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamHandle
import app.naviamp.domain.playback.*
import app.naviamp.testkit.*
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import platform.AVFAudio.*
import platform.Foundation.*
import kotlin.test.Test

/** Native notification/file/session effects only; the complete acceptance scenario lives in Core. */
class TvosAudioRecoveryIntegrationTest {
    @Test fun realAudioNotificationsRecoverCorePlaybackWithoutUnwantedAutoplay() = withHost { verifyNaviampAudioRecovery(it) }

    @Test fun remoteCommandsRecoverCorePlaybackThroughTheSurfaceSession() = withHost { verifyNaviampRemoteTransport(it) }

    private fun withHost(verify: suspend (NaviampAudioRecoveryHost<platform.UIKit.UIImage>) -> Unit) {
        check(NSThread.isMainThread)
        val directory = "${NSTemporaryDirectory().trimEnd('/')}/naviamp-tvos-audio-${NSUUID.UUID().UUIDString}"
        check(NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null))
        val backend = TvosBassAudioBackend()
        val handle = MutableStateFlow<BassStreamHandle?>(null)
        val observed = object : BassAudioBackend by backend {
            override fun play(stream: BassStreamHandle): Result<Unit> {
                handle.value = stream
                return backend.play(stream)
            }
        }
        val engine = CoreBassPlaybackEngine(Result.success(observed), AppleBassPlaybackEngineRuntime())
        val run = NaviampEventLoopTestRun(Dispatchers.Main) {
            verify(object : NaviampAudioRecoveryHost<platform.UIKit.UIImage> {
                override val engine = engine
                override val surface = ApplePlaybackSurface(AppleFixtureAudioSessionLifecyclePayloads)
                override val usesActivationLifecycle = appleAudioSessionUsesLifecycleNotifications()
                override val verifiesSystemInterruption = usesActivationLifecycle &&
                    NSProcessInfo.processInfo.environment["NAVIAMP_OS27_REQUIRE_CONTEXT"] == "1"
                override suspend fun requestSystemInterruption() {
                    val environment = NSProcessInfo.processInfo.environment
                    val path = checkNotNull(environment["NAVIAMP_OS27_INTERRUPTION_REQUEST"] as? String)
                    val token = checkNotNull(environment["NAVIAMP_OS27_INTERRUPTION_TOKEN"] as? String)
                    val bytes = token.encodeToByteArray()
                    bytes.usePinned {
                        check(NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()).writeToFile(path, true))
                    }
                }
                override suspend fun writeWave(bytes: ByteArray): String {
                    check(NSThread.isMainThread)
                    val path = "$directory/audio.wav"
                    bytes.usePinned {
                        check(NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()).writeToFile(path, true))
                    }
                    return checkNotNull(NSURL.fileURLWithPath(path).absoluteString)
                }
                override fun nativePositionSeconds(): Double? = handle.value?.let(backend::positionSeconds)
                override suspend fun deactivateAudioSession() {
                    check(NSThread.isMainThread)
                    check(AVAudioSession.sharedInstance().setActive(false, error = null))
                    println("audio recovery native session deactivated")
                }
                override suspend fun notify(stimulus: NaviampAudioRecoveryStimulus) {
                    check(NSThread.isMainThread)
                    val center = NSNotificationCenter.defaultCenter
                    val session = AVAudioSession.sharedInstance()
                    fun interruption(type: ULong, option: ULong? = null) {
                        val info = mutableMapOf<Any?, Any?>(AVAudioSessionInterruptionTypeKey to NSNumber(unsignedLongLong = type))
                        option?.let { info[AVAudioSessionInterruptionOptionKey] = NSNumber(unsignedLongLong = it) }
                        center.postNotificationName(AVAudioSessionInterruptionNotification, session, info)
                    }
                    fun route(reason: ULong) = center.postNotificationName(AVAudioSessionRouteChangeNotification, session,
                        mapOf(AVAudioSessionRouteChangeReasonKey to NSNumber(unsignedLongLong = reason)))
                    if (usesActivationLifecycle) {
                        when (stimulus) {
                            NaviampAudioRecoveryStimulus.Begin -> center.postNotificationName(AppleAudioSessionLifecycleNotificationNames.inactive, session,
                                mapOf(AppleFixtureAudioSessionLifecyclePayloads.fixtureSource to NaviampAudioSessionDeactivationSource.System.name))
                            NaviampAudioRecoveryStimulus.ApplicationDeactivation -> center.postNotificationName(AppleAudioSessionLifecycleNotificationNames.inactive, session,
                                mapOf(AppleFixtureAudioSessionLifecyclePayloads.fixtureSource to NaviampAudioSessionDeactivationSource.Application.name))
                            NaviampAudioRecoveryStimulus.EndMayResume, NaviampAudioRecoveryStimulus.EndDenied -> center.postNotificationName(
                                AppleAudioSessionLifecycleNotificationNames.resumption, session,
                                mapOf(AppleFixtureAudioSessionLifecyclePayloads.fixtureRecommendation to
                                    (if (stimulus == NaviampAudioRecoveryStimulus.EndMayResume) NaviampAudioSessionResumptionRecommendation.Resume else NaviampAudioSessionResumptionRecommendation.StayPaused).name))
                            NaviampAudioRecoveryStimulus.EndMissingOptions -> center.postNotificationName(AppleAudioSessionLifecycleNotificationNames.resumption, session, null)
                            NaviampAudioRecoveryStimulus.Activation -> center.postNotificationName(AppleAudioSessionLifecycleNotificationNames.active, session, null)
                            NaviampAudioRecoveryStimulus.Disconnect -> route(AVAudioSessionRouteChangeReasonOldDeviceUnavailable)
                            NaviampAudioRecoveryStimulus.NewRoute -> route(AVAudioSessionRouteChangeReasonNewDeviceAvailable)
                            NaviampAudioRecoveryStimulus.OverrideRoute -> route(AVAudioSessionRouteChangeReasonOverride)
                            NaviampAudioRecoveryStimulus.UnknownRoute -> route(999uL)
                            NaviampAudioRecoveryStimulus.Malformed -> {
                                center.postNotificationName(AppleAudioSessionLifecycleNotificationNames.inactive, session, mapOf("invalid" to "invalid"))
                                center.postNotificationName(AppleAudioSessionLifecycleNotificationNames.resumption, session, null)
                            }
                        }
                        return
                    }
                    when (stimulus) {
                        NaviampAudioRecoveryStimulus.Begin -> interruption(AVAudioSessionInterruptionTypeBegan)
                        NaviampAudioRecoveryStimulus.EndMayResume -> interruption(AVAudioSessionInterruptionTypeEnded, AVAudioSessionInterruptionOptionShouldResume)
                        NaviampAudioRecoveryStimulus.EndDenied -> interruption(AVAudioSessionInterruptionTypeEnded, 0uL)
                        NaviampAudioRecoveryStimulus.EndMissingOptions -> interruption(AVAudioSessionInterruptionTypeEnded)
                        NaviampAudioRecoveryStimulus.Disconnect -> route(AVAudioSessionRouteChangeReasonOldDeviceUnavailable)
                        NaviampAudioRecoveryStimulus.NewRoute -> route(AVAudioSessionRouteChangeReasonNewDeviceAvailable)
                        NaviampAudioRecoveryStimulus.OverrideRoute -> route(AVAudioSessionRouteChangeReasonOverride)
                        NaviampAudioRecoveryStimulus.UnknownRoute -> route(999uL)
                        NaviampAudioRecoveryStimulus.Activation -> Unit
                        NaviampAudioRecoveryStimulus.ApplicationDeactivation -> Unit
                        NaviampAudioRecoveryStimulus.Malformed -> {
                            center.postNotificationName(AVAudioSessionInterruptionNotification, session, null)
                            center.postNotificationName(AVAudioSessionInterruptionNotification, session, mapOf(AVAudioSessionInterruptionTypeKey to "invalid"))
                            interruption(999uL)
                            center.postNotificationName(AVAudioSessionRouteChangeNotification, session, null)
                        }
                    }
                }
            })
        }
        try {
            while (!run.isCompleted) NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
            run.finish()
        } finally {
            run.close()
            engine.release()
            check(NSFileManager.defaultManager.removeItemAtPath(directory, null))
        }
    }
}
