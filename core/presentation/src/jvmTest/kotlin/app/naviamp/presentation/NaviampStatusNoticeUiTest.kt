package app.naviamp.presentation

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.naviamp.app.NaviampConnectionPhase
import app.naviamp.app.NaviampConnectionRuntimeState
import app.naviamp.domain.settings.InterfaceLanguage
import app.naviamp.ui.NaviampApplicationSurface
import app.naviamp.ui.NaviampArtistAlbumCommand
import app.naviamp.ui.NaviampMediaItemActionRequest
import app.naviamp.ui.NaviampMediaItemCommand
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedTrackRowUi
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampStatusNoticeUiTest {
    private val politeLiveRegion = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    @Test
    fun coreStatusMessagesAreShownAnnouncedAndDismissedInTheSelectedLanguage() = withEnglishDefault {
        runDesktopComposeUiTest(1000, 740) {
            val (scope, core) = connectedCore()
            try {
                setContent { NaviampCoreApp(core) }
                waitForIdle()

                runBlocking { core.execute(NaviampCoreCommand.Settings.RefreshLibrary) }
                waitForIdle()
                onNode(hasText("Library refreshed.") and hasAnyAncestor(hasTestTag("status-notice")))
                    .assertIsDisplayed()
                onNodeWithTag("status-notice").assert(politeLiveRegion)

                runBlocking { core.execute(staleTrackAction()) }
                waitForIdle()
                // A newer status replaces the older one instead of stacking.
                onNodeWithText("That track is no longer available.").assertIsDisplayed()
                onNodeWithText("Library refreshed.").assertDoesNotExist()

                onNodeWithTag("status-notice-dismiss").performClick()
                waitForIdle()
                onNodeWithTag("status-notice").assertDoesNotExist()
                runOnIdle { assertEquals(null, core.state.value.overlays.status) }

                core.dispatch(NaviampCoreCommand.Settings.ChangeInterface(
                    core.state.value.shell.general.interfaceSettings.copy(language = InterfaceLanguage.German)))
                waitForIdle()
                runBlocking { core.execute(NaviampCoreCommand.Settings.RefreshLibrary) }
                waitForIdle()
                onNodeWithText("Bibliothek aktualisiert.").assertIsDisplayed()
                onNodeWithTag("status-notice").assert(politeLiveRegion)
            } finally { scope.cancel() }
        }
    }

    @Test
    fun theTelevisionShellShowsTheSameStatusNotice() = withEnglishDefault {
        runDesktopComposeUiTest(1280, 720) {
            val (scope, core) = connectedCore()
            try {
                setContent { NaviampCoreApp(core, applicationSurface = NaviampApplicationSurface.Television) }
                waitForIdle()
                runBlocking { core.execute(staleTrackAction()) }
                waitForIdle()
                onNodeWithText("That track is no longer available.").assertIsDisplayed()
                onNodeWithTag("status-notice").assert(politeLiveRegion)
            } finally { scope.cancel() }
        }
    }

    @Test
    fun addingAnAlbumToTheQueueConfirmsItInTheNoticeStack() = withEnglishDefault {
        runDesktopComposeUiTest(1000, 740) {
            val provider = FakeCoreMediaProvider()
            val (scope, core) = connectedCore(provider)
            try {
                setContent { NaviampCoreApp(core) }
                waitForIdle()
                val album = SharedMediaItemUi(provider.album.id.value, provider.album.title, provider.artist.name)
                core.actions.shell.mediaActions.onMediaItemAction(
                    NaviampMediaItemActionRequest(album, NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.Select)))
                waitForIdle()
                onNodeWithTag("status-notice").assertDoesNotExist()

                core.actions.shell.mediaActions.onMediaItemAction(
                    NaviampMediaItemActionRequest(album, NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.AddToQueue)))
                waitForIdle()
                onNode(hasText("Added to the queue.") and hasAnyAncestor(hasTestTag("status-notice"))).assertIsDisplayed()
            } finally { scope.cancel() }
        }
    }

    private fun connectedCore(provider: FakeCoreMediaProvider = FakeCoreMediaProvider()): Pair<CoroutineScope, NaviampCore> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        return scope to NaviampCore.create(scope, fakeCoreServices(provider), initialState = NaviampCoreInitialState(
            connection = NaviampConnectionRuntimeState(phase = NaviampConnectionPhase.Connected, sourceId = provider.id.value),
        ))
    }

    /** A row action for a track that is no longer in the library list it came from. */
    private fun staleTrackAction() = NaviampCoreCommand.Library.TrackAction(
        SharedTrackRowActionRequest(SharedTrackRowUi("missing-track", "Gone", "Nobody"), SharedTrackRowAction.PlayNext),
    )

    private fun withEnglishDefault(block: () -> Unit) {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try { block() } finally { Locale.setDefault(original) }
    }
}
