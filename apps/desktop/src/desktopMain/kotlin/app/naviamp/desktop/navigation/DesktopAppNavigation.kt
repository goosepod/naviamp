package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.ui.SharedBottomNavigationBar
import app.naviamp.ui.SharedRoute

enum class DesktopAppRoute {
    Player,
    Home,
    Playlists,
    PlaylistDetail,
    AlbumDetail,
    ArtistDetail,
    Library,
    Search,
    ArtistMix,
    InternetRadio,
    Downloads,
    Settings,
    ;

    companion object {
        fun fromStoredName(name: String?): DesktopAppRoute =
            entries.firstOrNull { it.name == name } ?: Home
    }
}

data class DesktopBottomNavigationItem(
    val route: DesktopAppRoute,
    val label: String,
    val icon: ImageVector,
)

val DesktopBottomNavigationItems = listOf(
    DesktopBottomNavigationItem(DesktopAppRoute.Home, "Home", DesktopNavigationIcons.Home),
    DesktopBottomNavigationItem(DesktopAppRoute.Playlists, "Playlists", DesktopNavigationIcons.Playlist),
    DesktopBottomNavigationItem(DesktopAppRoute.Library, "Library", DesktopNavigationIcons.Library),
    DesktopBottomNavigationItem(DesktopAppRoute.Search, "Search", DesktopNavigationIcons.Search),
    DesktopBottomNavigationItem(DesktopAppRoute.InternetRadio, "Radio", DesktopNavigationIcons.InternetRadio),
    DesktopBottomNavigationItem(DesktopAppRoute.Downloads, "Downloads", DesktopNavigationIcons.Downloads),
    DesktopBottomNavigationItem(DesktopAppRoute.Settings, "Settings", DesktopNavigationIcons.Settings),
)

@Composable
fun DesktopBottomNavigationBar(
    appColors: DesktopAppColors,
    selectedRoute: DesktopAppRoute,
    onRouteSelected: (DesktopAppRoute) -> Unit,
) {
    SharedBottomNavigationBar(
        colors = appColors,
        selectedRoute = selectedRoute.toSharedRoute(),
        onRouteSelected = { route -> onRouteSelected(route.toAppRoute()) },
    )
}

private fun DesktopAppRoute.toSharedRoute(): SharedRoute =
    when (this) {
        DesktopAppRoute.Player -> SharedRoute.Search
        DesktopAppRoute.Home -> SharedRoute.Home
        DesktopAppRoute.Playlists,
        DesktopAppRoute.PlaylistDetail,
        -> SharedRoute.Playlists
        DesktopAppRoute.Library -> SharedRoute.Library
        DesktopAppRoute.Search,
        DesktopAppRoute.AlbumDetail,
        DesktopAppRoute.ArtistDetail,
        -> SharedRoute.Search
        DesktopAppRoute.ArtistMix -> SharedRoute.ArtistMix
        DesktopAppRoute.InternetRadio -> SharedRoute.Radio
        DesktopAppRoute.Downloads -> SharedRoute.Downloads
        DesktopAppRoute.Settings -> SharedRoute.Settings
    }

private fun SharedRoute.toAppRoute(): DesktopAppRoute =
    when (this) {
        SharedRoute.Home -> DesktopAppRoute.Home
        SharedRoute.Playlists -> DesktopAppRoute.Playlists
        SharedRoute.Library -> DesktopAppRoute.Library
        SharedRoute.Search -> DesktopAppRoute.Search
        SharedRoute.ArtistMix -> DesktopAppRoute.ArtistMix
        SharedRoute.Radio -> DesktopAppRoute.InternetRadio
        SharedRoute.Downloads -> DesktopAppRoute.Downloads
        SharedRoute.Settings -> DesktopAppRoute.Settings
    }

fun DesktopAppRoute.toNaviampRoute(): NaviampRoute =
    when (this) {
        DesktopAppRoute.Player -> NaviampRoute.Player
        DesktopAppRoute.Home -> NaviampRoute.Home
        DesktopAppRoute.Playlists -> NaviampRoute.Playlists
        DesktopAppRoute.PlaylistDetail -> NaviampRoute.PlaylistDetail
        DesktopAppRoute.AlbumDetail -> NaviampRoute.AlbumDetail
        DesktopAppRoute.ArtistDetail -> NaviampRoute.ArtistDetail
        DesktopAppRoute.Library -> NaviampRoute.Library
        DesktopAppRoute.Search -> NaviampRoute.Search
        DesktopAppRoute.ArtistMix -> NaviampRoute.ArtistMix
        DesktopAppRoute.InternetRadio -> NaviampRoute.Radio
        DesktopAppRoute.Downloads -> NaviampRoute.Downloads
        DesktopAppRoute.Settings -> NaviampRoute.Settings
    }

@Composable
fun DesktopPlaceholderRoutePanel(
    appColors: DesktopAppColors,
    title: String,
    message: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Text(
            title,
            color = appColors.primaryText,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        Text(
            message,
            color = appColors.secondaryText,
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
