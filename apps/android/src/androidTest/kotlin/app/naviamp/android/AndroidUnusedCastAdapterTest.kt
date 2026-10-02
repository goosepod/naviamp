package app.naviamp.android

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test

/** The fresh TV emulator deliberately has no Google Cast sender Dynamite module. */
class AndroidUnusedCastAdapterTest {
    @Test fun constructingAndStoppingAnUnusedAdapterDoesNotLoadTheSenderSdk() = runBlocking {
        withContext(Dispatchers.Main) {
            val adapter = AndroidNaviampCastSessionEffect(
                InstrumentationRegistry.getInstrumentation().targetContext,
            )
            adapter.stop()
        }
    }
}
