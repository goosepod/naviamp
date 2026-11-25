package app.naviamp.presentation

import app.naviamp.domain.playback.SleepTimerState
import app.naviamp.domain.playback.sleepTimerFadeVolumeFactor
import app.naviamp.domain.playback.sleepTimerRemainingMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs the timed part of a duration sleep timer: it wakes at the fade start, steps the engine's
 * output fade, and reports the deadline. Steps only reach the engine, never presentation state.
 */
internal class NaviampCoreSleepTimerClock(
    private val scope: CoroutineScope,
    private val nowEpochMillis: () -> Long,
    private val applyFadeFactor: (Float) -> Unit,
    private val onDeadline: (SleepTimerState) -> Unit,
) {
    private var job: Job? = null
    private var appliedFactor = 1f

    fun schedule(timer: SleepTimerState?) {
        job?.cancel()
        job = null
        if (timer == null || !timer.isDuration) {
            apply(1f)
            return
        }
        apply(sleepTimerFadeVolumeFactor(timer, nowEpochMillis()))
        if (timer.fadeMillis > 0L && timer.playingSinceEpochMillis == null) return
        job = scope.launch {
            while (true) {
                val now = nowEpochMillis()
                val remaining = sleepTimerRemainingMillis(timer, now) ?: return@launch
                if (remaining <= 0L) {
                    apply(if (timer.fadeMillis > 0L) 0f else 1f)
                    job = null
                    onDeadline(timer)
                    return@launch
                }
                val fadeStart = timer.fadeStartEpochMillis
                when {
                    fadeStart == null -> delay(remaining)
                    now < fadeStart -> {
                        apply(1f)
                        delay(fadeStart - now)
                    }
                    else -> {
                        apply(sleepTimerFadeVolumeFactor(timer, now))
                        delay(minOf(FadeStepMillis, remaining))
                    }
                }
            }
        }
    }

    /** A faded ending stays silent while paused; the listener's resume restores the level. */
    fun restore() = apply(1f)

    private fun apply(factor: Float) {
        if (factor == appliedFactor) return
        appliedFactor = factor
        applyFadeFactor(factor)
    }
}

// A one-minute fade moves in steps well under a decibel until it is nearly silent.
private const val FadeStepMillis = 250L
