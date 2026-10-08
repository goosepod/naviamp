package app.naviamp.ui

import kotlin.math.ceil
import kotlin.math.floor

/** Whole-pixel reveals only need a native callback when their floor/ceil bounds can change.
 * Translation keeps the existing animation cadence. Native timeline compositors need no callbacks.
 */
internal fun naviampRasterFrameDelay(layers: List<NaviampRasterLayer>, width: Float, elapsedMillis: Long): Long? =
    layers.mapNotNull { layer ->
        layer.translation?.let { motion ->
            if (motion.repeat || elapsedMillis < motion.durationMillis) 16L else null
        } ?: if (layer.translation == null) layer.revealMotion?.nextPixelChangeDelay(width, elapsedMillis) else null
    }.minOrNull()

internal fun NaviampLayerMotion.nextPixelChangeDelay(scale: Float, elapsedMillis: Long): Long? {
    if (!scale.isFinite() || scale <= 0f) return null
    val elapsed = elapsedMillis.coerceAtLeast(0L)
    if (!repeat && elapsed >= durationMillis) return null
    val time = if (repeat) elapsed % durationMillis else elapsed
    val end = timesMillis.indexOfFirst { it > time }
    if (end <= 0) return null
    val remaining = timesMillis[end] - time
    val velocity = (values[end] - values[end - 1]).toDouble() * scale /
        (timesMillis[end] - timesMillis[end - 1])
    if (velocity == 0.0) return remaining
    val edge = valueAt(elapsed) * scale
    // At an integer boundary one side's ceil/floor changes immediately after the boundary.
    if (edge == floor(edge)) return 1L
    val boundary = if (velocity > 0) ceil(edge) else floor(edge)
    // Round toward the preceding millisecond; the next vsync must not miss a pixel boundary.
    return floor((boundary - edge) / velocity).toLong().coerceIn(1L, remaining)
}

internal sealed interface NaviampRasterFrameRequest {
    data object Unchanged : NaviampRasterFrameRequest
    data object Cancel : NaviampRasterFrameRequest
    data class Schedule(val delayMillis: Long) : NaviampRasterFrameRequest
}

/** Shared scheduling policy; a native adapter only posts/removes its OS frame callback. */
internal class NaviampRasterFrameSchedule {
    private var dueMillis: Long? = null
    fun request(delays: List<Long>, nowMillis: Long): NaviampRasterFrameRequest {
        val delay = delays.minOrNull()?.coerceAtLeast(0L)
        if (delay == null) {
            val hadCallback = dueMillis != null
            dueMillis = null
            return if (hadCallback) NaviampRasterFrameRequest.Cancel else NaviampRasterFrameRequest.Unchanged
        }
        val due = nowMillis + delay
        if (dueMillis?.let { it <= due } == true) return NaviampRasterFrameRequest.Unchanged
        dueMillis = due
        return NaviampRasterFrameRequest.Schedule(delay)
    }
    fun delivered() { dueMillis = null }
}
