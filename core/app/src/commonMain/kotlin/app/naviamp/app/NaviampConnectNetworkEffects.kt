package app.naviamp.app

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Core-owned scheduling boundary for potentially blocking native DNS-SD operations.
 *
 * Call start/stop from the product owner's context. Native operations for browsing and advertising
 * share one ordered worker because hosts may share a DNS-SD instance. Callbacks return to the owner
 * and are scoped to the request that produced them. Stop invalidates callbacks immediately; native
 * teardown completes in the background. Close drains cleanup independently of the owner's Job.
 */
class NaviampConnectNetworkEffects(
    private val owner: CoroutineScope,
    private val nativeDiscovery: NaviampConnectDiscoveryEffect?,
    private val nativeAdvertising: NaviampConnectAdvertisingEffect?,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val closed = MutableStateFlow(false)
    private val discoveryGeneration = MutableStateFlow(0L)
    private val advertisingGeneration = MutableStateFlow(0L)
    private val operations = Channel<() -> Unit>(Channel.UNLIMITED)
    private val workerScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val worker = workerScope.launch {
        try {
            for (operation in operations) runCatching(operation)
        } finally {
            workerScope.cancel()
        }
    }

    val discovery: NaviampConnectDiscoveryEffect? = nativeDiscovery?.let { effect ->
        object : NaviampConnectDiscoveryEffect {
            override fun start(listener: NaviampConnectDiscoveryListener): NaviampConnectDiscoveryStartResult {
                if (closed.value) return NaviampConnectDiscoveryStartResult.Started
                discoveryGeneration.update { it + 1 }
                val generation = discoveryGeneration.value
                fun deliver(action: () -> Unit) = callback(discoveryGeneration, generation, action)
                val callback = object : NaviampConnectDiscoveryListener {
                    override fun onServiceResolved(service: NaviampConnectResolvedService) { deliver { listener.onServiceResolved(service) } }
                    override fun onServiceLost(serviceName: String) { deliver { listener.onServiceLost(serviceName) } }
                    override fun onDiscoveryFailed(message: String) { deliver { listener.onDiscoveryFailed(message) } }
                    override fun onDiscoveryUnavailable(message: String) { deliver { listener.onDiscoveryUnavailable(message) } }
                    override fun onPermissionDenied() { deliver { listener.onPermissionDenied() } }
                }
                operations.trySend {
                    if (!closed.value && discoveryGeneration.value == generation) {
                        when (val result = runCatching { effect.start(callback) }.getOrElse {
                            NaviampConnectDiscoveryStartResult.Unavailable(it.message.orEmpty())
                        }) {
                            NaviampConnectDiscoveryStartResult.Started -> Unit
                            NaviampConnectDiscoveryStartResult.PermissionDenied -> callback.onPermissionDenied()
                            is NaviampConnectDiscoveryStartResult.Unavailable -> callback.onDiscoveryUnavailable(result.message)
                        }
                    }
                }
                return NaviampConnectDiscoveryStartResult.Started
            }
            override fun stop() {
                discoveryGeneration.update { it + 1 }
                if (!closed.value) operations.trySend { effect.stop() }
            }
        }
    }

    val advertising: NaviampConnectAdvertisingEffect? = nativeAdvertising?.let { effect ->
        object : NaviampConnectAdvertisingEffect {
            override fun start(service: NaviampConnectRegistrationService, listener: NaviampConnectAdvertisingListener): NaviampConnectAdvertisingStartResult {
                if (closed.value) return NaviampConnectAdvertisingStartResult.Started
                advertisingGeneration.update { it + 1 }
                val generation = advertisingGeneration.value
                fun deliver(action: () -> Unit) = callback(advertisingGeneration, generation, action)
                val callback = object : NaviampConnectAdvertisingListener {
                    override fun onServiceRegistered(registeredServiceName: String) { deliver { listener.onServiceRegistered(registeredServiceName) } }
                    override fun onRegistrationFailed(message: String) { deliver { listener.onRegistrationFailed(message) } }
                    override fun onPermissionDenied() { deliver { listener.onPermissionDenied() } }
                }
                operations.trySend {
                    if (!closed.value && advertisingGeneration.value == generation) {
                        when (val result = runCatching { effect.start(service, callback) }.getOrElse {
                            NaviampConnectAdvertisingStartResult.Unavailable(it.message.orEmpty())
                        }) {
                            NaviampConnectAdvertisingStartResult.Started -> Unit
                            NaviampConnectAdvertisingStartResult.PermissionDenied -> callback.onPermissionDenied()
                            is NaviampConnectAdvertisingStartResult.Unavailable -> callback.onRegistrationFailed(result.message)
                        }
                    }
                }
                return NaviampConnectAdvertisingStartResult.Started
            }
            override fun stop() {
                advertisingGeneration.update { it + 1 }
                if (!closed.value) operations.trySend { effect.stop() }
            }
        }
    }

    init {
        owner.coroutineContext[Job]?.invokeOnCompletion { close() }
    }

    private fun callback(generation: MutableStateFlow<Long>, expected: Long, action: () -> Unit) {
        owner.launch {
            if (!closed.value && generation.value == expected) action()
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        operations.trySend {
            // One failing native stop must not prevent the other resource from being released.
            runCatching { nativeDiscovery?.stop() }
            runCatching { nativeAdvertising?.stop() }
        }
        operations.close()
    }

    suspend fun awaitClosed() { worker.join() }
}
