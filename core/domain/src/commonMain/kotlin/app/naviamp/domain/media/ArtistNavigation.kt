package app.naviamp.domain.media

import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.resolvedArtistCredits

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
        return runCatching { searchArtists(name, ArtistNavigationSearchLimit) }
            .getOrDefault(emptyList())
            .firstOrNull { artist -> artist.name.trim().equals(name, ignoreCase = true) }
    }
    return credits.firstOrNull { it.id != null }?.let { credit ->
        Artist(requireNotNull(credit.id), credit.name)
    } ?: track.artistId?.let { id -> Artist(id, track.artistName) }
}

private const val ArtistNavigationSearchLimit = 20
