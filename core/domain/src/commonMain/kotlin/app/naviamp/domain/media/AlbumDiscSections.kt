package app.naviamp.domain.media

import app.naviamp.domain.Track

data class AlbumDiscSection(
    val number: Int,
    val title: String?,
    val tracks: List<Track>,
    val coverArtId: String? = null,
)

/** Untagged/invalid discs belong to disc 1; ties and unnumbered tracks keep provider order. */
fun List<Track>.albumDiscSections(): List<AlbumDiscSection> =
    groupBy { it.discNumber?.takeIf { number -> number > 0 } ?: 1 }
        .entries.sortedBy { it.key }
        .map { (number, tracks) ->
            AlbumDiscSection(
                number = number,
                title = tracks.firstNotNullOfOrNull { it.discTitle?.trim()?.takeIf(String::isNotEmpty) },
                coverArtId = tracks.firstNotNullOfOrNull { it.discCoverArtId?.trim()?.takeIf(String::isNotEmpty) },
                tracks = tracks.sortedBy { it.trackNumber?.takeIf { number -> number > 0 } ?: Int.MAX_VALUE },
            )
        }

fun List<Track>.inAlbumOrder(): List<Track> = albumDiscSections().flatMap { it.tracks }
