package app.naviamp.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object TransportIcons {
    val Play: ImageVector = ImageVector.Builder(
        name = "Play",
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = Viewport,
        viewportHeight = Viewport,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 5f)
            lineTo(19f, 12f)
            lineTo(8f, 19f)
            close()
        }
    }.build()

    val Pause: ImageVector = ImageVector.Builder(
        name = "Pause",
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = Viewport,
        viewportHeight = Viewport,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(7f, 5f)
            lineTo(10f, 5f)
            lineTo(10f, 19f)
            lineTo(7f, 19f)
            close()
            moveTo(14f, 5f)
            lineTo(17f, 5f)
            lineTo(17f, 19f)
            lineTo(14f, 19f)
            close()
        }
    }.build()

    val Previous: ImageVector = ImageVector.Builder(
        name = "Previous",
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = Viewport,
        viewportHeight = Viewport,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6f, 5f)
            lineTo(6f, 19f)
            moveTo(19f, 5f)
            lineTo(9f, 12f)
            lineTo(19f, 19f)
            close()
        }
    }.build()

    val Next: ImageVector = ImageVector.Builder(
        name = "Next",
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = Viewport,
        viewportHeight = Viewport,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(18f, 5f)
            lineTo(18f, 19f)
            moveTo(5f, 5f)
            lineTo(15f, 12f)
            lineTo(5f, 19f)
            close()
        }
    }.build()

    val Volume: ImageVector = ImageVector.Builder(
        name = "Volume",
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = Viewport,
        viewportHeight = Viewport,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4f, 9f)
            lineTo(8f, 9f)
            lineTo(13f, 5f)
            lineTo(13f, 19f)
            lineTo(8f, 15f)
            lineTo(4f, 15f)
            close()
            moveTo(17f, 9f)
            curveTo(18f, 10f, 18.5f, 11f, 18.5f, 12f)
            curveTo(18.5f, 13f, 18f, 14f, 17f, 15f)
            moveTo(19.5f, 6.5f)
            curveTo(21f, 8f, 22f, 10f, 22f, 12f)
            curveTo(22f, 14f, 21f, 16f, 19.5f, 17.5f)
        }
    }.build()

    val Radio: ImageVector = ImageVector.Builder(
        name = "Radio",
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = Viewport,
        viewportHeight = Viewport,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 12f)
            lineTo(12f, 12.1f)
            moveTo(8.7f, 8.7f)
            curveTo(6.9f, 10.5f, 6.9f, 13.5f, 8.7f, 15.3f)
            moveTo(15.3f, 8.7f)
            curveTo(17.1f, 10.5f, 17.1f, 13.5f, 15.3f, 15.3f)
            moveTo(5.7f, 5.7f)
            curveTo(2.2f, 9.2f, 2.2f, 14.8f, 5.7f, 18.3f)
            moveTo(18.3f, 5.7f)
            curveTo(21.8f, 9.2f, 21.8f, 14.8f, 18.3f, 18.3f)
        }
    }.build()

    val Menu: ImageVector = ImageVector.Builder(
        name = "Menu",
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = Viewport,
        viewportHeight = Viewport,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(5f, 7f)
            lineTo(19f, 7f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
            moveTo(5f, 17f)
            lineTo(19f, 17f)
        }
    }.build()
}

private val IconSize = 24.dp
private const val Viewport = 24f
