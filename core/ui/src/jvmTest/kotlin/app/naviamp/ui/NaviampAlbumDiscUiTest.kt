package app.naviamp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull

@OptIn(ExperimentalTestApi::class)
class NaviampAlbumDiscUiTest {
    /** Supply getAlbum JSON snapshots via NAVIAMP_DISC_FIXTURE_DIR; credentials are never fixtures. */
    @Test
    fun rendersLocalNavidromeAlbumFixturesAtDesktopAndPhoneSizes() {
        val directory = System.getenv("NAVIAMP_DISC_FIXTURE_DIR")?.let { java.io.File(it) } ?: return
        directory.listFiles { file -> file.name.startsWith("album-") && file.extension == "json" }
            .orEmpty().forEach { file ->
                val data = Json.parseToJsonElement(file.readText()).jsonObject["subsonic-response"]!!
                    .jsonObject["album"]!!.jsonObject
                val tracks = data["song"]!!.jsonArray.map { value ->
                    val song = value.jsonObject
                    Track(id = TrackId(song["id"]!!.jsonPrimitive.content),
                        title = song["title"]!!.jsonPrimitive.content,
                        artistName = song["artist"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        albumTitle = data["name"]!!.jsonPrimitive.content,
                        durationSeconds = song["duration"]?.jsonPrimitive?.intOrNull,
                        coverArtId = null, audioInfo = null, replayGain = null,
                        discNumber = song["discNumber"]?.jsonPrimitive?.intOrNull,
                        trackNumber = song["track"]?.jsonPrimitive?.intOrNull)
                }
                val detail = album(tracks).copy(album = album(tracks).album.copy(
                    title = data["name"]!!.jsonPrimitive.content,
                )).toSharedAlbumDetailUi({ null })
                assertEquals(data["discTitles"]!!.jsonArray.size, detail.discSections.size)
                assertEquals(tracks.size, detail.tracks.size)
                listOf(1200 to 750, 480 to 800).forEach { (width, height) ->
                    runDesktopComposeUiTest(width, height) {
                        setContent {
                            MaterialTheme(colorScheme = darkColorScheme()) {
                                NaviampAlbumDetailContent(NaviampColors.Dark, NaviampAlbumDetailScreenUi(detail = detail),
                                    actions = NaviampAlbumDetailActions(onBack = {}, onAlbumAction = {}, onTrackAction = {}))
                            }
                        }
                        detail.discSections.forEach { section ->
                            onNodeWithText("Disc ${section.number}").performScrollTo().assertIsDisplayed()
                            section.tracks.take(8).lastOrNull { track -> detail.tracks.count { it.title == track.title } == 1 }
                                ?.let { onNodeWithText(it.title).performScrollTo().assertIsDisplayed() }
                            onNodeWithText("Disc ${section.number}").assertIsDisplayed()
                            waitForIdle()
                            val image = org.jetbrains.skia.Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap())
                            val png = checkNotNull(image.encodeToData())
                            java.io.File(directory, "${file.nameWithoutExtension}-${width}-disc-${section.number}.png")
                                .writeBytes(png.bytes)
                            png.close()
                            image.close()
                        }
                    }
                }
            }
    }

    @Test
    fun rendersDiscSectionsAndSelectsTheCorrectRepeatedTrackNumber() = runDesktopComposeUiTest(1200, 750) {
        val detail = album(listOf(track("second", 2, 1).copy(discTitle = "Bonus"), track("first", 1, 1)))
            .toSharedAlbumDetailUi({ null })
        assertEquals(listOf("first", "second"), detail.tracks.map { it.id })
        val selected = mutableListOf<String>()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                NaviampAlbumDetailContent(NaviampColors.Dark, NaviampAlbumDetailScreenUi(detail = detail),
                    actions = NaviampAlbumDetailActions(onBack = {}, onAlbumAction = {},
                        onTrackAction = { selected += it.track.id }))
            }
        }
        onNodeWithText("Disc 1").assertIsDisplayed()
        onNodeWithText("Disc 2 — Bonus").assertIsDisplayed()
        onNodeWithText("second").performClick()
        assertEquals(listOf("second"), selected)
    }

    @Test
    fun singleDiscDoesNotShowAnUnnecessaryHeading() = runDesktopComposeUiTest(480, 800) {
        val detail = album(listOf(track("first", 1, 7))).toSharedAlbumDetailUi({ null })
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                NaviampAlbumDetailContent(NaviampColors.Dark, NaviampAlbumDetailScreenUi(detail = detail),
                    actions = NaviampAlbumDetailActions(onBack = {}, onAlbumAction = {}, onTrackAction = {}))
            }
        }
        onNodeWithText("Disc 1").assertDoesNotExist()
        onNodeWithText("7.").assertIsDisplayed()
    }

    private fun album(tracks: List<Track>) = AlbumDetails(
        Album(AlbumId("album"), "Album", "Artist", null, null), tracks,
    )

    private fun track(id: String, disc: Int, number: Int) = Track(
        id = TrackId(id), title = id, artistName = "Artist", albumTitle = "Album",
        durationSeconds = 120, coverArtId = null, audioInfo = null, replayGain = null,
        discNumber = disc, trackNumber = number,
    )
}
