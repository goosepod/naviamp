package app.naviamp.presentation

import app.naviamp.ui.NaviampSavedConnectionUi
import app.naviamp.ui.NaviampVehicleConnectionLabels
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class NaviampVehicleConnectionState(val working: Boolean = false, val failed: Boolean = false)

/** Scene-scoped recovery presentation; the shared connection owner performs the stored-credential handoff. */
internal class NaviampVehicleConnectionController(
    scope: CoroutineScope,
    private val product: StateFlow<NaviampCoreState>,
    val labels: NaviampVehicleConnectionLabels,
    private val selectSaved: suspend (String, Boolean) -> Boolean,
    private val timeoutMillis: Long = 30_000L,
) {
    private val session = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private val mutableState = MutableStateFlow(NaviampVehicleConnectionState())
    val state: StateFlow<NaviampVehicleConnectionState> = mutableState.asStateFlow()
    private var operation: Job? = null
    private var deadline: Job? = null
    private var serial = 0L
    private var closed = false
    private var target: NaviampSavedConnectionUi? = null
    private var previousSource: String? = null
    private fun settings() = product.value.shell.connectionSettings
    private fun saved() = settings().connection.savedConnections.filter { it.id.isNotBlank() }.distinctBy { it.id }
    private fun identity(source: NaviampSavedConnectionUi) = listOf(source.id, source.providerId, source.serverUrl, source.username)
    val busy: Boolean get() = mutableState.value.working || settings().connection.let { it.isConnecting || it.restoringConnection }
    val available: Boolean get() = !closed && !product.value.shell.connect.remotePlaybackAuthorityActive
    val currentSaved: NaviampSavedConnectionUi? get() = saved().firstOrNull { it.id == settings().currentSourceId }
    val recoveryNeeded: Boolean get() = settings().connection.let { it.offline || !it.connected || it.statusIsError }
    val fingerprint: Any get() = listOf(saved(), settings().currentSourceId, settings().connection.connected,
        settings().connection.statusIsError, busy, available, mutableState.value)
    val status: String get() = when {
        !available -> labels.unavailable
        busy -> labels.connecting
        mutableState.value.failed || settings().connection.statusIsError -> labels.failed
        settings().connection.offline -> labels.offline
        saved().isEmpty() -> labels.setupOnPhone
        else -> ""
    }

    init {
        session.launch {
            product.collect {
                val selected = target ?: return@collect
                val latest = saved().firstOrNull { it.id == selected.id }
                if (!available || latest == null || identity(latest) != identity(selected) ||
                    settings().currentSourceId !in setOf(previousSource, selected.id)) this@NaviampVehicleConnectionController.cancel()
            }
        }
    }

    fun rows(): List<NaviampVehicleBrowseRow> = buildList {
        currentSaved?.let { add(NaviampVehicleBrowseRow(Reconnect, labels.reconnect, it.displayName, enabled = available && !busy)) }
        addAll(saved().map { source ->
            val current = source.id == settings().currentSourceId
            NaviampVehicleBrowseRow("$Source${source.id}", source.displayName.ifBlank { source.username.ifBlank { labels.title } },
                listOfNotNull(source.username.takeIf { it.isNotBlank() }, labels.current.takeIf { current }).joinToString(" · "),
                enabled = available && !busy && !current)
        })
        add(NaviampVehicleBrowseRow(Setup, labels.setupOnPhone, "", enabled = false))
    }

    fun select(id: String) {
        if (!available || busy) return
        val reconnect = id == Reconnect
        val record = if (reconnect) currentSaved else saved().firstOrNull { "$Source${it.id}" == id && it.id != settings().currentSourceId }
        record ?: return
        val attempt = ++serial
        target = record; previousSource = settings().currentSourceId
        mutableState.value = NaviampVehicleConnectionState(working = true)
        val job = session.launch(start = CoroutineStart.LAZY) {
            try {
                val applied = selectSaved(record.id, reconnect)
                ensureActive()
                if (serial == attempt) mutableState.value = NaviampVehicleConnectionState(failed = !applied)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (serial == attempt) mutableState.value = NaviampVehicleConnectionState(failed = true)
            } finally {
                if (serial == attempt) {
                    target = null; operation = null
                    deadline?.cancel(); deadline = null
                    mutableState.value = mutableState.value.copy(working = false)
                }
            }
        }
        operation = job
        deadline = session.launch {
            delay(timeoutMillis)
            if (serial == attempt) {
                serial++; target = null; operation = null; deadline = null
                mutableState.value = NaviampVehicleConnectionState(failed = true)
                job.cancel()
            }
        }
        job.start()
    }
    fun cancel() {
        serial++; target = null
        deadline?.cancel(); deadline = null
        operation?.cancel(); operation = null
        mutableState.value = NaviampVehicleConnectionState()
    }
    fun close() { if (!closed) { closed = true; cancel(); session.cancel() } }
    companion object {
        const val Reconnect = "naviamp:vehicle:reconnect"
        private const val Source = "naviamp:vehicle:source:"
        private const val Setup = "naviamp:vehicle:connection-setup"
    }
}
