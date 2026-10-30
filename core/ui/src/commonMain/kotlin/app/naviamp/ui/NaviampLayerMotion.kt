package app.naviamp.ui

/** A portable linear timeline. Native compositors consume this data without owning motion policy. */
internal data class NaviampLayerMotion(
    val values: List<Float>,
    val timesMillis: List<Long>,
    val repeat: Boolean = false,
) {
    init {
        require(values.size >= 2 && values.size == timesMillis.size)
        require(values.all { it.isFinite() })
        require(timesMillis.first() == 0L)
        require(timesMillis.zipWithNext().all { (a, b) -> b > a })
    }

    val durationMillis: Long get() = timesMillis.last()

    fun valueAt(elapsedMillis: Long): Float {
        val bounded = elapsedMillis.coerceAtLeast(0L)
        val time = if (repeat) bounded % durationMillis else bounded.coerceAtMost(durationMillis)
        val end = timesMillis.indexOfFirst { it >= time }.coerceAtLeast(0)
        if (end == 0) return values.first()
        val fraction = (time - timesMillis[end - 1]).toDouble() / (timesMillis[end] - timesMillis[end - 1])
        return values[end - 1] + (values[end] - values[end - 1]) * fraction.toFloat()
    }
}

internal fun marqueeLayerMotion(overflowPixels: Float, rightToLeft: Boolean = false): NaviampLayerMotion? {
    if (!overflowPixels.isFinite() || overflowPixels <= 0f) return null
    val travelMillis = (overflowPixels.toDouble() * 24).toLong().coerceAtLeast(1800L)
    val edge = if (rightToLeft) overflowPixels else -overflowPixels
    return NaviampLayerMotion(
        values = listOf(0f, 0f, edge, edge, 0f),
        timesMillis = listOf(0L, 800L, 800L + travelMillis, 1600L + travelMillis, 1600L + 2 * travelMillis),
        repeat = true,
    )
}

internal fun progressLayerMotion(progress: Float, durationSeconds: Double?): NaviampLayerMotion? {
    if (!progress.isFinite() || durationSeconds == null || !durationSeconds.isFinite() || durationSeconds <= 0.0) return null
    val start = progress.coerceIn(0f, 1f)
    if (start == 1f) return null
    val remaining = ((1.0 - start) * durationSeconds * 1000).toLong().coerceAtLeast(1L)
    return NaviampLayerMotion(listOf(start, 1f), listOf(0L, remaining))
}

/** Keep continuous playback updates smooth, but honor pauses, seeks, and invalid durations immediately. */
internal class NaviampProgressPrediction {
    private var motion: NaviampLayerMotion? = null
    private var startedMillis = 0L
    private var previousDuration: Double? = null
    private var previousValue: Float? = null
    var revision = 0L
        private set

    fun update(target: Float, smooth: Boolean, duration: Double?, nowMillis: Long): Float {
        val bounded = if (target.isFinite()) target.coerceIn(0f, 1f) else 0f
        val predicted = motion?.valueAt(nowMillis - startedMillis) ?: bounded
        val canAdvance = smooth && duration != null && duration.isFinite() && duration > 0.0
        val threshold = if (canAdvance) minOf(.025f, (2.0 / requireNotNull(duration)).toFloat()) else 0f
        if (canAdvance && motion != null && duration == previousDuration &&
            kotlin.math.abs(predicted - bounded) <= threshold) return predicted
        if (!canAdvance && motion == null && previousValue == bounded && duration == previousDuration) return bounded
        val start = if (canAdvance && duration == previousDuration && kotlin.math.abs(predicted - bounded) <= threshold) predicted else bounded
        motion = if (canAdvance) progressLayerMotion(start, duration) else null
        startedMillis = nowMillis
        previousDuration = duration
        previousValue = bounded
        revision++
        return start
    }
}

/** Resume a finite compositor timeline at its current position after rebinding or resizing. */
internal fun NaviampLayerMotion.remainingAfter(elapsedMillis: Long): NaviampLayerMotion? {
    require(!repeat)
    val elapsed = elapsedMillis.coerceAtLeast(0L)
    if (elapsed >= durationMillis) return null
    val remaining = timesMillis.indices.filter { timesMillis[it] > elapsed }
    return NaviampLayerMotion(
        listOf(valueAt(elapsed)) + remaining.map { values[it] },
        listOf(0L) + remaining.map { timesMillis[it] - elapsed },
    )
}
