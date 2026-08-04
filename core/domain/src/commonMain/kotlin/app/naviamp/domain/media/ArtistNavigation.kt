package app.naviamp.domain.media

import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Track
import app.naviamp.domain.resolvedArtistCredits
import app.naviamp.domain.provider.MediaProvider

/**
 * Resolves one clicked artist credit without ever substituting a combined-artist identity.
 *
 * Some provider responses expose a display credit such as "HUGEL, David Guetta, Kehlani, Daecolm"
 * with only one combined artist ID. Individual names remain independently navigable by resolving
 * an exact provider artist match when their structured credit ID is absent.
 */
suspend fun resolveTrackArtistNavigation(
    track: Track,
    requestedId: String?,
    requestedName: String?,
    searchArtists: suspend (query: String, limit: Int) -> List<Artist>,
): Artist? {
    val credits = track.resolvedArtistCredits()
    val normalizedRequestedName = requestedName?.trim()?.takeIf(String::isNotEmpty)
    val requestedIdIsCombinedIdentity = requestedId != null &&
        requestedId == track.artistId?.value &&
        normalizedRequestedName != null &&
        !track.artistName.trim().equals(normalizedRequestedName, ignoreCase = true)
    val requestedCredit = credits.firstOrNull { credit ->
        requestedId?.let { credit.id?.value == it }
            ?: normalizedRequestedName?.let { credit.name.equals(it, ignoreCase = true) }
            ?: false
    }
    requestedId?.takeUnless { requestedIdIsCombinedIdentity }?.let { id ->
        return Artist(ArtistId(id), requestedName ?: requestedCredit?.name ?: track.artistName)
    }
    requestedCredit?.id?.takeUnless { requestedIdIsCombinedIdentity }?.let { id ->
        return Artist(id, requestedName ?: requestedCredit.name)
    }
    normalizedRequestedName?.let { name ->
        val exactArtist = runCatching { searchArtists(name, ArtistNavigationSearchLimit) }
            .getOrDefault(emptyList())
            .firstOrNull { artist -> artist.name.trim().equals(name, ignoreCase = true) }
        if (exactArtist != null) return exactArtist
        if (requestedCredit != null) return nameOnlyArtistCredit(name)
    }
    return credits.firstOrNull { it.id != null }?.let { credit ->
        Artist(requireNotNull(credit.id), credit.name)
    } ?: track.artistId?.let { id -> Artist(id, track.artistName) }
}

/** A stable Core-only identity for a credited name that has no provider artist entity. */
fun nameOnlyArtistCredit(name: String): Artist =
    Artist(ArtistId("$NameOnlyArtistCreditPrefix${name.trim().lowercase()}"), name.trim())

fun Artist.isNameOnlyArtistCredit(): Boolean = id.value.startsWith(NameOnlyArtistCreditPrefix)

/** Builds a useful virtual artist catalog from provider search results for name-only credits. */
suspend fun loadNameOnlyArtistCreditDetails(
    provider: MediaProvider,
    artist: Artist,
): ArtistDetails {
    require(artist.isNameOnlyArtistCredit())
    val results = provider.search(artist.name, NameOnlyArtistCatalogSearchLimit)
    val matchingTracks = results.tracks.filter { track ->
        track.resolvedArtistCredits().any { credit -> credit.name.equals(artist.name, ignoreCase = true) }
    }
    val trackAlbums = artistDetailsFromLibraryTracks(artist.id, artist.name, matchingTracks)
        ?.albums
        .orEmpty()
    val matchingAlbums = results.albums.filter { album ->
        album.resolvedArtistCredits().any { credit -> credit.name.equals(artist.name, ignoreCase = true) }
    }
    return ArtistDetails(
        artist = artist,
        albums = (matchingAlbums + trackAlbums).distinctBy { album -> album.id },
    )
}

private const val ArtistNavigationSearchLimit = 20
private const val NameOnlyArtistCatalogSearchLimit = 500
private const val NameOnlyArtistCreditPrefix = "naviamp:artist-credit:"
