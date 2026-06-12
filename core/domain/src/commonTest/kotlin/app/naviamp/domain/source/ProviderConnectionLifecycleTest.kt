package app.naviamp.domain.source

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProviderConnectionLifecycleTest {
    @Test
    fun lifecyclePreparesValidatesClearsAndPersistsMediaSource() = runTest {
        val calls = mutableListOf<String>()
        val cache = FakeCacheMaintenanceRepository(calls)
        val mediaSources = FakeProviderMediaSourceRepository(calls)

        val session = openProviderConnectionSession(
            request = ProviderConnectionLifecycleRequest(
                connection = FakeConnection("https://server", "user"),
                prepareConnection = { connection ->
                    calls += "prepare"
                    FakePreparedConnection(connection.copy(token = "prepared-token"), warning = "warning")
                },
                preparedConnection = { prepared -> prepared.connection },
                provider = { connection -> FakeProvider(connection, calls) },
                mediaSourceConnection = { connection ->
                    ProviderMediaSourceConnection(
                        displayName = "Server",
                        baseUrl = connection.baseUrl,
                        username = connection.username,
                        token = connection.token,
                        salt = "salt",
                    )
                },
                applyTlsDefaults = { calls += "tls" },
                smartPlaylistAuthWarning = { prepared -> prepared.warning },
                clearProviderData = true,
            ),
            cacheMaintenanceRepository = cache,
            providerMediaSourceRepository = mediaSources,
        )

        assertEquals(listOf("prepare", "tls", "validate", "clear-provider-data", "upsert"), calls)
        assertEquals("source_fake", session.sourceId)
        assertEquals("prepared-token", session.connection.token)
        assertEquals("warning", session.smartPlaylistAuthWarning)
        assertEquals(ConnectionValidation(serverVersion = "1.0", apiVersion = "1.16"), session.validation)
        assertEquals("fake-provider", mediaSources.upsertedProviderId)
        assertEquals("fake-cache", mediaSources.upsertedCacheNamespace)
    }

    @Test
    fun lifecycleSkipsCacheClearWhenNotRequested() = runTest {
        val calls = mutableListOf<String>()

        openProviderConnectionSession(
            request = ProviderConnectionLifecycleRequest(
                connection = FakeConnection("https://server", "user"),
                prepareConnection = { FakePreparedConnection(it, warning = null) },
                preparedConnection = { it.connection },
                provider = { connection -> FakeProvider(connection, calls) },
                mediaSourceConnection = { connection ->
                    ProviderMediaSourceConnection(
                        displayName = "Server",
                        baseUrl = connection.baseUrl,
                        username = connection.username,
                        token = connection.token,
                        salt = "salt",
                    )
                },
                clearProviderData = false,
            ),
            cacheMaintenanceRepository = FakeCacheMaintenanceRepository(calls),
            providerMediaSourceRepository = FakeProviderMediaSourceRepository(calls),
        )

        assertEquals(listOf("validate", "upsert"), calls)
    }

    @Test
    fun connectionFailureStatusUsesMessageOrFallback() {
        assertEquals("No route", connectionFailureStatus(IllegalStateException("No route")))
        assertEquals("Could not connect.", connectionFailureStatus(IllegalStateException(), "Could not connect."))
    }

    private data class FakeConnection(
        val baseUrl: String,
        val username: String,
        val token: String = "token",
    )

    private data class FakePreparedConnection(
        val connection: FakeConnection,
        val warning: String?,
    )

    private class FakeProvider(
        private val connection: FakeConnection,
        private val calls: MutableList<String>,
    ) : MediaProvider {
        override val id: ProviderId = ProviderId("fake-provider")
        override val displayName: String = connection.baseUrl
        override val capabilities: ProviderCapabilities = ProviderCapabilities(
            supportsStreamingTranscode = false,
            supportsDownloadTranscode = false,
            supportsArtistRadio = false,
            supportsAlbumRadio = false,
            supportsTrackRadio = false,
        )
        override val cacheNamespace: String = "fake-cache"

        override suspend fun validateConnection(): ConnectionValidation {
            calls += "validate"
            return ConnectionValidation(serverVersion = "1.0", apiVersion = "1.16")
        }

        override suspend fun recentlyAddedAlbums(limit: Int): List<Album> = emptyList()
        override suspend fun album(albumId: AlbumId): AlbumDetails =
            AlbumDetails(Album(albumId, "Album", "Artist", null, null), emptyList())
        override suspend fun artist(artistId: ArtistId): ArtistDetails =
            ArtistDetails(Artist(artistId, "Artist"), emptyList())
        override suspend fun artists(limit: Int): List<Artist> = emptyList()
        override suspend fun tracks(limit: Int): List<Track> = emptyList()
        override suspend fun search(query: String, limit: Int): MediaSearchResults = MediaSearchResults()
        override suspend fun streamUrl(request: StreamRequest): String = "stream"
        override fun coverArtUrl(coverArtId: String): String = coverArtId
    }

    private class FakeProviderMediaSourceRepository(
        private val calls: MutableList<String>,
    ) : ProviderMediaSourceRepository {
        var upsertedProviderId: String? = null
        var upsertedCacheNamespace: String? = null

        override fun upsertProviderMediaSource(
            connection: ProviderMediaSourceConnection,
            cacheNamespace: String,
            providerId: String,
        ): MediaSourceIdentity {
            calls += "upsert"
            upsertedProviderId = providerId
            upsertedCacheNamespace = cacheNamespace
            return MediaSourceIdentity(
                id = "source_fake",
                cacheNamespace = cacheNamespace,
                displayName = connection.displayName,
            )
        }
    }

    private class FakeCacheMaintenanceRepository(
        private val calls: MutableList<String>,
    ) : CacheMaintenanceRepository<Unit> {
        override fun clearProviderData() {
            calls += "clear-provider-data"
        }

        override fun clearCacheData() = Unit
        override fun clearDownloadData() = Unit
        override fun clearAll() = Unit
        override fun stats() = Unit
    }
}
