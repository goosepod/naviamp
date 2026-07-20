package app.naviamp.android.playback

import android.util.Log
import app.naviamp.android.AndroidSettingsStore
import app.naviamp.android.AndroidStorageDependencies
import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.CatalogPlaybackIntent
import app.naviamp.domain.radio.RadioService
import app.naviamp.domain.radio.withRadioCoverArtIds
import app.naviamp.domain.settings.RecentRadioKind
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.toNavidromeConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface AndroidAutoCatalogSelectionHost {
    val storage: AndroidStorageDependencies
    val settings: AndroidSettingsStore
    fun resume()
    fun playQueueItem(index: Int)
    fun launch(block: suspend () -> Unit)
    fun playQueue(tracks: List<Track>, index: Int = 0)
    fun fallbackQueue(track: Track): List<Track>
    suspend fun loadPlaylist(provider: NavidromeProvider, id: String): List<Track>
    suspend fun loadArtist(provider: NavidromeProvider, id: String, name: String?): List<Track>
    suspend fun loadAlbum(provider: NavidromeProvider, id: String, title: String?, artist: String?): List<Track>
    fun playStation(station: InternetRadioStation)
    fun playRecent(stream: RecentRadioStream)
    fun rememberRecent(stream: RecentRadioStream)
    fun failed(message: String, error: Throwable? = null)
}

/** Executes portable catalog intents using an Android service's native playback host. */
internal class AndroidAutoCatalogSelectionController(private val host: AndroidAutoCatalogSelectionHost) {
    fun playMediaId(mediaId: String): Boolean = AndroidAutoMediaIdParser.parse(mediaId)?.let(::play) ?: false

    fun play(selection: CatalogPlaybackIntent): Boolean {
        val storage = host.storage
        val source = storage.latestNavidromeSource() ?: return false
        val provider = NavidromeProvider(source.toNavidromeConnection())
        return when (selection) {
            CatalogPlaybackIntent.Resume -> handled { host.resume() }
            is CatalogPlaybackIntent.QueueItem -> handled { host.playQueueItem(selection.index) }
            CatalogPlaybackIntent.LibraryRadio -> {
                val recent = RecentRadioStream(AndroidAutoPlaybackControls.MediaIdRadioLibrary, "Library Radio", RecentRadioKind.Library)
                host.launch {
                    val radio = RadioService(provider = provider, tuning = host.settings.loadPlaybackSettings().radioTuning)
                    runCatching { withContext(Dispatchers.IO) { radio.libraryRadio() } }
                        .onSuccess { host.rememberRecent(recent.withRadioCoverArtIds(it)); host.playQueue(it) }
                        .onFailure { host.failed("Could not start Auto Library Radio", it) }
                }
                true
            }
            is CatalogPlaybackIntent.RadioDj -> {
                val dj = storage.radioDjPresets().firstOrNull { it.id == selection.id } ?: return false
                val recent = RecentRadioStream("dj:${dj.id}", dj.name, RecentRadioKind.Library)
                host.launch {
                    runCatching { withContext(Dispatchers.IO) { RadioService(provider = provider, tuning = dj.tuning).libraryRadio() } }
                        .onSuccess { host.rememberRecent(recent.withRadioCoverArtIds(it)); host.playQueue(it) }
                        .onFailure { host.failed("Could not start Auto DJ=${dj.name}", it) }
                }
                true
            }
            is CatalogPlaybackIntent.Playlist -> launchTracks("playlist=${selection.id}") {
                host.loadPlaylist(provider, selection.id).let { if (selection.shuffle) it.shuffled() else it }
            }
            is CatalogPlaybackIntent.PlaylistTrack -> launchSelection("playlist track=${selection.trackId}") {
                val tracks = host.loadPlaylist(provider, selection.playlistId)
                tracks.indexOfFirst { it.id.value == selection.trackId }.takeIf { it >= 0 }?.let { tracks to it }
            }
            is CatalogPlaybackIntent.InternetRadio -> handled {
                host.playStation(InternetRadioStation(selection.id, selection.name, selection.streamUrl, selection.homePageUrl))
            }
            is CatalogPlaybackIntent.RecentRadio -> {
                host.settings.loadRecentRadioStreams().firstOrNull { it.id == selection.id }?.let { host.playRecent(it); return true }
                host.settings.loadRecentInternetRadioStations().firstOrNull { it.id == selection.id }?.toStation()?.let { host.playStation(it); return true }
                false
            }
            is CatalogPlaybackIntent.Track -> {
                val title = selection.title
                val track = if (title == null) storage.libraryTrack(source.id, TrackId(selection.id)) else Track(
                    id = TrackId(selection.id), title = title,
                    artistId = selection.artistId?.let(::ArtistId), artistName = selection.artistName.orEmpty(),
                    albumId = selection.albumId?.let(::AlbumId), albumTitle = selection.albumTitle,
                    durationSeconds = selection.durationSeconds, coverArtId = selection.coverArtId,
                    audioInfo = null, replayGain = null,
                )
                track?.let { host.playQueue(listOf(it)); true } ?: false
            }
            is CatalogPlaybackIntent.ArtistTrack -> launchTrackSelection("artist track=${selection.trackId}", selection.trackId) {
                host.loadArtist(provider, selection.artistId, selection.artistName)
            }
            is CatalogPlaybackIntent.AlbumTrack -> launchTrackSelection("album track=${selection.trackId}", selection.trackId) {
                host.loadAlbum(provider, selection.albumId, null, null)
            }
            is CatalogPlaybackIntent.Artist -> launchTracks("artist=${selection.id}") {
                host.loadArtist(provider, selection.id, selection.name).let { if (selection.shuffle) it.shuffled() else it }
            }
            is CatalogPlaybackIntent.Album -> launchTracks("album=${selection.id}") {
                host.loadAlbum(provider, selection.id, selection.title, selection.artist).let { if (selection.shuffle) it.shuffled() else it }
            }
            is CatalogPlaybackIntent.Download -> {
                val tracks = storage.downloadedTracks(source.id).filter { it.file.exists() }.map { it.track }
                val index = tracks.indexOfFirst { it.id.value == selection.trackId }
                if (index < 0) false else handled { host.playQueue(tracks, index) }
            }
        }
    }

    private fun launchTracks(label: String, load: suspend () -> List<Track>): Boolean {
        host.launch { runCatching { load() }.onSuccess(host::playQueue).onFailure { host.failed("Could not play Auto $label", it) } }
        return true
    }

    private fun launchSelection(label: String, load: suspend () -> Pair<List<Track>, Int>?): Boolean {
        host.launch {
            runCatching { load() }.onSuccess { it?.let { (tracks, index) -> host.playQueue(tracks, index) } ?: host.failed("No Auto $label match") }
                .onFailure { host.failed("Could not start Auto $label", it) }
        }
        return true
    }

    private fun launchTrackSelection(label: String, trackId: String, load: suspend () -> List<Track>): Boolean = launchSelection(label) {
        val tracks = load()
        val track = tracks.firstOrNull { it.id.value == trackId }
            ?: host.storage.latestNavidromeSource()?.id?.let { host.storage.libraryTrack(it, TrackId(trackId)) }
        track?.let {
            val queue = tracks.takeIf { candidates -> candidates.any { candidate -> candidate.id == it.id } } ?: host.fallbackQueue(it)
            queue to queue.indexOfFirst { candidate -> candidate.id == it.id }.coerceAtLeast(0)
        }
    }

    private inline fun handled(block: () -> Unit): Boolean { block(); return true }
}
