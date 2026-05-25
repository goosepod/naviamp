package app.naviamp.domain.smartplaylist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SmartPlaylistDefinitionTest {
    @Test
    fun buildsNavidromeRecentlyPlayedJson() {
        val json = Json.parseToJsonElement(
            SmartPlaylistTemplates.recentlyPlayed(days = 30, limit = 100).toNspJson(),
        ).jsonObject

        assertEquals("Recently Played", json["name"]?.jsonPrimitive?.content)
        assertEquals("-lastplayed", json["sort"]?.jsonPrimitive?.content)
        assertEquals(100, json["limit"]?.jsonPrimitive?.int)

        val rule = json["all"]?.jsonArray?.single()?.jsonObject
        val inTheLast = rule?.get("inTheLast")?.jsonObject
        assertEquals(30, inTheLast?.get("lastplayed")?.jsonPrimitive?.int)
    }

    @Test
    fun buildsNestedAnyRulesForEightiesTemplate() {
        val json = SmartPlaylistTemplates.topSongsFromTheEighties().toJsonElement()
        val allRules = json["all"]?.jsonArray.orEmpty()
        val anyRules = allRules.first().jsonObject["any"]?.jsonArray.orEmpty()

        assertEquals(true, anyRules[0].jsonObject["is"]?.jsonObject?.get("loved")?.jsonPrimitive?.boolean)
        assertEquals(3, anyRules[1].jsonObject["gt"]?.jsonObject?.get("rating")?.jsonPrimitive?.int)
        assertEquals("-year", json["sort"]?.jsonPrimitive?.content)
    }

    @Test
    fun supportsPlaylistReferencesAndMultipleSortFields() {
        val definition = SmartPlaylistDefinition(
            name = "Referenced Playlist",
            rules = listOf(
                SmartPlaylistCondition(
                    SmartPlaylistOperator.InPlaylist,
                    "id",
                    SmartPlaylistValue.Text("playlist-1"),
                ),
            ),
            sort = listOf(
                SmartPlaylistSort(SmartPlaylistFields.Year, descending = true),
                SmartPlaylistSort(SmartPlaylistFields.Title),
            ),
            limitPercent = 10,
            isPublic = true,
        )
        val json = definition.toJsonElement()
        val reference = json["all"]
            ?.jsonArray
            ?.single()
            ?.jsonObject
            ?.get("inPlaylist")
            ?.jsonObject
            ?.get("id")

        assertEquals("playlist-1", reference?.jsonPrimitive?.content)
        assertEquals("-year,title", json["sort"]?.jsonPrimitive?.content)
        assertEquals(10, json["limitPercent"]?.jsonPrimitive?.int)
        assertEquals(true, json["public"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun rejectsInvalidLimits() {
        assertFailsWith<IllegalArgumentException> {
            SmartPlaylistDefinition(
                name = "Invalid",
                rules = listOf(
                    SmartPlaylistCondition(
                        SmartPlaylistOperator.Is,
                        SmartPlaylistFields.Loved,
                        SmartPlaylistValue.Flag(true),
                    ),
                ),
                limit = 10,
                limitPercent = 10,
            )
        }
    }

    @Test
    fun createsSafeDefaultFileName() {
        val definition = SmartPlaylistTemplates.favorites()
            .copy(name = "Favorites: 80s / New Wave!")

        assertEquals("favorites-80s-new-wave.nsp", definition.defaultFileName())
    }
}
