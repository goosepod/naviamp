package app.naviamp.desktop.platform

import app.naviamp.desktop.settings.defaultDesktopCoreSettingsPath
import java.awt.Rectangle
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

data class DesktopWindowGeometry(
    val widthDp: Float = 950f,
    val heightDp: Float = 640f,
    val xDp: Float? = null,
    val yDp: Float? = null,
) {
    fun normalized(availableScreens: List<Rectangle>): DesktopWindowGeometry {
        val normalizedSize = copy(
            widthDp = widthDp.coerceAtLeast(MinDesktopWindowWidthDp),
            heightDp = heightDp.coerceAtLeast(MinDesktopWindowHeightDp),
        )
        val x = normalizedSize.xDp ?: return normalizedSize.copy(xDp = null, yDp = null)
        val y = normalizedSize.yDp ?: return normalizedSize.copy(xDp = null, yDp = null)
        val restoredBounds = Rectangle(x.toInt(), y.toInt(), normalizedSize.widthDp.toInt(), normalizedSize.heightDp.toInt())
        return if (availableScreens.isEmpty() || availableScreens.any(restoredBounds::intersects)) {
            normalizedSize
        } else {
            normalizedSize.copy(xDp = null, yDp = null)
        }
    }
}

class DesktopWindowGeometryStore(
    private val path: Path = defaultDesktopCoreSettingsPath().resolveSibling("window.properties"),
) {
    fun load(): DesktopWindowGeometry {
        if (!path.exists()) return DesktopWindowGeometry()
        val values = Properties()
        return runCatching {
            path.inputStream().use(values::load)
            DesktopWindowGeometry(
                widthDp = values.float(WidthKey) ?: 950f,
                heightDp = values.float(HeightKey) ?: 640f,
                xDp = values.float(XKey),
                yDp = values.float(YKey),
            )
        }.getOrDefault(DesktopWindowGeometry())
    }

    @Synchronized
    fun save(geometry: DesktopWindowGeometry) {
        Files.createDirectories(path.parent)
        val values = Properties().apply {
            setProperty(WidthKey, geometry.widthDp.toString())
            setProperty(HeightKey, geometry.heightDp.toString())
            geometry.xDp?.let { setProperty(XKey, it.toString()) }
            geometry.yDp?.let { setProperty(YKey, it.toString()) }
        }
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        temporary.outputStream().use { output -> values.store(output, null) }
        runCatching {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

fun availableDesktopScreenBounds(): List<Rectangle> =
    java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
        .screenDevices
        .map { device -> device.defaultConfiguration.bounds }

const val MinDesktopWindowWidthDp = 320f
const val MinDesktopWindowHeightDp = 500f

private const val WidthKey = "widthDp"
private const val HeightKey = "heightDp"
private const val XKey = "xDp"
private const val YKey = "yDp"

private fun Properties.float(key: String): Float? = getProperty(key)?.toFloatOrNull()
