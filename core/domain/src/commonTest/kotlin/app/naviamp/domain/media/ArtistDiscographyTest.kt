package app.naviamp.domain.media

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistCredit
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals

class ArtistDiscographyTest {
    @Test
    fun reconcilesAliasesMissingMappedIdsDuplicatesAndPrimaryReleases() {
        val primaryAlbum = album("primary", "Own Album", "Featured Artist")
        val compilation = album("compilation", "Compilation", "Various Artists")
        val artistId = ArtistId("artist")
        val aliasCredit = ArtistCredit(artistId, "Stage Name")
        val missingIdCredit = ArtistCredit(null, "Featured Artist")
        val primaryTrack = track("primary-track", primaryAlbum.id, listOf(aliasCredit))
        val aliasedFeature = track("feature", compilation.id, listOf(aliasCredit))
        val unmappedRemix = track("remix", compilation.id, listOf(missingIdCredit))
        val standalone = track("standalone", null, listOf(missingIdCredit))

        val reconciled = ArtistDiscography(
            primary = ArtistDetails(Artist(artistId, "Featured Artist"), listOf(primaryAlbum)),
            appearanceAlbums = listOf(primaryAlbum, compilation, compilation),
            appearanceTracks = listOf(
                primaryTrack,
                aliasedFeature,
                aliasedFeature,
                unmappedRemix,
                standalone,
            ),
        ).reconciledAppearances()

        assertEquals(listOf("compilation"), reconciled.appearanceAlbums.map { it.id.value })
        assertEquals(listOf("feature", "remix", "standalone"), reconciled.appearanceTracks.map { it.id.value })
    }
}

private fun album(id: String, title: String, artistName: String) =
    Album(AlbumId(id), title, artistName, null, null)

private fun track(id: String, albumId: AlbumId?, credits: List<ArtistCredit>) = Track(
    id = TrackId(id),
    title = id,
    artistId = credits.firstOrNull()?.id,
    artistName = credits.joinToString(" & ") { it.name },
    albumId = albumId,
    albumTitle = null,
    durationSeconds = null,
    coverArtId = null,
    audioInfo = null,
    replayGain = null,
    artistCredits = credits,
)
