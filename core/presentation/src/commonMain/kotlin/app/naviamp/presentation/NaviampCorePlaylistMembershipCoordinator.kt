package app.naviamp.presentation

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.ui.NaviampPlaylistMembershipRowUi
import app.naviamp.ui.NaviampTrackPlaylistMembershipUi
import app.naviamp.ui.toPlaylistChoiceUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Source-scoped membership transactions shared by all song entry points. */
class NaviampCorePlaylistMembershipCoordinator(
    private val providerSource: NaviampCoreMediaProviderSource,
    private val currentEditor: () -> NaviampTrackPlaylistMembershipUi?,
    private val publish: (NaviampTrackPlaylistMembershipUi?) -> Unit,
    private val onPlaylistChanged: suspend (String) -> Unit = {},
    private val onPlaylistCreated: (app.naviamp.domain.Playlist) -> Unit = {},
    private val onContentsReconciled: (String, List<app.naviamp.domain.Track>) -> Unit = { _, _ -> },
) {
    private var generation = 0L
    private var editorSource: Pair<String, String>? = null
    private var track: Track? = null

    suspend fun open(track: Track) {
        if (currentEditor()?.saving == true) return
        this.track = track
        val request = ++generation
        val provider = providerSource.current()
        editorSource = provider?.sourceKey()
        var editor = NaviampTrackPlaylistMembershipUi(
            trackId = track.id.value, trackTitle = track.title,
            loading = provider != null, unavailable = provider == null,
        )
        publish(editor)
        if (provider == null) return
        val key = provider.sourceKey()
        try {
            val playlists = attempt { provider.playlists(MaximumPlaylists + 1) }
            if (!isCurrent(request, key)) return
            if (playlists.isFailure) {
                publish(editor.copy(loading = false, loadingFailed = true))
                return
            }
            val all = playlists.getOrThrow()
            val choices = all.take(MaximumPlaylists)
            editor = editor.copy(truncated = all.size > MaximumPlaylists)
            val rows = mutableListOf<NaviampPlaylistMembershipRowUi>()
            for (chunk in choices.chunked(ConcurrentReads)) {
                if (!isCurrent(request, key)) return
                rows += coroutineScope {
                    chunk.map { choice -> async {
                        val contents = attempt { provider.playlistTracks(choice.id) }
                        val selected = contents.getOrNull()?.any { it.id == track.id } == true
                        NaviampPlaylistMembershipRowUi(choice.toPlaylistChoiceUi(), selected, selected, failed = contents.isFailure, ruleBased = choice.isSmart)
                    } }.awaitAll()
                }
                if (!isCurrent(request, key)) return
                publish(editor.copy(rows = rows.toList()))
            }
            publish(editor.copy(rows = rows, loading = false))
        } catch (cause: CancellationException) {
            if (isCurrent(request, key)) publish(currentEditor()?.copy(loading = false, loadingFailed = true))
            throw cause
        }
    }

    fun toggle(id: String) {
        val editor = currentEditor() ?: return
        if (editor.loading || editor.saving || editor.unavailable) return
        publish(editor.copy(saved = false, rows = editor.rows.map {
            if (it.playlist.id == id && !it.failed && !it.ruleBased) it.copy(selected = !it.selected) else it
        }))
    }

    suspend fun retry() {
        val editor = currentEditor() ?: return
        if (editor.loading || editor.saving) return
        if (editor.loadingFailed || editor.unavailable) {
            track?.let { open(it) }
            return
        }
        val provider = providerSource.current() ?: return
        val key = provider.sourceKey()
        if (key != editorSource) return
        val request = ++generation
        publish(editor.copy(loading = true))
        try {
            val rows = editor.rows.map { row ->
                if (!isCurrent(request, key)) return
                if (!row.failed) row else {
                    val result = attempt { provider.playlistTracks(row.playlist.id) }
                    val selected = result.getOrNull()?.any { it.id.value == editor.trackId }
                    row.copy(selected = selected ?: row.selected,
                        originallySelected = selected ?: row.originallySelected, failed = result.isFailure)
                }
            }
            if (isCurrent(request, key)) publish(editor.copy(rows = rows, loading = false))
        } finally {
            if (isCurrent(request, key)) publish(currentEditor()?.copy(loading = false))
        }
    }

    suspend fun apply() {
        val editor = currentEditor() ?: return
        if (editor.loading || editor.saving || editor.unavailable || editor.loadingFailed) return
        val provider = providerSource.current()
        if (provider == null || provider.sourceKey() != editorSource) {
            publish(editor.copy(unavailable = true))
            return
        }
        val key = provider.sourceKey()
        val request = ++generation
        publish(editor.copy(saving = true, saved = false))
        val rows = editor.rows.toMutableList()
        try {
            for ((index, row) in rows.withIndex()) {
                if (!isCurrent(request, key)) return
                if (row.ruleBased || row.failed || row.selected == row.originallySelected) continue
                val mutation = attempt {
                    val contents = provider.playlistTracks(row.playlist.id)
                    if (!isCurrent(request, key)) return@attempt
                    val id = TrackId(editor.trackId)
                    if (row.selected) {
                        if (contents.none { it.id == id }) provider.addTracksToPlaylist(row.playlist.id, listOf(id))
                    } else provider.removeTrackFromPlaylist(row.playlist.id, id)
                }
                if (!isCurrent(request, key)) return
                val contents = attempt { provider.playlistTracks(row.playlist.id) }
                val selected = contents.getOrNull()?.any { it.id.value == editor.trackId }
                rows[index] = row.copy(selected = selected ?: row.selected,
                    originallySelected = selected ?: row.originallySelected,
                    failed = mutation.isFailure || contents.isFailure || selected != row.selected)
                if (!isCurrent(request, key)) return
                // Publish each reconciled result so later failures cannot discard successful work.
                publish(editor.copy(rows = rows.toList(), saving = true))
                contents.getOrNull()?.let { onContentsReconciled(row.playlist.id, it) }
                attempt { onPlaylistChanged(row.playlist.id) }
            }
            if (isCurrent(request, key)) publish(editor.copy(rows = rows.toList(), saved = rows.none { it.failed }))
        } finally {
            if (isCurrent(request, key)) publish(currentEditor()?.copy(saving = false))
        }
    }

    suspend fun create(name: String) {
        val editor = currentEditor() ?: return
        val trimmed = name.trim()
        if (editor.loading || editor.saving || trimmed.isEmpty()) return
        val provider = providerSource.current() ?: return
        val key = provider.sourceKey()
        if (key != editorSource) return
        val request = ++generation
        publish(editor.copy(saving = true, creationFailed = false))
        try {
            val created = attempt { provider.createPlaylist(trimmed, listOf(TrackId(editor.trackId))) }
            if (!isCurrent(request, key)) return
            created.fold(onSuccess = { playlist ->
                onPlaylistCreated(playlist)
                publish(editor.copy(rows = editor.rows + NaviampPlaylistMembershipRowUi(
                    playlist.toPlaylistChoiceUi(), selected = true, originallySelected = true,
                ), creationFailed = false, saved = true))
            }, onFailure = { publish(editor.copy(creationFailed = true)) })
        } finally {
            if (isCurrent(request, key)) publish(currentEditor()?.copy(saving = false))
        }
    }

    fun dismiss() {
        val editor = currentEditor() ?: return
        if (editor.saving) return
        reset()
    }

    fun reset() {
        generation++
        track = null
        editorSource = null
        publish(null)
    }

    private fun isCurrent(request: Long, key: Pair<String, String>) =
        request == generation && currentEditor() != null && providerSource.current()?.sourceKey() == key

    private fun MediaProvider.sourceKey() = id.value to cacheNamespace

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        Result.failure(cause)
    }

    private companion object {
        const val MaximumPlaylists = 100
        const val ConcurrentReads = 4
    }
}
