package app.naviamp.desktop

import app.naviamp.app.NaviampRuntimeError
import app.naviamp.app.NaviampRuntimeErrorReporter

/** Desktop host adapter that renders structured runtime failures to the process error stream. */
internal class DesktopRuntimeErrorReporter(
    private val write: (message: String, cause: Throwable?) -> Unit = ::writeDesktopRuntimeError,
) : NaviampRuntimeErrorReporter {
    override fun report(error: NaviampRuntimeError, cause: Throwable?) {
        write("Naviamp runtime ${error.operation}: ${error.message}", cause)
    }
}

private fun writeDesktopRuntimeError(message: String, cause: Throwable?) {
    System.err.println(message)
    cause?.printStackTrace(System.err)
}
