package app.naviamp.ios.playback

import app.naviamp.testkit.NaviampEventLoopTestRun
import platform.Foundation.*

/** Foundation's main run loop is an OS effect; test lifetime and failure policy stay in testkit. */
internal fun runIosNativeEventLoopTest(block: suspend () -> Unit) {
    check(NSThread.isMainThread) { "Native event-loop tests must enter on the main thread" }
    val run = NaviampEventLoopTestRun(block = block)
    try {
        while (!run.isCompleted) {
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
        }
        run.finish()
    } finally { run.close() }
}
