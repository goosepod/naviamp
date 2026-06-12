package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NaviampRadioArtworkLookupEffect(
    station: InternetRadioStation?,
    streamMetadata: PlaybackStreamMetadata,
    provider: MediaProvider?,
    artworkByKey: Map<String, String?>,
    onArtworkResolved: (String, String?) -> Unit,
) {
    LaunchedEffect(station?.id, streamMetadata.title, streamMetadata.properties, provider) {
        val activeStation = station ?: return@LaunchedEffect
        if (!radioArtworkNeedsTrackLookup(activeStation, streamMetadata.title, streamMetadata.properties)) {
            return@LaunchedEffect
        }
        val key = radioTrackArtworkKey(activeStation, streamMetadata.title) ?: return@LaunchedEffect
        if (artworkByKey.containsKey(key)) return@LaunchedEffect
        val activeProvider = provider ?: return@LaunchedEffect
        val query = radioTrackArtworkQuery(streamMetadata.title) ?: return@LaunchedEffect
        val artworkUrl = withContext(Dispatchers.IO) {
            runCatching {
                activeProvider
                    .search(query, limit = 5)
                    .tracks
                    .firstOrNull { it.coverArtId != null }
                    ?.coverArtId
                    ?.let(activeProvider::coverArtUrl)
            }.getOrNull()
        }
        onArtworkResolved(key, artworkUrl)
    }
}
