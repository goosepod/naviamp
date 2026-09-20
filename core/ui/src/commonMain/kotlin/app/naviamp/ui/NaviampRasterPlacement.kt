package app.naviamp.ui

/** Absolute scalar properties for a cached layer; native adapters only submit these timelines. */
internal data class NaviampRasterScalar(val value: Float, val motion: NaviampLayerMotion? = null)

internal data class NaviampRasterPlacement(
    val x: NaviampRasterScalar,
    val y: Float,
    val left: NaviampRasterScalar,
    val right: NaviampRasterScalar,
    val height: Float,
)

internal fun NaviampRasterLayer.placement(width: Float, height: Float): NaviampRasterPlacement {
    fun NaviampLayerMotion?.mapped(scale: Float, offset: Float = 0f) =
        this?.copy(values = values.map { it * scale + offset })
    val edge = NaviampRasterScalar(width * reveal, revealMotion.mapped(width))
    return NaviampRasterPlacement(
        x = NaviampRasterScalar(origin.x, translation.mapped(1f, origin.x)),
        y = origin.y,
        left = if (clipFromStart) edge else NaviampRasterScalar(0f),
        right = if (clipFromStart) NaviampRasterScalar(width) else edge,
        height = height,
    )
}

/** Layout moves preserve the timeline; new pixels, motion, or viewport size start a new scene. */
internal class NaviampRasterSceneClock {
    private var layers: List<NaviampRasterLayer>? = null
    private var width = 0f
    private var height = 0f
    var startedMillis = 0L
        private set

    fun update(layers: List<NaviampRasterLayer>, width: Float, height: Float, nowMillis: Long): Boolean {
        if (this.layers == layers && this.width == width && this.height == height) return false
        this.layers = layers
        this.width = width
        this.height = height
        startedMillis = nowMillis
        return true
    }
}
