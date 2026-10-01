package app.naviamp.provider.navidrome

import app.naviamp.domain.AlbumId
import app.naviamp.domain.media.albumDiscSections
import app.naviamp.domain.media.inAlbumOrder
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavidromeAlbumDiscFixtureTest {
    @Test
    fun mapsLocalGetAlbumFixturesWithoutLosingAnyDiscOrTrackNumber() = runTest {
        val directory = System.getenv("NAVIAMP_DISC_FIXTURE_DIR")?.let { java.io.File(it) } ?: return@runTest
        directory.listFiles { file -> file.name.startsWith("album-") && file.extension == "json" }
            .orEmpty().forEach { file ->
                val body = file.readText()
                val data = Json.parseToJsonElement(body).jsonObject["subsonic-response"]!!.jsonObject["album"]!!.jsonObject
                val provider = NavidromeProvider(
                    NavidromeConnection(baseUrl = "https://example.test", username = "fixture", token = "fixture-token", salt = "fixture-salt"),
                    object : NavidromeHttpClient { override suspend fun get(url: String): String = body },
                )
                val detail = provider.album(AlbumId(data["id"]!!.jsonPrimitive.content))
                val songs = data["song"]!!.jsonArray
                assertEquals(songs.size, detail.tracks.size)
                songs.zip(detail.tracks).forEach { (song, track) ->
                    assertEquals(song.jsonObject["discNumber"]?.jsonPrimitive?.intOrNull, track.discNumber)
                    assertEquals(song.jsonObject["track"]?.jsonPrimitive?.intOrNull, track.trackNumber)
                }
                assertEquals(data["discTitles"]!!.jsonArray.size, detail.tracks.albumDiscSections().size)
                assertEquals(detail.tracks.map { it.id }.toSet(), detail.tracks.inAlbumOrder().map { it.id }.toSet())
                assertTrue(detail.tracks.albumDiscSections().all { section ->
                    section.tracks.mapNotNull { it.trackNumber }.zipWithNext().all { (a, b) -> a <= b }
                })
            }
    }
}
