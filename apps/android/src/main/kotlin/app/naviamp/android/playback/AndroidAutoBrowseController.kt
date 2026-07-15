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
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.PlaybackHistoryRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.ApiCatalogService
import app.naviamp.domain.provider.MediaPageRequest
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.toNavidromeConnection
import app.naviamp.ui.defaultRadioArtworkUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

internal class AndroidAutoBrowseController(
    private val context: Context,
    private val storage: () -> AndroidStorageDependencies,
    private val currentQueue: () -> List<Track>,
    private val currentQueueIndex: () -> Int,
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
                            iconUri = autoStationArtUri(station),
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
                    iconUri = stream.coverArtIds.firstOrNull()?.let { coverArtId ->
                        storage.latestMediaSource()
                            ?.toNavidromeConnection()
                            ?.let { connection -> NavidromeProvider(connection).coverArtUrl(coverArtId) }
                    } ?: autoDrawableUri("ic_auto_radio"),
                )
            }
            val recentStations = settingsStore.loadRecentInternetRadioStations().map { station ->
                playableItem(
                    mediaId = "${AndroidAutoPlaybackControls.MediaIdRecentRadioPrefix}${Uri.encode(station.id)}",
                    title = station.name,
                    subtitle = station.homePageUrl ?: "Internet radio",
                    iconUri = autoStationArtUri(station.toStation()),
                )
            }
            sendChildren(parentId, (recentStreams + recentStations).toMutableList(), result)
            return
        }
        if (parentId == AndroidAutoPlaybackControls.MediaIdQueue) {
            loadAsyncChildren(parentId, result) { queuePageItems(0) }
            return
        }
        if (parentId == AndroidAutoPlaybackControls.MediaIdPlaylists) {
            loadAsyncChildren(parentId, result) {
                val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                val provider = NavidromeProvider(source.toNavidromeConnection())
                val playlists = withContext(Dispatchers.IO) {
                    providerResponseService(storage).playlists(provider, AndroidAutoBrowseLimit)
                }
                (
                    listOf(
                        browsableItem(
                            AndroidAutoPlaybackControls.MediaIdSmartPlaylists,
                            "Smart Playlists",
                            "Saved Navidrome smart playlists",
                        ),
                    ) + playlists.filterNot { it.isSmart }.map { playlist ->
                        val fallbackArtUri = runCatching {
                            provider.playlistTracks(playlist.id)
                                .firstNotNullOfOrNull { track -> storage.savedCoverArtUrl(track) }
                        }.getOrNull()
                        playlistItem(playlist, fallbackArtUri)
                    }
                )
                    .toMutableList()
            }
            return
        }
        if (parentId == AndroidAutoPlaybackControls.MediaIdSmartPlaylists) {
            loadAsyncChildren(parentId, result) {
                val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                val provider = NavidromeProvider(source.toNavidromeConnection())
                val savedSmartPlaylists = withContext(Dispatchers.IO) {
                    providerResponseService(storage).playlists(provider, AndroidAutoBrowseLimit)
                }.filter { it.isSmart }.map(::playlistItem)
                savedSmartPlaylists.toMutableList()
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
        if (parentId == AndroidAutoPlaybackControls.MediaIdLibraryArtists ||
            parentId == AndroidAutoPlaybackControls.MediaIdChartsArtists
        ) {
            loadAsyncChildren(parentId, result) {
                val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                val provider = NavidromeProvider(source.toNavidromeConnection())
                withContext(Dispatchers.IO) {
                    ApiCatalogService { provider }
                        .artistsPage("", MediaPageRequest(limit = AndroidAutoBrowseLimit))
                        .items
                }.map(::artistItem).toMutableList()
            }
            return
        }
        if (parentId == AndroidAutoPlaybackControls.MediaIdLibraryAlbums) {
            loadAsyncChildren(parentId, result) {
                val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                val provider = NavidromeProvider(source.toNavidromeConnection())
                withContext(Dispatchers.IO) {
                    ApiCatalogService { provider }
                        .albumsPage("", MediaPageRequest(limit = AndroidAutoBrowseLimit))
                        .items
                }.map(::albumItem).toMutableList()
            }
            return
        }
        if (parentId == AndroidAutoPlaybackControls.MediaIdLibraryTracks) {
            loadAsyncChildren(parentId, result) {
                val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                val provider = NavidromeProvider(source.toNavidromeConnection())
                withContext(Dispatchers.IO) {
                    ApiCatalogService { provider }
                        .tracksPage("", MediaPageRequest(limit = AndroidAutoBrowseLimit))
                        .items
                }.map(::trackItem).toMutableList()
            }
            return
        }
        val children = when (parentId) {
            AndroidAutoPlaybackControls.MediaIdRoot -> autoRootItems(storage, sourceId)
            AndroidAutoPlaybackControls.MediaIdHome -> mutableListOf(
                currentNowPlayingItem(),
                browsableItem(AndroidAutoPlaybackControls.MediaIdHomeMixes, "Suggested Mixes", "Quick queues from your library"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadioRecent, "Recent", "Radio you started from Naviamp"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdHomeRecentPlays, "Recent Plays", "Recently played tracks and radio"),
            )
            AndroidAutoPlaybackControls.MediaIdLibrary -> mutableListOf(
                browsableItem(AndroidAutoPlaybackControls.MediaIdPlaylists, "Playlists", "Saved Navidrome playlists"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdSmartPlaylists, "Smart Playlists", "Saved Navidrome smart playlists"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadioRecent, "Recent", "Radio you started from Naviamp"),
                playableItem(AndroidAutoPlaybackControls.MediaIdRadioLibrary, "Library Radio", "Random tracks from your library"),
            )
            AndroidAutoPlaybackControls.MediaIdRadio -> mutableListOf(
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadioRecent, "Recent", "Radio you started from Naviamp"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadioStations, "Internet Radio", "Saved Navidrome stations"),
                playableItem(AndroidAutoPlaybackControls.MediaIdRadioLibrary, "Library Radio", "Random tracks from your library"),
            )
            AndroidAutoPlaybackControls.MediaIdHomeMixes -> mutableListOf(
                playableItem(AndroidAutoPlaybackControls.MediaIdRadioLibrary, "Library Radio", "Random tracks from your library"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadioRecent, "Recent", "Radio you started from Naviamp"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdHomeDjs, "DJs", "Saved radio tuning presets"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdSmartPlaylists, "Smart Playlists", "Saved smart playlists and templates"),
            )
            AndroidAutoPlaybackControls.MediaIdHomeDjs -> storage.radioDjPresets()
                .map { dj ->
                    playableItem(
                        mediaId = "${AndroidAutoPlaybackControls.MediaIdRadioDjPrefix}${Uri.encode(dj.id)}",
                        title = dj.name,
                        subtitle = "DJ preset",
                    )
                }
                .toMutableList()
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
                browsableItem(AndroidAutoPlaybackControls.MediaIdSmartPlaylists, "Smart Playlists", "Saved smart playlists and templates"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadioStations, "Internet Radio", "Saved Navidrome stations"),
            )
            else -> dynamicChildren(parentId, storage, sourceId, result) ?: return
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
        if (parentId == AndroidAutoPlaybackControls.MediaIdQueue && options.hasBrowsePagination()) {
            val children = queueItems()
            val pagedChildren = children.paginated(options)
            Log.i(
                "NaviampAutoCommand",
                "Loaded paged Auto queue count=${children.size} delivered=${pagedChildren.size} options=${options.debugDescription()}",
            )
            result.sendResult(
                if (children.isEmpty()) {
                    emptyItems(parentId)
                } else {
                    pagedChildren
                },
            )
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
                loadAsyncChildren(parentId, result) {
                    val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                    val provider = NavidromeProvider(source.toNavidromeConnection())
                    withContext(Dispatchers.IO) {
                        ApiCatalogService { provider }
                            .artistsPage("", MediaPageRequest(limit = AndroidAutoBrowseLimit))
                            .items
                    }
                        .filter { it.name.autoArtistGroupKey() == group }
                        .map(::artistItem)
                        .toMutableList()
                }
                null
            }
            parentId.startsWith(AndroidAutoPlaybackControls.MediaIdQueuePagePrefix) -> {
                val page = Uri.decode(parentId.removePrefix(AndroidAutoPlaybackControls.MediaIdQueuePagePrefix))
                    .toIntOrNull()
                    ?.coerceAtLeast(0)
                    ?: 0
                loadAsyncChildren(parentId, result) { queuePageItems(page) }
                null
            }
            parentId.startsWith(AndroidAutoPlaybackControls.MediaIdPlaylistPrefix) -> {
                val playlistId = Uri.decode(parentId.removePrefix(AndroidAutoPlaybackControls.MediaIdPlaylistPrefix))
                loadAsyncChildren(parentId, result) {
                    val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                    val provider = NavidromeProvider(source.toNavidromeConnection())
                    val tracks = loadPlaylistTracks(provider, storage, playlistId)
                    val playlistArtUri = tracks.firstNotNullOfOrNull { track -> storage.savedCoverArtUrl(track) }
                    (
                        listOf(
                            playableItem(
                                mediaId = "${AndroidAutoPlaybackControls.MediaIdPlaylistPlayPrefix}${Uri.encode(playlistId)}",
                                title = "Play",
                                subtitle = "Play playlist",
                                iconUri = playlistArtUri,
                            ),
                            playableItem(
                                mediaId = "${AndroidAutoPlaybackControls.MediaIdPlaylistShufflePrefix}${Uri.encode(playlistId)}",
                                title = "Shuffle",
                                subtitle = "Shuffle playlist",
                                iconUri = autoDrawableUri("ic_shuffle_24"),
                            ),
                        ) + tracks.take(AndroidAutoBrowseLimit).map { track ->
                            trackItem(
                                track = track,
                                mediaId = AndroidAutoPlaybackControls.MediaIdPlaylistTrackPrefix + listOf(
                                    Uri.encode(playlistId),
                                    Uri.encode(track.id.value),
                                ).joinToString(MediaIdPartSeparator),
                            )
                        }
                    ).toMutableList()
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
                    val artistArtUri = tracks.firstNotNullOfOrNull { track -> storage.savedCoverArtUrl(track) }
                    val albums = artistId.takeIf { it.isNotBlank() }
                        ?.let { id ->
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    provider.artist(ArtistId(id)).albums
                                }
                            }.getOrDefault(emptyList())
                        }
                        .orEmpty()
                        .ifEmpty { tracks.toAlbumsForArtist() }
                    (
                        listOf(
                            playableItem(
                                mediaId = AndroidAutoPlaybackControls.MediaIdArtistPlayPrefix + listOf(
                                    Uri.encode(artistId),
                                    Uri.encode(artistName.orEmpty()),
                                ).joinToString(MediaIdPartSeparator),
                                title = "Play",
                                subtitle = "Play ${artistName ?: "artist"}",
                                iconUri = artistArtUri,
                            ),
                            playableItem(
                                mediaId = AndroidAutoPlaybackControls.MediaIdArtistShufflePrefix + listOf(
                                    Uri.encode(artistId),
                                    Uri.encode(artistName.orEmpty()),
                                ).joinToString(MediaIdPartSeparator),
                                title = "Shuffle",
                                subtitle = "Shuffle ${artistName ?: "artist"}",
                                iconUri = autoDrawableUri("ic_shuffle_24"),
                            ),
                        ) + albums.take(AndroidAutoBrowseLimit).map(::albumItem)
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
                    val albumArtUri = tracks.firstNotNullOfOrNull { track -> storage.savedCoverArtUrl(track) }
                    (
                        listOf(
                            playableItem(
                                mediaId = AndroidAutoPlaybackControls.MediaIdAlbumPlayPrefix + listOf(
                                    Uri.encode(albumId),
                                    Uri.encode(albumTitle.orEmpty()),
                                    Uri.encode(albumArtist.orEmpty()),
                                ).joinToString(MediaIdPartSeparator),
                                title = "Play",
                                subtitle = "Play ${albumTitle ?: "album"}",
                                iconUri = albumArtUri,
                            ),
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

    private fun autoRootItems(
        storage: AndroidStorageDependencies,
        sourceId: String?,
    ): MutableList<MediaBrowserCompat.MediaItem> =
        buildList {
            add(currentNowPlayingItem())
            add(browsableItem(AndroidAutoPlaybackControls.MediaIdRadioRecent, "Recent", "Radio you started from Naviamp"))
            add(browsableItem(AndroidAutoPlaybackControls.MediaIdPlaylists, "Playlists", "Saved Navidrome playlists"))
            add(browsableItem(AndroidAutoPlaybackControls.MediaIdRadio, "Radio", "Internet and library radio", iconName = "ic_auto_radio"))
            add(browsableItem(AndroidAutoPlaybackControls.MediaIdHome, "Suggested Mixes", "DJs, templates, and recent generated radio", iconName = "ic_auto_home"))
            if (sourceId != null && storage.downloadedTracks(sourceId).any { it.file.exists() }) {
                add(browsableItem(AndroidAutoPlaybackControls.MediaIdDownloads, "Downloads", "Offline tracks"))
            }
            if (currentQueue().isNotEmpty()) {
                add(browsableItem(AndroidAutoPlaybackControls.MediaIdQueue, "Current queue", "${currentQueue().size} tracks"))
            }
        }.toMutableList()

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

    private fun queuePageItems(page: Int): MutableList<MediaBrowserCompat.MediaItem> {
        val queue = currentQueue()
        if (queue.isEmpty()) return mutableListOf()
        val startIndex = currentQueueIndex().takeIf { it in queue.indices } ?: 0
        val ordered = queue.indices.map { offset ->
            val index = (startIndex + offset) % queue.size
            index to queue[index]
        }
        val pageSize = AndroidAutoQueuePageSize
        val pageStart = (page * pageSize).coerceAtMost(ordered.lastIndex)
        val pageItems = ordered.drop(pageStart).take(pageSize)
        return buildList {
            if (ordered.size > pageStart + pageSize) {
                add(
                    browsableItem(
                        mediaId = "${AndroidAutoPlaybackControls.MediaIdQueuePagePrefix}${Uri.encode((page + 1).toString())}",
                        title = "More queue",
                        subtitle = "${pageStart + pageSize + 1}-${min(pageStart + pageSize * 2, ordered.size)} of ${ordered.size}",
                    ),
                )
            }
            if (page > 0) {
                add(
                    browsableItem(
                        mediaId = "${AndroidAutoPlaybackControls.MediaIdQueuePagePrefix}${Uri.encode((page - 1).toString())}",
                        title = "Previous queue",
                        subtitle = "${(pageStart - pageSize + 1).coerceAtLeast(1)}-$pageStart of ${ordered.size}",
                    ),
                )
            }
            pageItems.forEach { (index, track) ->
                add(
                    trackItem(
                        track = track,
                        mediaId = "${AndroidAutoPlaybackControls.MediaIdQueueTrackPrefix}${Uri.encode(index.toString())}",
                    ),
                )
            }
        }.toMutableList()
    }

    private fun queueItems(): MutableList<MediaBrowserCompat.MediaItem> {
        val queue = currentQueue()
        val startIndex = currentQueueIndex().takeIf { it in queue.indices } ?: 0
        return queue.indices.map { offset ->
            val index = (startIndex + offset) % queue.size
            val track = queue[index]
            trackItem(
                track = track,
                mediaId = "${AndroidAutoPlaybackControls.MediaIdQueueTrackPrefix}${Uri.encode(index.toString())}",
            )
        }.toMutableList()
    }

    private fun trackItem(
        track: Track,
        mediaId: String = AndroidAutoPlaybackControls.MediaIdTrackPrefix + listOf(
            Uri.encode(track.id.value),
            Uri.encode(track.title),
            Uri.encode(track.artistId?.value.orEmpty()),
            Uri.encode(track.artistName),
            Uri.encode(track.albumId?.value.orEmpty()),
            Uri.encode(track.albumTitle.orEmpty()),
            Uri.encode(track.durationSeconds?.toString().orEmpty()),
            Uri.encode(track.coverArtId.orEmpty()),
        ).joinToString(MediaIdPartSeparator),
        includeArt: Boolean = true,
    ): MediaBrowserCompat.MediaItem =
        playableItem(
            mediaId = mediaId,
            title = track.title,
            subtitle = listOfNotNull(track.artistName, track.albumTitle).joinToString(" - "),
            iconUri = if (includeArt) storage().savedCoverArtUrl(track) else null,
        )

    private fun artistItem(artist: Artist): MediaBrowserCompat.MediaItem =
        browsableItem(
            mediaId = AndroidAutoPlaybackControls.MediaIdArtistPrefix + listOf(
                Uri.encode(artist.id.value),
                Uri.encode(artist.name),
            ).joinToString(MediaIdPartSeparator),
            title = artist.name,
            subtitle = "Artist",
            iconUri = storage().savedCoverArtUrl(artist),
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

    private fun playlistItem(
        playlist: Playlist,
        fallbackIconUri: String? = null,
    ): MediaBrowserCompat.MediaItem =
        browsableItem(
            mediaId = "${AndroidAutoPlaybackControls.MediaIdPlaylistPrefix}${Uri.encode(playlist.id)}",
            title = playlist.name,
            subtitle = if (playlist.isSmart) {
                "Smart playlist - ${playlist.trackCount} tracks"
            } else {
                "${playlist.trackCount} tracks"
            },
            iconUri = storage().savedCoverArtUrl(playlist) ?: fallbackIconUri,
        )

    private suspend fun autoSearchResults(query: String): MutableList<MediaBrowserCompat.MediaItem> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext mutableListOf()
            val storage = storage()
            val source = storage.latestNavidromeSource() ?: return@withContext noSourceItems()
            val provider = NavidromeProvider(source.toNavidromeConnection())
            val remote = runCatching {
                provider.search(trimmed, AndroidAutoBrowseLimit)
            }.getOrNull()
            buildList {
                addAll(remote?.artists.orEmpty().take(8).map(::artistItem))
                addAll(remote?.albums.orEmpty().take(12).map(::albumItem))
                addAll(remote?.tracks.orEmpty().take(AndroidAutoBrowseLimit).map(::trackItem))
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
                    when {
                        iconName != null -> setIconUri(Uri.parse(autoDrawableUri(iconName)))
                        iconUri != null -> setIconUri(Uri.parse(iconUri))
                    }
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
                    artUri?.let { setIconUri(Uri.parse(it)) }
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
            parentId == AndroidAutoPlaybackControls.MediaIdHomeDjs ->
                browsableItem("$parentId.empty", "No DJs saved", "Create DJs in Naviamp to use them from Android Auto.")
            parentId == AndroidAutoPlaybackControls.MediaIdSmartPlaylists ->
                browsableItem("$parentId.empty", "No smart playlists", "Create smart playlists in Naviamp to use them from Android Auto.")
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

    private fun autoStationArtUri(station: InternetRadioStation): String {
        val artworkUrl = station.defaultRadioArtworkUrl()
        return if (artworkUrl.startsWith("http://") || artworkUrl.startsWith("https://")) {
            artworkUrl
        } else {
            autoDrawableUri("ic_auto_radio")
        }
    }

    private fun recentPlaybackHistoryItems(
        playbackHistoryRepository: PlaybackHistoryRepository<AndroidPlaybackHistoryItem>,
        sourceId: String,
    ): List<MediaBrowserCompat.MediaItem> =
        playbackHistoryRepository.playbackHistory(sourceId, AndroidAutoBrowseLimit)
            .map { history -> trackItem(history.track) }

    private suspend fun loadPlaylistTracks(
        provider: NavidromeProvider,
        cacheRepository: ProviderResponseCacheRepository,
        playlistId: String,
    ): List<Track> =
        withContext(Dispatchers.IO) {
            providerResponseService(cacheRepository).invalidatePlaylistTracks(provider, playlistId)
            provider.playlistTracks(playlistId).also { tracks ->
                Log.i(
                    "NaviampAutoCommand",
                    "Auto playlist detail playlist=$playlistId count=${tracks.size} ids=${tracks.take(3).joinToString { it.id.value }}",
                )
            }
        }
}

private const val AndroidAutoBrowseLimit = 50
private const val AndroidAutoQueuePageSize = 1
private const val MediaIdPartSeparator = "|"

private fun Bundle.hasBrowsePagination(): Boolean =
    getInt(MediaBrowserCompat.EXTRA_PAGE, -1) >= 0 &&
        getInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, -1) > 0

private fun MutableList<MediaBrowserCompat.MediaItem>.paginated(options: Bundle): MutableList<MediaBrowserCompat.MediaItem> {
    val page = options.getInt(MediaBrowserCompat.EXTRA_PAGE, -1)
    val pageSize = options.getInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, -1)
    if (page < 0 || pageSize <= 0) return this
    val fromIndex = page * pageSize
    if (fromIndex >= size) return mutableListOf()
    val toIndex = min(fromIndex + pageSize, size)
    return subList(fromIndex, toIndex).toMutableList()
}

private fun String.mediaIdParts(): List<String> =
    split("|").map { Uri.decode(it) }

private fun String.autoArtistGroupKey(): String {
    val first = trim().firstOrNull { it.isLetterOrDigit() } ?: return "#"
    return if (first.isLetter()) first.uppercaseChar().toString() else "#"
}

private fun List<Track>.toAlbumsForArtist(): List<Album> =
    asSequence()
        .filter { it.albumId != null || !it.albumTitle.isNullOrBlank() }
        .groupBy { track ->
            track.albumId?.value ?: track.albumTitle.orEmpty().lowercase()
        }
        .values
        .mapNotNull { tracks ->
            val first = tracks.firstOrNull() ?: return@mapNotNull null
            val albumTitle = first.albumTitle?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Album(
                id = first.albumId ?: AlbumId("auto:${albumTitle.lowercase()}:${first.artistName.lowercase()}"),
                title = albumTitle,
                artistName = first.artistName,
                coverArtId = first.coverArtId ?: first.albumId?.value,
                recentlyAddedAtIso8601 = null,
                releaseYear = first.albumReleaseYear,
            )
        }
        .sortedWith(compareBy<Album> { it.releaseYear ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() })

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

private fun MediaSourceRepository.savedCoverArtUrl(artist: Artist): String? {
    val connection = latestMediaSource()?.toNavidromeConnection() ?: return null
    return NavidromeProvider(connection).coverArtUrl(artist.id.value)
}

private fun MediaSourceRepository.savedCoverArtUrl(playlist: Playlist): String? {
    val connection = latestMediaSource()?.toNavidromeConnection() ?: return null
    return NavidromeProvider(connection).coverArtUrl(playlist.coverArtId ?: playlist.id)
}
