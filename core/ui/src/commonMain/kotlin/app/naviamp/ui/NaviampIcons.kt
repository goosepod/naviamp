package app.naviamp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object NaviampIcons {
    private val IconSize = 24.dp
    private const val Viewport = 24f

    val Home = icon("Home") {
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
    val Player = filledIcon("Player") {
        moveTo(9f, 7f)
        lineTo(18f, 12f)
        lineTo(9f, 17f)
        close()
    }
    val Pause = filledIcon("Pause") {
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
    val Stop = filledIcon("Stop") {
        moveTo(7f, 7f)
        lineTo(17f, 7f)
        lineTo(17f, 17f)
        lineTo(7f, 17f)
        close()
    }
    val Playlist = icon("Playlist") {
        moveTo(5f, 6.5f)
        lineTo(14.5f, 6.5f)
        moveTo(5f, 11.5f)
        lineTo(14.5f, 11.5f)
        moveTo(5f, 16.5f)
        lineTo(11.5f, 16.5f)
        moveTo(17.5f, 13.5f)
        lineTo(17.5f, 20f)
        moveTo(14.3f, 16.7f)
        lineTo(20.7f, 16.7f)
    }
    val Brain = thinIcon("Brain") {
        moveTo(9f, 5.8f)
        curveTo(7.1f, 5.1f, 5.3f, 6.5f, 5.3f, 8.5f)
        curveTo(3.9f, 9.1f, 3.4f, 10.9f, 4.4f, 12.1f)
        curveTo(3.8f, 13.7f, 4.8f, 15.5f, 6.5f, 15.8f)
        curveTo(6.7f, 17.8f, 8.9f, 18.8f, 10.5f, 17.7f)
        lineTo(10.5f, 6.5f)
        curveTo(10.1f, 6.1f, 9.6f, 5.9f, 9f, 5.8f)
        close()
        moveTo(15f, 5.8f)
        curveTo(16.9f, 5.1f, 18.7f, 6.5f, 18.7f, 8.5f)
        curveTo(20.1f, 9.1f, 20.6f, 10.9f, 19.6f, 12.1f)
        curveTo(20.2f, 13.7f, 19.2f, 15.5f, 17.5f, 15.8f)
        curveTo(17.3f, 17.8f, 15.1f, 18.8f, 13.5f, 17.7f)
        lineTo(13.5f, 6.5f)
        curveTo(13.9f, 6.1f, 14.4f, 5.9f, 15f, 5.8f)
        close()
        moveTo(10.5f, 9.2f)
        curveTo(9.3f, 9.2f, 8.5f, 8.6f, 8.1f, 7.7f)
        moveTo(10.5f, 12.2f)
        curveTo(9.2f, 12.3f, 8.1f, 11.7f, 7.5f, 10.7f)
        moveTo(10.5f, 15.1f)
        curveTo(9.5f, 14.9f, 8.7f, 15.3f, 8.1f, 16.1f)
        moveTo(13.5f, 9.2f)
        curveTo(14.7f, 9.2f, 15.5f, 8.6f, 15.9f, 7.7f)
        moveTo(13.5f, 12.2f)
        curveTo(14.8f, 12.3f, 15.9f, 11.7f, 16.5f, 10.7f)
        moveTo(13.5f, 15.1f)
        curveTo(14.5f, 14.9f, 15.3f, 15.3f, 15.9f, 16.1f)
    }
    val Turntable = icon("Turntable") {
        moveTo(4.5f, 6f)
        lineTo(19.5f, 6f)
        curveTo(20.3f, 6f, 21f, 6.7f, 21f, 7.5f)
        lineTo(21f, 17.5f)
        curveTo(21f, 18.3f, 20.3f, 19f, 19.5f, 19f)
        lineTo(4.5f, 19f)
        curveTo(3.7f, 19f, 3f, 18.3f, 3f, 17.5f)
        lineTo(3f, 7.5f)
        curveTo(3f, 6.7f, 3.7f, 6f, 4.5f, 6f)
        close()
        moveTo(9.5f, 9f)
        curveTo(7.6f, 9f, 6f, 10.6f, 6f, 12.5f)
        curveTo(6f, 14.4f, 7.6f, 16f, 9.5f, 16f)
        curveTo(11.4f, 16f, 13f, 14.4f, 13f, 12.5f)
        curveTo(13f, 10.6f, 11.4f, 9f, 9.5f, 9f)
        close()
        moveTo(9.5f, 11.5f)
        curveTo(8.9f, 11.5f, 8.5f, 11.9f, 8.5f, 12.5f)
        curveTo(8.5f, 13.1f, 8.9f, 13.5f, 9.5f, 13.5f)
        curveTo(10.1f, 13.5f, 10.5f, 13.1f, 10.5f, 12.5f)
        curveTo(10.5f, 11.9f, 10.1f, 11.5f, 9.5f, 11.5f)
        close()
        moveTo(16.5f, 8.8f)
        lineTo(18.5f, 8.8f)
        moveTo(17.5f, 8.9f)
        lineTo(15.5f, 14.8f)
        lineTo(14.2f, 16.2f)
        moveTo(15.5f, 14.8f)
        lineTo(17.2f, 15.4f)
    }
    val Queue = icon("Queue") {
        moveTo(5f, 6.5f)
        lineTo(15.5f, 6.5f)
        moveTo(5f, 11.5f)
        lineTo(13f, 11.5f)
        moveTo(5f, 16.5f)
        lineTo(11.5f, 16.5f)
        moveTo(15.5f, 13.5f)
        lineTo(19.5f, 16.5f)
        lineTo(15.5f, 19.5f)
        moveTo(14.5f, 16.5f)
        lineTo(19.2f, 16.5f)
    }
    val Library = icon("Library") {
        moveTo(8f, 5f)
        lineTo(8f, 16.2f)
        curveTo(7.2f, 15.8f, 5.9f, 15.8f, 5f, 16.5f)
        curveTo(4f, 17.3f, 4.2f, 18.8f, 5.4f, 19.3f)
        curveTo(6.8f, 19.9f, 8f, 19f, 8f, 17.4f)
        moveTo(8f, 7.2f)
        lineTo(18f, 5.4f)
        lineTo(18f, 14.8f)
        curveTo(17.2f, 14.4f, 15.9f, 14.4f, 15f, 15.1f)
        curveTo(14f, 15.9f, 14.2f, 17.4f, 15.4f, 17.9f)
        curveTo(16.8f, 18.5f, 18f, 17.6f, 18f, 16f)
    }
    val Search = icon("Search") {
        moveTo(10.8f, 5f)
        curveTo(7.6f, 5f, 5f, 7.6f, 5f, 10.8f)
        curveTo(5f, 14f, 7.6f, 16.6f, 10.8f, 16.6f)
        curveTo(14f, 16.6f, 16.6f, 14f, 16.6f, 10.8f)
        curveTo(16.6f, 7.6f, 14f, 5f, 10.8f, 5f)
        close()
        moveTo(15.2f, 15.2f)
        lineTo(20f, 20f)
    }
    val Close = icon("Close") {
        moveTo(6f, 6f)
        lineTo(18f, 18f)
        moveTo(18f, 6f)
        lineTo(6f, 18f)
    }
    val Refresh = icon("Refresh") {
        moveTo(19f, 8f)
        curveTo(17.7f, 5.6f, 15.1f, 4f, 12f, 4f)
        curveTo(8.8f, 4f, 6.1f, 5.9f, 4.9f, 8.6f)
        moveTo(19f, 4.8f)
        lineTo(19f, 8f)
        lineTo(15.8f, 8f)
        moveTo(5f, 16f)
        curveTo(6.3f, 18.4f, 8.9f, 20f, 12f, 20f)
        curveTo(15.2f, 20f, 17.9f, 18.1f, 19.1f, 15.4f)
        moveTo(5f, 19.2f)
        lineTo(5f, 16f)
        lineTo(8.2f, 16f)
    }
    val Plus = icon("Plus") {
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(5f, 12f)
        lineTo(19f, 12f)
    }
    val Minus = icon("Minus") {
        moveTo(5f, 12f)
        lineTo(19f, 12f)
    }
    val InternetRadio = icon("InternetRadio") {
        moveTo(6f, 10f)
        lineTo(18.5f, 7.5f)
        moveTo(6f, 10f)
        lineTo(6f, 18.5f)
        lineTo(19f, 18.5f)
        lineTo(19f, 10f)
        close()
        moveTo(9f, 13.2f)
        lineTo(13f, 13.2f)
        moveTo(9f, 15.8f)
        lineTo(11.5f, 15.8f)
        moveTo(16f, 13.8f)
        curveTo(15.1f, 13.8f, 14.4f, 14.5f, 14.4f, 15.4f)
        curveTo(14.4f, 16.3f, 15.1f, 17f, 16f, 17f)
        curveTo(16.9f, 17f, 17.6f, 16.3f, 17.6f, 15.4f)
        curveTo(17.6f, 14.5f, 16.9f, 13.8f, 16f, 13.8f)
        close()
    }
    val Downloads = icon("Downloads") {
        moveTo(12f, 4f)
        lineTo(12f, 15f)
        moveTo(7.5f, 10.8f)
        lineTo(12f, 15.3f)
        lineTo(16.5f, 10.8f)
        moveTo(5f, 20f)
        lineTo(19f, 20f)
    }
    val Cache = icon("Cache") {
        moveTo(5f, 7f)
        curveTo(5f, 5.3f, 8.1f, 4f, 12f, 4f)
        curveTo(15.9f, 4f, 19f, 5.3f, 19f, 7f)
        curveTo(19f, 8.7f, 15.9f, 10f, 12f, 10f)
        curveTo(8.1f, 10f, 5f, 8.7f, 5f, 7f)
        close()
        moveTo(5f, 7f)
        lineTo(5f, 12f)
        curveTo(5f, 13.7f, 8.1f, 15f, 12f, 15f)
        curveTo(15.9f, 15f, 19f, 13.7f, 19f, 12f)
        lineTo(19f, 7f)
        moveTo(5f, 12f)
        lineTo(5f, 17f)
        curveTo(5f, 18.7f, 8.1f, 20f, 12f, 20f)
        curveTo(15.9f, 20f, 19f, 18.7f, 19f, 17f)
        lineTo(19f, 12f)
    }
    val Experience = icon("Experience") {
        moveTo(4f, 12f)
        curveTo(6.1f, 7.8f, 8.8f, 5.8f, 12f, 5.8f)
        curveTo(15.2f, 5.8f, 17.9f, 7.8f, 20f, 12f)
        curveTo(17.9f, 16.2f, 15.2f, 18.2f, 12f, 18.2f)
        curveTo(8.8f, 18.2f, 6.1f, 16.2f, 4f, 12f)
        close()
        moveTo(12f, 9f)
        curveTo(10.3f, 9f, 9f, 10.3f, 9f, 12f)
        curveTo(9f, 13.7f, 10.3f, 15f, 12f, 15f)
        curveTo(13.7f, 15f, 15f, 13.7f, 15f, 12f)
        curveTo(15f, 10.3f, 13.7f, 9f, 12f, 9f)
        close()
    }
    val Bug = icon("Bug") {
        moveTo(9f, 7f)
        curveTo(9.6f, 6.3f, 10.6f, 5.9f, 12f, 5.9f)
        curveTo(13.4f, 5.9f, 14.4f, 6.3f, 15f, 7f)
        moveTo(8f, 10f)
        lineTo(16f, 10f)
        moveTo(8f, 14f)
        lineTo(16f, 14f)
        moveTo(12f, 6f)
        lineTo(12f, 20f)
        moveTo(7f, 12f)
        lineTo(4f, 12f)
        moveTo(17f, 12f)
        lineTo(20f, 12f)
        moveTo(7.8f, 9f)
        lineTo(5.4f, 6.6f)
        moveTo(16.2f, 9f)
        lineTo(18.6f, 6.6f)
        moveTo(7.8f, 15f)
        lineTo(5.4f, 17.4f)
        moveTo(16.2f, 15f)
        lineTo(18.6f, 17.4f)
        moveTo(8f, 9.5f)
        curveTo(8f, 7.8f, 9.5f, 6.5f, 12f, 6.5f)
        curveTo(14.5f, 6.5f, 16f, 7.8f, 16f, 9.5f)
        lineTo(16f, 15.5f)
        curveTo(16f, 18f, 14.3f, 20f, 12f, 20f)
        curveTo(9.7f, 20f, 8f, 18f, 8f, 15.5f)
        close()
    }
    val AppMark = icon("AppMark") {
        moveTo(10.8f, 4.2f)
        curveTo(6.6f, 4.6f, 3.5f, 8f, 3.5f, 12f)
        curveTo(3.5f, 16f, 6.6f, 19.4f, 10.8f, 19.8f)
        moveTo(10.4f, 5.8f)
        curveTo(7.7f, 6.4f, 5.8f, 8.9f, 5.8f, 12f)
        moveTo(10.4f, 7.9f)
        curveTo(8.8f, 8.4f, 7.7f, 10f, 7.7f, 12f)
        moveTo(11.3f, 8.8f)
        curveTo(9.5f, 8.8f, 8.1f, 10.2f, 8.1f, 12f)
        curveTo(8.1f, 13.8f, 9.5f, 15.2f, 11.3f, 15.2f)
        curveTo(13.1f, 15.2f, 14.5f, 13.8f, 14.5f, 12f)
        curveTo(14.5f, 10.2f, 13.1f, 8.8f, 11.3f, 8.8f)
        close()
        moveTo(11.3f, 11.2f)
        curveTo(10.9f, 11.2f, 10.5f, 11.6f, 10.5f, 12f)
        curveTo(10.5f, 12.4f, 10.9f, 12.8f, 11.3f, 12.8f)
        curveTo(11.7f, 12.8f, 12.1f, 12.4f, 12.1f, 12f)
        curveTo(12.1f, 11.6f, 11.7f, 11.2f, 11.3f, 11.2f)
        close()
        moveTo(14.7f, 8.3f)
        lineTo(17.4f, 8.3f)
        moveTo(15.3f, 10.5f)
        lineTo(18.2f, 10.5f)
        moveTo(15.3f, 13.5f)
        lineTo(18.2f, 13.5f)
        moveTo(14.7f, 15.7f)
        lineTo(17.4f, 15.7f)
        moveTo(17.3f, 9.3f)
        lineTo(21f, 12f)
        lineTo(17.3f, 14.7f)
        close()
    }
    val Settings = icon("Settings") {
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
    val ChevronRight = icon("ChevronRight") {
        moveTo(9f, 6f)
        lineTo(15f, 12f)
        lineTo(9f, 18f)
    }
    val ChevronDown = icon("ChevronDown") {
        moveTo(6f, 9f)
        lineTo(12f, 15f)
        lineTo(18f, 9f)
    }
    val Back = icon("Back") {
        moveTo(15f, 5f)
        lineTo(8f, 12f)
        lineTo(15f, 19f)
    }
    val Trash = icon("Trash") {
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
    val Edit = icon("Edit") {
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
    val Alphabetical = icon("Alphabetical") {
        moveTo(5f, 18f)
        lineTo(8f, 6f)
        lineTo(11f, 18f)
        moveTo(6.1f, 14f)
        lineTo(9.9f, 14f)
        moveTo(14f, 7f)
        lineTo(19f, 7f)
        lineTo(14f, 17f)
        lineTo(19f, 17f)
    }
    val Clock = icon("Clock") {
        moveTo(12f, 4f)
        curveTo(7.6f, 4f, 4f, 7.6f, 4f, 12f)
        curveTo(4f, 16.4f, 7.6f, 20f, 12f, 20f)
        curveTo(16.4f, 20f, 20f, 16.4f, 20f, 12f)
        curveTo(20f, 7.6f, 16.4f, 4f, 12f, 4f)
        close()
        moveTo(12f, 8f)
        lineTo(12f, 12.4f)
        lineTo(15f, 14.2f)
    }
    val Info = icon("Info") {
        moveTo(12f, 10.5f)
        lineTo(12f, 17f)
        moveTo(12f, 7f)
        lineTo(12.02f, 7f)
        moveTo(12f, 3.5f)
        curveTo(7.3f, 3.5f, 3.5f, 7.3f, 3.5f, 12f)
        curveTo(3.5f, 16.7f, 7.3f, 20.5f, 12f, 20.5f)
        curveTo(16.7f, 20.5f, 20.5f, 16.7f, 20.5f, 12f)
        curveTo(20.5f, 7.3f, 16.7f, 3.5f, 12f, 3.5f)
        close()
    }
    val Album = icon("Album") {
        moveTo(12f, 4f)
        curveTo(7.6f, 4f, 4f, 7.6f, 4f, 12f)
        curveTo(4f, 16.4f, 7.6f, 20f, 12f, 20f)
        curveTo(16.4f, 20f, 20f, 16.4f, 20f, 12f)
        curveTo(20f, 7.6f, 16.4f, 4f, 12f, 4f)
        close()
        moveTo(12f, 9.2f)
        curveTo(10.5f, 9.2f, 9.2f, 10.5f, 9.2f, 12f)
        curveTo(9.2f, 13.5f, 10.5f, 14.8f, 12f, 14.8f)
        curveTo(13.5f, 14.8f, 14.8f, 13.5f, 14.8f, 12f)
        curveTo(14.8f, 10.5f, 13.5f, 9.2f, 12f, 9.2f)
        close()
    }
    val Artist = icon("Artist") {
        moveTo(12f, 5f)
        curveTo(10f, 5f, 8.4f, 6.6f, 8.4f, 8.6f)
        curveTo(8.4f, 10.6f, 10f, 12.2f, 12f, 12.2f)
        curveTo(14f, 12.2f, 15.6f, 10.6f, 15.6f, 8.6f)
        curveTo(15.6f, 6.6f, 14f, 5f, 12f, 5f)
        close()
        moveTo(5.5f, 20f)
        curveTo(6.4f, 16.8f, 8.8f, 15f, 12f, 15f)
        curveTo(15.2f, 15f, 17.6f, 16.8f, 18.5f, 20f)
    }
    val ExternalLink = icon("ExternalLink") {
        moveTo(9f, 7f)
        lineTo(6.5f, 7f)
        curveTo(5.7f, 7f, 5f, 7.7f, 5f, 8.5f)
        lineTo(5f, 17.5f)
        curveTo(5f, 18.3f, 5.7f, 19f, 6.5f, 19f)
        lineTo(15.5f, 19f)
        curveTo(16.3f, 19f, 17f, 18.3f, 17f, 17.5f)
        lineTo(17f, 15f)
        moveTo(13f, 5f)
        lineTo(19f, 5f)
        lineTo(19f, 11f)
        moveTo(11f, 13f)
        lineTo(18.5f, 5.5f)
    }
    val Fire = filledIcon("Fire") {
        moveTo(12f, 22f)
        curveTo(7.5f, 22f, 4f, 18.8f, 4f, 14.6f)
        curveTo(4f, 11.6f, 5.6f, 9.2f, 8.3f, 7.1f)
        curveTo(8.6f, 9.1f, 9.5f, 10.4f, 10.7f, 11f)
        curveTo(10.2f, 7.5f, 11.8f, 4.2f, 15.5f, 2f)
        curveTo(15.8f, 5.4f, 17.6f, 7.3f, 19.2f, 9.5f)
        curveTo(20.3f, 11f, 20.8f, 12.7f, 20.8f, 14.6f)
        curveTo(20.8f, 18.8f, 17.1f, 22f, 12f, 22f)
        close()
        moveTo(12.2f, 19.6f)
        curveTo(14.3f, 19.6f, 15.9f, 18.2f, 15.9f, 16.3f)
        curveTo(15.9f, 14.8f, 15f, 13.7f, 13.7f, 12.3f)
        curveTo(13.5f, 13.6f, 12.8f, 14.6f, 11.8f, 15.2f)
        curveTo(11.7f, 14f, 11.1f, 13f, 10.2f, 12.4f)
        curveTo(8.9f, 13.5f, 8.2f, 14.8f, 8.2f, 16.3f)
        curveTo(8.2f, 18.2f, 9.9f, 19.6f, 12.2f, 19.6f)
        close()
    }

    private fun icon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
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
                block()
            }
        }.build()

    private fun filledIcon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = IconSize,
            defaultHeight = IconSize,
            viewportWidth = Viewport,
            viewportHeight = Viewport,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                block()
            }
        }.build()

    private fun thinIcon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = IconSize,
            defaultHeight = IconSize,
            viewportWidth = Viewport,
            viewportHeight = Viewport,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.45f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                block()
            }
        }.build()
}
