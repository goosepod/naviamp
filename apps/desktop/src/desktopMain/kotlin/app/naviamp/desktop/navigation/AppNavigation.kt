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

enum class AppRoute {
    Player,
    Home,
    Playlists,
    PlaylistDetail,
    AlbumDetail,
    ArtistDetail,
    Library,
    Search,
    InternetRadio,
    Downloads,
    Settings,
    ;

    companion object {
        fun fromStoredName(name: String?): AppRoute =
            entries.firstOrNull { it.name == name } ?: Home
    }
}

data class BottomNavigationItem(
    val route: AppRoute,
    val label: String,
    val icon: ImageVector,
)

val BottomNavigationItems = listOf(
    BottomNavigationItem(AppRoute.Home, "Home", NavigationIcons.Home),
    BottomNavigationItem(AppRoute.Playlists, "Playlists", NavigationIcons.Playlist),
    BottomNavigationItem(AppRoute.Library, "Library", NavigationIcons.Library),
    BottomNavigationItem(AppRoute.Search, "Search", NavigationIcons.Search),
    BottomNavigationItem(AppRoute.InternetRadio, "Radio", NavigationIcons.InternetRadio),
    BottomNavigationItem(AppRoute.Downloads, "Downloads", NavigationIcons.Downloads),
    BottomNavigationItem(AppRoute.Settings, "Settings", NavigationIcons.Settings),
)

@Composable
fun BottomNavigationBar(
    appColors: AppColors,
    selectedRoute: AppRoute,
    onRouteSelected: (AppRoute) -> Unit,
) {
    SharedBottomNavigationBar(
        colors = appColors,
        selectedRoute = selectedRoute.toSharedRoute(),
        onRouteSelected = { route -> onRouteSelected(route.toAppRoute()) },
    )
}

private fun AppRoute.toSharedRoute(): SharedRoute =
    when (this) {
        AppRoute.Player -> SharedRoute.Search
        AppRoute.Home -> SharedRoute.Home
        AppRoute.Playlists,
        AppRoute.PlaylistDetail,
        -> SharedRoute.Playlists
        AppRoute.Library -> SharedRoute.Library
        AppRoute.Search,
        AppRoute.AlbumDetail,
        AppRoute.ArtistDetail,
        -> SharedRoute.Search
        AppRoute.InternetRadio -> SharedRoute.Radio
        AppRoute.Downloads -> SharedRoute.Downloads
        AppRoute.Settings -> SharedRoute.Settings
    }

private fun SharedRoute.toAppRoute(): AppRoute =
    when (this) {
        SharedRoute.Home -> AppRoute.Home
        SharedRoute.Playlists -> AppRoute.Playlists
        SharedRoute.Library -> AppRoute.Library
        SharedRoute.Search -> AppRoute.Search
        SharedRoute.Radio -> AppRoute.InternetRadio
        SharedRoute.Downloads -> AppRoute.Downloads
        SharedRoute.Settings -> AppRoute.Settings
    }

fun AppRoute.toNaviampRoute(): NaviampRoute =
    when (this) {
        AppRoute.Player -> NaviampRoute.Player
        AppRoute.Home -> NaviampRoute.Home
        AppRoute.Playlists -> NaviampRoute.Playlists
        AppRoute.PlaylistDetail -> NaviampRoute.PlaylistDetail
        AppRoute.AlbumDetail -> NaviampRoute.AlbumDetail
        AppRoute.ArtistDetail -> NaviampRoute.ArtistDetail
        AppRoute.Library -> NaviampRoute.Library
        AppRoute.Search -> NaviampRoute.Search
        AppRoute.InternetRadio -> NaviampRoute.Radio
        AppRoute.Downloads -> NaviampRoute.Downloads
        AppRoute.Settings -> NaviampRoute.Settings
    }

@Composable
fun PlaceholderRoutePanel(
    appColors: AppColors,
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
