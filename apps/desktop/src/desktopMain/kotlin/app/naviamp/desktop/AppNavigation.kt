package app.naviamp.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppRoute {
    Player,
    Home,
    Playlists,
    PlaylistDetail,
    AlbumDetail,
    ArtistDetail,
    Library,
    Search,
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
    BottomNavigationItem(AppRoute.Downloads, "Downloads", NavigationIcons.Downloads),
    BottomNavigationItem(AppRoute.Settings, "Settings", NavigationIcons.Settings),
)

@Composable
fun BottomNavigationBar(
    appColors: AppColors,
    selectedRoute: AppRoute,
    onRouteSelected: (AppRoute) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(vertical = 0.dp),
    ) {
        BottomNavigationItems.forEach { item ->
            val selected = item.route == selectedRoute
            IconButton(
                onClick = { onRouteSelected(item.route) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (selected) appColors.primaryText else appColors.mutedText,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
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
