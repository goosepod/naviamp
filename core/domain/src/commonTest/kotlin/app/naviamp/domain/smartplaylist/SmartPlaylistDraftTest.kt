package app.naviamp.domain.smartplaylist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

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
        assertEquals(true, rules[1].jsonObject["is"]?.jsonObject?.get("loved")?.jsonPrimitive?.boolean)
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

    @Test
    fun convertsGroupedDraftRules() {
        val ratingField = SmartPlaylistFieldCatalog.fields.first { it.field == SmartPlaylistFields.Rating }
        val lovedField = SmartPlaylistFieldCatalog.fields.first { it.field == SmartPlaylistFields.Loved }
        val dateAddedField = SmartPlaylistFieldCatalog.fields.first { it.field == SmartPlaylistFields.DateAdded }
        val draft = SmartPlaylistDraft(
            name = "Fresh Good Songs",
            match = SmartPlaylistMatch.All,
            conditions = emptyList(),
            groups = listOf(
                SmartPlaylistGroupDraft(
                    match = SmartPlaylistMatch.Any,
                    conditions = listOf(
                        SmartPlaylistConditionDraft(
                            field = ratingField,
                            operator = SmartPlaylistOperator.GreaterThan,
                            value = "3",
                        ),
                        SmartPlaylistConditionDraft(
                            field = lovedField,
                            operator = SmartPlaylistOperator.Is,
                            value = "true",
                        ),
                    ),
                ),
                SmartPlaylistGroupDraft(
                    match = SmartPlaylistMatch.All,
                    conditions = listOf(
                        SmartPlaylistConditionDraft(
                            field = dateAddedField,
                            operator = SmartPlaylistOperator.InTheLast,
                            value = "7",
                        ),
                    ),
                ),
            ),
        )

        val rules = draft.toDefinition().toJsonElement()["all"]?.jsonArray.orEmpty()

        assertEquals(2, rules.size)
        assertEquals(2, rules[0].jsonObject["any"]?.jsonArray?.size)
        assertEquals(1, rules[1].jsonObject["all"]?.jsonArray?.size)
        assertEquals(7, rules[1].jsonObject["all"]?.jsonArray?.get(0)?.jsonObject?.get("inTheLast")?.jsonObject?.get("dateadded")?.jsonPrimitive?.int)
    }

    @Test
    fun parsesRelativeDatesAsDayCounts() {
        val lastPlayedField = SmartPlaylistFieldCatalog.fields.first { it.field == SmartPlaylistFields.LastPlayed }
        val condition = SmartPlaylistConditionDraft(
            field = lastPlayedField,
            operator = SmartPlaylistOperator.InTheLast,
            value = "30",
        ).toConditionOrNull()

        val days = condition
            ?.toJsonElement()
            ?.get("inTheLast")
            ?.jsonObject
            ?.get("lastplayed")
            ?.jsonPrimitive
            ?.int

        assertEquals(30, days)
    }

    @Test
    fun playlistReferenceDraftUsesNavidromeIdKey() {
        val playlistField = SmartPlaylistFieldCatalog.fields.first { it.valueType == SmartPlaylistValueType.PlaylistId }
        val condition = SmartPlaylistConditionDraft(
            field = playlistField,
            operator = SmartPlaylistOperator.InPlaylist,
            value = "playlist-1",
        ).toConditionOrNull()

        val playlistId = condition
            ?.toJsonElement()
            ?.get("inPlaylist")
            ?.jsonObject
            ?.get("id")
            ?.jsonPrimitive
            ?.content

        assertEquals("playlist-1", playlistId)
    }

    @Test
    fun catalogIncludesDocumentedNavidromeFields() {
        val documentedFields = setOf(
            SmartPlaylistFields.HasCoverArt,
            SmartPlaylistFields.TrackNumber,
            SmartPlaylistFields.OriginalDate,
            SmartPlaylistFields.FilePath,
            SmartPlaylistFields.Duration,
            SmartPlaylistFields.AverageRating,
            SmartPlaylistFields.AlbumLoved,
            SmartPlaylistFields.ArtistPlayCount,
            SmartPlaylistFields.MusicBrainzRecordingId,
            SmartPlaylistFields.LibraryId,
        )

        assertEquals(
            emptySet(),
            documentedFields - SmartPlaylistFieldCatalog.fields.map { it.field }.toSet(),
        )
    }

    @Test
    fun parsesLargeNumericFields() {
        val sizeField = SmartPlaylistFieldCatalog.fields.first { it.field == SmartPlaylistFields.Size }
        val condition = SmartPlaylistConditionDraft(
            field = sizeField,
            operator = SmartPlaylistOperator.GreaterThan,
            value = "4294967296",
        ).toConditionOrNull()

        val size = condition
            ?.toJsonElement()
            ?.get("gt")
            ?.jsonObject
            ?.get("size")
            ?.jsonPrimitive
            ?.long

        assertEquals(4294967296L, size)
    }

    @Test
    fun importsNspJsonToEditableDraft() {
        val definition = SmartPlaylistDefinition.fromNspJson(
            """
            {
              "name": "Fresh Good Songs",
              "comment": "Imported",
              "public": true,
              "all": [
                {
                  "any": [
                    { "gt": { "rating": 3 } },
                    { "is": { "loved": true } }
                  ]
                },
                { "inTheLast": { "dateadded": 7 } }
              ],
              "sort": "-rating,title",
              "limit": 100
            }
            """.trimIndent(),
        )

        val draft = SmartPlaylistDraft.fromDefinition(definition)

        assertEquals("Fresh Good Songs", draft.name)
        assertEquals("Imported", draft.comment)
        assertEquals(true, draft.isPublic)
        assertEquals(1, draft.groups.size)
        assertEquals("3", draft.groups.single().conditions.first().value)
        assertEquals("7", draft.conditions.single().value)
        assertEquals(2, draft.sort.size)
        assertEquals("Rating", draft.sort.first().field.label)
    }

    @Test
    fun rejectsUnsupportedImportedFields() {
        val error = assertFailsWith<IllegalArgumentException> {
            SmartPlaylistDefinition.fromNspJson(
                """
                {
                  "name": "Custom",
                  "all": [
                    { "is": { "my_custom_tag": "yes" } }
                  ]
                }
                """.trimIndent(),
            )
        }

        assertEquals("Imported smart playlist field 'my_custom_tag' is not supported by the builder.", error.message)
    }
}
