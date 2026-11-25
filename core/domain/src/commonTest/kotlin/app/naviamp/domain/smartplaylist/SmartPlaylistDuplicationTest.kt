package app.naviamp.domain.smartplaylist

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SmartPlaylistDuplicationTest {
    private val format: suspend (String, Int) -> String = { base, ordinal ->
        if (ordinal == 1) "$base copy" else "$base copy $ordinal"
    }

    @Test
    fun duplicateNamesAreDistinctFromEveryExistingPlaylistIgnoringCaseAndSpacing() = runTest {
        assertEquals("Mix copy", duplicateSmartPlaylistName("Mix", listOf("Mix"), format))
        assertEquals("Mix copy 2", duplicateSmartPlaylistName(" Mix ", listOf("Mix", " mix COPY "), format))
        assertEquals("Mix copy 4", duplicateSmartPlaylistName("Mix", listOf("Mix", "Mix copy", "Mix copy 2", "Mix copy 3"), format))
    }

    @Test
    fun duplicateDraftKeepsTheCompleteDynamicDefinitionUnderTheNewName() {
        val source = SmartPlaylistDefinition(
            name = "Evening",
            comment = "Calm",
            match = SmartPlaylistMatch.Any,
            rules = listOf(
                SmartPlaylistCondition(SmartPlaylistOperator.Is, SmartPlaylistFields.Loved, SmartPlaylistValue.Flag(true)),
                SmartPlaylistGroup(
                    SmartPlaylistMatch.All,
                    listOf(
                        SmartPlaylistCondition(SmartPlaylistOperator.Contains, SmartPlaylistFields.Genre, SmartPlaylistValue.Text("Jazz")),
                        SmartPlaylistCondition(SmartPlaylistOperator.InTheLast, SmartPlaylistFields.DateAdded, SmartPlaylistValue.Number(30)),
                    ),
                ),
            ),
            sort = listOf(SmartPlaylistSort(SmartPlaylistFields.Rating, descending = true), SmartPlaylistSort(SmartPlaylistFields.Title)),
            limitPercent = 40,
            isPublic = true,
            libraryIds = listOf("2", "5"),
            refreshDelay = "2h",
        )

        val duplicate = source.duplicateDraft("Evening copy").toDefinition()

        assertEquals(source.copy(name = "Evening copy"), duplicate)
    }

    @Test
    fun unlimitedSourcesStayUnlimitedAfterDuplication() {
        val source = SmartPlaylistDefinition(
            name = "All loved",
            rules = listOf(SmartPlaylistCondition(SmartPlaylistOperator.Is, SmartPlaylistFields.Loved, SmartPlaylistValue.Flag(true))),
            isPublic = false,
        )

        val duplicate = source.duplicateDraft("All loved copy")

        assertEquals(SmartPlaylistLimitMode.Unlimited, duplicate.limitMode)
        assertNull(duplicate.toDefinition().limit)
        assertNull(duplicate.toDefinition().limitPercent)
        assertEquals(source.rules, duplicate.toDefinition().rules)
    }
}
