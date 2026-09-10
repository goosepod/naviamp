package app.naviamp.presentation

import app.naviamp.domain.connect.NaviampConnectProvisioningProfile
import app.naviamp.domain.settings.AppBackgroundStyle
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class NaviampCoreConnectProvisioningTest {
    @Test
    fun missingCredentialRequestsCredentialRepair() {
        val unsupported = assertIs<NaviampCoreConnectProvisioningExport.Unsupported>(
            ConnectionFormState(
                serverUrl = "https://music.example.test",
                username = "listener",
            ).toConnectProvisioningExport(),
        )

        assertEquals(true, unsupported.credentialUnavailable)
    }

    @Test
    fun exportsCredentialAndLibrariesWithoutMachineLocalCertificatePaths() {
        val ready = assertIs<NaviampCoreConnectProvisioningExport.Ready>(
            ConnectionFormState(
                providerId = "navidrome",
                displayName = "Home",
                serverUrl = "https://music.example.test",
                username = "listener",
                password = "secret-value",
                selectedMusicFolderIds = listOf("music", "music", " spoken "),
            ).toConnectProvisioningExport(),
        )

        assertEquals(listOf("music", "spoken"), ready.profile.selectedLibraryIds)
        assertFalse(ready.profile.toString().contains("secret-value"))
        assertIs<NaviampCoreConnectProvisioningExport.Unsupported>(
            ConnectionFormState(
                serverUrl = "https://music.example.test",
                username = "listener",
                password = "secret",
                customCertificatePath = "/local/certificate.pem",
            ).toConnectProvisioningExport(),
        )
    }

    @Test
    fun targetFormRetainsEncryptedConnectionFields() {
        val form = NaviampConnectProvisioningProfile(
            providerId = "navidrome",
            displayName = "Home",
            serverUrl = "https://music.example.test",
            username = "listener",
            password = "secret",
            skipTlsVerification = true,
            selectedLibraryIds = listOf("music"),
        ).toConnectionFormState()

        assertEquals("secret", form.password)
        assertEquals(true, form.skipTlsVerification)
        assertEquals(listOf("music"), form.selectedMusicFolderIds)
    }

    @Test
    fun portableSettingsPreserveTargetOnlyDeviceAndHostPolicy() {
        val currentInterface = InterfaceSettings(checkForUpdates = true)
        val offeredInterface = InterfaceSettings(
            checkForUpdates = false,
            keepScreenAwake = true,
            appBackgroundStyle = AppBackgroundStyle.SingleColor,
        )
        val currentPlayback = PlaybackSettings(volumePercent = 77, allowMobileDownloads = false)
        val offeredPlayback = PlaybackSettings(
            gaplessEnabled = false,
            crossfadeDurationSeconds = 8,
            volumePercent = 20,
            allowMobileDownloads = true,
        )

        val mergedInterface = currentInterface.withConnectPortableValues(offeredInterface)
        val mergedPlayback = currentPlayback.withConnectPortableValues(offeredPlayback)

        assertEquals(AppBackgroundStyle.SingleColor, mergedInterface.appBackgroundStyle)
        assertEquals(true, mergedInterface.checkForUpdates)
        assertEquals(true, mergedInterface.keepScreenAwake)
        assertEquals(false, mergedPlayback.gaplessEnabled)
        assertEquals(8, mergedPlayback.crossfadeDurationSeconds)
        assertEquals(77, mergedPlayback.volumePercent)
        assertEquals(false, mergedPlayback.allowMobileDownloads)
    }
}
