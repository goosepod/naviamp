package app.naviamp.desktop.playback.bass

import app.naviamp.desktop.playback.DesktopPlaybackEngineDiagnostics
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.AudioOutputDevice
import app.naviamp.domain.playback.AudioOutputDevicePlaybackEngine
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.EqualizerPlaybackEngine
import app.naviamp.domain.playback.EqualizerSettings
import app.naviamp.domain.playback.PlaybackReplayGainAdjustment
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.ReplayGainPlaybackEngine
import app.naviamp.domain.playback.VisualizerBandCount
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.playback.BassPlaybackCleanupReset
import app.naviamp.domain.playback.BassPlaybackActivationUpdate
import app.naviamp.domain.playback.BassPlaybackCreationPlan
import app.naviamp.domain.playback.BassPlaybackStartPolicy
import app.naviamp.domain.playback.PreparedPlaybackMetadataReset
import app.naviamp.domain.playback.PreparedBassPlaybackStateUpdate
import app.naviamp.domain.playback.PlaybackStreamStateReset
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.adoptPreparedBassSource
import app.naviamp.domain.bass.applyBassPlaybackVolume
import app.naviamp.domain.bass.applyEqualizer
import app.naviamp.domain.bass.bassErrorMessage
import app.naviamp.domain.bass.bassPlaybackSnapshot
import app.naviamp.domain.bass.bassPlaybackVisualizerFrame
import app.naviamp.domain.bass.bassStreamActiveStateLabel
import app.naviamp.domain.bass.bassVersionLabel
import app.naviamp.domain.bass.createBassPlayback
import app.naviamp.domain.bass.pause
import app.naviamp.domain.bass.play
import app.naviamp.domain.bass.prepareNextBassMixerSource
import app.naviamp.domain.bass.releaseBassStream
import app.naviamp.domain.bass.releaseBassStreams
import app.naviamp.domain.bass.seekBassPlaybackSource
import app.naviamp.domain.bass.setEndSync
import app.naviamp.domain.bass.stopAndReleaseBassPlayback
import app.naviamp.domain.playback.bassPlaybackFeatureSupport
import app.naviamp.domain.playback.BassPlaybackPollingState
import app.naviamp.domain.playback.BassPlaybackPollingPolicy
import app.naviamp.domain.playback.bassPlaybackActivated
import app.naviamp.domain.playback.clearBassPlaybackCleanupState
import app.naviamp.domain.playback.clearPreparedPlaybackMetadata
import app.naviamp.domain.playback.normalizedCrossfadeDurationSeconds
import app.naviamp.domain.playback.PreparedBassPlaybackPlan
import app.naviamp.domain.playback.planBassPlaybackPollingUpdate
import app.naviamp.domain.playback.planBassPlaybackCreation
import app.naviamp.domain.playback.planBassPlaybackStart
import app.naviamp.domain.playback.planPreparedBassPlayback
import app.naviamp.domain.playback.planPreparedBassPlaybackAdoption
import app.naviamp.domain.playback.playbackSourceHandle
import app.naviamp.domain.playback.playbackUserVolumeFactor
import app.naviamp.domain.playback.playbackReplayGainAdjustment
import app.naviamp.domain.playback.preparedBassPlaybackAdopted
import app.naviamp.domain.playback.preparedBassPlaybackFailed
import app.naviamp.domain.playback.preparedBassPlaybackSucceeded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI

class DesktopBassPlaybackEngine(
    private val backendResult: Result<BassAudioBackend> = loadDesktopBassAudioBackend(),
) : QueueAwarePlaybackEngine,
    VisualizerPlaybackEngine,
    EqualizerPlaybackEngine,
    ReplayGainPlaybackEngine,
    AudioOutputDevicePlaybackEngine,
    DesktopPlaybackEngineDiagnostics {
    private val backend: BassAudioBackend? = backendResult.getOrNull()
    private val loadError: Throwable? = backendResult.exceptionOrNull()

    override val name: String = "BASS"
    override val supportsPause: Boolean = true
    override val supportsSeek: Boolean = true
    private val featureSupport = bassPlaybackFeatureSupport(backend?.supportsMixer == true)

    override val supportsGapless: Boolean = featureSupport.supportsGapless
    override val supportsCrossfade: Boolean = featureSupport.supportsCrossfade
    override val supportsReplayGain: Boolean = true
    override val supportsEqualizer: Boolean = backend != null
    override val supportsAudioOutputDeviceSelection: Boolean = backend != null
    override val supportsVisualizer: Boolean = true
    override val supportsSoftwareVolume: Boolean = true
    override val prefersOriginalStream: Boolean = true

    private var job: Job? = null
    private var stream: Int = 0
    private var currentSourceStream: Int = 0
    private var playbackId: Int = 0
    private var volumePercent: Int = 100
    private var initialized = false
    private var internetStreamsConfigured = false
    private var onStateChanged: ((PlaybackState) -> Unit)? = null
    private var currentScope: CoroutineScope? = null
    private var currentRequest: PlaybackRequest? = null
    private var currentOnProgressChanged: ((PlaybackProgress) -> Unit)? = null
    private var currentOnMetadataChanged: ((PlaybackStreamMetadata) -> Unit)? = null
    private var lastProgress: PlaybackProgress = PlaybackProgress.Unknown
    private var lastRequestUrl: String? = null
    private var lastError: String? = loadError?.message
    private var preparedStream: Int = 0
    private var preparedRequest: PlaybackRequest? = null
    private var preparedReplayGainAdjustment: PlaybackReplayGainAdjustment? = null
    private var preparedError: String? = null
    private var endSyncCallbacks: MutableMap<Int, Int> = mutableMapOf()
    private var crossfadeDurationSeconds: Int = 0
    private var crossfadeActive: Boolean = false
    private var currentReplayGainAdjustment: PlaybackReplayGainAdjustment = PlaybackReplayGainAdjustment.off()
    private var equalizerSettings: EqualizerSettings = EqualizerSettings()
    private var selectedOutputDeviceId: String? = null
    @Volatile
    private var currentVisualizerFrame: PlaybackVisualizerFrame? = null

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ) {
        lastRequestUrl = request.url
        currentScope = scope
        currentRequest = request
        this.onStateChanged = onStateChanged
        currentOnProgressChanged = onProgressChanged
        currentOnMetadataChanged = onMetadataChanged
        lastProgress = PlaybackProgress.Unknown
        if (adoptQueuedPreparedStream(scope, request, onStateChanged, onProgressChanged, onMetadataChanged)) {
            return
        }

        stopActiveStream()
        val currentPlaybackId = nextPlaybackId()
        onStateChanged(PlaybackState.Loading)
        onProgressChanged(PlaybackProgress.Unknown)

        val bass = backend
        if (bass == null) {
            val message = loadError?.message ?: "BASS native library is not available."
            lastError = message
            onStateChanged(PlaybackState.Error(message))
            return
        }

        job = scope.launch(Dispatchers.IO) {
            var createdPlayback: BassPlaybackActivationUpdate? = null
            var retriedAfterBassReset = false
            try {
                while (isCurrentPlayback(currentPlaybackId)) {
                    try {
                        ensureInitialized(bass)
                        val creationPlan = planBassPlaybackCreation(
                            request = request,
                            supportsMixer = bass.supportsMixer,
                            requireMediaId = false,
                        )
                        createdPlayback = createPlayback(
                            bass = bass,
                            request = request,
                            plan = creationPlan,
                        ).getOrThrow()
                        if (!isCurrentPlayback(currentPlaybackId)) {
                            freeCreatedPlayback(bass, createdPlayback)
                            createdPlayback = null
                            return@launch
                        }
                        val playbackHandle = createdPlayback.playbackHandle
                        stream = playbackHandle
                        currentSourceStream = createdPlayback.sourceHandle
                        currentReplayGainAdjustment = createdPlayback.replayGainAdjustment
                        attachEndSync(bass, createdPlayback.sourceHandle, currentPlaybackId, onStateChanged)
                        createdPlayback = null
                        applyOutputVolume(bass)
                        applyEqualizer(bass)
                        val startPlan = planBassPlaybackStart(
                            request = request,
                            policy = BassPlaybackStartPolicy.DesktopEngine,
                        )
                        if (startPlan.shouldSeekBeforePlay) {
                            startPlan.startSeekSeconds?.let { seekCurrentSource(bass, it) }
                        }
                        bass.play(playbackHandle)
                            .getOrThrow()
                        onStateChanged(PlaybackState.Playing)

                        var pollingState = BassPlaybackPollingState()
                        while (isCurrentPlayback(currentPlaybackId)) {
                            val snapshot = bass.bassPlaybackSnapshot(playbackHandle, currentSourceStream)
                            val update = planBassPlaybackPollingUpdate(
                                snapshot = snapshot,
                                previous = pollingState,
                                policy = BassPlaybackPollingPolicy.DesktopEngine,
                            )
                            pollingState = update.state
                            update.playbackState?.let(onStateChanged)
                            update.progress?.let { progress ->
                                lastProgress = progress
                                onProgressChanged(progress)
                            }
                            update.metadata?.let(onMetadataChanged)
                            if (!update.shouldContinue) {
                                break
                            }
                            delay(BassPlaybackPollingPolicy.DesktopEngine.pollIntervalMillis)
                        }

                        if (BassPlaybackPollingPolicy.DesktopEngine.finishWhenPollingStops && isCurrentPlayback(currentPlaybackId)) {
                            onStateChanged(PlaybackState.Finished)
                        }
                        break
                    } catch (exception: Throwable) {
                        createdPlayback?.let { freeCreatedPlayback(bass, it) }
                        createdPlayback = null
                        if (
                            !retriedAfterBassReset &&
                            isCurrentPlayback(currentPlaybackId) &&
                            job?.isCancelled != true
                        ) {
                            retriedAfterBassReset = true
                            lastError = exception.message
                            resetBassAfterPlaybackFailure(bass)
                            onStateChanged(PlaybackState.Loading)
                            continue
                        }
                        throw exception
                    }
                }
            } catch (exception: Throwable) {
                createdPlayback?.let { freeCreatedPlayback(bass, it) }
                if (isCurrentPlayback(currentPlaybackId) && job?.isCancelled != true) {
                    val message = exception.message ?: "BASS playback failed."
                    lastError = message
                    onStateChanged(PlaybackState.Error(message))
                }
            } finally {
                if (isCurrentPlayback(currentPlaybackId)) {
                    val reset = freeAllStreams(bass)
                    applyStreamReset(reset.stream)
                    onProgressChanged(PlaybackProgress.Unknown)
                }
            }
        }
    }

    override fun pause() {
        val handle = stream
        val bass = backend ?: return
        if (handle != 0) {
            bass.pause(handle)
                .onSuccess { onStateChanged?.invoke(PlaybackState.Paused) }
                .onFailure { lastError = it.message }
        }
    }

    override fun resume() {
        val handle = stream
        val bass = backend ?: return
        if (handle != 0) {
            bass.play(handle)
                .onSuccess { onStateChanged?.invoke(PlaybackState.Playing) }
                .onFailure {
                    val message = it.message ?: "BASS playback failed."
                    lastError = message
                    restartCurrentPlaybackAfterResumeFailure(bass)
                }
        }
    }

    override fun seek(positionSeconds: Double) {
        val handle = playbackSourceHandle(stream, currentSourceStream)
        val bass = backend ?: return
        if (handle != 0) {
            freePreparedStream()
            seekCurrentSource(bass, positionSeconds)
        }
    }

    override fun setVolume(percent: Int) {
        volumePercent = percent.coerceIn(0, 100)
        val handle = stream
        val bass = backend ?: return
        if (handle != 0) {
            applyOutputVolume(bass)
        }
    }

    override fun stop() {
        freePreparedStream()
        stopActiveStream()
        onStateChanged = null
        currentScope = null
        currentRequest = null
        currentOnProgressChanged = null
        currentOnMetadataChanged = null
        lastProgress = PlaybackProgress.Unknown
    }

    override fun setCrossfadeDuration(seconds: Int) {
        crossfadeDurationSeconds = normalizedCrossfadeDurationSeconds(seconds)
    }

    override fun setEqualizer(settings: EqualizerSettings) {
        equalizerSettings = settings.normalized()
        backend?.let(::applyEqualizer)
    }

    override fun setReplayGain(mode: ReplayGainMode, preampDb: Float) {
        val request = currentRequest ?: return
        currentReplayGainAdjustment = playbackReplayGainAdjustment(
            request.copy(
                replayGainMode = mode,
                replayGainPreampDb = preampDb,
            ),
        )
        backend?.let(::applyOutputVolume)
    }

    override fun outputDevices(): List<AudioOutputDevice> =
        backend?.outputDevices().orEmpty()

    override fun setAudioOutputDevice(deviceId: String?): Result<Unit> {
        selectedOutputDeviceId = deviceId
        val bass = backend ?: return Result.failure(IllegalStateException("BASS native library is not available."))
        return bass.setOutputDevice(deviceId)
            .onSuccess {
                applyOutputDevice(bass)
                initialized = true
            }
            .onFailure { lastError = it.message }
    }

    override fun prepareNext(request: PlaybackRequest) {
        val bass = backend ?: return
        val plan = planPreparedBassPlayback(
            playbackHandle = stream,
            currentSourceHandle = currentSourceStream,
            preparedRequest = preparedRequest,
            preparedHandle = preparedStream,
            supportsMixer = bass.supportsMixer,
            request = request,
            allowDirectFallback = true,
        )
        if (plan == PreparedBassPlaybackPlan.ReusePrepared) return
        freePreparedStream()
        if (plan == PreparedBassPlaybackPlan.NotSupported) return
        runCatching {
            ensureInitialized(bass)
            when (plan) {
                is PreparedBassPlaybackPlan.PrepareMixer -> {
                    val localFile = if (plan.isLocalFileUrl) localFileFromUrl(request.url) else null
                    val prepared = bass.prepareNextBassMixerSource(
                        localPath = localFile?.absolutePath,
                        url = request.url,
                        mixer = stream,
                        currentSource = currentSourceStream,
                        currentSourceVolumeFactor = currentReplayGainAdjustment.volumeFactor,
                        crossfadeDurationSeconds = crossfadeDurationSeconds,
                        replayGainFactor = plan.replayGainFactor,
                        playbackDecode = true,
                    ).getOrThrow()
                    crossfadeActive = prepared.crossfadeActive
                    attachEndSync(bass, prepared.sourceHandle, playbackId)
                    preparedBassPlaybackSucceeded(
                        preparedHandle = prepared.sourceHandle,
                        request = request,
                        replayGainAdjustment = plan.replayGainAdjustment,
                    )
                }
                is PreparedBassPlaybackPlan.PrepareDirect -> {
                    val handle = createPlayback(
                        bass = bass,
                        request = request,
                        plan = BassPlaybackCreationPlan(
                            useMixer = false,
                            replayGainAdjustment = plan.replayGainAdjustment,
                            isLocalFileUrl = plan.isLocalFileUrl,
                        ),
                    ).getOrThrow().playbackHandle
                    preparedBassPlaybackSucceeded(
                        preparedHandle = handle,
                        request = request,
                        replayGainAdjustment = plan.replayGainAdjustment,
                    )
                }
                PreparedBassPlaybackPlan.NotSupported,
                PreparedBassPlaybackPlan.ReusePrepared,
                -> error("Unsupported prepared playback plan: $plan")
            }
        }.onSuccess { update ->
            applyPreparedUpdate(update)
        }.onFailure { error ->
            applyPreparedUpdate(preparedBassPlaybackFailed(error))
            lastError = preparedError
        }
    }

    private fun stopActiveStream() {
        playbackId += 1
        job?.cancel()
        job = null
        val handle = stream
        val bass = backend
        val cleanupReset = if (bass != null && handle != 0) {
            freeAllStreams(bass)
        } else {
            clearBassPlaybackCleanupState()
        }
        applyStreamReset(cleanupReset.stream)
        endSyncCallbacks.clear()
    }

    override fun visualizerFrame(): PlaybackVisualizerFrame? {
        val bass = backend ?: return null
        val handle = stream.takeIf { it != 0 } ?: return null
        return visualizerFrameFor(bass, handle)
            .also { currentVisualizerFrame = it }
    }

    override fun statsRows(): List<Pair<String, String>> =
        listOf(
            "BASS load state" to if (backend != null) "Loaded" else "Unavailable",
            "BASS version" to (backend?.version?.let(::bassVersionLabel) ?: "Unknown"),
            "BASSmix version" to (backend?.mixerVersion?.let(::bassVersionLabel) ?: "Unavailable"),
            "BASSmix error" to (backend?.mixerError ?: "None"),
            "BASS directory" to (backend?.libraryDirectory ?: "Not resolved"),
            "Loaded plugins" to backend?.pluginDiagnostics.orEmpty()
                .filter { it.loaded }
                .joinToString(", ") { it.stem }
                .ifBlank { "None" },
            "Failed plugins" to backend?.pluginDiagnostics.orEmpty()
                .filterNot { it.loaded }
                .joinToString(", ") { plugin ->
                    "${plugin.stem} (${plugin.errorCode?.let(::bassErrorMessage) ?: "unknown"})"
                }.ifBlank { "None" },
            "Active state" to (backend?.bassStreamActiveStateLabel(stream, "No stream") ?: "No stream"),
            "Active source state" to (backend?.bassStreamActiveStateLabel(currentSourceStream, "No source") ?: "No source"),
            "ReplayGain mode" to currentReplayGainAdjustment.mode.displayName,
            "ReplayGain source" to (currentReplayGainAdjustment.source?.displayName ?: "None"),
            "ReplayGain applied" to currentReplayGainAdjustment.label,
            "ReplayGain clipping guard" to currentReplayGainAdjustment.clippingGuardLabel,
            "Visualizer" to if (currentVisualizerFrame != null) {
                "${currentVisualizerFrame?.bands?.size ?: 0} FFT bands"
            } else {
                "Waiting"
            },
            "Crossfade duration" to if (crossfadeDurationSeconds > 0) "${crossfadeDurationSeconds}s" else "Off",
            "Crossfade active" to crossfadeActive.toString(),
            "Prepared next" to (preparedRequest?.mediaId ?: preparedRequest?.url ?: "None"),
            "Prepared next state" to when {
                preparedStream != 0 -> "Ready"
                preparedError != null -> "Failed: $preparedError"
                else -> "None"
            },
            "Volume" to "$volumePercent%",
            "Last request" to (lastRequestUrl ?: "None"),
            "Last error" to (lastError ?: "None"),
        )

    private fun ensureInitialized(bass: BassAudioBackend) {
        if (!initialized) {
            bass.init(selectedOutputDeviceId).getOrThrow()
            initialized = true
        } else {
            bass.setOutputDevice(selectedOutputDeviceId).getOrThrow()
        }
        if (!internetStreamsConfigured) {
            bass.configureInternetStreams().getOrThrow()
            internetStreamsConfigured = true
        }
    }

    private fun adoptQueuedPreparedStream(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ): Boolean {
        val bass = backend ?: return false
        val plan = planPreparedBassPlaybackAdoption(
            playbackHandle = stream,
            preparedRequest = preparedRequest,
            preparedHandle = preparedStream,
            supportsMixer = bass.supportsMixer,
            request = request,
        )
        val update = preparedBassPlaybackAdopted(
            adoption = plan,
            replayGainAdjustment = preparedReplayGainAdjustment ?: PlaybackReplayGainAdjustment.off(),
        ) ?: return false
        val queuedSource = update.currentSourceHandle
        job?.cancel()
        job = null
        val currentPlaybackId = nextPlaybackId()
        bass.adoptPreparedBassSource(
            playbackHandle = stream,
            currentSourceHandle = currentSourceStream,
            nextSourceHandle = queuedSource,
            userVolumeFactor = outputVolumeFactor(),
            replayGainFactor = update.replayGainFactor,
        ).forEach { result -> result.onFailure { lastError = it.message } }
        currentSourceStream = update.currentSourceHandle
        currentReplayGainAdjustment = update.replayGainAdjustment
        applyEqualizer(bass)
        crossfadeActive = false
        attachEndSync(bass, queuedSource, currentPlaybackId, onStateChanged)
        applyPreparedReset(update.preparedReset)
        onProgressChanged(PlaybackProgress.Unknown)
        onStateChanged(PlaybackState.Playing)
        job = scope.launch(Dispatchers.IO) {
            var pollingState = BassPlaybackPollingState()
            try {
                while (isCurrentPlayback(currentPlaybackId)) {
                    val snapshot = bass.bassPlaybackSnapshot(stream, currentSourceStream)
                    val update = planBassPlaybackPollingUpdate(
                        snapshot = snapshot,
                        previous = pollingState,
                        policy = BassPlaybackPollingPolicy.DesktopEngine,
                    )
                    pollingState = update.state
                    update.playbackState?.let(onStateChanged)
                    update.progress?.let { progress ->
                        lastProgress = progress
                        onProgressChanged(progress)
                    }
                    update.metadata?.let(onMetadataChanged)
                    if (!update.shouldContinue) {
                        break
                    }
                    delay(BassPlaybackPollingPolicy.DesktopEngine.pollIntervalMillis)
                }

                if (BassPlaybackPollingPolicy.DesktopEngine.finishWhenPollingStops && isCurrentPlayback(currentPlaybackId)) {
                    onStateChanged(PlaybackState.Finished)
                }
            } catch (exception: Throwable) {
                if (isCurrentPlayback(currentPlaybackId) && job?.isCancelled != true) {
                    val message = exception.message ?: "BASS playback failed."
                    lastError = message
                    onStateChanged(PlaybackState.Error(message))
                }
            } finally {
                if (isCurrentPlayback(currentPlaybackId)) {
                    val reset = freeAllStreams(bass)
                    applyStreamReset(reset.stream)
                    onProgressChanged(PlaybackProgress.Unknown)
                }
            }
        }
        return true
    }

    private fun freePreparedStream() {
        val handle = preparedStream
        val bass = backend
        if (bass != null && handle != 0) {
            bass.releaseBassStream(handle)
                .onFailure { lastError = it.message }
        }
        applyPreparedReset(clearPreparedPlaybackMetadata())
    }

    private fun createPlayback(
        bass: BassAudioBackend,
        request: PlaybackRequest,
        plan: BassPlaybackCreationPlan,
    ): Result<BassPlaybackActivationUpdate> {
        val localFile = if (plan.isLocalFileUrl) localFileFromUrl(request.url) else null
        return bass.createBassPlayback(
            localPath = localFile?.absolutePath,
            url = request.url,
            useMixer = plan.useMixer,
            crossfadeDurationSeconds = crossfadeDurationSeconds,
            replayGainFactor = plan.replayGainFactor,
            playbackDecode = plan.useMixer,
        ).map { playback -> bassPlaybackActivated(playback, plan.replayGainAdjustment) }
    }

    private fun attachEndSync(
        bass: BassAudioBackend,
        source: Int,
        currentPlaybackId: Int,
        stateCallback: ((PlaybackState) -> Unit)? = null,
    ) {
        bass.setEndSync(source) { channel ->
            if (channel.value == source && isCurrentPlayback(currentPlaybackId)) {
                (stateCallback ?: onStateChanged)?.invoke(PlaybackState.Finished)
            }
        }
            .onSuccess { endSyncCallbacks[source] = it }
            .onFailure { lastError = it.message }
    }

    private fun applyOutputVolume(bass: BassAudioBackend) {
        val handle = stream.takeIf { it != 0 } ?: return
        bass.applyBassPlaybackVolume(
            outputStream = handle,
            sourceStream = currentSourceStream,
            userVolumeFactor = outputVolumeFactor(),
            replayGainFactor = currentReplayGainAdjustment.volumeFactor,
        ).forEach { result -> result.onFailure { lastError = it.message } }
    }

    private fun applyEqualizer(bass: BassAudioBackend) {
        stream.takeIf { it != 0 }
            ?.let { handle -> bass.applyEqualizer(handle, equalizerSettings.bandsForBackend()) }
            ?.onFailure { lastError = it.message }
    }

    private fun applyOutputDevice(bass: BassAudioBackend) {
        listOf(stream, currentSourceStream, preparedStream)
            .filter { it != 0 }
            .distinct()
            .forEach { handle ->
                bass.setStreamOutputDevice(
                    stream = app.naviamp.domain.bass.BassStreamHandle(handle),
                    deviceId = selectedOutputDeviceId,
                ).onFailure { lastError = it.message }
            }
    }

    private fun outputVolumeFactor(): Float =
        playbackUserVolumeFactor(volumePercent)

    private fun visualizerFrameFor(
        bass: BassAudioBackend,
        sourceHandle: Int,
    ): PlaybackVisualizerFrame? =
        bass.bassPlaybackVisualizerFrame(
            stream = sourceHandle,
            bins = VisualizerBandCount,
            timestampMillis = System.currentTimeMillis(),
        )
            .onFailure { lastError = it.message }
            .getOrNull()

    private fun seekCurrentSource(bass: BassAudioBackend, seconds: Double) {
        bass.seekBassPlaybackSource(stream, currentSourceStream, seconds)
            .onFailure { lastError = it.message }
    }

    private fun restartCurrentPlaybackAfterResumeFailure(bass: BassAudioBackend) {
        val scope = currentScope
        val request = currentRequest
        val stateCallback = onStateChanged
        val progressCallback = currentOnProgressChanged
        val metadataCallback = currentOnMetadataChanged
        if (scope == null || request == null || stateCallback == null || progressCallback == null || metadataCallback == null) {
            stateCallback?.invoke(PlaybackState.Error(lastError ?: "BASS playback failed."))
            return
        }
        val restartRequest = request.copy(
            startPositionSeconds = lastProgress.positionSeconds
                ?.takeIf { position -> position > 0.0 }
                ?: request.startPositionSeconds,
        )
        stopActiveStream()
        resetBassAfterPlaybackFailure(bass)
        play(
            scope = scope,
            request = restartRequest,
            onStateChanged = stateCallback,
            onProgressChanged = progressCallback,
            onMetadataChanged = metadataCallback,
        )
    }

    private fun resetBassAfterPlaybackFailure(bass: BassAudioBackend) {
        if (stream != 0 || currentSourceStream != 0 || preparedStream != 0) {
            val reset = freeAllStreams(bass)
            applyStreamReset(reset.stream)
            endSyncCallbacks.clear()
        }
        runCatching { bass.free() }
            .onFailure { lastError = it.message }
        initialized = false
        internetStreamsConfigured = false
    }

    private fun freeAllStreams(bass: BassAudioBackend): BassPlaybackCleanupReset {
        bass.stopAndReleaseBassPlayback(stream, currentSourceStream, preparedStream)
            .forEach { result -> result.onFailure { lastError = it.message } }
        val reset = clearBassPlaybackCleanupState()
        crossfadeActive = reset.stream.crossfadeActive
        applyPreparedReset(reset.prepared)
        currentVisualizerFrame = null
        return reset
    }

    private fun applyStreamReset(reset: PlaybackStreamStateReset) {
        stream = reset.stream
        currentSourceStream = reset.currentSourceStream
        crossfadeActive = reset.crossfadeActive
        currentReplayGainAdjustment = reset.replayGainAdjustment
        currentVisualizerFrame = null
    }

    private fun applyPreparedReset(reset: PreparedPlaybackMetadataReset) {
        preparedStream = 0
        preparedRequest = reset.request
        preparedReplayGainAdjustment = reset.replayGainAdjustment
        preparedError = reset.error
    }

    private fun applyPreparedUpdate(update: PreparedBassPlaybackStateUpdate) {
        preparedStream = update.preparedHandle
        preparedRequest = update.preparedRequest
        preparedReplayGainAdjustment = update.replayGainAdjustment
        preparedError = update.error
    }

    private fun freeCreatedPlayback(
        bass: BassAudioBackend,
        created: BassPlaybackActivationUpdate,
    ) {
        bass.releaseBassStreams(created.playbackHandle, created.sourceHandle)
            .forEach { result -> result.onFailure { lastError = it.message } }
    }

    private fun localFileFromUrl(url: String): File? =
        runCatching {
            val uri = URI(url)
            if (uri.scheme == "file") File(uri) else null
        }.getOrNull()

    private fun nextPlaybackId(): Int {
        playbackId += 1
        return playbackId
    }

    private fun isCurrentPlayback(id: Int): Boolean =
        playbackId == id
}

private val PlaybackReplayGainAdjustment.label: String
    get() {
        val gainDb = gainDb ?: return "Off"
        return "${gainDb.formatDb()} dB -> ${volumeFactor.formatFactor()}x"
    }

private val PlaybackReplayGainAdjustment.clippingGuardLabel: String
    get() = when {
        gainDb == null -> "Off"
        clippingPrevented -> "Peak ${peak?.formatPeak() ?: "unknown"} limited boost"
        else -> "No clipping risk detected"
    }

private fun Double.formatDb(): String =
    "%+.2f".format(this)

private fun Float.formatFactor(): String =
    "%.3f".format(this)

private fun Double.formatPeak(): String =
    "%.6f".format(this)

private fun EqualizerSettings.bandsForBackend(): List<Float> =
    if (enabled) bandsDb else emptyList()
