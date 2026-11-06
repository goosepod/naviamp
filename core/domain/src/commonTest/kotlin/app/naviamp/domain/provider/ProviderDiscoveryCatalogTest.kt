package app.naviamp.domain.provider

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ProviderDiscoveryCatalogTest {
    @Test fun discoveryNamespacesRejectLibraryAndPreviewIds() {
        assertFailsWith<IllegalArgumentException> { DiscoveryArtistMbid("artist:7") }
        assertFailsWith<IllegalArgumentException> { DiscoveryReleaseGroupMbid("7") }
        assertFailsWith<IllegalArgumentException> { DiscoveryRecordingMbid("42") }
        assertFailsWith<IllegalArgumentException> { DiscoveryDeezerPreviewId("01234567-89ab-cdef-0123-456789abcdef") }
        assertFailsWith<IllegalArgumentException> { DiscoveryCanonicalAlbumId("0") }
        assertFailsWith<IllegalArgumentException> { DiscoveryJobId(" ") }
        assertFailsWith<IllegalArgumentException> { DiscoveryRequestGroupId("") }
        assertNotEquals(DiscoveryManagedAlbumId(DiscoveryLibraryManager.Aurral, "7"),
            DiscoveryManagedAlbumId(DiscoveryLibraryManager.Lidarr, "7"))
    }

    @Test fun existingDiscoveryProvidersRemainUnsupportedForNewOptionalContracts() = runTest {
        val discovery = ProviderDiscovery { ProviderDiscoveryFeed() }
        assertEquals(ProviderDiscoveryStatus.Unsupported, discovery.recommendations().status)
        assertEquals(ProviderDiscoveryStatus.Unsupported, discovery.searchCatalog("artist", ProviderCatalogScope.Artist).status)
        assertEquals(ProviderDiscoveryStatus.Unsupported, discovery.requestActivity().status)
    }
}
