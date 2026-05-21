package app.naviamp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object NaviampTransportIcons {
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

    val Shuffle: ImageVector = ImageVector.Builder(
        name = "Shuffle",
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
            moveTo(3.5f, 7.5f)
            lineTo(6.2f, 7.5f)
            curveTo(8.4f, 7.5f, 10.2f, 9.2f, 12f, 12f)
            curveTo(13.8f, 14.8f, 15.6f, 16.5f, 17.8f, 16.5f)
            lineTo(20.5f, 16.5f)
            moveTo(18.1f, 14.1f)
            lineTo(20.5f, 16.5f)
            lineTo(18.1f, 18.9f)
            moveTo(3.5f, 16.5f)
            lineTo(6.2f, 16.5f)
            curveTo(8.4f, 16.5f, 10.2f, 14.8f, 12f, 12f)
            curveTo(13.8f, 9.2f, 15.6f, 7.5f, 17.8f, 7.5f)
            lineTo(20.5f, 7.5f)
            moveTo(18.1f, 5.1f)
            lineTo(20.5f, 7.5f)
            lineTo(18.1f, 9.9f)
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

    val Stop: ImageVector = ImageVector.Builder(
        name = "Stop",
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = Viewport,
        viewportHeight = Viewport,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(7f, 7f)
            lineTo(17f, 7f)
            lineTo(17f, 17f)
            lineTo(7f, 17f)
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
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 13.35f)
            curveTo(11.25f, 13.35f, 10.65f, 12.75f, 10.65f, 12f)
            curveTo(10.65f, 11.25f, 11.25f, 10.65f, 12f, 10.65f)
            curveTo(12.75f, 10.65f, 13.35f, 11.25f, 13.35f, 12f)
            curveTo(13.35f, 12.75f, 12.75f, 13.35f, 12f, 13.35f)
            close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
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

    val Heart: ImageVector = ImageVector.Builder(
        name = "Heart",
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
            moveTo(12f, 20f)
            curveTo(8.4f, 17.1f, 5.2f, 14.3f, 4.1f, 11.4f)
            curveTo(3.1f, 8.8f, 4.4f, 6f, 7.2f, 5.5f)
            curveTo(9f, 5.2f, 10.8f, 6.1f, 12f, 7.7f)
            curveTo(13.2f, 6.1f, 15f, 5.2f, 16.8f, 5.5f)
            curveTo(19.6f, 6f, 20.9f, 8.8f, 19.9f, 11.4f)
            curveTo(18.8f, 14.3f, 15.6f, 17.1f, 12f, 20f)
            close()
        }
    }.build()

    val Repeat: ImageVector = ImageVector.Builder(
        name = "Repeat",
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
            moveTo(17f, 2.8f)
            lineTo(20.5f, 6.3f)
            lineTo(17f, 9.8f)
            moveTo(3.5f, 10f)
            lineTo(3.5f, 8.3f)
            curveTo(3.5f, 7.2f, 4.4f, 6.3f, 5.5f, 6.3f)
            lineTo(20.5f, 6.3f)
            moveTo(7f, 21.2f)
            lineTo(3.5f, 17.7f)
            lineTo(7f, 14.2f)
            moveTo(20.5f, 14f)
            lineTo(20.5f, 15.7f)
            curveTo(20.5f, 16.8f, 19.6f, 17.7f, 18.5f, 17.7f)
            lineTo(3.5f, 17.7f)
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

    val Lyrics: ImageVector = ImageVector.Builder(
        name = "Lyrics",
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
            moveTo(7f, 4f)
            lineTo(17f, 4f)
            curveTo(18.1f, 4f, 19f, 4.9f, 19f, 6f)
            lineTo(19f, 18f)
            curveTo(19f, 19.1f, 18.1f, 20f, 17f, 20f)
            lineTo(7f, 20f)
            curveTo(5.9f, 20f, 5f, 19.1f, 5f, 18f)
            lineTo(5f, 6f)
            curveTo(5f, 4.9f, 5.9f, 4f, 7f, 4f)
            close()
            moveTo(8f, 8f)
            lineTo(16f, 8f)
            moveTo(8f, 12f)
            lineTo(16f, 12f)
            moveTo(8f, 16f)
            lineTo(13f, 16f)
        }
    }.build()

    val Visualizer: ImageVector = ImageVector.Builder(
        name = "Visualizer",
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
            moveTo(5f, 14.5f)
            lineTo(5f, 9.5f)
            moveTo(9.7f, 18f)
            lineTo(9.7f, 6f)
            moveTo(14.3f, 16f)
            lineTo(14.3f, 8f)
            moveTo(19f, 13f)
            lineTo(19f, 11f)
        }
    }.build()
}

private val IconSize = 24.dp
private const val Viewport = 24f
