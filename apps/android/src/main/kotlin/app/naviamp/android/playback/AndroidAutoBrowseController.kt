package app.naviamp.android.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat
import app.naviamp.android.AndroidPlaybackHistoryItem
import app.naviamp.android.AndroidSettingsStore
import app.naviamp.android.AndroidStorageDependencies
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.PlaybackHistoryRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.toNavidromeConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AndroidAutoBrowseController(
    private val context: Context,
    private val storage: () -> AndroidStorageDependencies,
    private val currentQueue: () -> List<Track>,
    private val currentMetadata: () -> AndroidPlaybackNotificationMetadata,
    private val restoredNowPlayingMetadata: () -> AndroidPlaybackNotificationMetadata?,
    private val providerResponseService: (ProviderResponseCacheRepository) -> ProviderResponseService,
    private val loadArtistTracks: suspend (
        LocalLibraryIndexRepository,
        ProviderResponseCacheRepository,
        String,
        NavidromeProvider,
        String,
        String?,
    ) -> List<Track>,
    private val loadAlbumTracks: suspend (
        LocalLibraryIndexRepository,
        ProviderResponseCacheRepository,
        String,
        NavidromeProvider,
        String,
        String?,
        String?,
    ) -> List<Track>,
) {
    fun loadChildren(
        parentId: String,
        result: MediaBrowserServiceCompat.Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        Log.i("NaviampAutoCommand", "Loading Auto children parent=$parentId")
        val storage = storage()
        val sourceId = storage.latestNavidromeSource()?.id
        if (parentId == AndroidAutoPlaybackControls.MediaIdRadioStations) {
            loadAsyncChildren(parentId, result) {
                val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                val provider = NavidromeProvider(source.toNavidromeConnection())
                withContext(Dispatchers.IO) {
                    providerResponseService(storage).internetRadioStations(provider)
                }
                    .take(AndroidAutoBrowseLimit)
                    .map { station ->
                        playableItem(
                            mediaId = AndroidAutoPlaybackControls.MediaIdRadioStationPrefix + listOf(
                                Uri.encode(station.id),
                                Uri.encode(station.name),
                                Uri.encode(station.streamUrl),
                                Uri.encode(station.homePageUrl.orEmpty()),
                            ).joinToString(MediaIdPartSeparator),
                            title = station.name,
                            subtitle = station.homePageUrl ?: "Internet radio",
                        )
                    }
                    .toMutableList()
            }
            return
        }
        if (parentId == AndroidAutoPlaybackControls.MediaIdRadioRecent) {
            val settingsStore = AndroidSettingsStore(context)
            val recentStreams = settingsStore.loadRecentRadioStreams().map { stream ->
                playableItem(
                    mediaId = "${AndroidAutoPlaybackControls.MediaIdRecentRadioPrefix}${Uri.encode(stream.id)}",
                    title = stream.label,
                    subtitle = "Radio",
                )
            }
            val recentStations = settingsStore.loadRecentInternetRadioStations().map { station ->
                playableItem(
                    mediaId = "${AndroidAutoPlaybackControls.MediaIdRecentRadioPrefix}${Uri.encode(station.id)}",
                    title = station.name,
                    subtitle = station.homePageUrl ?: "Internet radio",
                )
            }
            sendChildren(parentId, (recentStreams + recentStations).toMutableList(), result)
            return
        }
        if (parentId == AndroidAutoPlaybackControls.MediaIdQueue) {
            val children = currentQueue().mapIndexed { index, track ->
                trackItem(
                    track = track,
                    mediaId = "${AndroidAutoPlaybackControls.MediaIdQueueTrackPrefix}${Uri.encode(index.toString())}",
                )
            }.toMutableList()
            sendChildren(parentId, children, result)
            return
        }
        if (parentId == AndroidAutoPlaybackControls.MediaIdPlaylists) {
            loadAsyncChildren(parentId, result) {
                val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                val provider = NavidromeProvider(source.toNavidromeConnection())
                withContext(Dispatchers.IO) {
                    providerResponseService(storage).playlists(provider, AndroidAutoBrowseLimit)
                }
                    .map { playlist ->
                        browsableItem(
                            mediaId = "${AndroidAutoPlaybackControls.MediaIdPlaylistPrefix}${Uri.encode(playlist.id)}",
                            title = playlist.name,
                            subtitle = "${playlist.trackCount} tracks",
                        )
                    }
                    .toMutableList()
            }
            return
        }
        if (parentId == AndroidAutoPlaybackControls.MediaIdHomeRecentlyAdded) {
            loadAsyncChildren(parentId, result) {
                val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                val provider = NavidromeProvider(source.toNavidromeConnection())
                withContext(Dispatchers.IO) {
                    providerResponseService(storage).albumList(provider, AlbumListType.Newest, AndroidAutoBrowseLimit)
                }
                    .map(::albumItem)
                    .toMutableList()
            }
            return
        }
        if (parentId == AndroidAutoPlaybackControls.MediaIdChartsAlbums) {
            loadAsyncChildren(parentId, result) {
                val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                val provider = NavidromeProvider(source.toNavidromeConnection())
                withContext(Dispatchers.IO) {
                    providerResponseService(storage).albumList(provider, AlbumListType.Frequent, AndroidAutoBrowseLimit)
                }
                    .map(::albumItem)
                    .toMutableList()
            }
            return
        }
        val children = when (parentId) {
            AndroidAutoPlaybackControls.MediaIdRoot -> mutableListOf(
                browsableItem(AndroidAutoPlaybackControls.MediaIdHome, "Home", "Mixes and recent music", iconName = "ic_auto_home"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdLibrary, "Library", "Browse your collection", iconName = "ic_auto_library"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdDownloads, "Downloads", "Offline tracks"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdPlaylists, "Playlists", "Saved Navidrome playlists"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadio, "Radio", "Library and internet radio", iconName = "ic_auto_radio"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdCharts, "Charts", "Top artists, albums, and tracks", iconName = "ic_auto_charts"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdMore, "More", "Queue and shortcuts"),
            )
            AndroidAutoPlaybackControls.MediaIdHome -> mutableListOf(
                browsableItem(AndroidAutoPlaybackControls.MediaIdHomeMixes, "Mixes For You", "Radio based on your library"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdHomeRecentPlays, "Recent Plays", "Recently played tracks and radio"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdHomeRecentlyAdded, "Recently Added in Music", "Newest albums"),
            )
            AndroidAutoPlaybackControls.MediaIdLibrary -> mutableListOf(
                browsableItem(AndroidAutoPlaybackControls.MediaIdLibraryArtists, "Artists A-Z", "Browse indexed artists"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdLibraryAlbums, "Albums", "Browse indexed albums"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdLibraryTracks, "Tracks", "Browse indexed tracks"),
            )
            AndroidAutoPlaybackControls.MediaIdLibraryArtists -> {
                sourceId?.let { id ->
                    storage.librarySnapshot(id, VoiceArtistScanLimit, 0)
                        .artists
                        .groupBy { it.name.autoArtistGroupKey() }
                        .toSortedMap(compareBy<String> { if (it == "#") "0" else it })
                        .map { (group, artists) ->
                            browsableItem(
                                mediaId = "${AndroidAutoPlaybackControls.MediaIdArtistGroupPrefix}${Uri.encode(group)}",
                                title = group,
                                subtitle = "${artists.size} artists",
                            )
                        }
                        .toMutableList()
                } ?: noSourceItems()
            }
            AndroidAutoPlaybackControls.MediaIdLibraryAlbums -> {
                sourceId?.let { id ->
                    storage.librarySnapshot(id, AndroidAutoBrowseLimit.toLong(), 0).albums.map(::albumItem).toMutableList()
                } ?: noSourceItems()
            }
            AndroidAutoPlaybackControls.MediaIdLibraryTracks -> {
                sourceId?.let { id ->
                    storage.librarySnapshot(id, AndroidAutoBrowseLimit.toLong(), 0).tracks.map(::trackItem).toMutableList()
                } ?: noSourceItems()
            }
            AndroidAutoPlaybackControls.MediaIdRadio -> mutableListOf(
                playableItem(AndroidAutoPlaybackControls.MediaIdRadioLibrary, "Library Radio", "Random tracks from your library"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadioRecent, "Recently Played Radio", "Radio you started from Naviamp"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadioStations, "Internet Radio", "Saved Navidrome stations"),
            )
            AndroidAutoPlaybackControls.MediaIdHomeMixes -> mutableListOf(
                playableItem(AndroidAutoPlaybackControls.MediaIdRadioLibrary, "Library Radio", "Random tracks from your library"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadioRecent, "Recently Played Radio", "Radio you started from Naviamp"),
            )
            AndroidAutoPlaybackControls.MediaIdHomeRecentPlays -> {
                sourceId?.let { id ->
                    val settingsStore = AndroidSettingsStore(context)
                    val recentStreams = settingsStore.loadRecentRadioStreams().map { stream ->
                        playableItem(
                            mediaId = "${AndroidAutoPlaybackControls.MediaIdRecentRadioPrefix}${Uri.encode(stream.id)}",
                            title = stream.label,
                            subtitle = "Radio",
                        )
                    }
                    val recentTracks = recentPlaybackHistoryItems(storage, id)
                    (recentStreams + recentTracks).take(AndroidAutoBrowseLimit).toMutableList()
                } ?: noSourceItems()
            }
            AndroidAutoPlaybackControls.MediaIdCharts -> mutableListOf(
                browsableItem(AndroidAutoPlaybackControls.MediaIdChartsArtists, "Top Artists", "Library favorites"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdChartsAlbums, "Top Albums", "Frequently played albums"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdChartsTracks, "Top Tracks", "Recently played tracks"),
            )
            AndroidAutoPlaybackControls.MediaIdChartsArtists -> {
                sourceId?.let { id ->
                    storage.librarySnapshot(id, AndroidAutoBrowseLimit.toLong(), 0).artists.map(::artistItem).toMutableList()
                } ?: noSourceItems()
            }
            AndroidAutoPlaybackControls.MediaIdChartsTracks -> {
                sourceId?.let { id ->
                    recentPlaybackHistoryItems(storage, id).toMutableList()
                } ?: noSourceItems()
            }
            AndroidAutoPlaybackControls.MediaIdDownloads -> {
                sourceId?.let { id ->
                    storage.downloadedTracks(id)
                        .filter { it.file.exists() }
                        .take(AndroidAutoBrowseLimit)
                        .map { download ->
                            trackItem(
                                track = download.track,
                                mediaId = "${AndroidAutoPlaybackControls.MediaIdDownloadPrefix}${Uri.encode(download.track.id.value)}",
                            )
                        }
                        .toMutableList()
                } ?: noSourceItems()
            }
            AndroidAutoPlaybackControls.MediaIdMore -> mutableListOf(
                currentNowPlayingItem(),
                browsableItem(AndroidAutoPlaybackControls.MediaIdQueue, "Current queue", "${currentQueue().size} tracks"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdPlaylists, "Playlists", "Saved Navidrome playlists"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadioStations, "Internet Radio", "Saved Navidrome stations"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdLibraryTracks, "All Tracks", "Browse indexed tracks"),
            )
            else -> dynamicChildren(parentId, storage, sourceId, result) ?: mutableListOf()
        }
        Log.i("NaviampAutoCommand", "Loaded Auto children parent=$parentId count=${children.size}")
        sendChildren(parentId, children, result)
    }

    fun loadChildren(
        parentId: String,
        result: MediaBrowserServiceCompat.Result<MutableList<MediaBrowserCompat.MediaItem>>,
        options: Bundle,
    ) {
        Log.i("NaviampAutoCommand", "Loading Auto children parent=$parentId options=${options.debugDescription()}")
        options.autoSearchQuery()?.let { query ->
            loadAsyncChildren(parentId, result) { autoSearchResults(query) }
            return
        }
        loadChildren(parentId, result)
    }

    fun search(
        query: String,
        extras: Bundle?,
        result: MediaBrowserServiceCompat.Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        val searchQuery = query.ifBlank { extras?.autoSearchQuery().orEmpty() }
        Log.i("NaviampAutoCommand", "Loading Auto search query=$searchQuery extras=${extras?.debugDescription().orEmpty()}")
        loadAsyncChildren(AndroidAutoPlaybackControls.MediaIdRoot, result) { autoSearchResults(searchQuery) }
    }

    private fun dynamicChildren(
        parentId: String,
        storage: AndroidStorageDependencies,
        sourceId: String?,
        result: MediaBrowserServiceCompat.Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ): MutableList<MediaBrowserCompat.MediaItem>? =
        when {
            parentId.startsWith(AndroidAutoPlaybackControls.MediaIdArtistGroupPrefix) -> {
                val group = Uri.decode(parentId.removePrefix(AndroidAutoPlaybackControls.MediaIdArtistGroupPrefix))
                sourceId?.let { id ->
                    storage.librarySnapshot(id, VoiceArtistScanLimit, 0)
                        .artists
                        .filter { it.name.autoArtistGroupKey() == group }
                        .take(AndroidAutoBrowseLimit)
                        .map(::artistItem)
                        .toMutableList()
                } ?: noSourceItems()
            }
            parentId.startsWith(AndroidAutoPlaybackControls.MediaIdPlaylistPrefix) -> {
                val playlistId = Uri.decode(parentId.removePrefix(AndroidAutoPlaybackControls.MediaIdPlaylistPrefix))
                loadAsyncChildren(parentId, result) {
                    val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                    val provider = NavidromeProvider(source.toNavidromeConnection())
                    withContext(Dispatchers.IO) {
                        providerResponseService(storage).playlistTracks(provider, playlistId)
                    }
                        .take(AndroidAutoBrowseLimit)
                        .map { track ->
                            trackItem(
                                track = track,
                                mediaId = AndroidAutoPlaybackControls.MediaIdPlaylistTrackPrefix + listOf(
                                    Uri.encode(playlistId),
                                    Uri.encode(track.id.value),
                                ).joinToString(MediaIdPartSeparator),
                            )
                        }
                        .toMutableList()
                }
                null
            }
            parentId.startsWith(AndroidAutoPlaybackControls.MediaIdArtistPrefix) -> {
                val parts = parentId.removePrefix(AndroidAutoPlaybackControls.MediaIdArtistPrefix).mediaIdParts()
                val artistId = parts.getOrNull(0).orEmpty()
                val artistName = parts.getOrNull(1)
                loadAsyncChildren(parentId, result) {
                    val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                    val provider = NavidromeProvider(source.toNavidromeConnection())
                    val tracks = loadArtistTracks(storage, storage, source.id, provider, artistId, artistName)
                    (
                        listOf(
                            playableItem(
                                mediaId = AndroidAutoPlaybackControls.MediaIdArtistShufflePrefix + listOf(
                                    Uri.encode(artistId),
                                    Uri.encode(artistName.orEmpty()),
                                ).joinToString(MediaIdPartSeparator),
                                title = "Shuffle",
                                subtitle = "Shuffle ${artistName ?: "artist"}",
                                iconUri = autoDrawableUri("ic_shuffle_24"),
                            ),
                        ) + tracks.take(AndroidAutoBrowseLimit).map { track ->
                            trackItem(
                                track = track,
                                mediaId = AndroidAutoPlaybackControls.MediaIdArtistTrackPrefix + listOf(
                                    Uri.encode(artistId),
                                    Uri.encode(artistName.orEmpty()),
                                    Uri.encode(track.id.value),
                                ).joinToString(MediaIdPartSeparator),
                            )
                        }
                    ).toMutableList()
                }
                null
            }
            parentId.startsWith(AndroidAutoPlaybackControls.MediaIdAlbumPrefix) -> {
                val parts = parentId.removePrefix(AndroidAutoPlaybackControls.MediaIdAlbumPrefix).mediaIdParts()
                val albumId = parts.getOrNull(0).orEmpty()
                val albumTitle = parts.getOrNull(1)
                val albumArtist = parts.getOrNull(2)
                loadAsyncChildren(parentId, result) {
                    val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                    val provider = NavidromeProvider(source.toNavidromeConnection())
                    val tracks = loadAlbumTracks(storage, storage, source.id, provider, albumId, albumTitle, albumArtist)
                    (
                        listOf(
                            playableItem(
                                mediaId = AndroidAutoPlaybackControls.MediaIdAlbumShufflePrefix + listOf(
                                    Uri.encode(albumId),
                                    Uri.encode(albumTitle.orEmpty()),
                                    Uri.encode(albumArtist.orEmpty()),
                                ).joinToString(MediaIdPartSeparator),
                                title = "Shuffle",
                                subtitle = "Shuffle ${albumTitle ?: "album"}",
                                iconUri = autoDrawableUri("ic_shuffle_24"),
                            ),
                        ) + tracks.take(AndroidAutoBrowseLimit).map { track ->
                            trackItem(
                                track = track,
                                mediaId = AndroidAutoPlaybackControls.MediaIdAlbumTrackPrefix + listOf(
                                    Uri.encode(albumId),
                                    Uri.encode(track.id.value),
                                ).joinToString(MediaIdPartSeparator),
                            )
                        }
                    ).toMutableList()
                }
                null
            }
            else -> mutableListOf()
        }

    private fun currentNowPlayingItem(): MediaBrowserCompat.MediaItem {
        val restored = restoredNowPlayingMetadata()
        val title = currentMetadata().title?.takeIf { it.isNotBlank() }
            ?: restored?.title
            ?: "Resume playback"
        val subtitle = currentMetadata().subtitle?.takeIf { it.isNotBlank() }
            ?: restored?.subtitle
            ?: "Continue your last Naviamp session"
        return playableItem(AndroidAutoPlaybackControls.MediaIdNowPlaying, title, subtitle)
    }

    private fun trackItem(
        track: Track,
        mediaId: String = "${AndroidAutoPlaybackControls.MediaIdTrackPrefix}${Uri.encode(track.id.value)}",
    ): MediaBrowserCompat.MediaItem =
        playableItem(
            mediaId = mediaId,
            title = track.title,
            subtitle = listOfNotNull(track.artistName, track.albumTitle).joinToString(" - "),
            iconUri = storage().savedCoverArtUrl(track),
        )

    private fun artistItem(artist: Artist): MediaBrowserCompat.MediaItem =
        browsableItem(
            mediaId = AndroidAutoPlaybackControls.MediaIdArtistPrefix + listOf(
                Uri.encode(artist.id.value),
                Uri.encode(artist.name),
            ).joinToString(MediaIdPartSeparator),
            title = artist.name,
            subtitle = "Artist",
        )

    private fun albumItem(album: Album): MediaBrowserCompat.MediaItem =
        browsableItem(
            mediaId = AndroidAutoPlaybackControls.MediaIdAlbumPrefix + listOf(
                Uri.encode(album.id.value),
                Uri.encode(album.title),
                Uri.encode(album.artistName),
            ).joinToString(MediaIdPartSeparator),
            title = album.title,
            subtitle = listOfNotNull(album.artistName, album.releaseYear?.toString()).joinToString(" - "),
            iconUri = storage().savedCoverArtUrl(album),
        )

    private suspend fun autoSearchResults(query: String): MutableList<MediaBrowserCompat.MediaItem> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext mutableListOf()
            val storage = storage()
            val source = storage.latestNavidromeSource() ?: return@withContext noSourceItems()
            val local = storage.searchLibrary(source.id, trimmed, AndroidAutoBrowseLimit.toLong(), 0)
            val provider = NavidromeProvider(source.toNavidromeConnection())
            val remote = if (local.isEmpty) {
                runCatching {
                    providerResponseService(storage).search(provider, trimmed, AndroidAutoBrowseLimit)
                }.getOrNull()
            } else {
                null
            }
            buildList {
                addAll(local.artists.ifEmpty { remote?.artists.orEmpty() }.take(8).map(::artistItem))
                addAll(local.albums.ifEmpty { remote?.albums.orEmpty() }.take(12).map(::albumItem))
                addAll(local.tracks.ifEmpty { remote?.tracks.orEmpty() }.take(AndroidAutoBrowseLimit).map(::trackItem))
            }.toMutableList()
        }

    private fun browsableItem(
        mediaId: String,
        title: String,
        subtitle: String,
        iconName: String? = null,
        iconUri: String? = null,
    ): MediaBrowserCompat.MediaItem =
        MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(mediaId)
                .setTitle(title)
                .setSubtitle(subtitle)
                .apply {
                    iconName?.let { setIconUri(Uri.parse(autoDrawableUri(it))) }
                    iconUri?.let { setIconUri(Uri.parse(it)) }
                }
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE,
        )

    private fun playableItem(
        mediaId: String,
        title: String,
        subtitle: String,
        iconUri: String? = null,
    ): MediaBrowserCompat.MediaItem =
        MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(mediaId)
                .setTitle(title)
                .setSubtitle(subtitle)
                .apply {
                    val artUri = iconUri ?: currentMetadata().coverArtUrl?.takeIf { mediaId == AndroidAutoPlaybackControls.MediaIdNowPlaying }
                    artUri?.let {
                        setIconUri(Uri.parse(it))
                    }
                }
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE,
        )

    private fun noSourceItems(): MutableList<MediaBrowserCompat.MediaItem> =
        mutableListOf(
            browsableItem(
                AndroidAutoPlaybackControls.MediaIdNoSource,
                "Connect Naviamp first",
                "Open the phone app and connect to Navidrome.",
            ),
        )

    private fun loadAsyncChildren(
        parentId: String,
        result: MediaBrowserServiceCompat.Result<MutableList<MediaBrowserCompat.MediaItem>>,
        load: suspend () -> MutableList<MediaBrowserCompat.MediaItem>,
    ) {
        result.detach()
        AndroidPlaybackRuntime.get(context).scope.launch {
            val children = runCatching { load() }
                .onFailure { error -> Log.w("NaviampAutoCommand", "Could not load Auto children", error) }
                .getOrDefault(loadErrorItems(parentId))
            sendChildren(parentId, children, result)
        }
    }

    private fun sendChildren(
        parentId: String,
        children: MutableList<MediaBrowserCompat.MediaItem>,
        result: MediaBrowserServiceCompat.Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        result.sendResult(
            children.ifEmpty {
                emptyItems(parentId)
            },
        )
    }

    private fun emptyItems(parentId: String): MutableList<MediaBrowserCompat.MediaItem> {
        val item = when {
            parentId == AndroidAutoPlaybackControls.MediaIdDownloads ->
                browsableItem("$parentId.empty", "No downloads", "Downloaded tracks will appear here.")
            parentId == AndroidAutoPlaybackControls.MediaIdPlaylists ->
                browsableItem("$parentId.empty", "No playlists", "Saved Navidrome playlists will appear here.")
            parentId == AndroidAutoPlaybackControls.MediaIdRadioStations ->
                browsableItem("$parentId.empty", "No stations", "Saved Navidrome internet radio stations will appear here.")
            parentId == AndroidAutoPlaybackControls.MediaIdRadioRecent ->
                browsableItem("$parentId.empty", "No recent radio", "Radio started from Naviamp will appear here.")
            parentId == AndroidAutoPlaybackControls.MediaIdQueue ->
                browsableItem("$parentId.empty", "Queue is empty", "Start playback to populate the queue.")
            parentId == AndroidAutoPlaybackControls.MediaIdLibraryArtists ||
                parentId == AndroidAutoPlaybackControls.MediaIdLibraryAlbums ||
                parentId == AndroidAutoPlaybackControls.MediaIdLibraryTracks ->
                browsableItem("$parentId.empty", "Library not indexed yet", "Open Naviamp on your phone to refresh the library.")
            parentId.startsWith(AndroidAutoPlaybackControls.MediaIdPlaylistPrefix) ->
                browsableItem("$parentId.empty", "Playlist is empty", "This playlist has no playable tracks.")
            parentId.startsWith(AndroidAutoPlaybackControls.MediaIdArtistPrefix) ->
                browsableItem("$parentId.empty", "No artist tracks", "No playable tracks were found for this artist.")
            parentId.startsWith(AndroidAutoPlaybackControls.MediaIdAlbumPrefix) ->
                browsableItem("$parentId.empty", "No album tracks", "No playable tracks were found for this album.")
            else ->
                browsableItem("$parentId.empty", "Nothing here yet", "Open Naviamp on your phone to refresh this section.")
        }
        return mutableListOf(item)
    }

    private fun loadErrorItems(parentId: String): MutableList<MediaBrowserCompat.MediaItem> =
        mutableListOf(
            browsableItem(
                "$parentId.error",
                "Could not load",
                "Open Naviamp on your phone and refresh this section.",
            ),
        )

    private fun autoDrawableUri(name: String): String =
        "android.resource://${context.packageName}/drawable/$name"

    private fun recentPlaybackHistoryItems(
        playbackHistoryRepository: PlaybackHistoryRepository<AndroidPlaybackHistoryItem>,
        sourceId: String,
    ): List<MediaBrowserCompat.MediaItem> =
        playbackHistoryRepository.playbackHistory(sourceId, AndroidAutoBrowseLimit)
            .map { history -> trackItem(history.track) }
}

private const val AndroidAutoBrowseLimit = 50
private const val MediaIdPartSeparator = "|"
private const val VoiceArtistScanLimit = 5_000L

private fun String.mediaIdParts(): List<String> =
    split("|").map { Uri.decode(it) }

private fun String.autoArtistGroupKey(): String {
    val first = trim().firstOrNull { it.isLetterOrDigit() } ?: return "#"
    return if (first.isLetter()) first.uppercaseChar().toString() else "#"
}

@Suppress("DEPRECATION")
private fun Bundle.debugDescription(): String =
    keySet().joinToString(prefix = "{", postfix = "}") { key -> "$key=${get(key)}" }

@Suppress("DEPRECATION")
private fun Bundle.autoSearchQuery(): String? {
    val keys = keySet()
    keys.firstOrNull { it.contains("search", ignoreCase = true) || it.contains("query", ignoreCase = true) }
        ?.let { key -> (get(key) as? String)?.trim()?.takeIf { it.isNotBlank() } }
        ?.let { return it }
    return keys.asSequence()
        .mapNotNull { key -> get(key) as? String }
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
}

private fun MediaSourceRepository.savedCoverArtUrl(track: Track): String? {
    val coverArtId = track.coverArtId ?: track.albumId?.value ?: return null
    val connection = latestMediaSource()?.toNavidromeConnection() ?: return null
    return NavidromeProvider(connection).coverArtUrl(coverArtId)
}

private fun MediaSourceRepository.savedCoverArtUrl(album: Album): String? {
    val coverArtId = album.coverArtId ?: album.id.value
    val connection = latestMediaSource()?.toNavidromeConnection() ?: return null
    return NavidromeProvider(connection).coverArtUrl(coverArtId)
}
