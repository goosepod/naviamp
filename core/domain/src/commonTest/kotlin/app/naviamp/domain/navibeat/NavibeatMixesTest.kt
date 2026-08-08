package app.naviamp.domain.navibeat

import app.naviamp.domain.Playlist
import app.naviamp.domain.home.HomeDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavibeatMixesTest {
    @Test
    fun parsesTheDocumentedMachineLineWithoutDependingOnPlaylistName() {
        val playlist = Playlist(
            id = "mix-1",
            name = "A completely custom name",
            trackCount = 30,
            comment = """
                Morning mix, built from what you actually play. Refreshes daily.
                Made by NaviBeat  ·  navibeat.app
                nb1:timeofday:morning:2026-08-08:affinity:30
            """.trimIndent(),
        )

        val mix = playlist.navibeatMixOrNull()!!

        assertEquals("timeofday", mix.metadata.kind)
        assertEquals("morning", mix.metadata.slot)
        assertEquals(NavibeatMixDate(2026, 8, 8), mix.metadata.generatedOn)
        assertEquals("affinity", mix.metadata.mode)
        assertEquals(30, mix.metadata.trackCount)
        assertEquals("Morning mix, built from what you actually play. Refreshes daily.", mix.metadata.description)
    }

    @Test
    fun rejectsMalformedTruncatedAndAmbiguousMarkers() {
        val invalid = listOf(
            "nb1:timeofday:morning:2026-08-08:affinity",
            "nb1:timeofday:morning:not-a-date:affinity:30",
            "nb1:time of day:morning:2026-08-08:affinity:30",
            "nb2:timeofday:morning:2026-08-08:affinity:30",
            "nb1:timeofday:morning:2026-08-08:affinity:30\nnb1:timeofday:night:2026-08-08:affinity:30",
        )

        invalid.forEach { marker ->
            assertNull(Playlist("playlist", "Playlist", 30, comment = marker).navibeatMixOrNull(), marker)
        }
    }

    @Test
    fun partitionsMixesAndPrioritizesThePluginsCurrentTimeSlot() {
        val ordinary = Playlist("ordinary", "Ordinary", 1)
        val morning = mix("morning")
        val evening = mix("evening")

        val partition = listOf(ordinary, morning, evening).partitionNavibeatMixes()

        assertEquals(listOf(ordinary), partition.ordinary)
        assertEquals(listOf("morning", "evening"), partition.mixes.map { it.metadata.slot })
        assertEquals(
            listOf("evening", "morning"),
            partition.mixes.prioritizedForHour(20).map { it.metadata.slot },
        )
    }

    @Test
    fun derivesLearningAndFreshnessLabels() {
        val fallback = mix("morning", mode = "fallback").navibeatMixOrNull()!!
        val affinity = mix("morning", mode = "affinity").navibeatMixOrNull()!!

        assertEquals("Still learning you", fallback.statusLabel(HomeDate(2026, 220)))
        assertEquals("Updated today", affinity.statusLabel(HomeDate(2026, 220)))
        assertEquals("Updated yesterday", affinity.statusLabel(HomeDate(2026, 221)))
    }

    private fun mix(slot: String, mode: String = "affinity") = Playlist(
        id = slot,
        name = slot,
        trackCount = 30,
        comment = "Description\nMade by NaviBeat  ·  navibeat.app\nnb1:timeofday:$slot:2026-08-08:$mode:30",
    )
}
