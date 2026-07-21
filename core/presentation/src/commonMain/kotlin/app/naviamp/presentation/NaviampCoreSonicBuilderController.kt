package app.naviamp.presentation

import app.naviamp.domain.Track
import app.naviamp.domain.sonicmix.SonicMixBias
import app.naviamp.domain.sonicmix.SonicMixDefaultTargetLength
import app.naviamp.domain.sonicmix.SonicMixMaxSeeds
import app.naviamp.domain.sonicmix.SonicMixMaxTargetLength
import app.naviamp.domain.sonicmix.SonicMixMinTargetLength
import app.naviamp.domain.sonicmix.SonicMixRequest
import app.naviamp.domain.sonicmix.SonicMixService
import app.naviamp.domain.sonicpath.SonicPathDefaultCount
import app.naviamp.domain.sonicpath.SonicPathMaxCount
import app.naviamp.domain.sonicpath.SonicPathMinCount
import app.naviamp.domain.sonicpath.SonicPathRequest
import app.naviamp.domain.sonicpath.SonicPathService
import app.naviamp.ui.SharedSonicMixBiasUi
import app.naviamp.ui.SharedSonicMixBuilderUi
import app.naviamp.ui.SharedSonicPathBuilderUi
import app.naviamp.ui.toSharedTrackRowUi

fun interface NaviampCoreSonicPlaybackPort {
    suspend fun play(tracks: List<Track>, label: String)
}

fun interface NaviampCoreSonicQueuePort {
    suspend fun add(tracks: List<Track>, label: String)
}

/** Owns Sonic Path and Sonic Mix builder state, generation, validation, queue, and save policy. */
class NaviampCoreSonicBuilderController(
    private val stateStore: NaviampCoreStateStore,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val playlistBrowseController: NaviampCorePlaylistBrowseController,
    private val playback: NaviampCoreSonicPlaybackPort,
    private val queue: NaviampCoreSonicQueuePort,
) : NaviampCoreCommandController {
    private var startTrack: Track? = null
    private var endTrack: Track? = null
    private var startSuggestions = emptyList<Track>()
    private var endSuggestions = emptyList<Track>()
    private var pathTracks = emptyList<Track>()
    private var selectedMixTracks = emptyList<Track>()
    private var mixSuggestions = emptyList<Track>()
    private var mixTracks = emptyList<Track>()
    private var startSearchGeneration = 0L
    private var endSearchGeneration = 0L
    private var mixSearchGeneration = 0L
    private var pathBuildGeneration = 0L
    private var mixBuildGeneration = 0L

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult = when (command) {
        is NaviampCoreCommand.MixBuilder.SonicPath -> if (applyImmediatePath(command.action)) {
            NaviampCoreImmediateCommandResult.Handled()
        } else {
            NaviampCoreImmediateCommandResult.Deferred
        }
        is NaviampCoreCommand.MixBuilder.SonicMix -> if (applyImmediateMix(command.action)) {
            NaviampCoreImmediateCommandResult.Handled()
        } else {
            NaviampCoreImmediateCommandResult.Deferred
        }
        else -> NaviampCoreImmediateCommandResult.Unhandled
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        when (command) {
            is NaviampCoreCommand.MixBuilder.SonicPath ->
                if (!applyImmediatePath(command.action)) executePath(command.action)
            is NaviampCoreCommand.MixBuilder.SonicMix ->
                if (!applyImmediateMix(command.action)) executeMix(command.action)
            else -> return null
        }
        return NaviampCoreCommandResult.Completed
    }

    private fun applyImmediatePath(action: NaviampCoreCommand.SonicPathAction): Boolean {
        when (action) {
            is NaviampCoreCommand.SonicPathAction.ChangeStartQuery ->
                updatePathUi { it.copy(startQuery = action.query) }
            is NaviampCoreCommand.SonicPathAction.ChangeEndQuery ->
                updatePathUi { it.copy(endQuery = action.query) }
            is NaviampCoreCommand.SonicPathAction.ChangeCount ->
                updatePathUi { it.copy(count = action.count.coerceIn(SonicPathMinCount, SonicPathMaxCount)) }
            is NaviampCoreCommand.SonicPathAction.SelectStart -> {
                val track = startSuggestions.firstOrNull { it.id.value == action.track.id }
                if (track == null) {
                    updatePathStatus("Track is no longer available.")
                } else {
                    startTrack = track
                    startSuggestions = emptyList()
                    pathTracks = emptyList()
                    publishPath()
                }
            }
            is NaviampCoreCommand.SonicPathAction.SelectEnd -> {
                val track = endSuggestions.firstOrNull { it.id.value == action.track.id }
                if (track == null) {
                    updatePathStatus("Track is no longer available.")
                } else {
                    endTrack = track
                    endSuggestions = emptyList()
                    pathTracks = emptyList()
                    publishPath()
                }
            }
            NaviampCoreCommand.SonicPathAction.ClearStart -> {
                startTrack = null
                pathTracks = emptyList()
                publishPath()
            }
            NaviampCoreCommand.SonicPathAction.ClearEnd -> {
                endTrack = null
                pathTracks = emptyList()
                publishPath()
            }
            NaviampCoreCommand.SonicPathAction.Reset -> resetPath()
            NaviampCoreCommand.SonicPathAction.SearchStart,
            NaviampCoreCommand.SonicPathAction.SearchEnd,
            NaviampCoreCommand.SonicPathAction.Build,
            NaviampCoreCommand.SonicPathAction.Play,
            NaviampCoreCommand.SonicPathAction.AddToQueue,
            is NaviampCoreCommand.SonicPathAction.SaveAsPlaylist,
            -> return false
        }
        return true
    }

    private fun applyImmediateMix(action: NaviampCoreCommand.SonicMixAction): Boolean {
        when (action) {
            is NaviampCoreCommand.SonicMixAction.ChangeQuery -> updateMixUi { it.copy(query = action.query) }
            is NaviampCoreCommand.SonicMixAction.ChangeLength -> updateMixUi {
                it.copy(targetLength = action.length.coerceIn(SonicMixMinTargetLength, SonicMixMaxTargetLength))
            }
            is NaviampCoreCommand.SonicMixAction.ChangeBias -> {
                mixTracks = emptyList()
                updateMixUi { it.copy(bias = action.bias, mixTracks = emptyList()) }
            }
            is NaviampCoreCommand.SonicMixAction.Select -> {
                val track = mixSuggestions.firstOrNull { it.id.value == action.track.id }
                if (track == null) {
                    updateMixStatus("Track is no longer available.")
                } else if (selectedMixTracks.none { it.id == track.id }) {
                    selectedMixTracks = (selectedMixTracks + track).take(SonicMixMaxSeeds)
                    mixSuggestions = mixSuggestions.filterNot { it.id == track.id }
                    mixTracks = emptyList()
                    publishMix()
                }
            }
            is NaviampCoreCommand.SonicMixAction.Remove -> {
                selectedMixTracks = selectedMixTracks.filterNot { it.id.value == action.track.id }
                mixTracks = emptyList()
                publishMix()
            }
            NaviampCoreCommand.SonicMixAction.Reset -> resetMix()
            NaviampCoreCommand.SonicMixAction.Search,
            NaviampCoreCommand.SonicMixAction.Build,
            NaviampCoreCommand.SonicMixAction.Play,
            NaviampCoreCommand.SonicMixAction.AddToQueue,
            is NaviampCoreCommand.SonicMixAction.SaveAsPlaylist,
            -> return false
        }
        return true
    }

    private suspend fun executePath(action: NaviampCoreCommand.SonicPathAction) {
        when (action) {
            NaviampCoreCommand.SonicPathAction.SearchStart -> searchPathTracks(start = true)
            NaviampCoreCommand.SonicPathAction.SearchEnd -> searchPathTracks(start = false)
            NaviampCoreCommand.SonicPathAction.Build -> buildPath()
            NaviampCoreCommand.SonicPathAction.Play -> useGenerated(pathTracks, "sonic path", playback::play)
            NaviampCoreCommand.SonicPathAction.AddToQueue -> useGenerated(pathTracks, "sonic path", queue::add)
            is NaviampCoreCommand.SonicPathAction.SaveAsPlaylist -> save(pathTracks, action.name, "sonic path")
            else -> applyImmediatePath(action)
        }
    }

    private suspend fun executeMix(action: NaviampCoreCommand.SonicMixAction) {
        when (action) {
            NaviampCoreCommand.SonicMixAction.Search -> searchMixTracks()
            NaviampCoreCommand.SonicMixAction.Build -> buildMix()
            NaviampCoreCommand.SonicMixAction.Play -> useGenerated(mixTracks, "sonic mix", playback::play)
            NaviampCoreCommand.SonicMixAction.AddToQueue -> useGenerated(mixTracks, "sonic mix", queue::add)
            is NaviampCoreCommand.SonicMixAction.SaveAsPlaylist -> save(mixTracks, action.name, "sonic mix")
            else -> applyImmediateMix(action)
        }
    }

    private suspend fun searchPathTracks(start: Boolean) {
        val query = if (start) currentPathUi().startQuery else currentPathUi().endQuery
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            updatePathUi { it.copy(status = "Enter a track search.") }
            return
        }
        val provider = sonicProvider(::updatePathStatus) ?: return
        val generation = if (start) ++startSearchGeneration else ++endSearchGeneration
        updatePathUi { it.copy(loading = true, status = "Searching tracks...") }
        runCatching { provider.search(trimmed, 12).tracks }
            .onSuccess { tracks ->
                val current = if (start) startSearchGeneration else endSearchGeneration
                if (generation != current) return@onSuccess
                if (start) startSuggestions = tracks else endSuggestions = tracks
                publishPath(false, if (tracks.isEmpty()) "No matching tracks." else null)
            }
            .onFailure { cause ->
                val current = if (start) startSearchGeneration else endSearchGeneration
                if (generation == current) publishPath(false, cause.message ?: "Could not search tracks.")
            }
    }

    private suspend fun buildPath() {
        val provider = sonicProvider(::updatePathStatus) ?: return
        val start = startTrack
        val end = endTrack
        if (start == null || end == null || start.id == end.id) {
            updatePathStatus("Choose two different tracks first.")
            return
        }
        val generation = ++pathBuildGeneration
        updatePathUi { it.copy(loading = true, status = "Finding sonic path...") }
        runCatching { SonicPathService(provider).findPath(SonicPathRequest(start, end, currentPathUi().count)) }
            .onSuccess { tracks ->
                if (generation != pathBuildGeneration) return@onSuccess
                pathTracks = tracks
                publishPath(false, if (tracks.isEmpty()) "Sonic path did not return any tracks." else null)
            }
            .onFailure { cause ->
                if (generation == pathBuildGeneration) {
                    publishPath(false, cause.message ?: "Could not find sonic path.")
                }
            }
    }

    private suspend fun searchMixTracks() {
        val query = currentMixUi().query.trim()
        if (query.isEmpty()) {
            updateMixStatus("Enter a track search.")
            return
        }
        val provider = sonicProvider(::updateMixStatus) ?: return
        val generation = ++mixSearchGeneration
        updateMixUi { it.copy(loading = true, status = "Searching tracks...") }
        runCatching { provider.search(query, 12).tracks }
            .onSuccess { tracks ->
                if (generation != mixSearchGeneration) return@onSuccess
                val selectedIds = selectedMixTracks.map(Track::id).toSet()
                mixSuggestions = tracks.filterNot { it.id in selectedIds }
                publishMix(false, if (mixSuggestions.isEmpty()) "No matching tracks." else null)
            }
            .onFailure { cause ->
                if (generation == mixSearchGeneration) {
                    publishMix(false, cause.message ?: "Could not search tracks.")
                }
            }
    }

    private suspend fun buildMix() {
        val provider = sonicProvider(::updateMixStatus) ?: return
        if (selectedMixTracks.size < 2) {
            updateMixStatus("Choose at least two seed tracks first.")
            return
        }
        val generation = ++mixBuildGeneration
        updateMixUi { it.copy(loading = true, status = "Building sonic mix...") }
        runCatching {
            SonicMixService(provider).buildMix(
                SonicMixRequest(
                    seedTracks = selectedMixTracks,
                    targetLength = currentMixUi().targetLength,
                    bias = currentMixUi().bias.toDomain(),
                ),
            )
        }.onSuccess { tracks ->
            if (generation != mixBuildGeneration) return@onSuccess
            mixTracks = tracks
            publishMix(false, if (tracks.isEmpty()) "Sonic mix did not return any tracks." else null)
        }.onFailure { cause ->
            if (generation == mixBuildGeneration) {
                publishMix(false, cause.message ?: "Could not build sonic mix.")
            }
        }
    }

    private suspend fun useGenerated(
        tracks: List<Track>,
        label: String,
        operation: suspend (List<Track>, String) -> Unit,
    ) {
        if (tracks.isEmpty()) {
            if (label == "sonic path") updatePathStatus("Build a sonic path first.")
            else updateMixStatus("Build a sonic mix first.")
            return
        }
        runCatching { operation(tracks, label) }
            .onFailure { cause ->
                if (label == "sonic path") updatePathStatus(cause.message ?: "Could not use sonic path.")
                else updateMixStatus(cause.message ?: "Could not use sonic mix.")
            }
    }

    private suspend fun save(tracks: List<Track>, requestedName: String, label: String) {
        val name = requestedName.trim()
        if (name.isEmpty() || tracks.isEmpty()) {
            val status = if (tracks.isEmpty()) "Build a $label first." else "Playlist name cannot be blank."
            if (label == "sonic path") updatePathStatus(status) else updateMixStatus(status)
            return
        }
        val provider = providerSource.current()
        if (provider == null) {
            val status = "Connect to Navidrome to save a $label."
            if (label == "sonic path") updatePathStatus(status) else updateMixStatus(status)
            return
        }
        runCatching { provider.createPlaylist(name, tracks.map(Track::id)) }
            .onSuccess { playlistBrowseController.refreshAfterMutation("Saved $name.") }
            .onFailure { cause ->
                if (label == "sonic path") updatePathStatus(cause.message ?: "Could not save sonic path.")
                else updateMixStatus(cause.message ?: "Could not save sonic mix.")
            }
    }

    private fun sonicProvider(publishFailure: (String) -> Unit) = providerSource.current().also { provider ->
        val status = when {
            provider == null -> "Connect to Navidrome to use Sonic features."
            !provider.capabilities.supportsSonicSimilarity -> "The connected server does not support Sonic features."
            else -> null
        }
        status?.let(publishFailure)
    }?.takeIf { it.capabilities.supportsSonicSimilarity }

    private fun resetPath() {
        startTrack = null
        endTrack = null
        startSuggestions = emptyList()
        endSuggestions = emptyList()
        pathTracks = emptyList()
        updatePathUi { SharedSonicPathBuilderUi(count = SonicPathDefaultCount) }
    }

    private fun resetMix() {
        selectedMixTracks = emptyList()
        mixSuggestions = emptyList()
        mixTracks = emptyList()
        updateMixUi { SharedSonicMixBuilderUi(targetLength = SonicMixDefaultTargetLength) }
    }

    private fun publishPath(loading: Boolean = false, status: String? = currentPathUi().status) {
        val art = coverArtUrl()
        updatePathUi {
            it.copy(
                startTrack = startTrack?.toSharedTrackRowUi(art),
                endTrack = endTrack?.toSharedTrackRowUi(art),
                startSuggestions = startSuggestions.map { track -> track.toSharedTrackRowUi(art) },
                endSuggestions = endSuggestions.map { track -> track.toSharedTrackRowUi(art) },
                pathTracks = pathTracks.map { track -> track.toSharedTrackRowUi(art) },
                loading = loading,
                status = status,
            )
        }
    }

    private fun publishMix(loading: Boolean = false, status: String? = currentMixUi().status) {
        val art = coverArtUrl()
        updateMixUi {
            it.copy(
                selectedTracks = selectedMixTracks.map { track -> track.toSharedTrackRowUi(art) },
                suggestedTracks = mixSuggestions.map { track -> track.toSharedTrackRowUi(art) },
                mixTracks = mixTracks.map { track -> track.toSharedTrackRowUi(art) },
                loading = loading,
                status = status,
            )
        }
    }

    private fun coverArtUrl(): (String?) -> String? {
        val provider = providerSource.current()
        return { id -> id?.let { provider?.coverArtUrl(it) } }
    }

    private fun updatePathStatus(status: String) = updatePathUi { it.copy(loading = false, status = status) }
    private fun updateMixStatus(status: String) = updateMixUi { it.copy(loading = false, status = status) }
    private fun currentPathUi() = stateStore.state.value.shell.sonicPathBuilder
    private fun currentMixUi() = stateStore.state.value.shell.sonicMixBuilder
    private fun updatePathUi(transform: (SharedSonicPathBuilderUi) -> SharedSonicPathBuilderUi) {
        stateStore.updateShell { shell -> shell.copy(sonicPathBuilder = transform(shell.sonicPathBuilder)) }
    }
    private fun updateMixUi(transform: (SharedSonicMixBuilderUi) -> SharedSonicMixBuilderUi) {
        stateStore.updateShell { shell -> shell.copy(sonicMixBuilder = transform(shell.sonicMixBuilder)) }
    }
}

private fun SharedSonicMixBiasUi.toDomain(): SonicMixBias = when (this) {
    SharedSonicMixBiasUi.Balanced -> SonicMixBias.Balanced
    SharedSonicMixBiasUi.Favorites -> SonicMixBias.Favorites
    SharedSonicMixBiasUi.Unplayed -> SonicMixBias.Unplayed
    SharedSonicMixBiasUi.Recent -> SonicMixBias.Recent
}
