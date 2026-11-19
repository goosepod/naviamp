package app.naviamp.presentation

import app.naviamp.ui.NaviampVehicleBrowseLabels
import app.naviamp.ui.loadNaviampVehicleBrowseLabels
import app.naviamp.ui.loadNaviampVehiclePlaybackLabels
import app.naviamp.ui.loadNaviampVehicleLibraryLabels
import app.naviamp.ui.NaviampVehicleSearchLabels
import app.naviamp.ui.loadNaviampVehicleSearchLabels
import app.naviamp.ui.loadNaviampVehicleDownloadsLabels
import app.naviamp.domain.provider.normalizedSearchQuery
import app.naviamp.domain.provider.SearchDebounceMillis
import app.naviamp.domain.provider.MediaPageRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class NaviampVehicleBrowseDestination { Home, Playlists, Radio, Library, Downloads }

data class NaviampVehicleBrowseRow(val id: String, val title: String, val subtitle: String, val enabled: Boolean = true, val artwork: NaviampVehicleArtwork? = null)
data class NaviampVehicleBrowseTab(
    val destination: NaviampVehicleBrowseDestination,
    val title: String,
    val rows: List<NaviampVehicleBrowseRow>,
    val status: String,
)
data class NaviampVehicleSearchResults(val revision: Long, val rows: List<NaviampVehicleBrowseRow>)

data class NaviampVehicleBrowsePage(
    val id: String,
    val title: String,
    val rows: List<NaviampVehicleBrowseRow>,
    val status: String,
    val search: Boolean = false,
)
data class NaviampVehicleBrowseState(
    val revision: Long,
    val tabs: List<NaviampVehicleBrowseTab>,
    val pages: List<NaviampVehicleBrowsePage> = emptyList(),
    val queueAvailable: Boolean = false,
    val navigationRevision: Long = 0L,
    val nowPlaying: Boolean = false,
    val presentationAvailable: Boolean = true,
    val playback: NaviampVehiclePlaybackState = NaviampVehiclePlaybackState(),
    val currentAlbumAvailable: Boolean = false,
    // When set, Now Playing sits immediately below this page instead of on top.
    val nowPlayingBeforePageId: String? = null,
)

/**
 * Scene-confined vehicle presentation. Native hosts report list limits and presentation lifetimes;
 * Core owns navigation, loading, source invalidation and audio-readiness completion. Detaching a
 * scene cancels only its requests/waiters, never the application runtime or ready audio.
 */
class NaviampVehicleBrowseController internal constructor(
    scope: CoroutineScope,
    private val product: StateFlow<NaviampCoreState>,
    private val bridge: NaviampCoreExternalPlaybackBridge,
    private val execute: suspend (NaviampCoreCommand) -> Unit,
    private val labels: NaviampVehicleBrowseLabels,
    maximumItems: Int,
    private val readinessTimeoutMillis: Long = 30_000L,
    private val maximumTabs: Int = 3,
    private val collections: NaviampVehicleCollectionAccess = NaviampVehicleCollectionAccess { error("No collection access") },
    private val playbackControls: NaviampVehiclePlaybackController? = null,
    private val library: NaviampVehicleLibraryAccess? = null,
    private val searchLabels: NaviampVehicleSearchLabels? = null,
    private val downloads: NaviampVehicleDownloadsAccess? = null,
    artworkSource: NaviampVehicleArtworkSource? = null,
    private val connections: NaviampVehicleConnectionController? = null,
) {
    private class Detail(
        val id: String,
        val connection: Boolean = false,
        val reference: NaviampVehicleCollectionReference? = null,
        val libraryReference: NaviampVehicleLibraryReference? = null,
        val libraryPages: MutableMap<Int, NaviampVehicleLibraryPage> = mutableMapOf(),
        val libraryRequests: MutableMap<Int, MediaPageRequest> = mutableMapOf(),
        var loadSerial: Long = 0L,
        var searchRevision: Long = 0L,
        var searchTimeout: Job? = null,
        var query: String = "",
        var searchStatus: String = "",
        var searchComplete: ((NaviampVehicleSearchResults) -> Unit)? = null,
        var submitPending: Boolean = false,
        var collection: NaviampVehicleCollection? = null,
        var loading: Boolean = false,
        var failed: Boolean = false,
        var page: Int = 0,
        var load: Job? = null,
        val aboveNowPlaying: Boolean = false,
    )
    private val session = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private val limit = maximumItems.coerceAtLeast(0)
    private val rootPages = mutableMapOf<NaviampVehicleBrowseDestination, Int>()
    private val details = mutableListOf<Detail>()
    private var detailSerial = 0L
    private var navigationRevision = 0L
    private var nowPlaying = false
    private var revision = 0L
    private var source = product.value.vehicleSourceKey()
    private var catalogFingerprint: Any? = null
    private var queueFingerprint: Any? = null
    private var albumFingerprint: NaviampVehicleCollectionReference? = null
    private var libraryFingerprint: Any? = null
    private var connectionFingerprint: Any? = null
    private var rootUnavailable = false
    private var failed = false
    private var closed = false
    private var selection: Job? = null
    private var selectionSerial = 0L
    private val artwork = NaviampVehicleArtworkController(session, artworkSource)
    private val mutableState = MutableStateFlow(project())
    val state: StateFlow<NaviampVehicleBrowseState> = mutableState.asStateFlow()

    init {
        connections?.let { owner -> session.launch { owner.state.collect { publish() } } }
        playbackControls?.let { controls ->
            session.launch { controls.state.collect { mutableState.value = mutableState.value.copy(playback = it) } }
        }
        session.launch {
            // Observe queue content/position, never progress, visualizer frames or natural time ticks.
            product.map { value -> listOf(value.vehicleSourceKey(), value.shell.connectionSettings,
                value.shell.home.content, value.shell.home.refreshing, value.shell.playlists, value.shell.radio,
                value.shell.nowPlaying?.let { listOf(it.id, it.title, it.subtitle, it.albumLine,
                    it.coverArtUrl, it.backTo, it.upNext, it.isLive, it.albumId, it.albumTitle, it.castSelected) }) }
                .distinctUntilChanged().collect { publish() }
        }
    }

    private fun connectionEntries(): List<NaviampExternalMediaItem> = connections?.let { owner -> buildList {
        add(NaviampExternalMediaItem(Connections, owner.labels.title, "", playable = false))
        if (owner.available && !owner.busy && owner.currentSaved != null && owner.recoveryNeeded)
            add(NaviampExternalMediaItem(NaviampVehicleConnectionController.Reconnect, owner.labels.reconnect, "", playable = false))
    } }.orEmpty()

    private fun catalog() = mapOf(
        NaviampVehicleBrowseDestination.Home to buildList {
            if (connections?.recoveryNeeded == true) addAll(connectionEntries())
            if (bridge.snapshot().let { it.queue.isNotEmpty() && !it.isLive }) add(NaviampExternalMediaItem(Queue, labels.queue, "", playable = false))
            if (library?.available == true && searchLabels != null && limit >= 4)
                add(NaviampExternalMediaItem(Search, searchLabels.title, "", playable = false))
            if (library?.available == true && maximumTabs < 4)
                add(NaviampExternalMediaItem(Library, library.labels.library, "", playable = false))
            if (downloads?.available == true && limit >= 5 && maximumTabs < 5)
                add(NaviampExternalMediaItem(Downloads, downloads.labels.downloads, "", playable = false))
            if (connections?.recoveryNeeded != true) addAll(connectionEntries())
            addAll(bridge.browseChildren(NaviampExternalRecentTracksId))
            addAll(bridge.browseChildren(NaviampExternalRecentAlbumsId))
        },
        NaviampVehicleBrowseDestination.Playlists to bridge.browseChildren(NaviampExternalPlaylistsId),
        NaviampVehicleBrowseDestination.Radio to bridge.browseChildren(NaviampExternalRadioId),
        NaviampVehicleBrowseDestination.Library to buildList {
            if (library?.available == true && searchLabels != null && limit >= 4) add(NaviampExternalMediaItem(Search, searchLabels.title, "", playable = false))
            if (downloads?.available == true && limit >= 5) add(NaviampExternalMediaItem(Downloads, downloads.labels.downloads, "", playable = false))
            addAll(library?.categories().orEmpty().map { NaviampExternalMediaItem(it.id, it.title, it.subtitle, playable = false) })
        },
        NaviampVehicleBrowseDestination.Downloads to downloads?.takeIf { limit >= 5 }?.categories().orEmpty().map {
            NaviampExternalMediaItem(it.id, it.title, it.subtitle, playable = false)
        },
    )

    private fun publish() { mutableState.value = project() }

    private fun project(): NaviampVehicleBrowseState {
        val nextSource = product.value.vehicleSourceKey()
        if (source != nextSource) {
            source = nextSource
            selection?.cancel()
            details.filterNot { it.connection }.forEach(::cancelDetail)
            details.removeAll { !it.connection }
            navigationRevision++
            nowPlaying = false
            rootPages.clear()
            revision++
            failed = false
        }
        val connectionKey = connections?.fingerprint
        if (connectionFingerprint != connectionKey) { connectionFingerprint = connectionKey; revision++ }
        artwork.begin(nextSource)
        val catalog = catalog()
        if (catalogFingerprint != catalog) {
            catalogFingerprint = catalog
            revision++
            rootPages.clear()
            failed = false
        }
        val libraryState = listOf(library?.available, library?.categories())
        if (libraryState != libraryFingerprint) { libraryFingerprint = libraryState; revision++ }
        val album = bridge.currentAlbumCollection()
        if (albumFingerprint != album) {
            albumFingerprint = album
            revision++
        }
        val queue = bridge.snapshot()
        val nextQueue = Triple(queue.queue, queue.currentQueueIndex, queue.isLive)
        if (queueFingerprint != nextQueue) {
            queueFingerprint = nextQueue
            details.filter { !it.connection && it.reference == null && it.libraryReference == null }.forEach { it.page = 0 }
            revision++
        }
        val shell = product.value.shell
        val connection = shell.connectionSettings.connection
        val tabs = NaviampVehicleBrowseDestination.entries.take(maximumTabs.coerceAtLeast(0)).map { destination ->
            val all = catalog.getValue(destination).map { externalRow(it) }
            val refreshing = when (destination) {
                NaviampVehicleBrowseDestination.Home -> shell.home.refreshing
                NaviampVehicleBrowseDestination.Playlists -> shell.playlists.refreshing
                NaviampVehicleBrowseDestination.Radio -> shell.radio.refreshing
                NaviampVehicleBrowseDestination.Library, NaviampVehicleBrowseDestination.Downloads -> false
            }
            val status = when {
                failed || rootUnavailable || limit < 3 -> labels.failed
                connection.isConnecting || connection.restoringConnection || refreshing -> labels.loading
                destination == NaviampVehicleBrowseDestination.Downloads && downloads?.available == true && limit >= 5 -> ""
                destination == NaviampVehicleBrowseDestination.Library && all.any { it.id == Downloads } && library?.available != true -> ""
                destination == NaviampVehicleBrowseDestination.Downloads && shell.connectionSettings.currentSourceId != null -> library?.labels?.unavailable ?: labels.failed
                destination == NaviampVehicleBrowseDestination.Library && library?.available != true &&
                    shell.connectionSettings.currentSourceId != null -> library?.labels?.unavailable ?: labels.failed
                connection.statusIsError -> labels.failed
                shell.connectionSettings.currentSourceId == null -> labels.disconnected
                all.isEmpty() -> labels.empty
                else -> ""
            }
            val (page, rows) = paginate(all, rootPages[destination] ?: 0)
            rootPages[destination] = page
            NaviampVehicleBrowseTab(destination, when (destination) {
                NaviampVehicleBrowseDestination.Home -> labels.home
                NaviampVehicleBrowseDestination.Playlists -> labels.playlists
                NaviampVehicleBrowseDestination.Radio -> labels.radio
                NaviampVehicleBrowseDestination.Library -> library?.labels?.library.orEmpty()
                NaviampVehicleBrowseDestination.Downloads -> downloads?.labels?.downloads.orEmpty()
            }, rows, status)
        }
        val projected = details.map { detail ->
            if (detail.connection) {
                val (page, rows) = paginate(connections?.rows().orEmpty(), detail.page)
                detail.page = page
                return@map NaviampVehicleBrowsePage(detail.id, connections?.labels?.title.orEmpty(), rows, connections?.status.orEmpty())
            }
            if (detail.libraryReference != null) return@map projectLibrary(detail)
            val all = if (detail.reference == null) {
                queue.takeUnless { it.isLive }?.automotiveQueue().orEmpty().map { externalRow(it) }
            } else buildList {
                if (detail.failed) add(NaviampVehicleBrowseRow(Retry, labels.retry, ""))
                detail.collection?.takeIf { it.tracks.isNotEmpty() }?.let { collection ->
                    add(NaviampVehicleBrowseRow(Play, labels.play, ""))
                    add(NaviampVehicleBrowseRow(Shuffle, labels.shuffle, ""))
                    addAll(collection.tracks.mapIndexed { index, track ->
                        NaviampVehicleBrowseRow("$Track$index", track.title, track.artistName,
                            artwork = artwork.describe(NaviampVehicleArtworkReference(app.naviamp.ui.SharedMediaItemKind.Track, coverArtId = track.coverArtId)))
                    })
                }
            }
            val (page, rows) = paginate(all, detail.page)
            detail.page = page
            NaviampVehicleBrowsePage(detail.id, detail.reference?.title ?: labels.queue, rows, when {
                failed || detail.failed || rootUnavailable || limit < 3 -> labels.failed
                detail.loading -> labels.loading
                all.isEmpty() -> labels.empty
                else -> ""
            })
        }
        artwork.end((tabs.flatMap { it.rows } + projected.flatMap { it.rows })
            .mapNotNull { it.artwork?.id }.toSet())
        return NaviampVehicleBrowseState(revision, tabs, projected, queue.queue.isNotEmpty() && !queue.isLive, navigationRevision, nowPlaying, !rootUnavailable, playbackControls?.state?.value ?: NaviampVehiclePlaybackState(),
            !closed && !rootUnavailable && album != null,
            details.firstOrNull { nowPlaying && it.aboveNowPlaying }?.id)
    }

    private fun externalRow(item: NaviampExternalMediaItem) = NaviampVehicleBrowseRow(item.mediaId, item.title, item.subtitle,
        artwork = item.mediaKind.takeUnless { it == app.naviamp.ui.SharedMediaItemKind.Unknown }?.let {
            artwork.describe(NaviampVehicleArtworkReference(it, url = item.artworkUrl))
        })

    fun requestArtwork(id: String, completed: (ByteArray?) -> Unit) {
        publish()
        artwork.request(id, completed)
    }

    private fun paginate(all: List<NaviampVehicleBrowseRow>, requested: Int): Pair<Int, List<NaviampVehicleBrowseRow>> {
        if (limit < 3) return 0 to emptyList()
        val size = limit - 2
        val page = requested.coerceIn(0, (all.size - 1).coerceAtLeast(0) / size)
        return page to buildList {
            if (page > 0) add(NaviampVehicleBrowseRow(Previous, labels.previous, ""))
            addAll(all.drop(page * size).take(size))
            if ((page + 1) * size < all.size) add(NaviampVehicleBrowseRow(Next, labels.next, ""))
        }
    }

    /** Native back navigation reports removed page identities, never chooses product destinations. */
    fun dismissPages(ids: List<String>) {
        if (closed) return
        val index = details.indexOfFirst { it.id in ids }
        if (index < 0) return
        selection?.cancel()
        details.drop(index).forEach(::cancelDetail)
        while (details.size > index) details.removeAt(details.lastIndex)
        revision++
        failed = false
        publish()
    }

    /** A failed root cannot receive input. A new scene controller is required to try again. */
    fun rootPresentationFailed() {
        if (closed) return
        rootUnavailable = true
        connections?.cancel()
        playbackControls?.close()
        selection?.cancel()
        details.forEach(::cancelDetail)
        details.clear()
        failed = true
        revision++
        publish()
    }

    /** A native presentation failure ends that request; only an explicit user action may retry. */
    fun presentationFailed(pageId: String?) {
        if (closed) return
        if (pageId != null) dismissPages(listOf(pageId))
        failed = true
        revision++
        publish()
    }

    fun openQueue() = openQueue(aboveNowPlaying = false)

    fun openUpNext() = openQueue(aboveNowPlaying = true)

    private fun openQueue(aboveNowPlaying: Boolean) {
        if (closed || rootUnavailable) return
        publish()
        if (!state.value.queueAvailable) return
        selection?.cancel()
        if (!aboveNowPlaying) details.removeAll { it.aboveNowPlaying }
        val existing = details.firstOrNull {
            !it.connection && it.reference == null && it.libraryReference == null && it.aboveNowPlaying == aboveNowPlaying
        }
        if (existing == null) {
            details += Detail("queue:${++detailSerial}", aboveNowPlaying = aboveNowPlaying)
            trimDetails()
        } else {
            dismissPages(details.dropWhile { it !== existing }.drop(1).map { it.id })
        }
        revision++
        navigationRevision++
        nowPlaying = aboveNowPlaying
        failed = false
        publish()
    }

    /** The native Now Playing context tap is bound to the last shared browse projection. */
    fun openCurrentAlbum(acceptedRevision: Long) {
        publish()
        if (closed || rootUnavailable || acceptedRevision != state.value.revision || !state.value.currentAlbumAvailable) return
        bridge.currentAlbumCollection()?.let { album ->
            if (downloads?.currentQueueDownloadedOnly == true) openLibrary(NaviampVehicleLibraryReference(
                NaviampVehicleLibraryKind.DownloadedAlbumTracks, album.title, album.id))
            else openCollection(album)
        }
    }

    private fun openConnections() {
        if (connections == null || closed || rootUnavailable) return
        selection?.cancel()
        val existing = details.firstOrNull { it.connection }
        if (existing != null) dismissPages(details.dropWhile { it !== existing }.drop(1).map { it.id })
        else {
            details.forEach(::cancelDetail); details.clear()
            details += Detail("connections:${++detailSerial}", connection = true)
        }
        failed = false; nowPlaying = false; navigationRevision++; revision++; publish()
    }

    private fun openCollection(reference: NaviampVehicleCollectionReference) {
        selection?.cancel()
        val existing = details.firstOrNull { it.reference == reference }
        if (existing != null) {
            dismissPages(details.dropWhile { it !== existing }.drop(1).map { it.id })
            navigationRevision++
            nowPlaying = false
            failed = false
            publish()
            return
        }
        details.forEach(::cancelDetail)
        details.clear()
        val detail = Detail("collection:${++detailSerial}", reference = reference)
        details += detail
        navigationRevision++
        nowPlaying = false
        load(detail)
    }

    /** Completion is called exactly once for navigation, stale input, timeout, supersession and detach. */
    fun select(destination: NaviampVehicleBrowseDestination, id: String, revision: Long, completed: (Boolean) -> Unit) {
        publish()
        val tab = state.value.tabs.firstOrNull { it.destination == destination }
        if (closed || rootUnavailable || revision != state.value.revision || tab == null || tab.rows.none { it.id == id && it.enabled }) {
            completed(false)
            return
        }
        when (id) {
            Next, Previous -> {
                rootPages[destination] = rootPages.getValue(destination) + if (id == Next) 1 else -1
                this.revision++
                publish()
                completed(false)
            }
            Connections -> { openConnections(); completed(false) }
            NaviampVehicleConnectionController.Reconnect -> {
                openConnections(); connections?.select(id); completed(false)
            }
            Queue -> { openQueue(); completed(false) }
            Search -> {
                if (library?.available == true && searchLabels != null && limit >= 4) {
                    selection?.cancel()
                    details.forEach(::cancelDetail)
                    details.clear()
                    val detail = Detail("search:${++detailSerial}", libraryReference = NaviampVehicleLibraryReference(NaviampVehicleLibraryKind.Search, searchLabels.title))
                    details += detail
                    navigationRevision++
                    nowPlaying = false
                    this.revision++
                    publish()
                }
                completed(false)
            }
            Downloads -> {
                downloads?.let { openLibrary(NaviampVehicleLibraryReference(NaviampVehicleLibraryKind.Downloads, it.labels.downloads)) }
                completed(false)
            }
            Library -> { openLibrary(NaviampVehicleLibraryReference(NaviampVehicleLibraryKind.Root, checkNotNull(library).labels.library)); completed(false) }
            else -> {
                if (destination == NaviampVehicleBrowseDestination.Downloads) {
                    selection?.cancel()
                    details.forEach(::cancelDetail)
                    details.clear()
                    downloads?.categories()?.firstOrNull { it.id == id }?.navigation?.let { openLibrary(it) }
                    completed(false)
                    return
                }
                if (destination == NaviampVehicleBrowseDestination.Library) {
                    selection?.cancel()
                    details.forEach(::cancelDetail)
                    details.clear()
                    library?.categories()?.firstOrNull { it.id == id }?.navigation?.let { openLibrary(it) }
                    completed(false)
                    return
                }
                val reference = bridge.browseCollection(id)
                if (reference != null) {
                    openCollection(reference)
                    completed(false)
                } else {
                    val command = bridge.browsePlaybackCommand(id)
                    if (command == null) completed(false)
                    else play(completed = completed) { execute(command) }
                }
            }
        }
    }

    fun selectPage(pageId: String, id: String, revision: Long, completed: (Boolean) -> Unit) {
        publish()
        val detail = details.lastOrNull()?.takeIf { it.id == pageId }
        val page = state.value.pages.lastOrNull()
        if (closed || rootUnavailable || revision != state.value.revision || detail == null || page?.rows?.none { it.id == id && it.enabled } != false) {
            completed(false)
            return
        }
        if (detail.connection) {
            if (id == Next || id == Previous) {
                detail.page += if (id == Next) 1 else -1
                this.revision++; publish()
            } else connections?.select(id)
            completed(false)
            return
        }
        if (detail.libraryReference != null) {
            selectLibrary(detail, id, completed)
            return
        }
        when (id) {
            Next, Previous -> {
                detail.page += if (id == Next) 1 else -1
                this.revision++
                publish()
                completed(false)
            }
            Retry -> { load(detail); completed(false) }
            else -> if (detail.reference == null) {
                val snapshot = bridge.snapshot()
                val current = snapshot.current?.mediaId == id
                val command = bridge.queuePlaybackCommand(id)
                if (current && snapshot.state == NaviampExternalPlaybackState.Playing) { requestNowPlaying(); completed(true) }
                else if (command == null) completed(false)
                else play(mayResumeCurrent = current, completed = completed) { execute(command) }
            } else {
                val collection = detail.collection
                val index = if (id == Play || id == Shuffle) 0 else id.removePrefix(Track).toIntOrNull()
                if (collection == null || index == null || index !in collection.tracks.indices) completed(false)
                else play(completed = completed) { check(collection.play(index, shuffle = id == Shuffle)) }
            }
        }
    }

    // Reserve room for the root and system Now Playing template within a five-template stack.
    private fun trimDetails() {
        while (details.size > 3) cancelDetail(details.removeAt(0))
    }

    private fun projectLibrary(detail: Detail): NaviampVehicleBrowsePage {
        if (detail.libraryReference?.kind == NaviampVehicleLibraryKind.Search) {
            val status = when {
                detail.query.isBlank() -> searchLabels?.hint.orEmpty()
                detail.loading -> labels.loading
                else -> detail.searchStatus
            }
            val rows = buildList {
                if (status.isNotBlank()) add(NaviampVehicleBrowseRow(SearchStatus, status, "", enabled = false))
                detail.libraryPages[0]?.entries?.forEach { add(NaviampVehicleBrowseRow(it.id, it.title, it.subtitle, artwork = it.artwork?.let(artwork::describe))) }
            }
            return NaviampVehicleBrowsePage(detail.id, checkNotNull(detail.libraryReference).title, rows, status, search = true)
        }
        val page = detail.libraryPages[detail.page]
        val rows = if (limit < 3) emptyList() else buildList {
            if (detail.page > 0) add(NaviampVehicleBrowseRow(Previous, labels.previous, ""))
            if (detail.failed) add(NaviampVehicleBrowseRow(Retry, labels.retry, ""))
            page?.entries?.forEach { add(NaviampVehicleBrowseRow(it.id, it.title, it.subtitle, artwork = it.artwork?.let(artwork::describe))) }
            if (page?.next != null) add(NaviampVehicleBrowseRow(Next, labels.next, ""))
        }
        return NaviampVehicleBrowsePage(detail.id, checkNotNull(detail.libraryReference).title, rows, when {
            failed || detail.failed || rootUnavailable || limit < 3 -> labels.failed
            detail.loading -> labels.loading
            page?.unavailable == true -> if (detail.libraryReference?.kind?.isDownloaded() == true) downloads?.unavailableLabel.orEmpty() else library?.labels?.unavailable.orEmpty()
            detail.libraryReference?.kind == NaviampVehicleLibraryKind.SearchResults && detail.searchStatus.isNotBlank() -> detail.searchStatus
            page?.entries.isNullOrEmpty() -> labels.empty
            else -> page?.status.orEmpty()
        })
    }

    private fun openLibrary(reference: NaviampVehicleLibraryReference) {
        if (reference.kind.isDownloaded()) {
            if (downloads?.available != true || limit < 5) return
        } else if (library?.available != true || limit < 3) return
        selection?.cancel()
        val existing = details.firstOrNull { it.libraryReference == reference }
        if (existing != null) {
            dismissPages(details.dropWhile { it !== existing }.drop(1).map { it.id })
            navigationRevision++
            nowPlaying = false
            failed = false
            publish()
            return
        }
        val detail = Detail("library:${++detailSerial}", libraryReference = reference)
        detail.libraryRequests[0] = MediaPageRequest(limit = (limit - 2).coerceIn(1, 200))
        details += detail
        trimDetails()
        navigationRevision++
        nowPlaying = false
        loadLibrary(detail)
    }

    private fun selectLibrary(detail: Detail, id: String, completed: (Boolean) -> Unit) {
        val loaded = detail.libraryPages[detail.page]
        when (id) {
            Next, Previous -> {
                detail.load?.cancel()
                detail.loadSerial++
                if (id == Next) {
                    detail.libraryRequests[detail.page + 1] = loaded?.next ?: run { completed(false); return }
                    detail.page++
                } else detail.page--
                detail.failed = false
                if (detail.libraryPages[detail.page] == null) loadLibrary(detail)
                else { detail.loading = false; revision++; publish() }
                completed(false)
            }
            Retry -> { loadLibrary(detail); completed(false) }
            else -> {
                val entry = loaded?.entries?.firstOrNull { it.id == id }
                when {
                    entry?.refresh == true -> {
                        if (detail.libraryReference?.kind?.isDownloaded() == true) downloads?.invalidate()
                        cancelDetail(detail)
                        detail.libraryPages.clear()
                        detail.libraryRequests.clear()
                        detail.page = 0
                        detail.libraryRequests[0] = MediaPageRequest(limit = (limit - 2).coerceIn(1, 200))
                        loadLibrary(detail)
                        completed(false)
                    }
                    entry?.navigation != null -> { openLibrary(entry.navigation); completed(false) }
                    entry?.album != null -> {
                        selection?.cancel()
                        val album = Detail("collection:${++detailSerial}", reference = entry.album)
                        details += album
                        trimDetails()
                        navigationRevision++
                        nowPlaying = false
                        load(album)
                        completed(false)
                    }
                    loaded != null && entry != null -> play(completed = completed) { check(loaded.play(entry.id)) }
                    else -> completed(false)
                }
            }
        }
    }

    private fun loadLibrary(detail: Detail) {
        detail.load?.cancel()
        val serial = ++detail.loadSerial
        val pageIndex = detail.page
        val source = source
        detail.failed = false
        detail.loading = true
        failed = false
        revision++
        publish()
        detail.load = session.launch {
            try {
                val loaded = withTimeout(readinessTimeoutMillis) {
                    val reference = checkNotNull(detail.libraryReference)
                    if (reference.kind.isDownloaded()) checkNotNull(downloads).load(reference, detail.libraryRequests.getValue(pageIndex))
                    else checkNotNull(library).load(reference, detail.libraryRequests.getValue(pageIndex))
                }
                ensureActive()
                check(loaded.entries.none { entry -> detail.libraryPages.filterKeys { it != pageIndex }.values.any { old -> old.entries.any { it.id == entry.id } } })
                if (product.value.vehicleSourceKey() == source && detail in details && serial == detail.loadSerial) detail.libraryPages[pageIndex] = loaded
            } catch (_: CancellationException) {
                if (isActive && serial == detail.loadSerial) detail.failed = true
            } catch (_: Exception) {
                if (serial == detail.loadSerial) detail.failed = true
            } finally {
                if (!closed && detail in details && serial == detail.loadSerial) {
                    detail.loading = false
                    revision++
                    publish()
                }
            }
        }
    }

    /** Complete native search callbacks before cancellation; a late provider must not clear a newer query. */
    private fun cancelDetail(detail: Detail) {
        if (detail.connection) connections?.cancel()
        detail.loadSerial++
        detail.searchRevision++
        detail.searchTimeout?.cancel()
        detail.searchTimeout = null
        val complete = detail.searchComplete
        detail.searchComplete = null
        detail.submitPending = false
        complete?.invoke(NaviampVehicleSearchResults(revision, emptyList()))
        detail.load?.cancel()
    }

    private fun searchCategories(query: String): List<NaviampVehicleLibraryEntry> {
        val vocabulary = library?.labels ?: return emptyList()
        return listOf(NaviampVehicleLibraryKind.SearchTracks to vocabulary.tracks,
            NaviampVehicleLibraryKind.SearchAlbums to vocabulary.albums,
            NaviampVehicleLibraryKind.SearchArtists to vocabulary.artists).map { (kind, title) ->
            NaviampVehicleLibraryEntry("search-category:${kind.name}", title, query,
                navigation = NaviampVehicleLibraryReference(kind, title, query = query))
        }
    }

    fun updateSearch(pageId: String, text: String, completed: (NaviampVehicleSearchResults) -> Unit) {
        publish()
        val detail = details.firstOrNull { it.id == pageId && it.libraryReference?.kind == NaviampVehicleLibraryKind.Search }
        if (closed || rootUnavailable || searchLabels == null || library?.available != true || detail == null || limit < 4) {
            completed(NaviampVehicleSearchResults(state.value.revision, emptyList()))
            return
        }
        dismissPages(details.dropWhile { it !== detail }.drop(1).map { it.id })
        selection?.cancel()
        cancelDetail(detail)
        detail.query = normalizedSearchQuery(text).orEmpty()
        detail.searchStatus = ""
        detail.libraryPages.clear()
        detail.loading = detail.query.isNotEmpty()
        revision++
        publish()
        if (detail.query.isEmpty()) {
            completed(NaviampVehicleSearchResults(detail.searchRevision, projectLibrary(detail).rows))
            return
        }
        detail.searchComplete = completed
        val serial = detail.loadSerial
        val source = source
        val query = detail.query
        val budget = minOf(limit, 20)
        val categories = searchCategories(query)
        val request = MediaPageRequest(limit = ((budget - 4) / 3).coerceAtLeast(1))
        detail.load = session.launch {
            try {
                delay(SearchDebounceMillis)
                val results = withTimeoutOrNull(readinessTimeoutMillis) {
                    supervisorScope {
                        categories.map { category -> async {
                            try { checkNotNull(library).load(checkNotNull(category.navigation), request) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { null }
                        } }.awaitAll()
                    }
                } ?: List(categories.size) { null }
                ensureActive()
                finishSearch(detail, serial, source, results)
            } finally {
                if (serial == detail.loadSerial) {
                    val callback = detail.searchComplete
                    detail.searchComplete = null
                    callback?.invoke(NaviampVehicleSearchResults(revision, emptyList()))
                }
            }
        }
        detail.searchTimeout = session.launch {
            delay(SearchDebounceMillis + readinessTimeoutMillis)
            if (serial == detail.loadSerial && detail.searchComplete != null) {
                detail.searchTimeout = null
                val expired = ++detail.loadSerial
                detail.load?.cancel()
                finishSearch(detail, expired, source, List(categories.size) { null })
            }
        }
    }

    private fun finishSearch(detail: Detail, serial: Long, source: List<Any?>, results: List<NaviampVehicleLibraryPage?>) {
        if (closed || product.value.vehicleSourceKey() != source || detail !in details || serial != detail.loadSerial) return
        detail.searchTimeout?.cancel()
        detail.searchTimeout = null
        val pages = results.filterNotNull()
        val failed = results.any { it == null || it.unavailable }
        val categories = searchCategories(detail.query)
        val previews = pages.flatMap { it.entries }.take((minOf(limit, 20) - 4).coerceAtLeast(0))
        detail.searchStatus = when {
            failed -> labels.failed
            pages.all { it.entries.isEmpty() } -> searchLabels?.empty.orEmpty()
            else -> ""
        }
        detail.libraryPages[0] = NaviampVehicleLibraryPage(categories + previews, start = { id -> pages.any { it.play(id) } })
        detail.loading = false
        detail.searchRevision++
        revision++
        publish()
        val callback = detail.searchComplete
        detail.searchComplete = null
        callback?.invoke(NaviampVehicleSearchResults(detail.searchRevision, projectLibrary(detail).rows))
        if (detail.submitPending) submitSearch(detail.id)
    }

    /** Query revisions remain valid across unrelated Now Playing metadata changes. */
    fun selectSearch(pageId: String, id: String, acceptedRevision: Long, completed: (Boolean) -> Unit) {
        publish()
        val detail = details.lastOrNull()?.takeIf { it.id == pageId && it.libraryReference?.kind == NaviampVehicleLibraryKind.Search }
        if (closed || rootUnavailable || library?.available != true || detail == null ||
            detail.searchRevision != acceptedRevision || projectLibrary(detail).rows.none { it.id == id && it.enabled }) {
            completed(false)
            return
        }
        selectLibrary(detail, id, completed)
    }

    fun submitSearch(pageId: String) {
        publish()
        val search = details.lastOrNull()?.takeIf { it.id == pageId && it.libraryReference?.kind == NaviampVehicleLibraryKind.Search } ?: return
        if (closed || rootUnavailable || library?.available != true || search.query.isBlank()) return
        if (search.loading) { search.submitPending = true; return }
        search.submitPending = false
        val page = search.libraryPages[0] ?: return
        val result = Detail("search-results:${++detailSerial}", libraryReference = NaviampVehicleLibraryReference(
            NaviampVehicleLibraryKind.SearchResults, checkNotNull(searchLabels).title, query = search.query))
        result.libraryPages[0] = page
        result.searchStatus = search.searchStatus
        details += result
        trimDetails()
        navigationRevision++
        nowPlaying = false
        revision++
        publish()
    }

    private fun load(detail: Detail) {
        detail.load?.cancel()
        detail.loading = true
        detail.failed = false
        detail.collection = null
        failed = false
        revision++
        publish()
        val source = source
        detail.load = session.launch {
            try {
                val collection = withTimeout(readinessTimeoutMillis) { collections.load(checkNotNull(detail.reference)) }
                ensureActive()
                if (product.value.vehicleSourceKey() == source && detail in details) detail.collection = collection
            } catch (_: CancellationException) {
                if (isActive) detail.failed = true // A load timeout is recoverable on this page.
            } catch (_: Exception) {
                detail.failed = true
            } finally {
                detail.loading = false
                if (!closed && detail in details) { revision++; publish() }
            }
        }
    }

    private fun requestNowPlaying() {
        details.removeAll { it.aboveNowPlaying }
        nowPlaying = true
        navigationRevision++
        publish()
    }

    private fun play(mayResumeCurrent: Boolean = false, completed: (Boolean) -> Unit, action: suspend () -> Unit) {
        if (product.value.shell.connectionSettings.connection.let { it.isConnecting || it.restoringConnection }) {
            completed(false)
            return
        }
        selection?.cancel()
        val serial = ++selectionSerial
        val source = source
        val before = product.value.playbackSelectionGeneration
        failed = false
        publish()
        selection = session.launch(start = CoroutineStart.UNDISPATCHED) {
            var ready = false
            try {
                ready = withTimeoutOrNull(readinessTimeoutMillis) {
                    coroutineScope {
                        val invalidation = launch(start = CoroutineStart.UNDISPATCHED) {
                            product.first { it.vehicleSourceKey() != source }
                            this@coroutineScope.cancel()
                        }
                        try {
                            action()
                            val expected = product.value.playbackSelectionGeneration
                            if (expected != before + 1L && !(mayResumeCurrent && expected == before)) return@coroutineScope false
                            val result = product.first {
                                it.playbackSelectionGeneration != expected ||
                                    it.failedPlaybackSelectionGeneration == expected ||
                                    (it.readyPlaybackSelectionGeneration == expected && it.shell.nowPlaying?.isPlaying == true)
                            }
                            result.playbackSelectionGeneration == expected &&
                                result.failedPlaybackSelectionGeneration != expected &&
                                result.readyPlaybackSelectionGeneration == expected && product.value.vehicleSourceKey() == source
                        } finally { invalidation.cancel() }
                    }
                } == true
                if (!ready) failed = true
            } catch (_: CancellationException) {
                // Source change, supersession or detach owns subsequent presentation.
            } catch (_: Exception) {
                failed = true
            } finally {
                if (!closed && serial == selectionSerial) {
                    if (ready) requestNowPlaying() else publish()
                }
                completed(ready)
            }
        }
    }

    fun selectPlaybackAction(action: app.naviamp.ui.NaviampExternalPlaybackAction, revision: Long) {
        if (!closed && !rootUnavailable) playbackControls?.select(action, revision)
    }

    fun dismissPlaybackAlert(id: Long) { playbackControls?.dismissAlert(id) }

    fun close() {
        if (closed) return
        closed = true
        details.forEach(::cancelDetail)
        playbackControls?.close()
        connections?.close()
        artwork.close()
        session.cancel()
    }

    private companion object {
        const val Downloads = "naviamp:vehicle:downloads"
        const val Search = "naviamp:vehicle:search"
        const val SearchStatus = "naviamp:vehicle:search:status"
        const val Library = "naviamp:vehicle:library"
        const val Next = "naviamp:vehicle:next"
        const val Previous = "naviamp:vehicle:previous"
        const val Queue = "naviamp:vehicle:queue"
        const val Connections = "naviamp:vehicle:connections"
        const val Retry = "naviamp:vehicle:retry"
        const val Play = "naviamp:vehicle:play"
        const val Shuffle = "naviamp:vehicle:shuffle"
        const val Track = "naviamp:vehicle:track:"
    }
}

suspend fun NaviampCore.vehicleBrowseController(scope: CoroutineScope, maximumItems: Int, maximumTabs: Int, supportsSearch: Boolean = false): NaviampVehicleBrowseController {
    val bridge = externalPlaybackBridge()
    val libraryLabels = loadNaviampVehicleLibraryLabels()
    val controls = NaviampVehiclePlaybackController(scope, state, bridge, { execute(it) }, loadNaviampVehiclePlaybackLabels())
    return NaviampVehicleBrowseController(scope, state, bridge, { execute(it) },
        loadNaviampVehicleBrowseLabels(), maximumItems, maximumTabs = maximumTabs, collections = vehicleCollections,
        connections = NaviampVehicleConnectionController(scope, state, app.naviamp.ui.loadNaviampVehicleConnectionLabels(),
            { id, reconnect -> (execute(NaviampCoreCommand.Connection.SelectSavedSource(id, reconnect)) as? NaviampCoreCommandResult.SavedSourceSelected)?.selected == true }),
        artworkSource = vehicleArtwork,
        playbackControls = controls, library = vehicleLibrary(libraryLabels),
        downloads = vehicleDownloads(libraryLabels, loadNaviampVehicleDownloadsLabels()),
        searchLabels = if (supportsSearch) loadNaviampVehicleSearchLabels() else null)
}
