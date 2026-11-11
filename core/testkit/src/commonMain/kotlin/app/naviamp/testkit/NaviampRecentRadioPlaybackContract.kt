package app.naviamp.testkit

import app.naviamp.app.NaviampConnectionPhase
import app.naviamp.app.NaviampConnectionRuntimeState
import app.naviamp.domain.*
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.provider.*
import app.naviamp.domain.radio.libraryRecentRadioStream
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.presentation.*
import app.naviamp.ui.SharedMediaItemUi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/** Native hosts supply audio and opaque storage; station behavior remains a shared contract. */
suspend fun verifyNaviampRecentRadioPlayback(
    engine: PlaybackEngine,
    streamUrl: String,
    values: NaviampCoreSettingsValueStore,
) = coroutineScope {
    val provider = RecentRadioContractProvider(streamUrl)
    var now = 100L
    val settings = naviampCoreSettingsValueCatalog(values).storedSettings
    val legacy = libraryRecentRadioStream().copy(id = "library:session:2", sourceId = "radio-fixture", startedAtEpochMillis = 2)
    settings.saveRecentRadioStreams(listOf(legacy))
    val stableId = settings.loadRecentRadioStreams().single().id
    repeat(2) { restart ->
        val defaults = naviampCoreTestServices(provider)
        val playback = naviampCoreStreamingPlaybackServices(this, engine, defaults.content.providerSource,
            PlaybackSettings(volumePercent = 0), {}, object : PlaybackSessionRepository {
                override fun loadPlaybackSession(sourceId: String?) = null
                override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) = Unit
            }, {})
        val core = NaviampCore.create(this, defaults.copy(
            playback = playback, clockEpochMillis = { now },
            radio = defaults.radio.copy(generatedRecents = NaviampCoreGeneratedRadioRecentsPort(
                load = settings.loadRecentRadioStreams, save = settings.saveRecentRadioStreams)),
        ), NaviampCoreInitialState(
            connection = NaviampConnectionRuntimeState(phase = NaviampConnectionPhase.Connected, sourceId = "radio-fixture"),
            connectionInventory = NaviampCoreConnectionInventory(listOf(NaviampCoreSavedConnectionRecord(
                "radio-fixture", "Fixture", "https://fixture.invalid", "fixture")), "radio-fixture"),
        ))
        try {
            repeat(3) {
                now++
                core.execute(NaviampCoreCommand.Home.SelectRecentRadio(
                    SharedMediaItemUi(stableId, legacy.label, "Radio")))
                val station = settings.loadRecentRadioStreams().single()
                check(station.id == stableId && station.startedAtEpochMillis == now)
                check(station.sessionTracks.map { it.id }.toSet() == provider.queue.map { it.id.value }.toSet())
                check(core.state.value.shell.home.content.recentRadioStreams.map { it.id } == listOf(stableId))
                // Require a moving real engine clock on every replay, not a stale Playing label.
                val position = core.playbackProgress.value.positionSeconds ?: 0.0
                withTimeout(15_000) { core.playbackProgress.first { (it.positionSeconds ?: 0.0) > position + 0.2 } }
                check(core.state.value.shell.nowPlaying?.isPlaying == true)
                println("recent-radio restart=$restart id=$stableId recency=$now nativePosition=${core.playbackProgress.value.positionSeconds}")
            }
        } finally {
            core.close()
            playback.effects.stop()
        }
    }
}

private class RecentRadioContractProvider(private val url: String) : MediaProvider {
    override val id = ProviderId("recent-radio-contract")
    override val displayName = "Radio fixture"
    override val capabilities = ProviderCapabilities(false, false, false, false, false)
    val queue = (1..3).map { Track(TrackId("track-$it"), "Track $it", artistName = "Artist $it",
        albumTitle = null, durationSeconds = 30, coverArtId = null, audioInfo = null, replayGain = null) }
    override suspend fun streamUrl(request: StreamRequest) = url
    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<Album>()
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("Not used")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Not used")
    override suspend fun artists(limit: Int) = emptyList<Artist>()
    override suspend fun tracks(limit: Int) = queue
    override suspend fun randomSongs(limit: Int, genre: String?, fromYear: Int?, toYear: Int?) = queue
    override suspend fun search(query: String, limit: Int) = MediaSearchResults()
    override suspend fun playlists(limit: Int) = emptyList<Playlist>()
    override suspend fun genres(limit: Int) = emptyList<Genre>()
    override fun coverArtUrl(coverArtId: String) = coverArtId
}
