@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.ios.playback

import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import kotlin.test.Test
import kotlin.test.assertEquals

class IosBassPlaybackSessionTest {
    @Test fun nativeInitializationPreservesTheSessionRequiredBySystemMediaControls() {
        val backend = IosBassAudioBackend()
        try {
            backend.init().getOrThrow()
            val session = AVAudioSession.sharedInstance()
            assertEquals(AVAudioSessionCategoryPlayback, session.category)
            assertEquals(0uL, session.categoryOptions and AVAudioSessionCategoryOptionMixWithOthers)
        } finally { backend.free().getOrThrow() }
    }
}
