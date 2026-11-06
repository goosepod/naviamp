package app.naviamp.android

import android.content.res.Configuration
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.naviamp.app.NaviampCastSessionController
import app.naviamp.app.NaviampPlaybackOutputSelection
import app.naviamp.app.NaviampPlaybackOutputSelectionController
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.test.*

/** Real Cast Dynamite loading, failed-start cleanup and Activity recreation at the Android boundary. */
class AndroidCastAvailabilityInstrumentedTest {
    @Test fun nativeSenderAvailabilityDoesNotPreventApplicationStartup(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val available = withContext(Dispatchers.Main) {
            val initialization = runCatching { CastContext.getSharedInstance(context) }
            InstrumentationRegistry.getArguments().getString("expectedCastAvailable")?.let {
                assertEquals(it.toBooleanStrict(), initialization.isSuccess, "Unexpected native sender availability")
            }
            Log.i("NaviampCastAcceptance", "senderAvailable=${initialization.isSuccess}; failure=${initialization.exceptionOrNull()}")
            val effect = AndroidNaviampCastSessionEffect(context)
            val outputs = NaviampPlaybackOutputSelectionController()
            val controller = NaviampCastSessionController(effect, outputs)
            repeat(2) {
                assertEquals(initialization.isSuccess, controller.start())
                assertEquals(NaviampPlaybackOutputSelection.Local, outputs.state.value)
                controller.shutdown()
                // A failed lazy SDK load must not be retried by cleanup or poison the next start.
                effect.stop()
                effect.disconnect()
            }
            initialization.isSuccess
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val television = activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
                    Configuration.UI_MODE_TYPE_TELEVISION
                assertEquals(available && !television, AndroidNaviampApplicationRuntime.get(activity).core.castAvailable)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                if (!available) {
                    val core = AndroidNaviampApplicationRuntime.get(activity).core
                    assertFalse(core.castAvailable)
                    core.showCastPicker()
                }
                assertFalse(activity.isFinishing)
            }
        }
    }
}
