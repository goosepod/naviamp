package app.naviamp.domain.home

import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.settings.FavoriteArtistSort

enum class FavoriteArtistsStatus { Unsupported, Ready, Cached, Failed }

/** Missing timestamps sort last; stable names and identities resolve every tie. */
fun List<Artist>.sortedFavoriteArtists(sort: FavoriteArtistSort, lastPlayed: Map<ArtistId, String>): List<Artist> {
    fun timestamp(artist: Artist): String? = when (sort) {
        FavoriteArtistSort.Name -> null
        FavoriteArtistSort.DateFavorited -> artist.favoritedAtIso8601
        FavoriteArtistSort.LastRadioPlayed -> lastPlayed[artist.id]
    }?.takeUnless { it.isBlank() || it == "favorite" }
    return sortedWith(compareByDescending<Artist> { timestamp(it) }
        .thenBy { it.name.lowercase() }.thenBy { it.name }.thenBy { it.id.value })
}
