package app.naviamp.ui

import app.naviamp.domain.Playlist
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.navibeat.NavibeatMix
import app.naviamp.domain.navibeat.NavibeatMixDate
import app.naviamp.domain.navibeat.NavibeatMixMetadata
import kotlin.test.Test
import kotlin.test.assertEquals

class NavibeatHomeCardMapperTest {
    @Test
    fun navibeatCardsPlayByDefaultAndRemainIdentifiableForTheirActionMenu() {
        val playlist = Playlist(id = "navibeat-1", name = "Afternoon", trackCount = 20)
        val home = HomeContent(
            date = HomeDate(2026, 223),
            navibeatMixes = listOf(
                NavibeatMix(
                    playlist = playlist,
                    metadata = NavibeatMixMetadata(
                        kind = "timeofday",
                        slot = "afternoon",
                        generatedOn = NavibeatMixDate(2026, 8, 11),
                        mode = "personalized",
                        trackCount = 20,
                        description = "Afternoon mix",
                    ),
                ),
            ),
        )

        val item = home.toSharedHomeUi(coverArtUrl = { null })
            .collectionSections
            .single { it.id == SharedHomeCollectionSectionIds.NavibeatMixes }
            .items
            .single()

        assertEquals(SharedHomeCollectionItemAction.PlayPlaylist, item.action)
        assertEquals(SharedHomeCollectionArtwork.NavibeatGenerated, item.artwork)
        assertEquals("timeofday", item.artworkKey)
    }

    @Test
    fun generatedStationsCarryIconArtworkKeys() {
        val stations = HomeContent().toSharedHomeUi(coverArtUrl = { null })
            .collectionSections
            .single { it.id == "stations" }
            .items

        assertEquals(listOf("library", "random-album"), stations.map { it.artworkKey })
        stations.forEach { station ->
            assertEquals(SharedHomeCollectionArtwork.StationGenerated, station.artwork)
            assertEquals(SharedHomeCollectionItemAction.SelectStation, station.action)
        }
    }
}
