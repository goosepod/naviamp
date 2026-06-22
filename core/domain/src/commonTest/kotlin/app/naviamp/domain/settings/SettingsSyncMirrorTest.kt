package app.naviamp.domain.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsSyncMirrorTest {
    @Test
    fun providerWinsWhenLocalMirrorIsMissing() {
        val provider = document(20L)

        val selection = selectSettingsSyncMirrorDocument(
            localMirrorDocument = null,
            providerDocument = provider,
        )

        assertEquals(provider, selection.document)
        assertEquals(SettingsSyncMirrorDocumentSource.Provider, selection.source)
    }

    @Test
    fun localMirrorWinsWhenProviderIsMissing() {
        val local = document(20L)

        val selection = selectSettingsSyncMirrorDocument(
            localMirrorDocument = local,
            providerDocument = null,
        )

        assertEquals(local, selection.document)
        assertEquals(SettingsSyncMirrorDocumentSource.LocalMirror, selection.source)
    }

    @Test
    fun newerProviderWins() {
        val local = document(10L)
        val provider = document(20L)

        val selection = selectSettingsSyncMirrorDocument(
            localMirrorDocument = local,
            providerDocument = provider,
        )

        assertEquals(provider, selection.document)
        assertEquals(SettingsSyncMirrorDocumentSource.Provider, selection.source)
    }

    @Test
    fun newerLocalMirrorWins() {
        val local = document(30L)
        val provider = document(20L)

        val selection = selectSettingsSyncMirrorDocument(
            localMirrorDocument = local,
            providerDocument = provider,
        )

        assertEquals(local, selection.document)
        assertEquals(SettingsSyncMirrorDocumentSource.LocalMirror, selection.source)
    }

    @Test
    fun equalTimestampsKeepLocalMirror() {
        val local = document(20L)
        val provider = document(20L)

        val selection = selectSettingsSyncMirrorDocument(
            localMirrorDocument = local,
            providerDocument = provider,
        )

        assertEquals(local, selection.document)
        assertEquals(SettingsSyncMirrorDocumentSource.LocalMirror, selection.source)
    }

    @Test
    fun missingDocumentsNoOp() {
        val selection = selectSettingsSyncMirrorDocument(
            localMirrorDocument = null,
            providerDocument = null,
        )

        assertNull(selection.document)
        assertEquals(SettingsSyncMirrorDocumentSource.None, selection.source)
    }

    private fun document(updatedAt: Long): SettingsSyncDocument =
        SettingsSyncDocument(updatedAtEpochMillis = updatedAt)
}
