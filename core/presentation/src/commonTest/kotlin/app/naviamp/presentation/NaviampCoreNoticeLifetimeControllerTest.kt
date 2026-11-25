package app.naviamp.presentation

import app.naviamp.domain.radio.RadioBuildDiagnostics
import app.naviamp.domain.radio.RadioBuildOutcome
import app.naviamp.ui.NaviampNoticeBaseTimeoutMillis
import app.naviamp.ui.NaviampSomethingNewNotice
import app.naviamp.ui.NaviampTimedNotice
import app.naviamp.ui.NaviampTimedNoticeAction
import app.naviamp.ui.NaviampTimedNoticeRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreNoticeLifetimeControllerTest {
    @Test
    fun aSomethingNewNoticeDismissesItselfAfterTheBaseTimeout() = runTest {
        val fixture = fixture()
        fixture.show(NaviampSomethingNewNotice.NothingUnplayed)

        advanceTimeBy(NaviampNoticeBaseTimeoutMillis - 1)
        runCurrent()
        assertEquals(NaviampSomethingNewNotice.NothingUnplayed, fixture.somethingNew())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(null, fixture.somethingNew())
    }

    @Test
    fun theAccessibilityRecommendedTimeoutExtendsTheWindow() = runTest {
        val fixture = fixture()
        fixture.show(NaviampSomethingNewNotice.NothingUnplayed)
        fixture.lifetime(NaviampTimedNotice.SomethingNew, NaviampTimedNoticeAction.Presented, 30_000)

        advanceTimeBy(29_999)
        runCurrent()
        assertEquals(NaviampSomethingNewNotice.NothingUnplayed, fixture.somethingNew())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(null, fixture.somethingNew())
    }

    @Test
    fun aFocusedNoticeStaysUntilFocusLeaves() = runTest {
        val fixture = fixture()
        fixture.show(NaviampSomethingNewNotice.NothingUnplayed)
        fixture.lifetime(NaviampTimedNotice.SomethingNew, NaviampTimedNoticeAction.FocusGained)

        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(NaviampSomethingNewNotice.NothingUnplayed, fixture.somethingNew())
        fixture.lifetime(NaviampTimedNotice.SomethingNew, NaviampTimedNoticeAction.FocusLost)
        advanceTimeBy(NaviampNoticeBaseTimeoutMillis + 1)
        runCurrent()
        assertEquals(null, fixture.somethingNew())
    }

    @Test
    fun offlineNoticesClearAsSoonAsTheConnectionIsBack() = runTest {
        val fixture = fixture(offline = true)
        fixture.store.update { it.copy(overlays = it.overlays.copy(
            somethingNewNotice = NaviampSomethingNewNotice.PlayingFromDownloads,
            radioNotice = RadioBuildDiagnostics(RadioBuildOutcome.Offline),
        )) }
        runCurrent()

        fixture.setOffline(false)
        runCurrent()

        assertEquals(null, fixture.somethingNew())
        assertEquals(null, fixture.store.state.value.overlays.radioNotice)
    }

    @Test
    fun onlyTheOfflineRadioNoticeIsTimedAndOnlineNoticesSurviveReconnecting() = runTest {
        val fixture = fixture()
        fixture.store.update { it.copy(overlays = it.overlays.copy(
            somethingNewNotice = NaviampSomethingNewNotice.NothingUnplayed,
            radioNotice = RadioBuildDiagnostics(RadioBuildOutcome.Empty),
        )) }
        runCurrent()
        fixture.setOffline(false)
        advanceTimeBy(NaviampNoticeBaseTimeoutMillis * 10)
        runCurrent()

        assertEquals(RadioBuildOutcome.Empty, fixture.store.state.value.overlays.radioNotice?.outcome)
        fixture.store.update { it.copy(overlays = it.overlays.copy(radioNotice = RadioBuildDiagnostics(RadioBuildOutcome.Offline))) }
        runCurrent()
        advanceTimeBy(NaviampNoticeBaseTimeoutMillis + 1)
        runCurrent()
        assertEquals(null, fixture.store.state.value.overlays.radioNotice)
    }

    @Test
    fun aReplacedNoticeGetsItsOwnFullWindow() = runTest {
        val fixture = fixture()
        fixture.show(NaviampSomethingNewNotice.NothingUnplayed)
        advanceTimeBy(NaviampNoticeBaseTimeoutMillis - 1_000)
        fixture.show(NaviampSomethingNewNotice.Failed)

        advanceTimeBy(NaviampNoticeBaseTimeoutMillis - 1)
        runCurrent()
        assertEquals(NaviampSomethingNewNotice.Failed, fixture.somethingNew())
    }

    private class Fixture(val store: NaviampCoreStateStore, val controller: NaviampCoreNoticeLifetimeController, val scope: TestScope) {
        fun somethingNew() = store.state.value.overlays.somethingNewNotice
        fun show(notice: NaviampSomethingNewNotice) {
            store.update { it.copy(overlays = it.overlays.copy(somethingNewNotice = notice)) }
            scope.runCurrent()
        }
        fun lifetime(notice: NaviampTimedNotice, action: NaviampTimedNoticeAction, timeoutMillis: Long? = null) {
            controller.dispatch(NaviampCoreCommand.Home.NoticeLifetime(NaviampTimedNoticeRequest(notice, action, timeoutMillis)))
        }
        fun setOffline(offline: Boolean) = store.updateShell { shell ->
            shell.copy(connectionSettings = shell.connectionSettings.copy(
                connection = shell.connectionSettings.connection.copy(offline = offline)))
        }
    }

    private fun TestScope.fixture(offline: Boolean = false): Fixture {
        val store = NaviampCoreStateStore()
        val fixture = Fixture(store, NaviampCoreNoticeLifetimeController(backgroundScope, store), this)
        fixture.setOffline(offline)
        fixture.controller.start()
        runCurrent()
        return fixture
    }
}
