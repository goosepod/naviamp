package app.naviamp.desktop

import app.naviamp.app.NaviampRuntimeError
import app.naviamp.app.NaviampRuntimeOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DesktopRuntimeErrorReporterTest {
    @Test
    fun rendersStructuredRuntimeErrorsForTheDesktopSink() {
        var capturedMessage: String? = null
        var capturedCause: Throwable? = null
        val cause = IllegalStateException("restore unavailable")
        val reporter = DesktopRuntimeErrorReporter { message, throwable ->
            capturedMessage = message
            capturedCause = throwable
        }

        reporter.report(
            NaviampRuntimeError(
                operation = NaviampRuntimeOperation.Restore,
                message = "Saved session restoration failed.",
            ),
            cause,
        )

        assertEquals(
            "Naviamp runtime Restore: Saved session restoration failed.",
            capturedMessage,
        )
        assertSame(cause, capturedCause)
    }
}
