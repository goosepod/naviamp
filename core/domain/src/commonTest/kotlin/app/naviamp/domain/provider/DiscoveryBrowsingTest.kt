package app.naviamp.domain.provider

import kotlin.test.*
import kotlin.time.Instant

class DiscoveryBrowsingTest {
    private fun artist(n: Int, depth: Int? = null, date: String? = null, seed: String = "Seed") = ProviderDiscoveryArtist(
        n.toString(), "Artist $n", genres = listOf("Rock"), recommendation = ProviderRecommendationContext(
            rank = n, sourceArtists = listOf(seed), depth = depth, firstDiscoveredAt = date))
    @Test fun genreFilterMatchesTheProvidersSeparatorInsensitiveIdentity() {
        val entry = artist(1).copy(genres = listOf("alternative_rock"))
        assertEquals(listOf(entry), DiscoveryBrowserState(recommendations = listOf(entry), genre = "Alternative Rock").filtered())
        assertTrue(DiscoveryBrowserState(recommendations = listOf(entry), genre = "jazz").filtered().isEmpty())
    }
    @Test fun groupingExcludesFirstCohortAndNeverDuplicatesPicks() {
        val now = Instant.parse("2026-11-05T00:00:00Z").toEpochMilliseconds()
        val rows = (1..8).map { artist(it, 1, "2026-11-01T00:00:00Z") } +
            artist(9, 2, "2026-11-04T00:00:00Z") + artist(10, 2, "2026-11-01T00:00:00Z")
        val screen = DiscoveryBrowserState(recommendations = rows, seeds = listOf(ProviderDiscoverySeed("Seed")), nowMillis = now)
        assertFalse(screen.isNew(rows.first()))
        assertTrue(screen.isNew(rows[8]))
        val groups = screen.groups()
        assertEquals(10, groups.flatMap { it.second }.size)
        assertEquals(10, groups.flatMap { it.second }.distinctBy { it.id }.size)
        assertEquals(listOf("9"), groups.single { it.first.kind == DiscoveryRecommendationGroupKind.New }.second.map { it.id })
        assertEquals(listOf("10"), groups.single { it.first.kind == DiscoveryRecommendationGroupKind.Further }.second.map { it.id })
    }
    @Test fun olderMetadataAndPartialDatesDoNotInventDepthOrNewPicks() {
        val screen = DiscoveryBrowserState(recommendations = listOf(artist(1, date = "2026"), artist(2)))
        assertFalse(screen.hasDates); assertFalse(screen.hasDepth)
        assertTrue(screen.groups().none { it.first.kind in setOf(DiscoveryRecommendationGroupKind.New, DiscoveryRecommendationGroupKind.Direct, DiscoveryRecommendationGroupKind.Further) })
    }
    @Test fun filtersCoverTheWholeLoadedCollectionAndKeepServerRank() {
        val screen = DiscoveryBrowserState(recommendations = (1..205).map { artist(it, if (it % 2 == 0) 1 else 2) },
            seeds = listOf(ProviderDiscoverySeed("Seed")), genre = "rock", seed = "Seed", filter = DiscoveryRecommendationFilter.Direct)
        assertEquals(102, screen.filtered().size)
        assertEquals(2, screen.filtered().first().recommendation?.rank)
        assertEquals(204, screen.filtered().last().recommendation?.rank)
    }
}
