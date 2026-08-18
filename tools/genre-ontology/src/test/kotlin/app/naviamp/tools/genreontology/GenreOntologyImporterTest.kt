package app.naviamp.tools.genreontology

import kotlin.test.Test
import kotlin.test.assertEquals

class GenreOntologyImporterTest {
    @Test
    fun parsesAliasesFromMusicBrainzAliasTable() {
        val page = """
            <table class="tbl"><tbody>
              <tr class="odd"><td colSpan="2"><bdi>hip-hop soul</bdi></td><td>Genre name</td></tr>
              <tr class="even"><td colSpan="2"><bdi>R&amp;B</bdi></td><td>Genre name</td></tr>
            </tbody></table>
        """.trimIndent()

        assertEquals(listOf("hip-hop soul", "R&B"), parseAliases(page))
    }

    @Test
    fun parsesRelationshipDirection() {
        val current = "dddddddd-dddd-dddd-dddd-dddddddddddd"
        val page = """
            <tr><th>subgenre of:</th><td><a href="/genre/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"><bdi>Parent</bdi></a></td></tr>
            <tr><th>subgenres:</th><td><a href="/genre/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"><bdi>Child</bdi></a></td></tr>
            <tr><th>influenced by:</th><td><a href="/genre/cccccccc-cccc-cccc-cccc-cccccccccccc"><bdi>Influence</bdi></a></td></tr>
        """.trimIndent()

        assertEquals(
            setOf(
                RelationRecord("subgenre", current, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                RelationRecord("subgenre", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", current),
                RelationRecord("influenced_by", current, "cccccccc-cccc-cccc-cccc-cccccccccccc"),
            ),
            parseRelationships(page, current),
        )
    }

    @Test
    fun auditDetectsMultipleParentsAndCycles() {
        val ids = listOf(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            "cccccccc-cccc-cccc-cccc-cccccccccccc",
        )
        val report = auditOntology(
            genres = ids.map { GenreRecord(it, it.take(1)) },
            relationships = setOf(
                RelationRecord("subgenre", ids[0], ids[1]),
                RelationRecord("subgenre", ids[0], ids[2]),
                RelationRecord("subgenre", ids[1], ids[0]),
            ),
        )

        assertEquals(1, report.multipleParentCount)
        assertEquals(1, report.cycleComponents.size)
        assertEquals(2, report.cycleComponents.single().size)
    }
}
