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
import app.naviamp.domain.settings.SettingsSyncAudioPrefetch
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncPreferences
import app.naviamp.domain.settings.SettingsSyncServerProfile
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.host.*
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
            first.close()
            val reopened = application(driver, values)
            assertEquals(effective, naviampCoreSettingsValueCatalog(values).storedSettings.loadCache())
            assertFalse(reopened.environment.initialState.product.shell.cache.settings.audioCachingEnabled)
            assertFalse(reopened.environment.initialState.product.shell.playback.gaplessAvailable)
            assertNotNull(reopened.environment.services.downloads.storage)
            reopened.close()
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
            app.close()
        } finally { driver.close() }
    }

    @Test fun singlePrefetchDepthMigratesToBothNetworksAndBothDepthsSyncThroughTheStoredApplication() = runTest {
        val driver = storedApplicationTestDriver()
        val values = TestValues().apply { write("naviamp.cache", """{"audioPrefetchDepth":10,"waveformsEnabled":false}""") }
        try {
            val app = application(driver, values)
            val shownCache = app.environment.initialState.product.shell.cache.settings
            assertEquals(10, shownCache.unmeteredAudioPrefetchDepth)
            assertEquals(10, shownCache.meteredAudioPrefetchDepth)
            assertFalse(shownCache.waveformsEnabled)
            val sync = app.environment.services.settings.sync.controller

            sync.applySyncedDocument(SettingsSyncDocument(updatedAtEpochMillis = 5))
            val settings = naviampCoreSettingsValueCatalog(values).storedSettings
            assertEquals(10, settings.loadCache().unmeteredAudioPrefetchDepth, "Older exports keep the migrated depth")
            assertEquals(10, settings.loadCache().meteredAudioPrefetchDepth)

            sync.applySyncedDocument(SettingsSyncDocument(updatedAtEpochMillis = 6, preferences = SettingsSyncPreferences(
                audioPrefetch = SettingsSyncAudioPrefetch(unmeteredDepth = 15, meteredDepth = 99))))
            assertEquals(15, settings.loadCache().unmeteredAudioPrefetchDepth)
            assertEquals(25, settings.loadCache().meteredAudioPrefetchDepth)
            assertFalse(settings.loadCache().waveformsEnabled, "Device-local cache settings are kept")
            val exported = assertNotNull(sync.exportCurrent().documentToWrite)
            assertEquals(SettingsSyncAudioPrefetch(unmeteredDepth = 15, meteredDepth = 25), exported.preferences.audioPrefetch)
            app.close()
        } finally { driver.close() }
    }

    private val calls = mutableListOf<String>()
    @Test fun boundedImportCommitsCompleteSettingsAndStampOrLeavesEverythingUnchanged() = runTest {
        val driver = storedApplicationTestDriver()
        val document = TestRecoveryDocument()
        val box = TestRecoveryBox()
        val protector = StorageAeadCredentialProtector(box)
        val state = StorageRecoveryState(document, protector, object : StorageRecoveryLock {
            override fun lock() = Unit
            override fun unlock() = Unit
        })
        val values = NaviampRecoverySettingsValueStore(state)
        try {
            val app = application(driver, values, state, protector)
            val sync = app.environment.services.settings.sync.controller
            val imported = SettingsSyncDocument(updatedAtEpochMillis = 42,
                serverProfiles = listOf(SettingsSyncServerProfile("main", displayName = "Main", username = "user", primaryUrl = "https://fixture.invalid")),
                preferences = SettingsSyncPreferences(playback = PlaybackSettings(gaplessEnabled = true, crossfadeDurationSeconds = 7, volumePercent = 62)))
            sync.applySyncedDocument(imported)
            val settings = naviampCoreSettingsValueCatalog(values).storedSettings
            assertEquals(42L, settings.loadSyncRuntime().lastAppliedSyncUpdateEpochMillis)
            assertEquals(7, settings.loadPlayback().crossfadeDurationSeconds)
            assertTrue(settings.loadPlayback().gaplessEnabled) // Retain portable preferences even on direct-output TV.
            val exported = assertNotNull(sync.exportCurrent().documentToWrite)
            assertEquals(imported.preferences.playback.copy(volumePercent = 100), exported.preferences.playback)
            assertEquals(1, exported.serverProfiles.size)
            val before = document.value
            val playback = settings.loadPlayback()
            document.fail = true
            assertFailsWith<StorageRecoveryException> {
                sync.applySyncedDocument(imported.copy(updatedAtEpochMillis = 99,
                    preferences = imported.preferences.copy(playback = playback.copy(volumePercent = 10))))
            }
            assertEquals(before, document.value)
            assertEquals(playback, settings.loadPlayback())
            assertEquals(playback, sync.localSnapshot().playback)
            assertEquals(42L, settings.loadSyncRuntime().lastAppliedSyncUpdateEpochMillis)
            app.close()
        } finally { driver.close() }
    }

    private fun kotlinx.coroutines.CoroutineScope.application(driver: SqlDriver, values: NaviampCoreSettingsValueStore,
        recovery: StorageRecoveryState? = null, protector: StorageCredentialProtector = PassthroughStorageCredentialProtector) =
        NaviampStoredApplication(
            scope = this, database = NaviampStorageDatabase(driver),
            credentialProtector = protector, recoveryState = recovery,
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

private class TestRecoveryDocument : StorageRecoveryDocumentPort {
    var value: String? = null
    var fail = false
    override fun read() = StorageRecoveryDocument(value != null, value)
    override fun projectedDomainBytes(encoded: String) = encoded.length.toLong()
    override fun compareAndSet(expected: String?, replacement: String?): StorageRecoveryWriteResult {
        if (value != expected) return StorageRecoveryWriteResult.Conflict
        if (fail) return StorageRecoveryWriteResult.Failed
        value = replacement
        return StorageRecoveryWriteResult.Committed
    }
    override fun remove(): Boolean { value = null; return true }
}

private class TestRecoveryBox : StorageSecretBox {
    private val entries = mutableMapOf<String, ByteArray>()
    override fun seal(plaintext: ByteArray): StorageSealedBytes {
        val token = "token-${entries.size}"
        entries[token] = plaintext.copyOf()
        return StorageSealedBytes("test", token.encodeToByteArray())
    }
    override fun open(keyReference: String, ciphertext: ByteArray) = entries[ciphertext.decodeToString()]?.copyOf()
    override fun clearKey(): Boolean { entries.clear(); return true }
}

private object NoBassEffects : BassAudioBackend {
    override fun createFileDecodeStream(path: String): Result<BassStreamHandle> = error("Unexpected decode")
    override fun createUrlDecodeStream(url: String): Result<BassStreamHandle> = error("Unexpected decode")
    override fun lengthBytes(stream: BassStreamHandle): Long? = error("Unexpected decode")
    override fun readFloatData(stream: BassStreamHandle, buffer: FloatArray): Result<Int> = error("Unexpected decode")
    override fun freeStream(stream: BassStreamHandle): Result<Unit> = error("Unexpected decode")
}

private fun directory(path: String) = StorageAudioDirectory(path, object : AudioByteStore {
    override fun audioFilePath(fileName: String): String = error("Unexpected audio path")
    override suspend fun writeAudioBytes(temporaryFileName: String, errorMessage: String, writeBytes: suspend (AudioByteWriter) -> String): StoredAudioBytes = error("Unexpected audio write")
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
