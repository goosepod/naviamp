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


data class DesktopBottomNavigationItem(
    val route: NaviampRoute,
    val label: String,
    val icon: ImageVector,
)

val DesktopBottomNavigationItems = listOf(
    DesktopBottomNavigationItem(NaviampRoute.Home, "Home", DesktopNavigationIcons.Home),
    DesktopBottomNavigationItem(NaviampRoute.Playlists, "Playlists", DesktopNavigationIcons.Playlist),
    DesktopBottomNavigationItem(NaviampRoute.Library, "Library", DesktopNavigationIcons.Library),
    DesktopBottomNavigationItem(NaviampRoute.Search, "Search", DesktopNavigationIcons.Search),
    DesktopBottomNavigationItem(NaviampRoute.Radio, "Radio", DesktopNavigationIcons.InternetRadio),
    DesktopBottomNavigationItem(NaviampRoute.Downloads, "Downloads", DesktopNavigationIcons.Downloads),
    DesktopBottomNavigationItem(NaviampRoute.Settings, "Settings", DesktopNavigationIcons.Settings),
)

@Composable
fun DesktopBottomNavigationBar(
    appColors: DesktopAppColors,
    selectedRoute: NaviampRoute,
    supportsDownloads: Boolean = false,
    onRouteSelected: (NaviampRoute) -> Unit,
) {
    SharedBottomNavigationBar(
        colors = appColors,
        selectedRoute = selectedRoute.toSharedRoute(),
        supportsDownloads = supportsDownloads,
        onRouteSelected = { route -> onRouteSelected(route.toAppRoute()) },
    )
}

internal fun NaviampRoute.toSharedRoute(): SharedRoute =
    when (this) {
        NaviampRoute.Player -> SharedRoute.Search
        NaviampRoute.Home -> SharedRoute.Home
        NaviampRoute.Playlists,
        NaviampRoute.PlaylistDetail,
        -> SharedRoute.Playlists
        NaviampRoute.Library -> SharedRoute.Library
        NaviampRoute.Search,
        NaviampRoute.AlbumDetail,
        NaviampRoute.ArtistDetail,
        -> SharedRoute.Search
        NaviampRoute.ArtistMix -> SharedRoute.ArtistMix
        NaviampRoute.AlbumMix -> SharedRoute.AlbumMix
        NaviampRoute.GenreMix -> SharedRoute.GenreMix
        NaviampRoute.SonicPath -> SharedRoute.SonicPath
        NaviampRoute.SonicMix -> SharedRoute.SonicMix
        NaviampRoute.Radio -> SharedRoute.Radio
        NaviampRoute.Downloads -> SharedRoute.Downloads
        NaviampRoute.Settings -> SharedRoute.Settings
    }

internal fun SharedRoute.toAppRoute(): NaviampRoute =
    when (this) {
        SharedRoute.Home -> NaviampRoute.Home
        SharedRoute.Playlists -> NaviampRoute.Playlists
        SharedRoute.Library -> NaviampRoute.Library
        SharedRoute.Search -> NaviampRoute.Search
        SharedRoute.ArtistMix -> NaviampRoute.ArtistMix
        SharedRoute.AlbumMix -> NaviampRoute.AlbumMix
        SharedRoute.GenreMix -> NaviampRoute.GenreMix
        SharedRoute.SonicPath -> NaviampRoute.SonicPath
        SharedRoute.SonicMix -> NaviampRoute.SonicMix
        SharedRoute.Radio -> NaviampRoute.Radio
        SharedRoute.Downloads -> NaviampRoute.Downloads
        SharedRoute.Settings -> NaviampRoute.Settings
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
