package app.naviamp.domain.media

import app.naviamp.domain.Artist
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.resolvedArtistCredits
import app.naviamp.domain.navibeat.navibeatMixOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class ArtistPlaylists(
    val playlists: List<Playlist> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    val truncated: Boolean = false,
)

/** Match individual credits, preferring identities over names whenever both are available. */
fun Track.isCreditedTo(artist: Artist): Boolean = resolvedArtistCredits().any { credit ->
    if (!artist.isNameOnlyArtistCredit() && credit.id != null) credit.id == artist.id
    else credit.name.trim().equals(artist.name.trim(), ignoreCase = true)
}

/** Bounded, cancellable lookup. Never mistakes shared editing permission for creator ownership. */
suspend fun loadArtistPlaylists(
    provider: MediaProvider,
    artist: Artist,
    publish: (ArtistPlaylists) -> Unit,
    isCurrent: () -> Boolean = { true },
) {
    if (!isCurrent()) return
    var state = ArtistPlaylists(loading = true)
    publish(state)
    val listed = artistPlaylistAttempt { provider.playlists(MaximumArtistPlaylists + 1) }
    if (!isCurrent()) return
    if (listed.isFailure) {
        publish(state.copy(loading = false, failed = true))
        return
    }
    val choices = listed.getOrThrow().distinctBy { it.id }
    state = state.copy(truncated = listed.getOrThrow().size > MaximumArtistPlaylists)
    for (chunk in choices.take(MaximumArtistPlaylists).chunked(ArtistPlaylistConcurrentReads)) {
        if (!isCurrent()) return
        val results = coroutineScope {
            chunk.map { playlist -> async {
                artistPlaylistAttempt {
                    if (!isCurrent() || playlist.navibeatMixOrNull() != null) return@artistPlaylistAttempt null
                    when (provider.playlistOwnedByCurrentUser(playlist)) {
                        true -> playlist.takeIf { provider.playlistTracks(it.id).any { track -> track.isCreditedTo(artist) } }
                        false -> null
                        null -> error("Playlist ownership unavailable")
                    }
                }
            } }.awaitAll()
        }
        if (!isCurrent()) return
        state = state.copy(
            playlists = (state.playlists + results.mapNotNull { it.getOrNull() })
                .sortedWith(compareBy<Playlist> { it.name.lowercase() }.thenBy { it.id }),
            failed = state.failed || results.any { it.isFailure },
        )
        publish(state)
    }
    publish(state.copy(loading = false))
}

private suspend fun <T> artistPlaylistAttempt(block: suspend () -> T): Result<T> = try {
    Result.success(withTimeout(ArtistPlaylistRequestTimeoutMillis) { block() })
} catch (cause: TimeoutCancellationException) {
    currentCoroutineContext().ensureActive()
    Result.failure(cause)
} catch (cause: CancellationException) {
    throw cause
} catch (cause: Exception) {
    Result.failure(cause)
}

private const val MaximumArtistPlaylists = 500
private const val ArtistPlaylistConcurrentReads = 4
private const val ArtistPlaylistRequestTimeoutMillis = 15_000L
