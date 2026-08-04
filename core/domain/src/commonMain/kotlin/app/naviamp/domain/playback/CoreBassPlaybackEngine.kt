package app.naviamp.domain.playback

import app.naviamp.domain.playback.BassPlaybackEngine
import app.naviamp.domain.playback.BassPlaybackEngineRuntime
import app.naviamp.domain.playback.PlaybackEngineDiagnostics
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
import app.naviamp.domain.playback.ReleasablePlaybackEngine
import app.naviamp.domain.playback.SampleRateConverterPlaybackEngine
import app.naviamp.domain.playback.SampleRateMatchingPlaybackEngine
import app.naviamp.domain.settings.SampleRateConverter
import app.naviamp.domain.settings.SampleRateMatching
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
import app.naviamp.domain.bass.BassPlaybackBufferPolicy
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
import app.naviamp.domain.bass.setBassPlaybackMuted
import app.naviamp.domain.bass.stopAndReleaseBassPlayback
import app.naviamp.domain.playback.bassPlaybackFeatureSupport
import app.naviamp.domain.playback.BassPlaybackPollingState
import app.naviamp.domain.playback.BassPlaybackPollingPolicy
import app.naviamp.domain.playback.BassPlaybackExecutionCoordinator
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
import app.naviamp.domain.playback.shouldRestoreCurrentSourceForSeek
import app.naviamp.domain.playback.targetOutputSampleRate
import app.naviamp.domain.playback.downloadFallbackRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

open class CoreBassPlaybackEngine(
    private val backendResult: Result<BassAudioBackend>,
    private val runtime: BassPlaybackEngineRuntime,
    private val pollingPolicy: BassPlaybackPollingPolicy = BassPlaybackPollingPolicy.CoreEngine,
    private val bufferPolicy: BassPlaybackBufferPolicy = BassPlaybackBufferPolicy(),
) : QueueAwarePlaybackEngine,
    BassPlaybackEngine,
    AudioOutputDevicePlaybackEngine,
    PlaybackEngineDiagnostics {
    private val backend: BassAudioBackend? = backendResult.getOrNull()
    private val loadError: Throwable? = backendResult.exceptionOrNull()
    private val startPolicy: BassPlaybackStartPolicy = BassPlaybackStartPolicy.CoreEngine
    private var sampleRateConverter = SampleRateConverter.Sinc16
    private var sampleRateMatching = SampleRateMatching.Disabled

    override val name: String = "BASS"
    override val supportsPause: Boolean = true
    override val supportsSeek: Boolean = true
    private val featureSupport = bassPlaybackFeatureSupport(backend?.supportsMixer == true)

    override val supportsGapless: Boolean = featureSupport.supportsGapless
    override val supportsCrossfade: Boolean = featureSupport.supportsCrossfade
    override val supportsReplayGain: Boolean = true
    override val supportsEqualizer: Boolean = backend != null

    override fun setSampleRateConverter(converter: SampleRateConverter) {
        sampleRateConverter = converter
        backend?.setSampleRateConverterQuality(converter.bassQuality)
    }

    override fun setSampleRateMatching(mode: SampleRateMatching) {
        sampleRateMatching = mode
    }

    override val supportsAudioOutputDeviceSelection: Boolean = backend?.supportsOutputDeviceSelection == true
    override val supportsVisualizer: Boolean = true
    override val supportsSoftwareVolume: Boolean = true
    override val prefersOriginalStream: Boolean = true

    private var job: Job? = null
    private var stream: Int = 0
    private var currentSourceStream: Int = 0
    private val execution = BassPlaybackExecutionCoordinator()
    private var volumePercent: Int = 100
    private var initialized = false
    private var released = false
    private var internetStreamsConfigured = false
    private var activeOutputSampleRateHz: Int? = null
    private var currentScope: CoroutineScope? = null
    private var lastProgress: PlaybackProgress = PlaybackProgress.Unknown
    private var lastRequestUrl: String? = null
    private var lastRequestedSampleRateHz: Int? = null
    private var lastTargetOutputSampleRateHz: Int? = null
    private var lastError: String? = loadError?.message
    private var preparedStream: Int = 0
    private var preparedRequest: PlaybackRequest? = null
    private var preparedReplayGainAdjustment: PlaybackReplayGainAdjustment? = null
    private var preparedError: String? = null
    private var preparedNextGeneration: Long = 0L
    private var endSyncCallbacks: MutableMap<Int, Int> = mutableMapOf()
    private var crossfadeDurationSeconds: Int = 0
    private var crossfadeActive: Boolean = false
    private var currentReplayGainAdjustment: PlaybackReplayGainAdjustment = PlaybackReplayGainAdjustment.off()
    private var equalizerSettings: EqualizerSettings = EqualizerSettings()
    private var selectedOutputDeviceId: String? = null
    private var transientOutputVolumeFactor: Float = 1f
    private var verifyNetworkCertificates: Boolean = true
    private var currentVisualizerFrame: PlaybackVisualizerFrame? = null

    override open fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ) {
        lastRequestUrl = request.url
        currentScope = scope
        execution.attach(request, onStateChanged, onProgressChanged, onMetadataChanged)
        lastProgress = PlaybackProgress.Unknown
        val startingFromIdle = stream == 0
        val targetSampleRateHz = targetOutputSampleRate(
            mode = sampleRateMatching,
            requestedSampleRateHz = request.samplingRateHz,
            startingFromIdle = startingFromIdle,
        )
        lastRequestedSampleRateHz = request.samplingRateHz
        lastTargetOutputSampleRateHz = targetSampleRateHz
        val canAdoptPreparedStream = targetSampleRateHz == null || targetSampleRateHz == activeOutputSampleRateHz
        if (
            canAdoptPreparedStream &&
            adoptQueuedPreparedStream(scope, request, onStateChanged, onProgressChanged, onMetadataChanged)
        ) {
            return
        }

        stopActiveStream()
        val currentPlaybackId = execution.nextPlaybackId()
        onStateChanged(PlaybackState.Loading)
        onProgressChanged(PlaybackProgress.Unknown)

        val bass = backend
        if (bass == null) {
            val message = loadError?.message ?: "BASS native library is not available."
            lastError = message
            onStateChanged(PlaybackState.Error(message))
            return
        }

        job = scope.launch(runtime.workContext) {
            var createdPlayback: BassPlaybackActivationUpdate? = null
            var retriedAfterBassReset = false
            var activeRequest = request
            var triedFallback = false
            try {
                while (execution.isCurrent(currentPlaybackId)) {
                    try {
                        ensureInitialized(bass, targetSampleRateHz)
                        val creationPlan = planBassPlaybackCreation(
                            request = activeRequest,
                            supportsMixer = bass.supportsMixer,
                            requireMediaId = false,
                        )
                        createdPlayback = createPlayback(
                            bass = bass,
                            request = activeRequest,
                            plan = creationPlan,
                        ).getOrThrow()
                        if (!execution.isCurrent(currentPlaybackId)) {
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
                            request = activeRequest,
                            policy = startPolicy,
                        )
                        val seekedBeforePlay = if (startPlan.shouldSeekBeforePlay) {
                            startPlan.startSeekSeconds
                                ?.let { seekCurrentSource(bass, it).isSuccess }
                                ?: false
                        } else {
                            false
                        }
                        val prePlayPlan = planBassPlaybackPrePlay(startPlan, seekedBeforePlay)
                        if (prePlayPlan.shouldMuteBeforePlay) setPlaybackMuted(bass, true)
                        bass.play(playbackHandle)
                            .getOrThrow()
                        if (prePlayPlan.shouldRetrySeekAfterPlay) {
                            val position = requireNotNull(startPlan.startSeekSeconds)
                            val seekedAfterPlay = retryStartSeek(bass, playbackHandle, currentPlaybackId, position)
                            setPlaybackMuted(bass, false)
                            check(seekedAfterPlay) { "BASS start seek did not apply seconds=$position" }
                        }
                        onStateChanged(PlaybackState.Playing)

                        var pollingState = BassPlaybackPollingState()
                        while (execution.isCurrent(currentPlaybackId)) {
                            val snapshot = bass.bassPlaybackSnapshot(playbackHandle, currentSourceStream)
                            val update = planBassPlaybackPollingUpdate(
                                snapshot = snapshot,
                                previous = pollingState,
                                policy = pollingPolicy,
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
                            delay(pollingPolicy.pollIntervalMillis)
                        }

                        if (pollingPolicy.finishWhenPollingStops && execution.isCurrent(currentPlaybackId)) {
                            onStateChanged(PlaybackState.Finished)
                        }
                        break
                    } catch (exception: Throwable) {
                        createdPlayback?.let { freeCreatedPlayback(bass, it) }
                        createdPlayback = null
                        if (
                            !retriedAfterBassReset &&
                            execution.isCurrent(currentPlaybackId) &&
                            job?.isCancelled != true
                        ) {
                            retriedAfterBassReset = true
                            lastError = exception.message
                            resetBassAfterPlaybackFailure(bass)
                            onStateChanged(PlaybackState.Loading)
                            continue
                        }
                        val fallbackRequest = activeRequest.downloadFallbackRequest(lastProgress.positionSeconds)
                        if (!triedFallback && fallbackRequest != null && execution.isCurrent(currentPlaybackId)) {
                            triedFallback = true
                            retriedAfterBassReset = false
                            activeRequest = fallbackRequest
                            execution.updateRequest(activeRequest)
                            lastRequestUrl = activeRequest.url
                            resetBassAfterPlaybackFailure(bass)
                            onStateChanged(PlaybackState.Loading)
                            continue
                        }
                        throw exception
                    }
                }
            } catch (exception: Throwable) {
                createdPlayback?.let { freeCreatedPlayback(bass, it) }
                if (execution.isCurrent(currentPlaybackId) && job?.isCancelled != true) {
                    val message = exception.message ?: "BASS playback failed."
                    lastError = message
                    onStateChanged(PlaybackState.Error(message))
                }
            } finally {
                if (execution.isCurrent(currentPlaybackId)) {
                    if (!shouldRetainPreparedPlaybackAfterCurrentFinishes(preparedStream)) {
                        val reset = freeAllStreams(bass)
                        applyStreamReset(reset.stream)
                        onProgressChanged(PlaybackProgress.Unknown)
                    }
                }
            }
        }
    }

    override open fun pause() {
        val handle = stream
        val bass = backend ?: return
        if (handle != 0) {
            bass.pause(handle)
                .onSuccess { execution.publishState(PlaybackState.Paused) }
                .onFailure { lastError = it.message }
        }
    }

    override open fun resume() {
        val handle = stream
        val bass = backend ?: return
        if (handle != 0) {
            bass.play(handle)
                .onSuccess { execution.publishState(PlaybackState.Playing) }
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
            val restoreCurrentSource = shouldRestoreCurrentSourceForSeek(
                preparedHandle = preparedStream,
                crossfadeActive = crossfadeActive,
            )
            freePreparedStream()
            if (restoreCurrentSource) {
                crossfadeActive = false
                applyOutputVolume(bass)
            }
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

    override open fun stop() {
        freePreparedStream()
        stopActiveStream()
        execution.publishProgress(PlaybackProgress.Unknown)
        execution.publishState(PlaybackState.Stopped)
        execution.clear()
        currentScope = null
        lastProgress = PlaybackProgress.Unknown
    }

    override open fun release() {
        if (released) return
        stop()
        backend?.free()
            ?.onFailure { lastError = it.message }
        initialized = false
        internetStreamsConfigured = false
        activeOutputSampleRateHz = null
        released = true
    }

    override fun setCrossfadeDuration(seconds: Int) {
        crossfadeDurationSeconds = normalizedCrossfadeDurationSeconds(seconds)
    }

    override fun setEqualizer(settings: EqualizerSettings) {
        equalizerSettings = settings.normalized()
        backend?.let(::applyEqualizer)
    }

    override fun setReplayGain(mode: ReplayGainMode, preampDb: Float) {
        val request = execution.currentRequest ?: return
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
                activeOutputSampleRateHz = null
            }
            .onFailure { lastError = it.message }
    }

    override fun prepareNext(request: PlaybackRequest) {
        val bass = backend ?: return
        val (plan, generation) = runtime.withPreparedPlaybackLock {
            val planned = planPreparedBassPlayback(
                playbackHandle = stream,
                currentSourceHandle = currentSourceStream,
                preparedRequest = preparedRequest,
                preparedHandle = preparedStream,
                supportsMixer = bass.supportsMixer,
                request = request,
                allowDirectFallback = true,
            )
            if (planned != PreparedBassPlaybackPlan.ReusePrepared) freePreparedStream()
            planned to preparedNextGeneration
        }
        if (plan == PreparedBassPlaybackPlan.ReusePrepared) return
        if (plan == PreparedBassPlaybackPlan.NotSupported) return
        runCatching {
            ensureInitialized(bass)
            when (plan) {
                is PreparedBassPlaybackPlan.PrepareMixer -> {
                    val localPath = if (plan.isLocalFileUrl) runtime.localFilePath(request.url) else null
                    val prepared = bass.prepareNextBassMixerSource(
                        localPath = localPath,
                        url = request.url,
                        mixer = stream,
                        currentSource = currentSourceStream,
                        currentSourceVolumeFactor = currentReplayGainAdjustment.volumeFactor,
                        crossfadeDurationSeconds = crossfadeDurationSeconds,
                        replayGainFactor = plan.replayGainFactor,
                        playbackDecode = true,
                    ).getOrThrow()
                    crossfadeActive = prepared.crossfadeActive
                    attachEndSync(bass, prepared.sourceHandle, execution.currentPlaybackId)
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
            runtime.withPreparedPlaybackLock {
                if (generation != preparedNextGeneration) {
                    bass.releaseBassStream(update.preparedHandle)
                        .onFailure { lastError = it.message }
                    endSyncCallbacks.remove(update.preparedHandle)
                    crossfadeActive = false
                } else {
                    applyPreparedUpdate(update)
                }
            }
        }.onFailure { error ->
            applyPreparedUpdate(preparedBassPlaybackFailed(error))
            lastError = preparedError
        }
    }

    override fun clearPreparedNext() {
        runtime.withPreparedPlaybackLock {
            preparedNextGeneration += 1L
            freePreparedStream()
        }
    }

    private fun stopActiveStream() {
        execution.invalidate()
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
            "Sample-rate matching" to sampleRateMatching.label,
            "Sample-rate converter" to "${sampleRateConverter.label} (BASS quality ${sampleRateConverter.bassQuality})",
            "Track source sample rate" to lastRequestedSampleRateHz.sampleRateDiagnosticLabel("Unknown"),
            "Requested output sample rate" to lastTargetOutputSampleRateHz.sampleRateDiagnosticLabel("Device default"),
            "Active output sample rate" to activeOutputSampleRateHz.sampleRateDiagnosticLabel("Device default/fallback"),
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

    private fun ensureInitialized(
        bass: BassAudioBackend,
        targetSampleRateHz: Int? = null,
    ) {
        if (initialized && targetSampleRateHz != null && targetSampleRateHz != activeOutputSampleRateHz) {
            runCatching { bass.free().getOrThrow() }
                .onFailure { lastError = it.message }
            initialized = false
            internetStreamsConfigured = false
            activeOutputSampleRateHz = null
        }
        if (!initialized) {
            bass.configurePlaybackBuffers(bufferPolicy).getOrThrow()
            val initResult = if (targetSampleRateHz != null) {
                bass.init(selectedOutputDeviceId, targetSampleRateHz)
                    .onFailure { lastError = it.message }
            } else {
                bass.init(selectedOutputDeviceId)
            }
            if (initResult.isFailure && targetSampleRateHz != null) {
                bass.init(selectedOutputDeviceId).getOrThrow()
                activeOutputSampleRateHz = null
            } else {
                initResult.getOrThrow()
                activeOutputSampleRateHz = targetSampleRateHz
            }
            initialized = true
        } else if (bass.supportsOutputDeviceSelection || selectedOutputDeviceId != null) {
            bass.setOutputDevice(selectedOutputDeviceId).getOrThrow()
        }
        bass.setVerifyNet(verifyNetworkCertificates).getOrThrow()
        bass.setSampleRateConverterQuality(sampleRateConverter.bassQuality).getOrThrow()
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
        val previousJob = job
        job = null
        val currentPlaybackId = execution.replaceCurrentExecution {
            previousJob?.cancel()
        }
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
        job = scope.launch(runtime.workContext) {
            var pollingState = BassPlaybackPollingState()
            try {
                while (execution.isCurrent(currentPlaybackId)) {
                    val snapshot = bass.bassPlaybackSnapshot(stream, currentSourceStream)
                    val update = planBassPlaybackPollingUpdate(
                        snapshot = snapshot,
                        previous = pollingState,
                        policy = pollingPolicy,
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
                    delay(pollingPolicy.pollIntervalMillis)
                }

                if (pollingPolicy.finishWhenPollingStops && execution.isCurrent(currentPlaybackId)) {
                    onStateChanged(PlaybackState.Finished)
                }
            } catch (exception: Throwable) {
                if (execution.isCurrent(currentPlaybackId) && job?.isCancelled != true) {
                    val message = exception.message ?: "BASS playback failed."
                    lastError = message
                    onStateChanged(PlaybackState.Error(message))
                }
            } finally {
                if (execution.isCurrent(currentPlaybackId)) {
                    if (!shouldRetainPreparedPlaybackAfterCurrentFinishes(preparedStream)) {
                        val reset = freeAllStreams(bass)
                        applyStreamReset(reset.stream)
                        onProgressChanged(PlaybackProgress.Unknown)
                    }
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
        val localPath = if (plan.isLocalFileUrl) runtime.localFilePath(request.url) else null
        return bass.createBassPlayback(
            localPath = localPath,
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
            if (channel.value == source && execution.isCurrent(currentPlaybackId)) {
                (stateCallback ?: execution.callbacks?.onStateChanged)?.invoke(PlaybackState.Finished)
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
        playbackUserVolumeFactor(volumePercent, transientOutputVolumeFactor)

    protected fun setTransientOutputVolumeFactor(factor: Float) {
        transientOutputVolumeFactor = factor.coerceIn(0f, 1f)
        backend?.let(::applyOutputVolume)
    }

    override fun setNetworkCertificateVerification(enabled: Boolean) {
        verifyNetworkCertificates = enabled
        backend?.setVerifyNet(enabled)
            ?.onFailure { lastError = it.message }
    }

    private fun visualizerFrameFor(
        bass: BassAudioBackend,
        sourceHandle: Int,
    ): PlaybackVisualizerFrame? =
        bass.bassPlaybackVisualizerFrame(
            stream = sourceHandle,
            bins = VisualizerBandCount,
            timestampMillis = runtime.nowEpochMillis(),
        )
            .onFailure { lastError = it.message }
            .getOrNull()

    private fun seekCurrentSource(bass: BassAudioBackend, seconds: Double): Result<Unit> =
        bass.seekBassPlaybackSource(stream, currentSourceStream, seconds)
            .onFailure { lastError = it.message }

    private fun setPlaybackMuted(bass: BassAudioBackend, muted: Boolean) {
        bass.setBassPlaybackMuted(
            outputStream = stream,
            sourceStream = currentSourceStream,
            muted = muted,
            userVolumeFactor = outputVolumeFactor(),
            replayGainFactor = currentReplayGainAdjustment.volumeFactor,
        ).forEach { result -> result.onFailure { lastError = it.message } }
    }

    private suspend fun retryStartSeek(
        bass: BassAudioBackend,
        playbackHandle: Int,
        currentPlaybackId: Int,
        positionSeconds: Double,
    ): Boolean {
        repeat(DefaultStartSeekRetryCount) {
            if (stream != playbackHandle || !execution.isCurrent(currentPlaybackId)) return false
            delay(DefaultStartSeekRetryDelayMillis)
            if (seekCurrentSource(bass, positionSeconds).isSuccess) return true
        }
        return false
    }

    private fun restartCurrentPlaybackAfterResumeFailure(bass: BassAudioBackend) {
        val scope = currentScope
        val request = execution.currentRequest
        val callbacks = execution.callbacks
        val stateCallback = callbacks?.onStateChanged
        val progressCallback = callbacks?.onProgressChanged
        val metadataCallback = callbacks?.onMetadataChanged
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
        activeOutputSampleRateHz = null
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
    "${if (this >= 0.0) "+" else ""}$this"

private fun Float.formatFactor(): String =
    toString()

private fun Double.formatPeak(): String =
    toString()

private fun Int?.sampleRateDiagnosticLabel(fallback: String): String {
    val sampleRateHz = this ?: return fallback
    val wholeKhz = sampleRateHz / 1_000
    val remainderHz = sampleRateHz % 1_000
    val khz = if (remainderHz == 0) {
        wholeKhz.toString()
    } else {
        "$wholeKhz.${remainderHz.toString().padStart(3, '0').trimEnd('0')}"
    }
    return "$khz kHz ($sampleRateHz Hz)"
}

private fun EqualizerSettings.bandsForBackend(): List<Float> =
    if (enabled) bandsDb else emptyList()

private const val DefaultStartSeekRetryCount = 80
private const val DefaultStartSeekRetryDelayMillis = 100L
