package app.naviamp.domain.playback

/** Shared resume eligibility for engine focus and external audio-session interruptions. */
class PlaybackInterruptionPolicy {
    private var resumeAfterInterruption = false

    fun began(wasPlaying: Boolean): Boolean {
        resumeAfterInterruption = resumeAfterInterruption || wasPlaying
        return wasPlaying
    }

    fun ended(shouldResume: Boolean): Boolean {
        val resume = resumeAfterInterruption && shouldResume
        cancel()
        return resume
    }

    fun cancel() { resumeAfterInterruption = false }
}
