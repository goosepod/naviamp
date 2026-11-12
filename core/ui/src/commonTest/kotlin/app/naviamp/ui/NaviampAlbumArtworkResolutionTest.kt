package app.naviamp.ui

import app.naviamp.domain.*
import kotlin.test.*

class NaviampAlbumArtworkResolutionTest {
    @Test fun albumHeadersPreferServerHeroArtworkWhileRowsKeepThumbnails() {
        val album = Album(AlbumId("album"), "Album", "Artist", coverArtId = "cover", recentlyAddedAtIso8601 = null)
        val detail = AlbumDetails(album, emptyList(), AlbumInfo(largeImageUrl = "external-small"))
        val ui = detail.toSharedAlbumDetailUi(
            coverArtUrl = { "thumb-$it" }, heroCoverArtUrl = { "hero-$it" })
        assertEquals("hero-cover", ui.album.coverArtUrl)
        assertEquals("external-small", ui.album.fallbackCoverArtUrl)
        assertEquals("external-small", detail.toSharedAlbumDetailUi(
            coverArtUrl = { null }, heroCoverArtUrl = { null }).album.coverArtUrl)
    }
}
