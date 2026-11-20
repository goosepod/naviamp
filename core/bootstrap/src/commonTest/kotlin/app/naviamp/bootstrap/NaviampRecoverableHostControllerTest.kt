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
    @Test fun failedCriticalAttachmentRequiresReloadAndLateFailureCannotCloseTheReplacement() = runTest {
        val callbacks = mutableListOf<(Throwable) -> Unit>()
        val closed = mutableListOf<Int>()
        var opened = 0
        val controller = NaviampRecoverableHostController(
            open = { _, _ -> ++opened }, disposeApplication = { closed += it },
            closeNative = {}, erase = { error("Must preserve recovery data") }, context = StandardTestDispatcher(testScheduler),
            attachApplication = { _, _, retain, failure -> callbacks += failure; retain {} },
        )
        controller.start()
        callbacks.first()(IllegalStateException("Native publication failed"))
        assertNull(controller.state.value.application)
        assertEquals(NaviampRecoveryFailureUi.Unavailable, controller.state.value.failure)
        assertEquals(listOf(1), closed)
        controller.dismissFailure()
        assertEquals(NaviampRecoveryFailureUi.Unavailable, controller.state.value.failure)
        controller.retry()
        assertEquals(2, controller.state.value.application)
        callbacks.first()(IllegalStateException("Stale native callback"))
        assertEquals(2, controller.state.value.application)
        controller.close()
    }

    @Test fun hostWithoutEraseOffersRetryButNeverReset() = runTest {
        var opened = 0
        val controller = NaviampRecoverableHostController(
            open = { _, _ -> if (++opened == 1) error("Database unavailable") else opened },
            disposeApplication = {}, closeNative = {}, erase = null, context = StandardTestDispatcher(testScheduler),
        )
        assertFalse(controller.resetAvailable)
        controller.start()
        assertEquals(NaviampRecoveryFailureUi.Unavailable, controller.state.value.failure)
        controller.requestReset()
        assertFalse(controller.state.value.confirmReset)
        controller.confirmReset()
        assertEquals(1, opened)
        controller.retry()
        assertEquals(2, controller.state.value.application)
        assertNull(controller.state.value.failure)
        controller.close()
    }

    @Test fun attachmentsCloseInReverseOrderBeforeCoreAndNativeResourcesOnRetry() = runTest {
        val events = mutableListOf<String>()
        var generation = 0
        val controller = NaviampRecoverableHostController(
            open = { _, _ -> ++generation }, disposeApplication = { events += "core:$it" },
            closeNative = { events += "native" }, erase = { true }, context = StandardTestDispatcher(testScheduler),
            attachApplication = { app, _, retain, _ ->
                retain { events += "first:$app" }; retain { events += "second:$app" }
            },
        )
        controller.start()
        controller.retry()
        assertEquals(listOf("second:1", "first:1", "core:1", "native"), events)
        assertEquals(2, controller.state.value.application)
        controller.close()
        assertEquals(listOf("second:2", "first:2", "core:2", "native"), events.takeLast(4))
    }

    @Test fun partialAttachmentFailureRollsBackAllRegisteredEffectsAndCanRetry() = runTest {
        val events = mutableListOf<String>()
        var generation = 0
        val controller = NaviampRecoverableHostController(
            open = { _, _ -> ++generation }, disposeApplication = { events += "core:$it" },
            closeNative = { events += "native" }, erase = { error("Keep recovery data") }, context = StandardTestDispatcher(testScheduler),
            attachApplication = { app, _, retain, _ ->
                retain { events += "media:$app" }
                if (app == 1) error("Partial native registration")
            },
        )
        controller.start()
        assertNull(controller.state.value.application)
        assertEquals(NaviampRecoveryFailureUi.Unavailable, controller.state.value.failure)
        assertEquals(listOf("media:1", "core:1", "native"), events)
        controller.retry()
        assertEquals(2, controller.state.value.application)
        controller.close()
    }

    @Test fun cleanupFailureStillReleasesAllEffectsCoreAndNativeAndReportsRetryFailure() = runTest {
        val events = mutableListOf<String>()
        val controller = NaviampRecoverableHostController(
            open = { _, _ -> "app" }, disposeApplication = { events += "core" },
            closeNative = { events += "native" }, erase = { true }, context = StandardTestDispatcher(testScheduler),
            attachApplication = { _, _, retain, _ ->
                retain { events += "first" }
                retain { events += "second"; error("cleanup failed") }
            },
        )
        controller.start()
        controller.retry()
        assertEquals(listOf("second", "first", "core", "native"), events)
        assertNull(controller.state.value.application)
        assertEquals(NaviampRecoveryFailureUi.Unavailable, controller.state.value.failure)
        runCatching { controller.close() }
    }

    @Test fun reentrantConflictDuringAttachmentCannotPublishTheDisposedApplication() = runTest {
        val events = mutableListOf<String>()
        lateinit var lateRetain: (() -> Unit) -> Unit
        val controller = NaviampRecoverableHostController(
            open = { _, _ -> "app" }, disposeApplication = { events += "core" },
            closeNative = { events += "native" }, erase = { true }, context = StandardTestDispatcher(testScheduler),
            attachApplication = { _, _, retain, failure ->
                lateRetain = retain
                retain { events += "media" }
                failure(StorageRecoveryException(StorageRecoveryFailure.Conflict))
            },
        )
        controller.start()
        assertNull(controller.state.value.application)
        assertEquals(NaviampRecoveryFailureUi.Conflict, controller.state.value.failure)
        assertEquals(listOf("media", "core", "native"), events)
        lateRetain { events += "late" }
        assertEquals("late", events.last())
        controller.close()
    }

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
