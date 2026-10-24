package app.naviamp.ui

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

/** Publish live semantics without queuing the host's snapshot-observer drawing pass. */
internal fun <T> Modifier.naviampLiveSemantics(
    value: () -> T,
    apply: SemanticsPropertyReceiver.(T) -> Unit,
): Modifier = then(LiveSemanticsElement(value, apply))

private data class LiveSemanticsElement<T>(
    val value: () -> T,
    val apply: SemanticsPropertyReceiver.(T) -> Unit,
) : ModifierNodeElement<LiveSemanticsNode<T>>() {
    override fun create() = LiveSemanticsNode(value, apply)
    override fun update(node: LiveSemanticsNode<T>) = node.update(value, apply)
    override fun InspectorInfo.inspectableProperties() { name = "naviampLiveSemantics" }
}

private class LiveSemanticsNode<T>(
    private var read: () -> T,
    private var apply: SemanticsPropertyReceiver.(T) -> Unit,
) : Modifier.Node(), SemanticsModifierNode, CompositionLocalConsumerModifierNode {
    private var latest: T = Snapshot.withoutReadObservation { read() }
    private var observation: Job? = null
    override val shouldAutoInvalidate = false
    override fun onAttach() = observe()
    override fun onDetach() { observation?.cancel(); observation = null }
    fun update(read: () -> T, apply: SemanticsPropertyReceiver.(T) -> Unit) {
        this.read = read
        this.apply = apply
        if (isAttached) observe()
    }
    private fun observe() {
        observation?.cancel()
        val visibility = currentValueOf(LocalNaviampWindowVisibility)
        observation = coroutineScope.launch {
            naviampObserveVisibleSemantics(visibility, { read() }) {
                latest = it
                invalidateSemantics()
            }
        }
    }
    override fun SemanticsPropertyReceiver.applySemantics() { apply(latest) }
}

/** Hidden windows retain their last semantics and catch up before becoming interactive again. */
internal suspend fun <T> naviampObserveVisibleSemantics(
    visibility: StateFlow<Boolean>, read: () -> T, publish: (T) -> Unit,
) {
    visibility.collectLatest { visible ->
        if (visible) snapshotFlow(read).collect(publish)
    }
}
