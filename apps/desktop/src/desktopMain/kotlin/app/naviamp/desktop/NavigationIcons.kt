package app.naviamp.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object NavigationIcons {
    val Home: ImageVector = ImageVector.Builder(
        name = "Home",
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
            moveTo(4f, 11f)
            lineTo(12f, 4f)
            lineTo(20f, 11f)
            moveTo(6.5f, 10f)
            lineTo(6.5f, 20f)
            lineTo(10f, 20f)
            lineTo(10f, 15f)
            lineTo(14f, 15f)
            lineTo(14f, 20f)
            lineTo(17.5f, 20f)
            lineTo(17.5f, 10f)
        }
    }.build()

    val Library: ImageVector = ImageVector.Builder(
        name = "Library",
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
            moveTo(8f, 5f)
            lineTo(8f, 16f)
            curveTo(7.1f, 15.5f, 5.7f, 15.4f, 4.8f, 16.1f)
            curveTo(3.8f, 16.9f, 3.9f, 18.3f, 5.1f, 18.8f)
            curveTo(6.4f, 19.4f, 8f, 18.6f, 8f, 17.1f)
            moveTo(8f, 7f)
            lineTo(18f, 5.2f)
            lineTo(18f, 14.8f)
            curveTo(17.1f, 14.3f, 15.7f, 14.3f, 14.8f, 15.1f)
            curveTo(13.8f, 15.9f, 13.9f, 17.3f, 15.1f, 17.8f)
            curveTo(16.4f, 18.4f, 18f, 17.6f, 18f, 16.1f)
        }
    }.build()

    val Search: ImageVector = ImageVector.Builder(
        name = "Search",
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
            moveTo(10.8f, 5f)
            curveTo(7.6f, 5f, 5f, 7.6f, 5f, 10.8f)
            curveTo(5f, 14f, 7.6f, 16.6f, 10.8f, 16.6f)
            curveTo(14f, 16.6f, 16.6f, 14f, 16.6f, 10.8f)
            curveTo(16.6f, 7.6f, 14f, 5f, 10.8f, 5f)
            close()
            moveTo(15.2f, 15.2f)
            lineTo(20f, 20f)
        }
    }.build()

    val Downloads: ImageVector = ImageVector.Builder(
        name = "Downloads",
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
            moveTo(12f, 4f)
            lineTo(12f, 15f)
            moveTo(7.5f, 10.8f)
            lineTo(12f, 15.3f)
            lineTo(16.5f, 10.8f)
            moveTo(5f, 20f)
            lineTo(19f, 20f)
        }
    }.build()

    val Settings: ImageVector = ImageVector.Builder(
        name = "Settings",
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = Viewport,
        viewportHeight = Viewport,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 8.3f)
            curveTo(10f, 8.3f, 8.3f, 10f, 8.3f, 12f)
            curveTo(8.3f, 14f, 10f, 15.7f, 12f, 15.7f)
            curveTo(14f, 15.7f, 15.7f, 14f, 15.7f, 12f)
            curveTo(15.7f, 10f, 14f, 8.3f, 12f, 8.3f)
            close()
            moveTo(10.5f, 3.2f)
            lineTo(13.5f, 3.2f)
            lineTo(14f, 5.5f)
            curveTo(14.6f, 5.7f, 15.1f, 5.9f, 15.6f, 6.3f)
            lineTo(17.8f, 5.4f)
            lineTo(18.9f, 6.5f)
            lineTo(18f, 8.7f)
            curveTo(18.3f, 9.2f, 18.6f, 9.7f, 18.8f, 10.3f)
            lineTo(21f, 11.2f)
            lineTo(21f, 12.8f)
            lineTo(18.8f, 13.7f)
            curveTo(18.6f, 14.3f, 18.3f, 14.8f, 18f, 15.3f)
            lineTo(18.9f, 17.5f)
            lineTo(17.8f, 18.6f)
            lineTo(15.6f, 17.7f)
            curveTo(15.1f, 18.1f, 14.6f, 18.3f, 14f, 18.5f)
            lineTo(13.5f, 20.8f)
            lineTo(10.5f, 20.8f)
            lineTo(10f, 18.5f)
            curveTo(9.4f, 18.3f, 8.9f, 18.1f, 8.4f, 17.7f)
            lineTo(6.2f, 18.6f)
            lineTo(5.1f, 17.5f)
            lineTo(6f, 15.3f)
            curveTo(5.7f, 14.8f, 5.4f, 14.3f, 5.2f, 13.7f)
            lineTo(3f, 12.8f)
            lineTo(3f, 11.2f)
            lineTo(5.2f, 10.3f)
            curveTo(5.4f, 9.7f, 5.7f, 9.2f, 6f, 8.7f)
            lineTo(5.1f, 6.5f)
            lineTo(6.2f, 5.4f)
            lineTo(8.4f, 6.3f)
            curveTo(8.9f, 5.9f, 9.4f, 5.7f, 10f, 5.5f)
            lineTo(10.5f, 3.2f)
            close()
        }
    }.build()

    val ChevronDown: ImageVector = ImageVector.Builder(
        name = "ChevronDown",
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = Viewport,
        viewportHeight = Viewport,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6f, 9f)
            lineTo(12f, 15f)
            lineTo(18f, 9f)
        }
    }.build()

    val Back: ImageVector = ImageVector.Builder(
        name = "Back",
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = Viewport,
        viewportHeight = Viewport,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.1f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(15f, 5f)
            lineTo(8f, 12f)
            lineTo(15f, 19f)
        }
    }.build()

    val Trash: ImageVector = ImageVector.Builder(
        name = "Trash",
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
            moveTo(5f, 7f)
            lineTo(19f, 7f)
            moveTo(10f, 11f)
            lineTo(10f, 17f)
            moveTo(14f, 11f)
            lineTo(14f, 17f)
            moveTo(8f, 7f)
            lineTo(8.8f, 20f)
            lineTo(15.2f, 20f)
            lineTo(16f, 7f)
            moveTo(9.5f, 7f)
            lineTo(10.2f, 4.5f)
            lineTo(13.8f, 4.5f)
            lineTo(14.5f, 7f)
        }
    }.build()

    val Edit: ImageVector = ImageVector.Builder(
        name = "Edit",
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
            moveTo(5f, 19f)
            lineTo(9f, 18.2f)
            lineTo(18.2f, 9f)
            curveTo(19f, 8.2f, 19f, 6.9f, 18.2f, 6.1f)
            lineTo(17.9f, 5.8f)
            curveTo(17.1f, 5f, 15.8f, 5f, 15f, 5.8f)
            lineTo(5.8f, 15f)
            lineTo(5f, 19f)
            close()
            moveTo(13.8f, 7f)
            lineTo(17f, 10.2f)
        }
    }.build()
}

private val IconSize = 24.dp
private const val Viewport = 24f
