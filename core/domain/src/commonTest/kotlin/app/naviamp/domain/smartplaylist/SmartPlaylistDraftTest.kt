package app.naviamp.domain.smartplaylist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SmartPlaylistDraftTest {
    @Test
    fun convertsDraftToDefinition() {
        val draft = SmartPlaylistDraft(
            name = "Green Day Favorites",
            match = SmartPlaylistMatch.All,
            conditions = listOf(
                SmartPlaylistConditionDraft(
                    field = SmartPlaylistFieldCatalog.fields.first { it.field == SmartPlaylistFields.Artist },
                    operator = SmartPlaylistOperator.Contains,
                    value = "Green Day",
                ),
                SmartPlaylistConditionDraft(
                    field = SmartPlaylistFieldCatalog.fields.first { it.field == SmartPlaylistFields.Loved },
                    operator = SmartPlaylistOperator.Is,
                    value = "true",
                ),
            ),
            sort = listOf(
                SmartPlaylistSortDraft(
                    field = SmartPlaylistFieldCatalog.fields.first { it.field == SmartPlaylistFields.Year },
                    descending = true,
                ),
            ),
            limit = 25,
        )

        val json = draft.toDefinition().toJsonElement()
        val rules = json["all"]?.jsonArray.orEmpty()

        assertEquals("Green Day Favorites", json["name"]?.jsonPrimitive?.content)
        assertEquals("Green Day", rules[0].jsonObject["contains"]?.jsonObject?.get("artist")?.jsonPrimitive?.content)
        assertEquals("true", rules[1].jsonObject["is"]?.jsonObject?.get("loved")?.jsonPrimitive?.content)
        assertEquals("-year", json["sort"]?.jsonPrimitive?.content)
    }

    @Test
    fun convertsRangeDraftValues() {
        val yearField = SmartPlaylistFieldCatalog.fields.first { it.field == SmartPlaylistFields.Year }
        val condition = SmartPlaylistConditionDraft(
            field = yearField,
            operator = SmartPlaylistOperator.InTheRange,
            value = "1990",
            secondValue = "1999",
        ).toConditionOrNull()

        val range = condition
            ?.toJsonElement()
            ?.get("inTheRange")
            ?.jsonObject
            ?.get("year")
            ?.jsonArray

        assertEquals("1990", range?.get(0)?.jsonPrimitive?.content)
        assertEquals("1999", range?.get(1)?.jsonPrimitive?.content)
    }

    @Test
    fun rejectsIncompleteDrafts() {
        val draft = SmartPlaylistDraft(
            conditions = listOf(SmartPlaylistConditionDraft(value = "")),
        )

        assertFailsWith<IllegalArgumentException> {
            draft.toDefinition()
        }
    }
}
