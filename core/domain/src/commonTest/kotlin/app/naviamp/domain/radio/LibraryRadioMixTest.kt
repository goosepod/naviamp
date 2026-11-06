package app.naviamp.domain.radio

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.*

class LibraryRadioMixTest {
    @Test fun genreMatchesLeadAcrossArtistsWithoutLettingTheSeedArtistDominate() {
        val seed = track("seed", "Seed artist")
        val genre = (1..30).map { track("same-$it", "Seed artist") } +
            (1..5).flatMap { artist -> (1..5).map { track("$artist-$it", "Artist $artist") } }
        val result = libraryRadioMixTracks(seed, genre, count = 12)
        assertEquals(12, result.size)
        assertEquals(6, result.map { it.artistName }.distinct().size)
        assertTrue(result.groupingBy { it.artistName }.eachCount().values.all { it <= 3 })
        assertTrue(result.zipWithNext().all { (one, two) -> one.artistName != two.artistName })
        assertNotEquals(seed.artistName, result.first().artistName)
        assertFalse(libraryRadioMixNeedsMoreCandidates(seed, result, 12))
    }

    @Test fun sparseGenreMatchesArePreferredWhileLibraryTracksProvideVariety() {
        val seed = track("seed", "Seed")
        val matched = listOf(track("genre", "Related artist"))
        val general = (1..10).map { track("library-$it", "Library artist $it") }
        val result = libraryRadioMixTracks(seed, matched, general, count = 10)
        assertEquals("genre", result.first().id.value)
        assertEquals(10, result.map { it.artistName }.distinct().size)
        assertTrue(libraryRadioMixNeedsMoreCandidates(seed, matched, 10))
    }

    @Test fun seedDuplicatesAndDiscoveryPreviewsNeverEnterLibraryFallbackQueues() {
        val seed = track("seed", "Seed")
        val match = track("owned", "Other")
        val result = libraryRadioMixTracks(seed,
            listOf(seed, match, match, track("discovery-preview:42", "Preview")),
            listOf(match), listOf(seed), count = 10)
        assertEquals(listOf(match), result)
    }

    @Test fun artistNamesAreCaseInsensitiveAndSparseLibrariesReturnHonestPartialMixes() {
        val seed = track("seed", "Seed")
        val result = libraryRadioMixTracks(seed,
            (1..20).map { track("dominant-$it", if (it % 2 == 0) "Artist" else " artist ") } +
                (1..3).map { track("other-$it", "Other $it") }, count = 12)
        assertEquals(6, result.size)
        assertEquals(3, result.count { it.artistName.trim().equals("artist", true) })
        assertTrue(libraryRadioMixNeedsMoreCandidates(seed, result, 12))
    }

    @Test fun aSingleArtistLibraryStillProducesABoundedSeedFreeMix() {
        val seed = track("seed", "Only artist")
        val tracks = (1..10).map { track("same-$it", "Only artist") }
        val result = libraryRadioMixTracks(seed, tracks, count = 5)
        assertEquals(5, result.size)
        assertTrue(libraryRadioMixNeedsMoreCandidates(seed, result, 5))
        assertEquals(1, libraryRadioMixTracks(seed, tracks, count = 0).size)
        assertTrue(libraryRadioMixTracks(seed, emptyList(), count = 5).isEmpty())
    }

    private fun track(id: String, artist: String) = Track(TrackId(id), id, artistName = artist,
        albumTitle = null, durationSeconds = 180, coverArtId = null, audioInfo = null, replayGain = null)
}
