package app.naviamp.app

import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.DownloadReplacementRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.KeepDownloadedCollectionKind
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.KeepDownloadedRepository
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.app.PlatformCapabilities
import app.naviamp.domain.app.PlatformCapability
import app.naviamp.domain.app.PlatformCapabilityStatus
import app.naviamp.domain.cache.DownloadJob
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.SettingsSyncOperationKind
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NaviampApplicationServicesTest {
    @Test
    fun preservesTheRequiredDependencyAwareServiceInstances() {
        val settingsSync = NaviampSettingsSyncController(
            deviceId = "test",
            state = { SettingsSyncRuntimeState() },
            saveState = {},
            nowEpochMillis = { 1L },
            snapshot = { SettingsSyncLocalSnapshot() },
            applyDocument = {},
        )
        val cacheSettings = NaviampCacheSettingsController(
            setSettings = {},
            saveSettings = {},
        )
        val cacheMaintenance = NaviampCacheMaintenanceController(
            repository = EmptyCacheMaintenanceRepository,
            setStatus = {},
        )
        val downloadJobs = NaviampDownloadJobController(jobs = { emptyList() }, setJobs = {})
        val downloads = NaviampDownloadCoordinator(
            downloadRepository = EmptyDownloadStore,
            downloadReplacementRepository = EmptyDownloadStore,
            keepDownloadedRepository = EmptyDownloadStore,
            jobs = downloadJobs,
            downloadedTrackId = { _: Unit -> "" },
            loadStats = { Unit },
        )

        val services = NaviampApplicationServices(
            settingsSync = settingsSync,
            cacheSettings = cacheSettings,
            cacheMaintenance = cacheMaintenance,
            downloadJobs = downloadJobs,
            downloads = downloads,
        )

        assertSame(settingsSync, services.settingsSync)
        assertSame(cacheSettings, services.cacheSettings)
        assertSame(cacheMaintenance, services.cacheMaintenance)
        assertSame(downloadJobs, services.downloadJobs)
        assertSame(downloads, services.downloads)
    }

    @Test
    fun downloadCoordinatorOwnsKeepDownloadedToggleAndCollectionDispatch() = runTest {
        val store = RecordingDownloadStore()
        val jobs = NaviampDownloadJobController(jobs = { emptyList() }, setJobs = {})
        val downloads = NaviampDownloadCoordinator(
            downloadRepository = store,
            downloadReplacementRepository = store,
            keepDownloadedRepository = store,
            jobs = jobs,
            downloadedTrackId = { _: Unit -> "" },
            loadStats = { Unit },
        )
        val playlistPolicy = KeepDownloadedCollectionPolicy(
            sourceId = "source",
            kind = KeepDownloadedCollectionKind.Playlist,
            collectionId = "playlist",
            name = "Playlist",
        )

        assertEquals(NaviampKeepDownloadedToggleResult.Enable, downloads.toggleKeepDownloaded(playlistPolicy))
        store.policy = playlistPolicy
        assertEquals(NaviampKeepDownloadedToggleResult.Disabled, downloads.toggleKeepDownloaded(playlistPolicy))
        assertNull(store.policy)
        assertEquals(
            "playlist",
            downloads.loadKeepDownloadedTracks(
                policy = playlistPolicy,
                loadPlaylistTracks = { id -> listOf(compositionTrack(id)) },
                loadFavoriteTracks = { error("favorite loader should not run") },
            ).single().id.value,
        )
        assertEquals(
            "favorites",
            downloads.loadKeepDownloadedTracks(
                policy = naviampKeepDownloadedFavoritesPolicy("source"),
                loadPlaylistTracks = { error("playlist loader should not run") },
                loadFavoriteTracks = { listOf(compositionTrack("favorites")) },
            ).single().id.value,
        )
    }

    @Test
    fun completeFakeCompositionDrivesRuntimeSettingsCacheAndDownloadState() = runTest {
        var syncState = SettingsSyncRuntimeState()
        var cacheStatus: String? = null
        var jobs = emptyList<DownloadJob>()
        val session = RecordingApplicationSession(restoreFailures = 1)
        val pendingActions = RecordingPendingActions().apply {
            pending += pendingAction(id = 1, entityId = "queued-track")
        }
        val controllers = NaviampApplicationControllers(
            pendingProviderActions = pendingActions,
        )
        val runtime = NaviampApplicationRuntime(
            services = NaviampPlatformServices(
                capabilities = PlatformCapabilities().withStatus(
                    PlatformCapability.Downloads,
                    PlatformCapabilityStatus.Available,
                ),
                session = session,
                playbackSessions = NaviampPlaybackSessionController(EmptyCompositionPlaybackSessions),
                playbackExecution = EmptyCompositionPlaybackExecution,
                clock = NaviampClock { 0L },
                connectivity = NaviampConnectivityMonitor { NaviampConnectivitySnapshot(available = true) },
                errorReporter = NaviampRuntimeErrorReporter { _, _ -> },
            ),
            controllers = controllers,
        )
        val downloadJobs = NaviampDownloadJobController({ jobs }, { jobs = it })
        val services = NaviampApplicationServices(
            settingsSync = NaviampSettingsSyncController(
                deviceId = "fake-device",
                state = { syncState },
                saveState = { syncState = it },
                nowEpochMillis = { 10L },
                snapshot = { SettingsSyncLocalSnapshot() },
                applyDocument = {},
            ),
            cacheSettings = NaviampCacheSettingsController(setSettings = {}, saveSettings = {}),
            cacheMaintenance = NaviampCacheMaintenanceController(
                repository = EmptyCacheMaintenanceRepository,
                setStatus = { cacheStatus = it },
            ),
            downloadJobs = downloadJobs,
            downloads = NaviampDownloadCoordinator(
                downloadRepository = EmptyDownloadStore,
                downloadReplacementRepository = EmptyDownloadStore,
                keepDownloadedRepository = EmptyDownloadStore,
                jobs = downloadJobs,
                downloadedTrackId = { _: Unit -> "" },
                loadStats = { Unit },
            ),
        )
        val composition = NaviampApplicationComposition(runtime, services)

        composition.runtime.handle(NaviampHostLifecycleEvent.Start)
        assertEquals(NaviampRuntimePhase.Failed, composition.runtime.state.value.phase)
        composition.runtime.handle(NaviampHostLifecycleEvent.Start)

        val firstConnectionPlan = composition.controllers.connection.begin(restoreSavedSession = true)
        composition.controllers.connection.failed("Server unavailable.")
        val retryConnectionPlan = composition.controllers.connection.begin(restoreSavedSession = true)
        composition.controllers.connection.connected(
            sourceId = "source",
            serverVersion = "1.2.3",
            status = "Connected.",
        )
        val replayResult = composition.controllers.providerActions.replay(
            sourceId = "source",
            provider = RecordingProvider(failReports = false),
        )
        val exported = composition.services.settingsSync.exportCurrent(markChanged = true)
        composition.services.cacheMaintenance.clearCache()
        val job = composition.services.downloadJobs.create(
            label = "Fake album",
            tracks = listOf(compositionTrack("one")),
            replaceExisting = false,
        )

        assertEquals(NaviampRuntimePhase.Ready, composition.runtime.state.value.phase)
        assertEquals(listOf("restore", "restore"), session.events)
        assertTrue(firstConnectionPlan?.restoreSavedSession == true)
        assertTrue(retryConnectionPlan?.restoreSavedSession == true)
        assertEquals(NaviampConnectionPhase.Connected, composition.controllers.connection.state.value.phase)
        assertEquals("source", composition.controllers.connection.state.value.sourceId)
        assertEquals(1, replayResult.completed)
        assertTrue(pendingActions.pending.isEmpty())
        assertEquals(
            "Synced 1 offline action.",
            composition.controllers.status.state.value?.message,
        )
        assertSame(controllers, composition.controllers)
        assertTrue(composition.capabilities.downloads.enabled)
        assertEquals(SettingsSyncOperationKind.Exported, exported.kind)
        assertEquals(10L, exported.documentToWrite?.updatedAtEpochMillis)
        assertEquals("Cache cleared.", cacheStatus)
        assertNotNull(job)
        assertEquals(listOf(job), composition.services.downloadJobs.currentJobs)
    }
}

private fun compositionTrack(id: String) = Track(
    id = TrackId(id),
    title = "Track $id",
    artistName = "Artist",
    albumTitle = "Album",
    durationSeconds = 180,
    coverArtId = null,
    audioInfo = null,
    replayGain = null,
)

private object EmptyDownloadStore :
    DownloadRepository<Unit, Unit>,
    DownloadReplacementRepository<Unit>,
    KeepDownloadedRepository {
    override suspend fun downloadedAudioFile(sourceId: String, trackId: TrackId, quality: StreamQuality) = null
    override suspend fun downloadedAudioFile(sourceId: String, trackId: TrackId) = null
    override suspend fun downloadAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        maxDownloadBytes: Long,
    ) = Unit

    override fun downloadedTracks(sourceId: String) = emptyList<Unit>()
    override fun removeDownloadedAudio(sourceId: String, trackId: TrackId, quality: StreamQuality) = Unit
    override fun removeDownloadedAudio(sourceId: String, trackId: TrackId) = Unit

    override suspend fun replaceDownloadedAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        maxDownloadBytes: Long,
    ) = Unit

    override fun keepDownloadedPolicies(sourceId: String) = emptyList<KeepDownloadedCollectionPolicy>()
    override fun keepDownloadedPolicy(
        sourceId: String,
        kind: KeepDownloadedCollectionKind,
        collectionId: String,
    ) = null

    override fun upsertKeepDownloadedPolicy(policy: KeepDownloadedCollectionPolicy) = Unit
    override fun deleteKeepDownloadedPolicy(
        sourceId: String,
        kind: KeepDownloadedCollectionKind,
        collectionId: String,
    ) = Unit

    override fun keepDownloadedTrackIds(
        sourceId: String,
        kind: KeepDownloadedCollectionKind,
        collectionId: String,
    ) = emptySet<String>()

    override fun replaceKeepDownloadedTrackIds(policy: KeepDownloadedCollectionPolicy, trackIds: Set<String>) = Unit
    override fun managedKeepDownloadedTrackIds(sourceId: String) = emptySet<String>()
    override fun markManagedKeepDownloadedTracks(sourceId: String, trackIds: Set<String>) = Unit
    override fun unmarkManagedKeepDownloadedTracks(sourceId: String, trackIds: Set<String>) = Unit
}

private class RecordingDownloadStore :
    DownloadRepository<Unit, Unit> by EmptyDownloadStore,
    DownloadReplacementRepository<Unit> by EmptyDownloadStore,
    KeepDownloadedRepository by EmptyDownloadStore {
    var policy: KeepDownloadedCollectionPolicy? = null

    override fun keepDownloadedPolicy(
        sourceId: String,
        kind: KeepDownloadedCollectionKind,
        collectionId: String,
    ): KeepDownloadedCollectionPolicy? =
        policy?.takeIf { it.sourceId == sourceId && it.kind == kind && it.collectionId == collectionId }

    override fun deleteKeepDownloadedPolicy(
        sourceId: String,
        kind: KeepDownloadedCollectionKind,
        collectionId: String,
    ) {
        if (keepDownloadedPolicy(sourceId, kind, collectionId) != null) policy = null
    }
}

private object EmptyCacheMaintenanceRepository : CacheMaintenanceRepository<Unit> {
    override fun clearProviderData() = Unit
    override fun clearCacheData() = Unit
    override fun clearDownloadData() = Unit
    override fun clearAll() = Unit
    override fun stats() = Unit
}

private class RecordingApplicationSession(private var restoreFailures: Int = 0) : NaviampApplicationSession {
    val events = mutableListOf<String>()

    override suspend fun restore() {
        events += "restore"
        if (restoreFailures > 0) {
            restoreFailures -= 1
            error("restore failed")
        }
    }
}

private object EmptyCompositionPlaybackSessions : PlaybackSessionRepository {
    override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? = null
    override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) = Unit
}

private object EmptyCompositionPlaybackExecution : NaviampPlaybackExecution {
    override fun pause() = Unit
    override fun resume() = Unit
    override fun startOrRestore(): Boolean = false
    override fun seek(positionSeconds: Double) = Unit
    override fun replayCurrent(positionSeconds: Double) = Unit
    override fun setVolume(percent: Int) = Unit
    override fun stop() = Unit
}
