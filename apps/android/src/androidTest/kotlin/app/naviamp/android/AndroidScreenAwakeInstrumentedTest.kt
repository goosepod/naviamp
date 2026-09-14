package app.naviamp.android

import android.content.res.Configuration
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.naviamp.presentation.NaviampCoreCommand
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

/** Opt-in tests of the actual TV window and shared mounted lifecycle, using a disposable install. */
class AndroidScreenAwakeInstrumentedTest {
    @Test fun preferenceFollowsVisibleLifecycleAndSurvivesActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("tvLocalFixture") == "true")
        assertEquals(Configuration.UI_MODE_TYPE_TELEVISION,
            instrumentation.targetContext.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK)
        val activity = AtomicReference<MainActivity>()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity.set(it) }
            fun setEnabled(value: Boolean) = scenario.onActivity { host ->
                val core = AndroidNaviampApplicationRuntime.get(host).core
                core.dispatch(NaviampCoreCommand.Settings.ChangeInterface(
                    core.state.value.shell.general.interfaceSettings.copy(keepScreenAwake = value)))
            }
            awaitFlag(activity, false)
            setEnabled(true)
            awaitFlag(activity, true)
            scenario.moveToState(Lifecycle.State.STARTED)
            awaitFlag(activity, true) // Losing input focus alone must not release a visible surface.
            scenario.moveToState(Lifecycle.State.CREATED)
            awaitFlag(activity, false)
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitFlag(activity, true)
            val oldActivity = activity.get()
            scenario.recreate()
            scenario.onActivity {
                activity.set(it)
                assertTrue(AndroidNaviampApplicationRuntime.get(it).core.state.value.shell.general.interfaceSettings.keepScreenAwake)
            }
            awaitFlag(AtomicReference(oldActivity), false)
            awaitFlag(activity, true)
            setEnabled(false)
            awaitFlag(activity, false)
            setEnabled(true)
            awaitFlag(activity, true)
        } finally {
            scenario.onActivity { host ->
                val core = AndroidNaviampApplicationRuntime.get(host).core
                core.dispatch(NaviampCoreCommand.Settings.ChangeInterface(
                    core.state.value.shell.general.interfaceSettings.copy(keepScreenAwake = false)))
            }
            scenario.close()
        }
        awaitFlag(activity, false)
    }

    @Test fun nativeLeasePreservesAnExistingWindowFlagAndReleasesItsOwnFlagOnce() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tvLocalFixture") == "true")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                activity.window.addFlags(flag)
                val effect = AndroidScreenAwakeEffect(activity.window)
                val borrowed = effect.acquire("Test")
                borrowed.release()
                assertTrue(activity.window.attributes.flags and flag != 0)
                activity.window.clearFlags(flag)
                val owned = effect.acquire("Test")
                assertTrue(activity.window.attributes.flags and flag != 0)
                owned.release()
                owned.release()
                assertFalse(activity.window.attributes.flags and flag != 0)
            }
        }
    }

    private fun awaitFlag(activity: AtomicReference<MainActivity>, expected: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = System.nanoTime() + 10_000_000_000L
        var value = !expected
        do {
            instrumentation.runOnMainSync {
                value = activity.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
            }
            if (value == expected) return
            Thread.sleep(25)
        } while (System.nanoTime() < deadline)
        assertEquals(expected, value, "Actual Android TV window flag")
    }
}
