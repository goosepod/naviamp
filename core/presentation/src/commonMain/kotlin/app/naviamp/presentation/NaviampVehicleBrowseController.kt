package app.naviamp.presentation

import app.naviamp.ui.NaviampVehicleBrowseLabels
import app.naviamp.ui.loadNaviampVehicleBrowseLabels
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class NaviampVehicleBrowseDestination { Home, Playlists, Radio }

data class NaviampVehicleBrowseRow(val id: String, val title: String, val subtitle: String)
data class NaviampVehicleBrowseTab(
    val destination: NaviampVehicleBrowseDestination,
    val title: String,
    val rows: List<NaviampVehicleBrowseRow>,
    val status: String,
)
data class NaviampVehicleBrowseState(val revision: Long, val tabs: List<NaviampVehicleBrowseTab>)

/**
 * Scene-confined shared vehicle presentation. Android Auto's bridge remains the catalog and command
 * owner. Native hosts supply their item limit, render this projection and complete native handlers.
 * Closing a scene cancels its requests/waiters, never the application runtime or ready audio.
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
) {
    private val session = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    // Reserve two slots for previous/next navigation. CarPlay supplies its runtime capability.
    private val limit = maximumItems.coerceAtLeast(0)
    private val pages = mutableMapOf<NaviampVehicleBrowseDestination, Int>()
    private var revision = 0L
    private var fingerprint: Any? = null
    private var failed = false
    private var closed = false
    private var selection: Job? = null
    private var selectionSerial = 0L
    private val mutableState = MutableStateFlow(project())
    val state: StateFlow<NaviampVehicleBrowseState> = mutableState.asStateFlow()

    init {
        session.launch {
            // Progress/visualizer observations must not rebuild the browse catalog.
            product.map { value -> listOf(value.sourceGeneration, value.shell.connectionSettings,
                value.shell.home.content, value.shell.home.refreshing, value.shell.playlists, value.shell.radio) }
                .distinctUntilChanged().collect { mutableState.value = project() }
        }
    }

    private fun sourceKey(): List<Any?> = product.value.let {
        listOf(it.sourceGeneration, it.shell.connectionSettings.currentSourceId,
            it.shell.connectionSettings.accountSelectionGeneration,
            it.shell.connectionSettings.connection.isConnecting)
    }

    private fun catalog() = mapOf(
        NaviampVehicleBrowseDestination.Home to (
            bridge.browseChildren(NaviampExternalRecentTracksId) + bridge.browseChildren(NaviampExternalRecentAlbumsId)),
        NaviampVehicleBrowseDestination.Playlists to bridge.browseChildren(NaviampExternalPlaylistsId),
        NaviampVehicleBrowseDestination.Radio to bridge.browseChildren(NaviampExternalRadioId),
    )

    private fun project(): NaviampVehicleBrowseState {
        val catalog = catalog()
        val nextFingerprint = sourceKey() to catalog
        if (fingerprint != nextFingerprint) {
            fingerprint = nextFingerprint
            revision++
            pages.clear()
            failed = false
        }
        val shell = product.value.shell
        val connection = shell.connectionSettings.connection
        return NaviampVehicleBrowseState(revision, NaviampVehicleBrowseDestination.entries.take(maximumTabs.coerceAtLeast(0)).map { destination ->
            val all = catalog.getValue(destination)
            val pageSize = (limit - 2).coerceAtLeast(1)
            val page = (pages[destination] ?: 0).coerceIn(0, ((all.size - 1).coerceAtLeast(0) / pageSize))
            pages[destination] = page
            val refreshing = when (destination) {
                NaviampVehicleBrowseDestination.Home -> shell.home.refreshing
                NaviampVehicleBrowseDestination.Playlists -> shell.playlists.refreshing
                NaviampVehicleBrowseDestination.Radio -> shell.radio.refreshing
            }
            val status = when {
                failed || limit < 3 -> labels.failed
                connection.isConnecting || connection.restoringConnection || refreshing -> labels.loading
                connection.statusIsError -> labels.failed
                shell.connectionSettings.currentSourceId == null -> labels.disconnected
                all.isEmpty() -> labels.empty
                else -> ""
            }
            NaviampVehicleBrowseTab(destination, when (destination) {
                NaviampVehicleBrowseDestination.Home -> labels.home
                NaviampVehicleBrowseDestination.Playlists -> labels.playlists
                NaviampVehicleBrowseDestination.Radio -> labels.radio
            }, buildList {
                if (limit < 3) return@buildList
                if (page > 0) add(NaviampVehicleBrowseRow(Previous, labels.previous, ""))
                addAll(all.drop(page * pageSize).take(pageSize).map { NaviampVehicleBrowseRow(it.mediaId, it.title, it.subtitle) })
                if ((page + 1) * pageSize < all.size) add(NaviampVehicleBrowseRow(Next, labels.next, ""))
            }, status)
        })
    }

    /** Completion is called exactly once, including stale input, timeout, supersession and detach. */
    fun select(destination: NaviampVehicleBrowseDestination, id: String, revision: Long, completed: (Boolean) -> Unit) {
        mutableState.value = project()
        val tab = state.value.tabs.firstOrNull { it.destination == destination }
        if (closed || revision != state.value.revision || tab == null || tab.rows.none { it.id == id }) {
            completed(false)
            return
        }
        if (id == Next || id == Previous) {
            pages[destination] = pages.getValue(destination) + if (id == Next) 1 else -1
            this.revision++
            mutableState.value = project()
            completed(false)
            return
        }
        val command = bridge.browsePlaybackCommand(id)
        if (command == null || product.value.shell.connectionSettings.connection.isConnecting) {
            completed(false)
            return
        }
        selection?.cancel()
        val serial = ++selectionSerial
        val source = sourceKey()
        val before = product.value.playbackSelectionGeneration
        failed = false
        mutableState.value = project()
        selection = session.launch(start = CoroutineStart.UNDISPATCHED) {
            var ready = false
            try {
                ready = withTimeoutOrNull(readinessTimeoutMillis) {
                    // An account transition invalidates a request even while a provider call suspends.
                    coroutineScope {
                        val invalidation = launch(start = CoroutineStart.UNDISPATCHED) {
                            product.first { sourceKey() != source }
                            this@coroutineScope.cancel()
                        }
                        try {
                            execute(command)
                            val expected = product.value.playbackSelectionGeneration
                            if (expected != before + 1L) return@coroutineScope false
                            val result = product.first {
                                it.playbackSelectionGeneration != expected ||
                                    it.failedPlaybackSelectionGeneration == expected ||
                                    (it.readyPlaybackSelectionGeneration == expected && it.shell.nowPlaying?.isPlaying == true)
                            }
                            result.playbackSelectionGeneration == expected &&
                                result.readyPlaybackSelectionGeneration == expected && sourceKey() == source
                        } finally { invalidation.cancel() }
                    }
                } == true
                if (!ready) failed = true
            } catch (_: CancellationException) {
                // Source change, a newer selection or scene detach owns the next presentation.
            } catch (_: Exception) {
                failed = true
            } finally {
                if (!closed && serial == selectionSerial) mutableState.value = project()
                completed(ready)
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        session.cancel()
    }

    private companion object {
        const val Next = "naviamp:vehicle:next"
        const val Previous = "naviamp:vehicle:previous"
    }
}

suspend fun NaviampCore.vehicleBrowseController(scope: CoroutineScope, maximumItems: Int, maximumTabs: Int): NaviampVehicleBrowseController =
    NaviampVehicleBrowseController(scope, state, externalPlaybackBridge(), { execute(it) },
        loadNaviampVehicleBrowseLabels(), maximumItems, maximumTabs = maximumTabs)
