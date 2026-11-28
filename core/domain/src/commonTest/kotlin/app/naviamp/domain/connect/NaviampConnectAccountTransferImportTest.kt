package app.naviamp.domain.connect

import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncHeaderDefinition
import app.naviamp.domain.settings.SettingsSyncServerProfile
import app.naviamp.domain.source.AurralCompanion
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.SubsonicAuthApiKey
import app.naviamp.domain.source.SubsonicAuthPassword
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampConnectAccountTransferImportTest {
    @Test fun eachProfileBecomesAFormWithItsCredentialAndNoCertificateFiles() {
        val planned = transfer(
            profiles = listOf(
                profile("home", "https://home.example", mode = SubsonicAuthPassword,
                    headers = listOf(SettingsSyncHeaderDefinition("X-Gate", null, valueIsSecret = true))),
                profile("keyed", "https://keyed.example", mode = SubsonicAuthApiKey),
            ),
            credentials = listOf(
                NaviampConnectAccountCredential("home", NaviampConnectCredentialKind.Password, "pw",
                    headerSecrets = mapOf("x-gate" to "gate")),
                NaviampConnectAccountCredential("keyed", NaviampConnectCredentialKind.ApiKey, "key"),
            ),
        ).plannedAccounts(existing = emptyList())
        val home = planned.first { it.profileId == "home" }.form
        assertEquals("pw", home.password)
        assertEquals("gate", home.customHeaders.single().value)
        assertEquals("", home.customCertificatePath)
        val keyed = planned.first { it.profileId == "keyed" }.form
        assertEquals("key", keyed.apiKey)
        assertEquals("", keyed.password)
        assertEquals(SubsonicAuthApiKey, keyed.authenticationMode)
        assertTrue(planned.all { it.existingSourceId == null })
    }

    @Test fun theSameServerAccountUpdatesTheReceiversRowAndKeepsItsLibrariesAndName() {
        val planned = transfer(
            profiles = listOf(profile("home", "https://home.example/", folders = listOf("all-new"))),
            credentials = listOf(NaviampConnectAccountCredential("home", NaviampConnectCredentialKind.Password, "new")),
        ).plannedAccounts(existing = listOf(saved("local-home", "https://home.example", "My server", listOf("jazz"))))
        val account = planned.single()
        assertEquals("local-home", account.existingSourceId)
        assertEquals(listOf("jazz"), account.form.selectedMusicFolderIds)
        assertEquals("My server", account.form.displayName)
        assertEquals("new", account.form.password)
    }

    @Test fun aDifferentUserOnTheSameServerIsANewAccount() {
        val planned = transfer(
            profiles = listOf(profile("home", "https://home.example", username = "bo")),
            credentials = listOf(NaviampConnectAccountCredential("home", NaviampConnectCredentialKind.Password, "pw")),
        ).plannedAccounts(existing = listOf(saved("local-home", "https://home.example", "Home", emptyList())))
        assertNull(planned.single().existingSourceId)
    }

    @Test fun theCompanionReceivesItsPasswordAndSecretHeaders() {
        val planned = transfer(
            profiles = listOf(profile("home", "https://home.example").copy(aurralCompanion = AurralCompanion(
                enabled = true, baseUrl = "https://aurral.example", username = "a",
                customHeaders = listOf(app.naviamp.domain.source.ConnectionHeaderDefinition("X-A", null, true))))),
            credentials = listOf(NaviampConnectAccountCredential("home", NaviampConnectCredentialKind.Password, "pw",
                companionPassword = "cpw", companionHeaderSecrets = mapOf("X-A" to "ah"))),
        ).plannedAccounts(existing = emptyList())
        val companion = planned.single().form.aurralCompanion!!
        assertEquals("cpw", companion.password)
        assertEquals("ah", companion.customHeaders.single().value)
    }

    @Test fun aProfileWithoutACredentialIsNotPlanned() {
        val planned = transfer(profiles = listOf(profile("home", "https://home.example")), credentials = emptyList())
            .plannedAccounts(existing = emptyList())
        assertTrue(planned.isEmpty())
    }

    private fun transfer(profiles: List<SettingsSyncServerProfile>, credentials: List<NaviampConnectAccountCredential>) =
        NaviampConnectAccountTransfer(SettingsSyncDocument(serverProfiles = profiles), credentials)

    private fun profile(
        id: String,
        url: String,
        username: String = "ana",
        mode: String = SubsonicAuthPassword,
        headers: List<SettingsSyncHeaderDefinition> = emptyList(),
        folders: List<String> = emptyList(),
    ) = SettingsSyncServerProfile(id = id, displayName = id, username = username, primaryUrl = url,
        authenticationMode = mode, customHeaders = headers, selectedMusicFolderIds = folders)

    private fun saved(id: String, url: String, name: String, folders: List<String>) = SavedMediaSource(
        id = id, providerId = "navidrome", cacheNamespace = "navidrome:$url:ana", displayName = name, baseUrl = url,
        username = "ana", token = "", salt = "", password = "old", selectedMusicFolderIds = folders,
        createdAtEpochMillis = 1L, lastConnectedAtEpochMillis = 2L, lastSyncStartedAtEpochMillis = null,
        lastSyncCompletedAtEpochMillis = null,
    )
}
