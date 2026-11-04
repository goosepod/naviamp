package app.naviamp.ios.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSThread
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class IosNativeTestRunLoopTest {
    @Test fun mainQueueCallbackCompletesWhileCommonTestAwaitsIt() = runIosNativeEventLoopTest {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                withContext(Dispatchers.Main) { assertTrue(NSThread.isMainThread) }
            }
        }
    }
    @Test fun asynchronousFailureAfterAMainCallbackIsNotSwallowed() {
        assertFailsWith<IllegalArgumentException> {
            runIosNativeEventLoopTest {
                withTimeout(5_000) {
                    withContext(Dispatchers.Main) { assertTrue(NSThread.isMainThread) }
                }
                throw IllegalArgumentException("Expected callback test failure")
            }
        }
    }
}
