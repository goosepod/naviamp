package app.naviamp.domain.connect

import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.source.AurralCompanion
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.SubsonicAuthApiKey
import app.naviamp.domain.source.SubsonicAuthPassword
import app.naviamp.domain.source.SubsonicAuthToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampConnectAccountTransferExportTest {
    @Test fun everyAccountWithALongLivedSecretTravelsAndSessionTokensNever() {
        val transfer = buildNaviampConnectAccountTransfer(
            snapshot = SettingsSyncLocalSnapshot(serverProfiles = listOf(
                source("home", "https://home.example", password = "pw-home", token = "session-token", salt = "salt"),
                source("keyed", "https://keyed.example", mode = SubsonicAuthApiKey, token = "api-key"),
                source("jelly", "https://jelly.example", providerId = "jellyfin", password = "pw-jelly",
                    nativeToken = "device-bound"),
            )),
            activeSourceId = "keyed",
            includeSettings = false,
            nowEpochMillis = 5L,
            deviceId = "phone",
        )
        assertEquals(listOf("home", "keyed", "jelly"), transfer.document.serverProfiles.map { it.id })
        assertEquals("keyed", transfer.activeProfileId)
        val byId = transfer.credentials.associateBy { it.profileId }
        assertEquals(NaviampConnectCredentialKind.Password to "pw-home", byId.getValue("home").let { it.kind to it.secret })
        assertEquals(NaviampConnectCredentialKind.ApiKey to "api-key", byId.getValue("keyed").let { it.kind to it.secret })
        assertEquals("pw-jelly", byId.getValue("jelly").secret)
        val wire = NaviampConnectWireCodec.encode(NaviampConnectEnvelope(1, "s", 1,
            message = NaviampConnectAccountTransferPayload("t", transfer)))
        listOf("session-token", "salt\"", "device-bound").forEach { token ->
            assertFalse(token in wire, "Session tokens are never copied: $token")
        }
        assertTrue(transfer.skipped.isEmpty())
    }

    @Test fun accountsWithoutAPortableSecretOrWithCertificateFilesAreReportedNotDropped() {
        val transfer = buildNaviampConnectAccountTransfer(
            snapshot = SettingsSyncLocalSnapshot(serverProfiles = listOf(
                source("token-only", "https://old.example", token = "md5", salt = "salt"),
                source("cert", "https://cert.example", password = "pw",
                    tls = ConnectionTlsSettings(clientCertificateKeyStorePath = "/data/client.p12")),
                source("companion-cert", "https://companion.example", password = "pw",
                    companion = AurralCompanion(enabled = true, baseUrl = "https://aurral.example", username = "a",
                        password = "c", tlsSettings = ConnectionTlsSettings(customCertificatePath = "/data/ca.pem"))),
                source("ok", "https://ok.example", password = "pw"),
            )),
            activeSourceId = "token-only",
            includeSettings = false,
            nowEpochMillis = 5L,
            deviceId = "phone",
        )
        assertEquals(listOf("ok"), transfer.document.serverProfiles.map { it.id })
        assertNull(transfer.activeProfileId, "An account that cannot travel is not activated on the receiver")
        assertEquals(
            mapOf(
                "token-only" to NaviampConnectAccountSkipReason.NeedsPassword,
                "cert" to NaviampConnectAccountSkipReason.SetUpOnDevice,
                "companion-cert" to NaviampConnectAccountSkipReason.SetUpOnDevice,
            ),
            transfer.skipped.associate { it.displayName to it.reason },
        )
    }

    @Test fun aPasswordTypedOnTheSendingDeviceFillsAMissingSecret() {
        val sources = listOf(source("token-only", "https://old.example", token = "md5", salt = "salt"))
        assertEquals(mapOf("token-only" to NaviampConnectTransferReadiness.NeedsPassword),
            naviampConnectTransferReadiness(sources))
        val transfer = buildNaviampConnectAccountTransfer(
            snapshot = SettingsSyncLocalSnapshot(serverProfiles = sources),
            activeSourceId = "token-only",
            includeSettings = false,
            typedSecrets = mapOf("token-only" to "typed"),
            nowEpochMillis = 5L,
            deviceId = "phone",
        )
        assertEquals("typed", transfer.credentials.single().secret)
        assertEquals("token-only", transfer.activeProfileId)
    }

    @Test fun secretHeadersAndTheCompanionPasswordTravelAsCredentialsNotInTheDocument() {
        val transfer = buildNaviampConnectAccountTransfer(
            snapshot = SettingsSyncLocalSnapshot(serverProfiles = listOf(
                source("home", "https://home.example", password = "pw",
                    headers = listOf(ConnectionHeaderDefinition("X-Gate", "gate-secret", valueIsSecret = true),
                        ConnectionHeaderDefinition("X-Plain", "plain", valueIsSecret = false)),
                    companion = AurralCompanion(enabled = true, baseUrl = "https://aurral.example", username = "a",
                        password = "companion-secret")),
            )),
            activeSourceId = null,
            includeSettings = false,
            nowEpochMillis = 5L,
            deviceId = "phone",
        )
        val credential = transfer.credentials.single()
        assertEquals(mapOf("X-Gate" to "gate-secret"), credential.headerSecrets)
        assertEquals("companion-secret", credential.companionPassword)
        val document = NaviampConnectWireCodec.encode(NaviampConnectEnvelope(1, "s", 1,
            message = NaviampConnectAccountTransferPayload("t", transfer.copy(credentials = emptyList()))))
        assertFalse("gate-secret" in document)
        assertFalse("companion-secret" in document)
        assertTrue("plain" in document)
    }

    @Test fun onlySelectedAccountsAndOneRowPerServerAccountAreSent() {
        val transfer = buildNaviampConnectAccountTransfer(
            snapshot = SettingsSyncLocalSnapshot(serverProfiles = listOf(
                source("home-all", "https://home.example", password = "pw", lastConnected = 10L),
                source("home-jazz", "https://home.example", password = "pw", folders = listOf("jazz"), lastConnected = 20L),
                source("work", "https://work.example", password = "pw"),
            )),
            activeSourceId = "home-all",
            includeSettings = false,
            selectedSourceIds = setOf("home-all"),
            nowEpochMillis = 5L,
            deviceId = "phone",
        )
        assertEquals(listOf("home-all"), transfer.document.serverProfiles.map { it.id })
    }

    @Test fun settingsTravelOnlyWhenIncluded() {
        val snapshot = SettingsSyncLocalSnapshot(
            serverProfiles = listOf(source("home", "https://home.example", password = "pw")),
            interfaceSettings = InterfaceSettings(language = app.naviamp.domain.settings.InterfaceLanguage.Spanish),
        )
        val without = buildNaviampConnectAccountTransfer(snapshot, null, includeSettings = false, nowEpochMillis = 1L, deviceId = "p")
        val with = buildNaviampConnectAccountTransfer(snapshot, null, includeSettings = true, nowEpochMillis = 1L, deviceId = "p")
        assertFalse(without.includeSettings)
        assertEquals(app.naviamp.domain.settings.InterfaceLanguage.Spanish, with.document.preferences.interfaceSettings.language)
        assertTrue(with.includeSettings)
    }

    private fun source(
        id: String,
        url: String,
        providerId: String = "navidrome",
        password: String? = null,
        token: String = "",
        salt: String = "",
        nativeToken: String? = null,
        mode: String = if (password != null) SubsonicAuthPassword else SubsonicAuthToken,
        tls: ConnectionTlsSettings = ConnectionTlsSettings(),
        headers: List<ConnectionHeaderDefinition> = emptyList(),
        companion: AurralCompanion? = null,
        folders: List<String> = emptyList(),
        lastConnected: Long? = null,
    ) = SavedMediaSource(
        id = id, providerId = providerId, cacheNamespace = "$providerId:$url:ana:$folders", displayName = id,
        baseUrl = url, username = "ana", token = token, salt = salt, nativeToken = nativeToken, password = password,
        tlsSettings = tls, customHeaders = headers, selectedMusicFolderIds = folders,
        createdAtEpochMillis = 1L, lastConnectedAtEpochMillis = lastConnected,
        lastSyncStartedAtEpochMillis = null, lastSyncCompletedAtEpochMillis = null,
        authenticationMode = mode, aurralCompanion = companion,
    )
}
