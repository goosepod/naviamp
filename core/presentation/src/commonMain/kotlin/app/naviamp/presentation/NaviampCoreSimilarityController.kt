package app.naviamp.presentation

import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.radio.SimilarityRequestTimeoutMillis
import app.naviamp.domain.radio.testSimilarity
import app.naviamp.ui.NaviampSimilarityTestUi
import app.naviamp.ui.SimilarityTestState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** Playback-independent testing; stale results from another server/account are discarded. */
class NaviampCoreSimilarityController(
    private val stateStore: NaviampCoreStateStore,
    private val providers: NaviampCoreMediaProviderSource,
    private val seedTrack: suspend (MediaProvider) -> Track?,
) {
    private var running = false
    private var generation = 0L
    private var request: Job? = null

    fun resetForSourceChange() {
        generation++
        request?.cancel()
        request = null
        running = false
        update(NaviampSimilarityTestUi())
    }

    suspend fun test(): Unit = coroutineScope {
        if (running) return@coroutineScope
        val provider = providers.current()
        val sourceId = stateStore.state.value.shell.connectionSettings.currentSourceId
        if (provider == null) {
            update(NaviampSimilarityTestUi(SimilarityTestState.NoSeed, sourceId))
            return@coroutineScope
        }
        running = true
        val started = generation
        request = currentCoroutineContext()[Job]
        update(NaviampSimilarityTestUi(SimilarityTestState.Running, sourceId))
        try {
            val seed = withTimeoutOrNull(SimilarityRequestTimeoutMillis) { seedTrack(provider) }
            val result = if (seed == null) NaviampSimilarityTestUi(SimilarityTestState.NoSeed, sourceId)
            else NaviampSimilarityTestUi(SimilarityTestState.Complete, sourceId, testSimilarity(provider, seed))
            if (started == generation && current(provider, sourceId)) update(result)
        } catch (cancelled: CancellationException) {
            if (started == generation && current(provider, sourceId)) update(NaviampSimilarityTestUi(sourceId = sourceId))
            throw cancelled
        } catch (_: Exception) {
            if (started == generation && current(provider, sourceId)) update(NaviampSimilarityTestUi(SimilarityTestState.Failed, sourceId))
        } finally {
            if (started == generation) {
                running = false
                request = null
                if (!current(provider, sourceId)) update(NaviampSimilarityTestUi())
            }
        }
    }

    private fun current(provider: MediaProvider, sourceId: String?) =
        sourceId == stateStore.state.value.shell.connectionSettings.currentSourceId && providers.isCurrent(provider)

    private fun update(test: NaviampSimilarityTestUi) = stateStore.updateShell { shell ->
        shell.copy(playback = shell.playback.copy(similarityTest = test))
    }
}
