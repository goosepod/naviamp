package app.naviamp.domain.provider

import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineSaveEndpointTest {
    private val base = ProviderCapabilities(true, true, false, false, false)
    private val transcoded = StreamQuality.Transcoded(AudioCodec.Opus, 128)

    @Test
    fun downloadRoleGovernsOnlyTheDownloadOperation() {
        data class Case(
            val downloadRole: Boolean?,
            val streamRole: Boolean?,
            val original: OfflineSaveEndpoint?,
            val transcoded: OfflineSaveEndpoint?,
        )
        val cases = listOf(
            Case(true, true, OfflineSaveEndpoint.Download, OfflineSaveEndpoint.Stream),
            Case(false, true, OfflineSaveEndpoint.Stream, OfflineSaveEndpoint.Stream),
            Case(true, false, OfflineSaveEndpoint.Download, null),
            Case(false, false, null, null),
            // Optional account metadata that is missing never adds a restriction.
            Case(null, null, OfflineSaveEndpoint.Download, OfflineSaveEndpoint.Stream),
            Case(false, null, OfflineSaveEndpoint.Stream, OfflineSaveEndpoint.Stream),
        )
        for (case in cases) {
            val capabilities = base.withAccountMediaRoles(case.downloadRole, case.streamRole)
            assertEquals(case.original, capabilities.offlineSaveEndpoint(StreamQuality.Original), "$case")
            assertEquals(case.transcoded, capabilities.offlineSaveEndpoint(transcoded), "$case")
            assertEquals(case.original != null, capabilities.supportsDownloads, "$case")
        }
    }

    @Test
    fun providerExceptionsCanRouteOriginalSavingThroughStream() {
        assertEquals(OfflineSaveEndpoint.Stream,
            base.offlineSaveEndpoint(StreamQuality.Original, originalUsesStream = true))
        assertNull(base.withAccountMediaRoles(downloadRole = true, streamRole = false)
            .offlineSaveEndpoint(StreamQuality.Original, originalUsesStream = true))
    }

    @Test
    fun defaultsKeepExistingProvidersUnrestricted() {
        assertTrue(base.supportsDownloadEndpoint)
        assertTrue(base.supportsStreaming)
        assertTrue(base.supportsDownloads)
        assertFalse(base.withAccountMediaRoles(false, false).supportsDownloads)
    }
}
