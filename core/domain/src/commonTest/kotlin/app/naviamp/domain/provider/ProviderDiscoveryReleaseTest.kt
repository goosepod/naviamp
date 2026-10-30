package app.naviamp.domain.provider

import app.naviamp.domain.media.AlbumReleaseSection
import kotlin.test.*

class ProviderDiscoveryReleaseTest {
    @Test fun discoveryUsesTheSamePrimaryAndSecondaryReleasePolicyAsLibraryAlbums() {
        val expected = listOf("Album" to AlbumReleaseSection.Albums, "EP" to AlbumReleaseSection.Eps,
            "Single" to AlbumReleaseSection.Singles, "Broadcast" to AlbumReleaseSection.Other,
            "Mixtape/Street" to AlbumReleaseSection.Mixtapes)
        expected.forEach { (type, section) -> assertEquals(section, ProviderDiscoveryRelease("id", "Title", type = type).section) }
        listOf("Live" to AlbumReleaseSection.Live, "Compilation" to AlbumReleaseSection.Compilations,
            "Remix" to AlbumReleaseSection.Remixes, "Soundtrack" to AlbumReleaseSection.Soundtracks).forEach { (type, section) ->
            assertEquals(section, ProviderDiscoveryRelease("id", "Title", type = "Album", secondaryTypes = listOf(type)).section)
        }
    }
}
