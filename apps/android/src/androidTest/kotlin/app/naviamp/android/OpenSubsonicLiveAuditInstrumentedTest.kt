package app.naviamp.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.provider.MediaPageRequest
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.toNavidromeConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import kotlin.test.*

/** Explicit opt-in: uses Android Keystore-backed saved credentials and a disposable server playlist. */
@RunWith(AndroidJUnit4::class)
class OpenSubsonicLiveAuditInstrumentedTest {
    @Test fun savedAndroidConnectionPreservesPlaylistOccurrencesAndDownloadsOriginalAudio() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveOpenSubsonicAudit") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AndroidStorageDependencies(context).use { storage ->
            val source = storage.mediaSources().first { it.providerId == "navidrome" }
            val provider = NavidromeProvider(source.toNavidromeConnection())
            provider.validateConnection()
            val tracks = provider.tracksPage(MediaPageRequest(limit = 2)).items
            assertEquals(2, tracks.size)
            val initial = listOf(tracks[0].id, tracks[1].id, tracks[0].id)
            val playlist = provider.createPlaylist("Naviamp API audit ${System.currentTimeMillis()}", initial)
            try {
                assertEquals(initial, provider.playlistTracks(playlist.id).map { it.id })
                val replacement = listOf(tracks[1].id, tracks[0].id, tracks[0].id)
                provider.replacePlaylistTracks(playlist.id, initial, replacement)
                assertEquals(replacement, provider.playlistTracks(playlist.id).map { it.id })
                provider.replacePlaylistTracks(playlist.id, replacement, emptyList())
                assertTrue(provider.playlistTracks(playlist.id).isEmpty())
            } finally {
                provider.deletePlaylist(playlist.id)
            }
            val genre = assertNotNull(tracks.first().genres.firstOrNull())
            val genrePage = assertNotNull(provider.genreTracksPage(genre, MediaPageRequest(limit = 2)))
            assertTrue(genrePage.items.isNotEmpty())
            val request = StreamRequest(tracks.first().id, StreamQuality.Original)
            assertTrue(provider.streamUrl(request).contains("format=raw"))
            val downloadUrl = provider.downloadUrl(request)
            assertTrue(downloadUrl.contains("/download.view?"))
            var bytes = 0L
            assertTrue(provider.download(downloadUrl) { _, count -> bytes += count })
            assertTrue(bytes > 4096)
        }
    }
}
