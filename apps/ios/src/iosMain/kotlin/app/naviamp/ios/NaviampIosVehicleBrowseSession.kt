package app.naviamp.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import app.naviamp.app.NaviampPresentationSessionOwner
import app.naviamp.presentation.NaviampVehicleBrowseRow
import app.naviamp.presentation.NaviampVehicleBrowseController
import app.naviamp.presentation.NaviampVehicleBrowseDestination
import app.naviamp.ui.NaviampExternalPlaybackAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Objective-C/Swift ABI projections. All browsing and selection behavior belongs to Core. */
class NaviampIosVehicleBrowseRow(
    val id: String, val title: String, val subtitle: String, val enabled: Boolean,
    val artworkId: String? = null, val artworkCircular: Boolean = false, val artworkPlaceholder: String? = null,
)
private fun NaviampVehicleBrowseRow.toIosRow() = NaviampIosVehicleBrowseRow(id, title, subtitle, enabled,
    artwork?.id, artwork?.circular == true, artwork?.placeholder)
class NaviampIosVehicleBrowseTab(
    val id: String,
    val title: String,
    val rows: List<NaviampIosVehicleBrowseRow>,
    val status: String,
)
class NaviampIosVehicleSearchResults(val revision: Long, val rows: List<NaviampIosVehicleBrowseRow>)
class NaviampIosVehicleBrowsePage(
    val id: String,
    val title: String,
    val rows: List<NaviampIosVehicleBrowseRow>,
    val status: String,
    val search: Boolean,
)
class NaviampIosVehiclePlaybackButton(val action: String, val title: String, val selected: Boolean, val enabled: Boolean)
class NaviampIosVehiclePlaybackAlert(val id: Long, val title: String, val dismiss: String)
class NaviampIosVehiclePlaybackState(
    val revision: Long, val buttons: List<NaviampIosVehiclePlaybackButton>, val alert: NaviampIosVehiclePlaybackAlert?,
)
class NaviampIosVehicleBrowseState(
    val revision: Long,
    val tabs: List<NaviampIosVehicleBrowseTab>,
    val pages: List<NaviampIosVehicleBrowsePage>,
    val queueAvailable: Boolean,
    val navigationRevision: Long,
    val nowPlaying: Boolean,
    val presentationAvailable: Boolean,
    val playback: NaviampIosVehiclePlaybackState,
    val currentAlbumAvailable: Boolean,
    val nowPlayingBeforePageId: String?,
)

/** Binds Swift callbacks and native template lifetimes to the application's main-dispatcher session. */
class NaviampIosVehicleBrowseSession internal constructor(
    scope: CoroutineScope,
    create: suspend () -> NaviampVehicleBrowseController,
    changed: (NaviampIosVehicleBrowseState) -> Unit,
) {
    private val owner = NaviampPresentationSessionOwner(create, NaviampVehicleBrowseController::close)
    private val controller: NaviampVehicleBrowseController? get() = owner.current
    private val observation: Job = scope.launch {
        owner.withPresentation { shared -> shared.state.collect { state ->
            changed(NaviampIosVehicleBrowseState(state.revision, state.tabs.map { tab ->
                NaviampIosVehicleBrowseTab(tab.destination.name, tab.title,
                    tab.rows.map { it.toIosRow() }, tab.status)
            }, state.pages.map { page ->
                NaviampIosVehicleBrowsePage(page.id, page.title,
                    page.rows.map { it.toIosRow() }, page.status, page.search)
            }, state.queueAvailable, state.navigationRevision, state.nowPlaying, state.presentationAvailable,
                NaviampIosVehiclePlaybackState(state.playback.revision, state.playback.buttons.map {
                    NaviampIosVehiclePlaybackButton(it.action.name, it.title, it.selected, it.enabled)
                }, state.playback.alert?.let { NaviampIosVehiclePlaybackAlert(it.id, it.title, it.dismiss) }), state.currentAlbumAvailable, state.nowPlayingBeforePageId))
        } }
    }

    /** Foundation byte transport only; Core owns request lifetime, authentication and caching. */
    @OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
    fun requestArtwork(id: String, completed: (NSData?) -> Unit) {
        val shared = controller
        if (shared == null) completed(null)
        else shared.requestArtwork(id) { bytes ->
            completed(bytes?.takeIf { it.isNotEmpty() }?.usePinned {
                NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong())
            })
        }
    }

    fun select(destination: String, id: String, revision: Long, completed: (Boolean) -> Unit) {
        val shared = controller
        val nativeDestination = NaviampVehicleBrowseDestination.entries.firstOrNull { it.name == destination }
        if (shared == null || nativeDestination == null) completed(false)
        else shared.select(nativeDestination, id, revision, completed)
    }

    fun selectPage(pageId: String, id: String, revision: Long, completed: (Boolean) -> Unit) {
        val shared = controller
        if (shared == null) completed(false)
        else shared.selectPage(pageId, id, revision, completed)
    }

    fun updateSearch(pageId: String, text: String, completed: (NaviampIosVehicleSearchResults) -> Unit) {
        val shared = controller
        if (shared == null) completed(NaviampIosVehicleSearchResults(-1, emptyList()))
        else shared.updateSearch(pageId, text) { result ->
            completed(NaviampIosVehicleSearchResults(result.revision,
                result.rows.map { it.toIosRow() }))
        }
    }
    fun selectSearch(pageId: String, id: String, revision: Long, completed: (Boolean) -> Unit) {
        val shared = controller
        if (shared == null) completed(false) else shared.selectSearch(pageId, id, revision, completed)
    }
    fun submitSearch(pageId: String) { controller?.submitSearch(pageId) }

    fun selectPlaybackAction(action: String, revision: Long) {
        val sharedAction = NaviampExternalPlaybackAction.entries.firstOrNull { it.name == action } ?: return
        controller?.selectPlaybackAction(sharedAction, revision)
    }
    fun dismissPlaybackAlert(id: Long) { controller?.dismissPlaybackAlert(id) }

    fun openCurrentAlbum(revision: Long) { controller?.openCurrentAlbum(revision) }
    fun openQueue() { controller?.openQueue() }
    fun openUpNext() { controller?.openUpNext() }
    fun dismissPages(ids: List<String>) { controller?.dismissPages(ids) }
    fun rootPresentationFailed() { controller?.rootPresentationFailed() }
    fun presentationFailed(pageId: String?) { controller?.presentationFailed(pageId) }

    fun close() {
        observation.cancel()
        owner.close()
    }
}
