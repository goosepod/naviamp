package app.naviamp.presentation

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.playback.NamedMediaKind
import app.naviamp.domain.playback.NamedMediaMatchStatus
import app.naviamp.domain.playback.NamedMediaRequest
import app.naviamp.domain.playback.namedMediaMatch
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.ui.NaviampVoiceFailure
import kotlinx.coroutines.CancellationException

enum class NaviampNamedMediaStatus {
    Started, NoSource, NoMatch, Ambiguous, Unsupported, Empty, Failed, SourceChanged,
}

data class NaviampNamedMediaResult(
    val status: NaviampNamedMediaStatus,
    val kind: NamedMediaKind,
    val name: String,
)

fun NaviampNamedMediaStatus.voiceFailure(): NaviampVoiceFailure? = when (this) {
    NaviampNamedMediaStatus.Started -> null
    NaviampNamedMediaStatus.NoSource -> NaviampVoiceFailure.NoSource
    NaviampNamedMediaStatus.NoMatch -> NaviampVoiceFailure.NoMatch
    NaviampNamedMediaStatus.Ambiguous -> NaviampVoiceFailure.Ambiguous
    NaviampNamedMediaStatus.Unsupported -> NaviampVoiceFailure.Unsupported
    NaviampNamedMediaStatus.Empty,
    NaviampNamedMediaStatus.Failed,
    NaviampNamedMediaStatus.SourceChanged,
    -> NaviampVoiceFailure.PlaybackFailed
}

sealed interface NaviampNamedMediaSelection {
    data class ArtistRadio(val artist: Artist) : NaviampNamedMediaSelection
    data class ArtistCatalog(val artist: Artist, val tracks: List<Track>) : NaviampNamedMediaSelection
    data class AlbumCatalog(val album: Album, val tracks: List<Track>) : NaviampNamedMediaSelection
    data class PlaylistCatalog(val playlist: Playlist, val tracks: List<Track>) : NaviampNamedMediaSelection
}

fun interface NaviampNamedMediaPlayback {
    suspend fun play(selection: NaviampNamedMediaSelection): Boolean
}

/** Resolves and executes named voice requests against the active provider catalog. */
class NaviampCoreNamedMediaController(
    private val currentProvider: () -> MediaProvider?,
    private val isCurrent: (MediaProvider) -> Boolean,
    private val playback: NaviampNamedMediaPlayback,
) {
    suspend fun play(request: NamedMediaRequest): NaviampNamedMediaResult {
        fun result(status: NaviampNamedMediaStatus) = NaviampNamedMediaResult(status, request.kind, request.name)
        val provider = currentProvider() ?: return result(NaviampNamedMediaStatus.NoSource)
        try {
            val selected = when (request.kind) {
                NamedMediaKind.ArtistRadio, NamedMediaKind.Artist -> {
                    val match = namedMediaMatch(request.name, provider.search(request.name, 50).artists, Artist::name)
                    if (match.status != NamedMediaMatchStatus.Matched) return result(match.status.toResult())
                    val artist = match.item ?: return result(NaviampNamedMediaStatus.NoMatch)
                    if (request.kind == NamedMediaKind.ArtistRadio && !provider.capabilities.supportsArtistRadio) {
                        return result(NaviampNamedMediaStatus.Unsupported)
                    }
                    if (request.kind == NamedMediaKind.Artist) {
                        val tracks = provider.artist(artist.id).albums.flatMap { provider.album(it.id).tracks }
                        NaviampNamedMediaSelection.ArtistCatalog(artist, tracks)
                    } else NaviampNamedMediaSelection.ArtistRadio(artist)
                }
                NamedMediaKind.Album -> {
                    val match = namedMediaMatch(request.name, provider.search(request.name, 50).albums, Album::title)
                    if (match.status != NamedMediaMatchStatus.Matched) return result(match.status.toResult())
                    val album = match.item ?: return result(NaviampNamedMediaStatus.NoMatch)
                    NaviampNamedMediaSelection.AlbumCatalog(album, provider.album(album.id).tracks)
                }
                NamedMediaKind.Playlist -> {
                    val match = namedMediaMatch(request.name, provider.playlists(5_000), Playlist::name)
                    if (match.status != NamedMediaMatchStatus.Matched) return result(match.status.toResult())
                    val playlist = match.item ?: return result(NaviampNamedMediaStatus.NoMatch)
                    NaviampNamedMediaSelection.PlaylistCatalog(playlist, provider.playlistTracks(playlist.id))
                }
            }
            if (!isCurrent(provider)) return result(NaviampNamedMediaStatus.SourceChanged)
            val tracks = when (selected) {
                is NaviampNamedMediaSelection.ArtistRadio -> null
                is NaviampNamedMediaSelection.ArtistCatalog -> selected.tracks
                is NaviampNamedMediaSelection.AlbumCatalog -> selected.tracks
                is NaviampNamedMediaSelection.PlaylistCatalog -> selected.tracks
            }
            if (tracks != null && tracks.isEmpty()) {
                return result(NaviampNamedMediaStatus.Empty)
            }
            return result(if (playback.play(selected)) {
                NaviampNamedMediaStatus.Started
            } else NaviampNamedMediaStatus.Failed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return result(NaviampNamedMediaStatus.Failed)
        }
    }
}

private fun NamedMediaMatchStatus.toResult(): NaviampNamedMediaStatus = when (this) {
    NamedMediaMatchStatus.Matched -> error("A matched item is handled before status mapping")
    NamedMediaMatchStatus.Missing -> NaviampNamedMediaStatus.NoMatch
    NamedMediaMatchStatus.Ambiguous -> NaviampNamedMediaStatus.Ambiguous
}
