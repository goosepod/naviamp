package app.naviamp.domain.playback

data class PlaybackProgressEffectApplier(
    val clearPendingSeek: () -> Unit = {},
    val clearPendingRestoreStart: () -> Unit = {},
    val resetProgress: () -> Unit = {},
    val savePlaybackPosition: (PlaybackProgress) -> Unit = {},
    val reportPlaybackProgress: (PlaybackProgress) -> Unit = {},
    val updateProgress: (PlaybackProgress) -> Unit = {},
)

data class PlaybackProgressEffectResult(
    val ignored: Boolean = false,
    val resetToUnknown: Boolean = false,
    val progress: PlaybackProgress? = null,
)

fun applyPlaybackProgressEffects(
    plan: PlaybackProgressUpdatePlan,
    updateProgress: Boolean = true,
    applier: PlaybackProgressEffectApplier,
): PlaybackProgressEffectResult {
    if (plan.ignore) return PlaybackProgressEffectResult(ignored = true)
    if (plan.clearPendingSeek) applier.clearPendingSeek()
    if (plan.clearPendingRestoreStart) applier.clearPendingRestoreStart()
    if (plan.resetToUnknown) {
        applier.resetProgress()
        return PlaybackProgressEffectResult(resetToUnknown = true)
    }
    val progress = plan.progress ?: return PlaybackProgressEffectResult()
    if (plan.shouldSavePlaybackPosition) applier.savePlaybackPosition(progress)
    applier.reportPlaybackProgress(progress)
    if (updateProgress) applier.updateProgress(progress)
    return PlaybackProgressEffectResult(progress = progress)
}
