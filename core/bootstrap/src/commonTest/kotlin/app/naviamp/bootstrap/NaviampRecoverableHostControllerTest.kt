package app.naviamp.bootstrap

import app.naviamp.storage.StorageRecoveryException
import app.naviamp.storage.StorageRecoveryFailure
import app.naviamp.ui.NaviampRecoveryFailureUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NaviampRecoverableHostControllerTest {
    @Test fun conflictClosesTheUncertainApplicationEvenIfCleanupThrowsAndRetryOpensFreshState() = runTest {
        var attempt = 0
        var nativeCloses = 0
        lateinit var callback: (Throwable) -> Unit
        val controller = NaviampRecoverableHostController(
            open = { _, failure -> callback = failure; ++attempt },
            disposeApplication = { error("Injected cleanup failure") },
            closeNative = { nativeCloses++ }, erase = { error("Must preserve the document") },
            context = StandardTestDispatcher(testScheduler),
        )
        controller.start()
        callback(StorageRecoveryException(StorageRecoveryFailure.Conflict))
        assertNull(controller.state.value.application)
        assertEquals(NaviampRecoveryFailureUi.Conflict, controller.state.value.failure)
        assertEquals(1, nativeCloses)
        controller.retry()
        assertEquals(2, controller.state.value.application)
        runCatching { controller.close() }
    }
    @Test fun unreadableStartupPreservesDataAndRetryReleasesTheFailedScope() = runTest {
        var attempts = 0
        var erased = 0
        var closed = 0
        val scopes = mutableListOf<CoroutineScope>()
        val controller = NaviampRecoverableHostController(
            open = { scope, _ ->
                scopes += scope
                if (++attempts == 1) throw StorageRecoveryException(StorageRecoveryFailure.Unreadable)
                "ready"
            }, disposeApplication = {}, closeNative = { closed++ }, erase = { erased++; true },
            context = StandardTestDispatcher(testScheduler),
        )
        controller.start()
        assertEquals(NaviampRecoveryFailureUi.Unreadable, controller.state.value.failure)
        assertEquals(0, erased)
        assertFalse(scopes.first().coroutineContext[Job]!!.isActive)
        controller.retry()
        assertEquals("ready", controller.state.value.application)
        assertNull(controller.state.value.failure)
        assertEquals(0, erased)
        controller.close()
        assertFalse(scopes.last().coroutineContext[Job]!!.isActive)
        val closeCount = closed
        controller.close()
        controller.start()
        assertEquals(closeCount, closed)
        assertEquals(2, attempts)
    }

    @Test fun resetRequiresConfirmationAndOldCallbacksCannotChangeTheRestartedHost() = runTest {
        var erased = 0
        val disposed = mutableListOf<String>()
        val callbacks = mutableListOf<(Throwable) -> Unit>()
        val controller = NaviampRecoverableHostController(
            open = { _, callback -> callbacks += callback; "app-${callbacks.size}" },
            disposeApplication = { disposed += it }, closeNative = {}, erase = { erased++; true },
            context = StandardTestDispatcher(testScheduler),
        )
        controller.start()
        controller.confirmReset()
        assertEquals(0, erased)
        controller.requestReset()
        controller.cancelReset()
        controller.confirmReset()
        assertEquals(0, erased)
        callbacks.first()(StorageRecoveryException(StorageRecoveryFailure.Capacity))
        assertEquals("app-1", controller.state.value.application)
        assertEquals(NaviampRecoveryFailureUi.Capacity, controller.state.value.failure)
        controller.dismissFailure()
        assertNull(controller.state.value.failure)
        controller.requestReset()
        controller.confirmReset()
        assertEquals(1, erased)
        assertEquals(listOf("app-1"), disposed)
        assertEquals("app-2", controller.state.value.application)
        callbacks.first()(StorageRecoveryException(StorageRecoveryFailure.Unreadable))
        assertNull(controller.state.value.failure)
        controller.close()
    }
}
