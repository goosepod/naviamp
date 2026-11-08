package app.naviamp.provider.navidrome

import app.naviamp.domain.smartplaylist.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.test.*

/** Opt-in acceptance on a disposable server; only this test's playlist is changed and removed. */
class NavidromeSmartPlaylistLiveTest {
    @Test
    fun supportedRoundTripAndUnsupportedInspectionPreserveServerRules() = runBlocking {
        val url = System.getenv("NAVIAMP_PLAYLIST_TEST_URL") ?: return@runBlocking
        check(System.getenv("NAVIAMP_PLAYLIST_TEST_DISPOSABLE") == "true")
        val user = checkNotNull(System.getenv("NAVIAMP_PLAYLIST_TEST_USER"))
        val password = checkNotNull(System.getenv("NAVIAMP_PLAYLIST_TEST_PASSWORD"))
        val connection = NavidromeConnection.fromPassword(baseUrl = url, username = user, password = password)
            .withNativeTokenFromPassword(password, required = true)
        val provider = NavidromeProvider(connection)
        val seed = provider.tracks(limit = 1).first()
        val definition = SmartPlaylistDefinition.fromNspJson(buildJsonObject {
            put("name", "Naviamp acceptance ${UUID.randomUUID()}")
            put("all", buildJsonArray { add(buildJsonObject {
                put("is", buildJsonObject { put("title", seed.title) })
            }) })
            put("sort", "-duration,+title")
            put("order", "desc")
        }.toString())
        val created = provider.createSmartPlaylist(definition)
        try {
            val editing = assertIs<SmartPlaylistEditingResult.Editable>(provider.inspectSmartPlaylist(created.id))
            val saved = SmartPlaylistDraft.fromDefinition(editing.definition).copy(comment = "Round-trip acceptance").toDefinition()
            assertNull(saved.limit)
            assertNull(saved.limitPercent)
            assertEquals(definition.sort, saved.sort)
            assertEquals(definition.rules, saved.rules)
            provider.updateSmartPlaylist(created.id, saved)
            val reloaded = assertIs<SmartPlaylistEditingResult.Editable>(provider.inspectSmartPlaylist(created.id))
            assertEquals(saved.rules, reloaded.definition.rules)
            assertEquals(saved.sort, reloaded.definition.sort)
            assertNull(reloaded.definition.limit)

            val http = KtorNavidromeHttpClient()
            val headers = mapOf(NavidromeNativeAuthorizationHeader to "Bearer ${provider.connectionWithCurrentNativeToken().nativeToken}")
            val endpoint = "${url.trimEnd('/')}/api/playlist/${created.id}"
            val nestedRules = buildJsonObject {
                put("all", buildJsonArray { add(buildJsonObject {
                    put("any", buildJsonArray { add(buildJsonObject {
                        put("all", buildJsonArray { add(definition.rules.single().toJsonElement()) })
                    }) })
                }) })
            }
            http.putJson(endpoint, buildJsonObject { put("rules", nestedRules) }.toString(), headers)
            val before = Json.parseToJsonElement(http.get(endpoint, headers)).jsonObject
            val inspection = assertIs<SmartPlaylistEditingResult.ReadOnly>(provider.inspectSmartPlaylist(created.id))
            val original = Json.parseToJsonElement(inspection.originalJson).jsonObject
            assertEquals(before["rules"], original["rules"])
            val after = Json.parseToJsonElement(http.get(endpoint, headers)).jsonObject
            assertEquals(before["rules"], after["rules"])
            var matches = provider.playlistTracks(created.id)
            repeat(20) {
                if (matches.isEmpty()) { delay(250); matches = provider.playlistTracks(created.id) }
            }
            assertTrue(matches.any { it.id == seed.id }, "Unsupported editing must still permit listening to matching tracks")
            println("Smart playlist live acceptance: supported round-trip, original nested rules retained, matching tracks available")
        } finally {
            provider.deletePlaylist(created.id)
        }
        val deleted = assertIs<SmartPlaylistEditingResult.Unavailable>(provider.inspectSmartPlaylist(created.id))
        assertEquals(SmartPlaylistDefinitionUnavailable.NotFound, deleted.reason)
        println("Smart playlist live acceptance: disposable fixture deleted")
    }
}
