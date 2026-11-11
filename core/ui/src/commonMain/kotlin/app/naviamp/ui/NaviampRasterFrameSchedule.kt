package app.naviamp.ui

import kotlin.math.ceil
import kotlin.math.floor

/** Shared deadlines for cached pixels. Translation retains its original sampling cadence. */
internal fun naviampRasterMotionDelay(
    translation: NaviampLayerMotion?, reveal: NaviampLayerMotion?, width: Float, elapsedMillis: Long,
): Long? = when {
    translation != null -> if (translation.repeat || elapsedMillis < translation.durationMillis) 16L else null
    reveal != null -> reveal.nextPixelChangeDelay(width, elapsedMillis)
    else -> null
}

internal fun naviampRasterFrameDelay(
    layers: List<NaviampRasterLayer>, width: Float, elapsedMillis: Long,
    available: Boolean = true, needsUpdate: Boolean = false,
): Long? {
    if (!available || layers.isEmpty()) return null
    if (needsUpdate) return 0L
    var next: Long? = null
    for (layer in layers) {
        val delay = naviampRasterMotionDelay(layer.translation, layer.revealMotion, width, elapsedMillis) ?: continue
        next = next?.let { minOf(it, delay) } ?: delay
    }
    return next
}

/** Wake by the next floor/ceil edge, without polling unchanged whole-pixel reveal geometry. */
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
    if (edge == floor(edge)) return 1L
    val boundary = if (velocity > 0) ceil(edge) else floor(edge)
    // Schedule before the boundary; the native vsync chooses the actual presentation time.
    return floor((boundary - edge) / velocity).toLong().coerceIn(1L, remaining)
}

internal sealed interface NaviampRasterFrameRequest {
    data object Unchanged : NaviampRasterFrameRequest
    data object Cancel : NaviampRasterFrameRequest
    data class Schedule(val delayMillis: Long) : NaviampRasterFrameRequest
}

/** Adapters execute the requested OS callback operation; Core selects its deadline and lifetime. */
internal class NaviampRasterFrameSchedule {
    private var dueMillis: Long? = null

    fun request(delays: List<Long>, nowMillis: Long): NaviampRasterFrameRequest {
        val delay = delays.minOrNull()?.coerceAtLeast(0L)
        if (delay == null) {
            val pending = dueMillis != null
            dueMillis = null
            return if (pending) NaviampRasterFrameRequest.Cancel else NaviampRasterFrameRequest.Unchanged
        }
        val due = nowMillis + delay
        if (dueMillis?.let { it <= due } == true) return NaviampRasterFrameRequest.Unchanged
        dueMillis = due
        return NaviampRasterFrameRequest.Schedule(delay)
    }

    fun delivered() { dueMillis = null }
}
