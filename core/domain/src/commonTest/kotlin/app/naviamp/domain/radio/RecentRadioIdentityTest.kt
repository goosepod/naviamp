package app.naviamp.domain.radio

import app.naviamp.domain.Genre
import app.naviamp.domain.settings.RecentRadioKind
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.SavedArtist
import app.naviamp.domain.settings.SavedTrack
import app.naviamp.domain.settings.migratedProviderIdentities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class RecentRadioIdentityTest {
    @Test
    fun providerIdentityMigrationTransformsEntitySeedsButPreservesGenreNames() {
        val artist = RecentRadioStream("artist-mix:a:b", "Mix", RecentRadioKind.Artist,
            sourceId = "source", stationSeedIds = listOf("a", "b"))
        assertEquals(listOf("new-a", "new-b"), artist.migratedProviderIdentities("source") { "new-$it" }.stationSeedIds)
        val genre = genreMixRadioRequest(listOf(Genre("Jazz"), Genre("Rock"))).recentRadioStream.copy(sourceId = "source")
        assertEquals(genre.stationSeedIds, genre.migratedProviderIdentities("source") { "new-$it" }.stationSeedIds)
        assertEquals(artist, artist.migratedProviderIdentities("other-source") { "new-$it" })
    }

    @Test
    fun identityUsesAccountScopedSourceAndSeedNotVisibleMetadataOrQueue() {
        val station = RecentRadioStream("artist:a", "Same", RecentRadioKind.Artist,
            artist = SavedArtist("a", "Name"), sourceId = "account-a")
        assertEquals(station.stationIdentity(), station.copy(id = "artist:a:session:1", startedAtEpochMillis = 1,
            label = "Renamed", sessionTracks = listOf(SavedTrack("one", "One", artistName = "Artist"))).stationIdentity())
        assertNotEquals(station.stationIdentity(), station.copy(artist = SavedArtist("b", "Name")).stationIdentity())
        assertNotEquals(station.stationIdentity(), station.copy(sourceId = "account-b").stationIdentity())
        assertNotEquals(station.stationIdentity(), station.copy(sourceId = null).stationIdentity())
        assertNotEquals(station.stationIdentity(), station.copy(kind = RecentRadioKind.Album).stationIdentity())
    }

    @Test
    fun optionsAndCompleteMixMembershipDistinguishStations() {
        val mix = genreMixRadioRequest(listOf(Genre("Jazz"), Genre("Rock"))).recentRadioStream
        val reversed = genreMixRadioRequest(listOf(Genre("Rock"), Genre("Jazz"))).recentRadioStream
        assertEquals(mix.stationIdentity(), reversed.stationIdentity())
        assertNotEquals(mix.stationIdentity(), genreMixRadioRequest(listOf(Genre("Jazz"), Genre("Pop"))).recentRadioStream.stationIdentity())
        assertNotEquals(mix.stationIdentity(), genreRecentRadioStream(Genre("Jazz")).stationIdentity())
        val configured = mix.copy(generationOptions = RecentRadioGenerationOptions())
        assertNotEquals(mix.stationIdentity(), configured.stationIdentity())
        assertNotEquals(configured.stationIdentity(), configured.copy(generationOptions = RecentRadioGenerationOptions(minimumTracks = 20)).stationIdentity())
        assertNotEquals(configured.stationIdentity(), configured.copy(generationOptions = RecentRadioGenerationOptions(sonicSimilarity = true)).stationIdentity())
        assertNotEquals(configured.stationIdentity(), configured.copy(generationOptions = RecentRadioGenerationOptions(tuning = RadioTuningSettings(sameDecadeOnly = true))).stationIdentity())
    }

    @Test
    fun reconciliationKeepsNewestIdentityAndUsefulQueueAndIsIdempotent() {
        val newest = libraryRecentRadioStream().copy(id = "library:session:2", sourceId = "source", startedAtEpochMillis = 2)
        val older = newest.copy(id = "library:session:1", startedAtEpochMillis = 1,
            sessionTracks = listOf(SavedTrack("one", "One", artistName = "Artist")), coverArtIds = listOf("cover"))
        val other = genreRecentRadioStream(Genre("Jazz")).copy(label = newest.label)
        val result = reconciledRecentRadioStreams(listOf(newest, other, older))
        assertEquals(2, result.size)
        assertEquals(newest.id, result.first().id)
        assertEquals(2L, result.first().startedAtEpochMillis)
        assertEquals(older.sessionTracks, result.first().sessionTracks)
        assertEquals(older.coverArtIds, result.first().coverArtIds)
        assertEquals(result, reconciledRecentRadioStreams(result))
    }

    @Test
    fun missingSeedsAndUnknownCompositeIdsAreNotMergedByName() {
        val a = RecentRadioStream("missing-a", "Same", RecentRadioKind.Artist)
        val b = a.copy(id = "missing-b")
        assertEquals(2, reconciledRecentRadioStreams(listOf(a, b)).size)
        val mix = genreRecentRadioStream(Genre("Jazz")).copy(id = "genre-mix:Jazz:Rock")
        val different = mix.copy(id = "genre-mix:Jazz:Pop")
        assertNotEquals(mix.stationIdentity(), different.stationIdentity())
        assertNull(recentRadioAction(mix)) // Never silently rebuild only its first seed.
    }

    @Test
    fun optionsAreNormalizedAndLegacySeedIdsContainingSessionTextRemainIntact() {
        val station = libraryRecentRadioStream().copy(generationOptions = RecentRadioGenerationOptions(minimumTracks = 999))
        assertEquals(MaxRadioMinimumTracks, reconciledRecentRadioStreams(listOf(station)).single().generationOptions?.minimumTracks)
        val artist = RecentRadioStream("artist:x:session:123", "Artist", RecentRadioKind.Artist,
            artist = SavedArtist("x:session:123", "Artist"))
        assertNotEquals(artist.stationIdentity(), artist.copy(artist = SavedArtist("x", "Artist")).stationIdentity())
    }
}
