package app.naviamp.storage

import app.cash.sqldelight.db.SqlDriver
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.source.*
import app.naviamp.domain.radio.RadioDjPreset
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.SavedTrack
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.*
import kotlin.test.*

expect fun recoveryTestDriver(): SqlDriver

class StorageConnectionRecoveryTest {
    @Test fun emptyCacheRecoversMainCompanionSecretsScopesSelectionAndPresetsButNoPlaybackQueue() {
        val fixture = RecoveryFixture()
        val first = recoveryTestDriver()
        var savedId = ""
        var activeId = ""
        try {
            val state = fixture.open()
            val queries = NaviampStorageDatabase(first).naviampStorageQueries
            var clock = 20L
            val sources = StorageMediaSourceStore(queries, { clock++ }, fixture.protector, recovery = state)
            savedId = sources.upsertProviderMediaSource(connection(), "scope-a", "navidrome").id
            activeId = sources.upsertProviderMediaSource(connection().copy(displayName = "Other", username = "other", aurralCompanion = null), "scope-b", "jellyfin").id
            sources.markLibrarySyncCompleted(savedId)
            StorageRadioDjPresetStore(queries, { 20L }, state).replaceRadioDjPresets(listOf(RadioDjPreset("dj", "My DJ")))
            StoragePlaybackProfileStore(queries, { 20L }, state).savePlaybackProfile(savedId, profileTarget, profile)
            StorageLyricsOffsetStore(queries, { 20L }, state).saveLyricsOffsetMillis(savedId, TrackId("track"), -175)
            sources.migrateProviderIdentities(savedId, "navidrome", 1) { "new-$it" }
            state.write("naviamp.playback", "portable prefs")
            StoragePlaybackSessionStore(queries, { 20L }).savePlaybackSession(
                PlaybackSessionSettings(tracks = listOf(SavedTrack("track", "Track", artistName = "Artist")), currentIndex = 0, positionSeconds = 80.0), savedId,
            )
            assertFalse(requireNotNull(fixture.document.value).contains("main-password"))
        } finally { first.close() }

        // A fresh native/JDBC database models the operating system purging the entire cache.
        val replacement = recoveryTestDriver()
        try {
            val state = fixture.open()
            val queries = NaviampStorageDatabase(replacement).naviampStorageQueries
            assertEquals(emptyList(), queries.selectMediaSources().executeAsList(), "The purge fixture must actually start empty")
            val sources = StorageMediaSourceStore(queries, { 99L }, fixture.protector, recovery = state)
            val saved = assertNotNull(sources.mediaSource(savedId))
            assertEquals("main-password", saved.password)
            assertEquals("main-token", saved.token)
            assertEquals("native-token", saved.nativeToken)
            assertEquals(listOf("one", "two"), saved.selectedMusicFolderIds)
            assertEquals("header-secret", saved.customHeaders.single().value)
            assertEquals("tls-password", saved.tlsSettings.clientCertificateKeyStorePassword)
            assertEquals(connection().aurralCompanion, saved.aurralCompanion)
            assertEquals(activeId, sources.latestMediaSource()?.id)
            assertNull(saved.lastSyncCompletedAtEpochMillis)
            assertNull(StoragePlaybackSessionStore(queries, { 99L }).loadPlaybackSession(savedId))
            assertEquals(listOf(RadioDjPreset("dj", "My DJ")), StorageRadioDjPresetStore(queries, { 99L }, state).radioDjPresets())
            assertEquals("portable prefs", state.read("naviamp.playback"))
            assertEquals(profile, StoragePlaybackProfileStore(queries, { 99L }, state).playbackProfile(savedId, profileTarget.copy(id = "new-album")))
            assertEquals(-175, StorageLyricsOffsetStore(queries, { 99L }, state).lyricsOffsetMillis(savedId, TrackId("new-track")))
            assertEquals(1L, sources.providerIdentityVersion(savedId))
        } finally { replacement.close() }
    }

    @Test fun failedDocumentCommitRollsBackConnectionAndCompanionChanges() {
        val fixture = RecoveryFixture()
        val driver = recoveryTestDriver()
        try {
            val state = fixture.open()
            val queries = NaviampStorageDatabase(driver).naviampStorageQueries
            val sources = StorageMediaSourceStore(queries, { 1L }, fixture.protector, recovery = state)
            val id = sources.upsertProviderMediaSource(connection(), "scope", "navidrome").id
            val before = sources.mediaSource(id)
            val encoded = fixture.document.value
            fixture.document.fail = true
            assertFailsWith<StorageRecoveryException> { sources.updateAurralCompanion(id, connection().aurralCompanion!!.copy(password = "changed")) }
            assertEquals(before, sources.mediaSource(id))
            assertEquals(encoded, fixture.document.value)
            assertFailsWith<StorageRecoveryException> { sources.deleteMediaSource(id) }
            assertEquals(before, sources.mediaSource(id))
        } finally { driver.close() }
    }

    @Test fun oversizedBatchRollsBackSettingsConnectionsAndDjPresetsTogether() {
        val fixture = RecoveryFixture()
        val driver = recoveryTestDriver()
        try {
            val state = fixture.open()
            val queries = NaviampStorageDatabase(driver).naviampStorageQueries
            val sources = StorageMediaSourceStore(queries, { 1L }, fixture.protector, recovery = state)
            val presets = StorageRadioDjPresetStore(queries, { 1L }, state)
            assertEquals(StorageRecoveryFailure.Capacity, assertFailsWith<StorageRecoveryException> {
                state.transaction(queries) {
                    sources.upsertProviderMediaSource(connection(), "scope", "navidrome")
                    presets.replaceRadioDjPresets(listOf(RadioDjPreset("dj", "New")))
                    state.write("naviamp.interface", "x".repeat(200_000))
                }
            }.reason)
            assertEquals(emptyList(), sources.mediaSources())
            assertEquals(emptyList(), presets.radioDjPresets())
            assertNull(state.read("naviamp.interface"))
            assertEquals(0, fixture.document.writes)
        } finally { driver.close() }
    }

    @Test fun deletionsAndDatabaseResetDoNotResurrectConnectionsAfterPurge() {
        val fixture = RecoveryFixture()
        val driver = recoveryTestDriver()
        try {
            val state = fixture.open()
            val queries = NaviampStorageDatabase(driver).naviampStorageQueries
            val sources = StorageMediaSourceStore(queries, { 1L }, fixture.protector, recovery = state)
            val id = sources.upsertProviderMediaSource(connection(), "scope", "navidrome").id
            sources.deleteMediaSource(id)
            assertEquals(emptyList(), fixture.open().connections())
            sources.upsertProviderMediaSource(connection(), "scope", "navidrome")
            StorageMaintenanceStore(queries, state).clearAllRows()
            assertEquals(emptyList(), fixture.open().connections())
        } finally { driver.close() }
    }

    @Test fun certificateBytesForMainAndCompanionRecoverIntoRelocatedCacheAndDeletionStaysDeleted() {
        val fixture = RecoveryFixture()
        val original = TestCertificateFiles("/original")
        original.files["/picked/ca.pem"] = byteArrayOf(1, 2, 3)
        original.files["/picked/client.p12"] = byteArrayOf(4, 5, 6)
        val driver = recoveryTestDriver()
        var id = ""
        try {
            val state = fixture.open(original)
            val queries = NaviampStorageDatabase(driver).naviampStorageQueries
            val source = connection().copy(tlsSettings = ConnectionTlsSettings(customCertificatePath = "/picked/ca.pem"),
                aurralCompanion = connection().aurralCompanion!!.copy(tlsSettings = ConnectionTlsSettings(clientCertificateKeyStorePath = "/picked/client.p12", clientCertificateKeyStorePassword = "p12-password")))
            id = StorageMediaSourceStore(queries, { 1L }, fixture.protector, recovery = state)
                .upsertProviderMediaSource(source, "scope", "navidrome").id
        } finally { driver.close() }
        val relocated = TestCertificateFiles("/new-container/cache")
        val replacement = recoveryTestDriver()
        try {
            val state = fixture.open(relocated)
            val sources = StorageMediaSourceStore(NaviampStorageDatabase(replacement).naviampStorageQueries, { 2L }, fixture.protector, recovery = state)
            val saved = assertNotNull(sources.mediaSource(id))
            assertContentEquals(byteArrayOf(1, 2, 3), relocated.files[saved.tlsSettings.customCertificatePath])
            assertContentEquals(byteArrayOf(4, 5, 6), relocated.files[saved.aurralCompanion!!.tlsSettings.clientCertificateKeyStorePath])
            assertEquals("p12-password", saved.aurralCompanion!!.tlsSettings.clientCertificateKeyStorePassword)
            // A normal edit recaptures the restored files without duplicating references.
            sources.updateAurralCompanion(id, saved.aurralCompanion!!.copy(password = "updated"))
            sources.deleteMediaSource(id)
            assertEquals(emptyMap(), relocated.files)
            assertEquals(emptyList(), fixture.open(relocated).connections())
        } finally { replacement.close() }
    }

    @Test fun oversizedOrMissingCertificateRejectsConnectionWithoutChangingSqlOrDocument() {
        val fixture = RecoveryFixture()
        val files = TestCertificateFiles("/cache")
        files.files["/picked/large"] = ByteArray(32 * 1024 + 1)
        val driver = recoveryTestDriver()
        try {
            val state = fixture.open(files)
            val sources = StorageMediaSourceStore(NaviampStorageDatabase(driver).naviampStorageQueries, { 1L }, fixture.protector, recovery = state)
            for (path in listOf("/picked/large", "/picked/missing")) {
                assertFailsWith<StorageRecoveryException> {
                    sources.upsertProviderMediaSource(connection().copy(tlsSettings = ConnectionTlsSettings(customCertificatePath = path)), "scope", "navidrome")
                }
                assertEquals(emptyList(), sources.mediaSources())
                assertEquals(0, fixture.document.writes)
            }
        } finally { driver.close() }
    }

    @Test fun externalCertificateWithRecoveryBasenameDoesNotReplaceAnotherConnectionsCertificate() {
        val fixture = RecoveryFixture()
        val files = TestCertificateFiles("/owned")
        files.files["/picked/ca"] = byteArrayOf(1)
        files.files["/external/naviamp-recovery-certificate-0.bin"] = byteArrayOf(9)
        val driver = recoveryTestDriver()
        var first = ""
        var second = ""
        try {
            val state = fixture.open(files)
            val sources = StorageMediaSourceStore(NaviampStorageDatabase(driver).naviampStorageQueries, { 1L }, fixture.protector, recovery = state)
            first = sources.upsertProviderMediaSource(connection().copy(tlsSettings = ConnectionTlsSettings(customCertificatePath = "/picked/ca")), "one", "navidrome").id
            second = sources.upsertProviderMediaSource(connection().copy(tlsSettings = ConnectionTlsSettings(customCertificatePath = "/external/naviamp-recovery-certificate-0.bin")), "two", "navidrome").id
        } finally { driver.close() }
        val replacement = recoveryTestDriver()
        try {
            val relocated = TestCertificateFiles("/relocated")
            val sources = StorageMediaSourceStore(NaviampStorageDatabase(replacement).naviampStorageQueries, { 1L }, fixture.protector, recovery = fixture.open(relocated))
            assertContentEquals(byteArrayOf(1), relocated.files[sources.mediaSource(first)!!.tlsSettings.customCertificatePath])
            assertContentEquals(byteArrayOf(9), relocated.files[sources.mediaSource(second)!!.tlsSettings.customCertificatePath])
        } finally { replacement.close() }
    }

    @Test fun connectionLimitRejectsSecondSourceAndDeletingFirstAlsoRemovesItsUserConfiguration() {
        val fixture = RecoveryFixture(StorageRecoveryLimits(connectionCount = 1))
        val driver = recoveryTestDriver()
        try {
            val state = fixture.open()
            val queries = NaviampStorageDatabase(driver).naviampStorageQueries
            val sources = StorageMediaSourceStore(queries, { 1L }, fixture.protector, recovery = state)
            val id = sources.upsertProviderMediaSource(connection(), "one", "navidrome").id
            val profiles = StoragePlaybackProfileStore(queries, { 1L }, state)
            profiles.savePlaybackProfile(id, profileTarget, profile)
            StorageLyricsOffsetStore(queries, { 1L }, state).saveLyricsOffsetMillis(id, TrackId("track"), 50)
            assertEquals(StorageRecoveryFailure.Capacity, assertFailsWith<StorageRecoveryException> {
                sources.upsertProviderMediaSource(connection(), "two", "navidrome")
            }.reason)
            assertEquals(listOf(id), sources.mediaSources().map { it.id })
            assertEquals(profile, profiles.playbackProfile(id, profileTarget))
            sources.deleteMediaSource(id)
            assertEquals(emptyList(), queries.selectRecoveryPlaybackProfiles().executeAsList())
            assertEquals(emptyList(), queries.selectRecoveryLyricsOffsets().executeAsList())
            assertEquals(emptyList(), fixture.open().connections())
        } finally { driver.close() }
    }

    private val profileTarget = PlaybackProfileTarget(PlaybackProfileTargetType.Album, "album")
    private val profile = PlaybackProfile(transitionMode = PlaybackTransitionMode.Gapless)

    private fun connection() = ProviderMediaSourceConnection(
        displayName = "Main", baseUrl = "https://fixture.invalid", username = "user", token = "main-token", salt = "salt",
        nativeToken = "native-token", password = "main-password",
        tlsSettings = ConnectionTlsSettings(clientCertificateKeyStorePassword = "tls-password"),
        secondaryUrls = listOf(ConnectionSecondaryUrl("https://secondary.invalid")),
        customHeaders = listOf(ConnectionHeaderDefinition("Authorization", "header-secret", true)),
        selectedMusicFolderIds = listOf("one", "two"),
        aurralCompanion = AurralCompanion(true, "https://companion.invalid", "companion", "companion-password",
            customHeaders = listOf(ConnectionHeaderDefinition("X-Secret", "companion-header", true))),
    )
}

private class TestCertificateFiles(private val root: String) : StorageRecoveryFiles {
    val files = mutableMapOf<String, ByteArray>()
    override fun ownedFilePath(fileName: String) = "$root/$fileName"
    override fun read(filePath: String, maximumBytes: Int) = files[filePath]
    override fun write(fileName: String, bytes: ByteArray): String = "$root/$fileName".also { files[it] = bytes.copyOf() }
    override fun remove(fileName: String): Boolean { files.remove("$root/$fileName"); return true }
}
