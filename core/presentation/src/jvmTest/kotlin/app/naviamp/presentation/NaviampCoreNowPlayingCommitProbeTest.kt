package app.naviamp.presentation

import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.CompositionObserverHandle
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.observe
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.naviamp.ui.NaviampAppShellUiState
import app.naviamp.ui.NaviampConnectionSettingsUi
import app.naviamp.ui.NaviampShellChromeUi
import app.naviamp.ui.NaviampShellConnectionUi
import app.naviamp.ui.NaviampSharedAppShell
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.SharedRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Counts recompose scopes that run in each frame of the full shared shell when only the Now
 * Playing open flag changes, which is what committing an open or close command does.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeRuntimeApi::class)
class NaviampCoreNowPlayingCommitProbeTest {
    private class ScopeCounter : CompositionObserver, CompositionRegistrationObserver {
        var entered = 0
        private val handles = mutableListOf<CompositionObserverHandle>()
        override fun onCompositionRegistered(composition: ObservableComposition) {
            handles += composition.setObserver(this)
        }
        override fun onCompositionUnregistered(composition: ObservableComposition) = Unit
        override fun onBeginComposition(composition: ObservableComposition) = Unit
        var recording = false
        val names = mutableMapOf<String, Int>()
        override fun onScopeEnter(scope: RecomposeScope) {
            entered++
            if (recording) {
                val block = generateSequence<Class<*>>(scope.javaClass) { it.superclass }.flatMap { it.declaredFields.asSequence() }
                    .firstOrNull { kotlin.Function::class.java.isAssignableFrom(it.type) }
                    ?.apply { isAccessible = true }?.get(scope)
                val name = block?.javaClass?.name ?: "none"
                names[name] = (names[name] ?: 0) + 1
            }
        }
        override fun onReadInScope(scope: RecomposeScope, value: Any) = Unit
        override fun onScopeExit(scope: RecomposeScope) = Unit
        override fun onEndComposition(composition: ObservableComposition) = Unit
        override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) = Unit
        override fun onScopeDisposed(scope: RecomposeScope) = Unit
    }

    private fun runningRecomposer(): Recomposer {
        val info = Recomposer.runningRecomposers.value.single()
        val outer = info.javaClass.getDeclaredField("this\$0").apply { isAccessible = true }
        return outer.get(info) as Recomposer
    }

    private fun ComposeUiTest.scopesPerFrame(counter: ScopeCounter, frames: Int, action: () -> Unit): List<Int> {
        mainClock.autoAdvance = false
        var last = counter.entered
        action()
        val result = List(frames) {
            mainClock.advanceTimeByFrame()
            (counter.entered - last).also { last = counter.entered }
        }
        mainClock.autoAdvance = true
        return result
    }

    @Test
    fun committingTheOpenFlagRecomposesOnlyWhatChanges() = runDesktopComposeUiTest(400, 800) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val core = NaviampCore.create(scope, fakeCoreServices())
        val open = mutableStateOf(false)
        val counter = ScopeCounter()
        val bands = { emptyList<Float>() }
        try {
            setContent {
                NaviampSharedAppShell(
                    uiState = NaviampAppShellUiState(
                        connectionSettings = NaviampConnectionSettingsUi(
                            connection = NaviampShellConnectionUi(connected = true),
                        ),
                        shellChrome = NaviampShellChromeUi(selectedRoute = SharedRoute.Settings, nowPlayingOpen = open.value),
                        nowPlaying = NowPlayingUi(id = "song", title = "Song", subtitle = "Artist", stateLabel = "Paused"),
                    ),
                    visualizerBandsProvider = bands,
                    actions = core.actions.shell,
                    syncActions = core.actions.settingsSync,
                )
            }
            waitForIdle()
            val handle = runningRecomposer().observe(counter)
            val open1 = scopesPerFrame(counter, 3) { open.value = true }
            waitForIdle()
            val settledOpen = scopesPerFrame(counter, 3) {}
            counter.recording = true
            val close = scopesPerFrame(counter, 3) { open.value = false }
            counter.recording = false
            println("PROBE close-commit scopes by restart lambda:")
            counter.names.entries.sortedByDescending { it.value }.take(15).forEach { println("PROBE   ${it.value} ${it.key}") }
            waitForIdle()
            val open2 = scopesPerFrame(counter, 3) { open.value = true }
            waitForIdle()
            println("PROBE shell scopes per frame: open=$open1 settledOpen=$settledOpen close=$close reopen=$open2")
            handle.dispose()
            assertTrue(settledOpen.all { it == 0 })
            // Before the shell kept its theme and unchanged browse state stable, each of these
            // frames ran about 400 scopes because every Material component recomposed.
            assertTrue(close.first() <= 100, "close commit ran ${close.first()} scopes")
            assertTrue(open1.first() <= 60, "open commit ran ${open1.first()} scopes")
        } finally {
            scope.cancel()
        }
    }
}
