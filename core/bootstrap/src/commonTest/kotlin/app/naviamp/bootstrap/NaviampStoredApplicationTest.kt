package app.naviamp.bootstrap

import app.cash.sqldelight.db.SqlDriver
import app.naviamp.domain.audio.AudioTagReader
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamHandle
import app.naviamp.domain.cache.AudioByteStore
import app.naviamp.domain.cache.AudioByteWriter
import app.naviamp.domain.cache.StoredAudioBytes
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.network.SharedHttpResponse
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.presentation.*
import app.naviamp.storage.*
import app.naviamp.ui.NaviampShellCapabilitiesUi
import kotlinx.coroutines.test.runTest
import kotlin.test.*

expect fun storedApplicationTestDriver(): SqlDriver

class NaviampStoredApplicationTest {
    @Test fun completeAssemblyRetainsSettingsAcrossRecreationAndKeepsMixerFreeCapabilities() = runTest {
        val driver = storedApplicationTestDriver()
        val values = TestValues()
        try {
            val first = application(driver, values)
            val cache = CacheSettings(audioCachingEnabled = false, maxAudioCacheBytes = 123456L)
            val effective = cache.copy(maxAudioCacheBytes = 256L * 1024 * 1024)
            assertEquals(effective, first.environment.services.settings.cacheSettings.apply(cache))
            assertEquals(effective, naviampCoreSettingsValueCatalog(values).storedSettings.loadCache())
            assertFalse(first.environment.initialState.product.shell.capabilities.crossfade)
            first.core.close()
            val reopened = application(driver, values)
            assertEquals(effective, naviampCoreSettingsValueCatalog(values).storedSettings.loadCache())
            assertFalse(reopened.environment.initialState.product.shell.cache.settings.audioCachingEnabled)
            assertFalse(reopened.environment.initialState.product.shell.playback.gaplessAvailable)
            assertNotNull(reopened.environment.services.downloads.storage)
            reopened.core.close()
        } finally { driver.close() }
    }

    @Test fun artworkFallbackCachesSuccessfulBytesAndDoesNotPersistFailedFetch() = runTest {
        val driver = storedApplicationTestDriver()
        try {
            val app = application(driver, TestValues())
            assertNull(app.loadProviderArtwork("https://fixture.invalid/missing"))
            assertContentEquals(byteArrayOf(1, 2, 3), app.loadProviderArtwork("https://fixture.invalid/art"))
            assertContentEquals(byteArrayOf(1, 2, 3), app.loadProviderArtwork("https://fixture.invalid/art"))
            assertEquals(listOf("https://fixture.invalid/missing", "https://fixture.invalid/art"), calls)
            app.core.close()
        } finally { driver.close() }
    }

    private val calls = mutableListOf<String>()
    private fun kotlinx.coroutines.CoroutineScope.application(driver: SqlDriver, values: TestValues) =
        NaviampStoredApplication(
            scope = this, database = NaviampStorageDatabase(driver),
            credentialProtector = PassthroughStorageCredentialProtector,
            settingsValueStore = values, settingsSyncPort = NoDocumentEffects, certificateFiles = null,
            databaseLabel = "test", audioCacheDirectory = directory("/cache"), downloadDirectory = directory("/downloads"),
            playbackEngine = UnavailableNaviampPlaybackEngine, waveformBass = NoBassEffects,
            localFilePath = { null }, audioTagReader = AudioTagReader { emptyList() },
            externalUri = NaviampCoreExternalUriPort {}, homeDate = NaviampCoreHomeDateSource { HomeDate(2026, 319) },
            shellCapabilities = NaviampShellCapabilitiesUi(gapless = false, crossfade = false),
            actionAvailability = NaviampCoreActionAvailability(), providerDeviceId = "test", providerDeviceName = "Test",
            settingsSyncDeviceId = "test", downloadLocations = emptyList(), audioCacheLocations = emptyList(),
            selectedDownloadLocationId = null, selectedAudioCacheLocationId = null,
            httpClient = object : SharedHttpClient {
                override suspend fun get(url: String, headers: Map<String, String>): String? = error("Unexpected text request")
                override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? {
                    calls += url
                    return if (url.endsWith("/art")) byteArrayOf(1, 2, 3) else null
                }
                override suspend fun getResponse(url: String, headers: Map<String, String>): SharedHttpResponse? = error("Unexpected response request")
                override suspend fun download(url: String, headers: Map<String, String>, writeChunk: suspend (ByteArray, Int) -> Unit): Boolean = error("Unexpected download")
            },
        )
}

private object NoBassEffects : BassAudioBackend {
    override fun createFileDecodeStream(path: String): Result<BassStreamHandle> = error("Unexpected decode")
    override fun createUrlDecodeStream(url: String): Result<BassStreamHandle> = error("Unexpected decode")
    override fun lengthBytes(stream: BassStreamHandle): Long? = error("Unexpected decode")
    override fun readFloatData(stream: BassStreamHandle, buffer: FloatArray): Result<Int> = error("Unexpected decode")
    override fun freeStream(stream: BassStreamHandle): Result<Unit> = error("Unexpected decode")
}

private fun directory(path: String) = StorageAudioDirectory(path, object : AudioByteStore {
    override suspend fun writeAudioBytes(fileName: String, errorMessage: String, writeBytes: suspend (AudioByteWriter) -> Boolean): StoredAudioBytes = error("Unexpected audio write")
    override fun deleteAudioBytes(filePath: String) = error("Unexpected audio deletion")
}, { false }, { error("Unexpected file deletion") }, { "file://$it" })

private class TestValues : NaviampCoreSettingsValueStore {
    private val entries = mutableMapOf<String, String>()
    override fun read(key: String) = entries[key]
    override fun write(key: String, value: String) { entries[key] = value }
}

private object NoDocumentEffects : NaviampCoreSettingsSyncPort {
    override val available = false
    override fun configuration() = NaviampCoreSettingsSyncConfiguration()
    override fun saveConfiguration(configuration: NaviampCoreSettingsSyncConfiguration) = error("Unexpected sync configuration")
    override suspend fun readDocument(directoryPath: String): SettingsSyncDocument? = error("Unexpected document read")
    override suspend fun readDocumentFile(filePath: String): SettingsSyncDocument? = error("Unexpected document read")
    override suspend fun writeDocument(directoryPath: String, document: SettingsSyncDocument): String = error("Unexpected document write")
    override suspend fun chooseDirectory(currentPath: String?, title: String): String? = error("Unexpected directory picker")
    override suspend fun chooseDocument(currentPath: String?, title: String): String? = error("Unexpected document picker")
    override fun defaultDirectory() = "/test"
}
