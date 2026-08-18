package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class StorageGenreOntologyInstallerTest {
    @Test
    fun installsAndReplacesVersionedReferenceData() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            val database = initializeNaviampStorageDatabase(driver)
            installGenreOntologySnapshot(
                database,
                "one",
                "sha-one",
                payload(firstGenres, firstRelations, listOf(Alias("house", "House Music", "musicbrainz"))),
            )

            assertEquals(2L, database.naviampStorageQueries.selectGenreOntologyCounts().executeAsOne().genre_count)
            assertEquals(1L, database.naviampStorageQueries.selectGenreOntologyCounts().executeAsOne().relation_count)
            assertEquals("one", database.naviampStorageQueries.selectGenreOntologyMetadata().executeAsOne().snapshot_version)
            assertEquals("House Music", database.naviampStorageQueries.selectGenreOntologyAliases().executeAsOne().alias_name)

            installGenreOntologySnapshot(database, "two", "sha-two", payload(secondGenres, emptyList()))

            assertEquals(1L, database.naviampStorageQueries.selectGenreOntologyCounts().executeAsOne().genre_count)
            assertEquals(0L, database.naviampStorageQueries.selectGenreOntologyCounts().executeAsOne().relation_count)
            assertEquals(emptyList(), database.naviampStorageQueries.selectGenreOntologyAliases().executeAsList())
            assertEquals("two", database.naviampStorageQueries.selectGenreOntologyMetadata().executeAsOne().snapshot_version)
        } finally {
            driver.close()
        }
    }

    @Test
    fun invalidReplacementRollsBackToTheInstalledSnapshot() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            val database = initializeNaviampStorageDatabase(driver)
            installGenreOntologySnapshot(database, "one", "sha-one", payload(firstGenres, firstRelations))

            assertFails {
                installGenreOntologySnapshot(
                    database,
                    "broken",
                    "sha-broken",
                    payload(
                        firstGenres.take(1),
                        listOf(Relation("subgenre", firstGenres[0].id, firstGenres[1].id)),
                    ),
                )
            }

            assertEquals(2L, database.naviampStorageQueries.selectGenreOntologyCounts().executeAsOne().genre_count)
            assertEquals(1L, database.naviampStorageQueries.selectGenreOntologyCounts().executeAsOne().relation_count)
            assertEquals("one", database.naviampStorageQueries.selectGenreOntologyMetadata().executeAsOne().snapshot_version)
        } finally {
            driver.close()
        }
    }
}

private data class Genre(val id: String, val name: String)
private data class Relation(val type: String, val source: String, val target: String)
private data class Alias(val genreId: String, val name: String, val source: String)

private val firstGenres = listOf(Genre("electronic", "Electronic"), Genre("house", "House"))
private val firstRelations = listOf(Relation("subgenre", "house", "electronic"))
private val secondGenres = listOf(Genre("jazz", "Jazz"))

private fun payload(
    genres: List<Genre>,
    relations: List<Relation>,
    aliases: List<Alias> = emptyList(),
): String = buildString {
    append("{\"source\":\"test\",\"sourceUrl\":\"https://example.test\",\"snapshot\":\"test\",\"genres\":[")
    append(genres.joinToString(",") { "{\"id\":\"${it.id}\",\"name\":\"${it.name}\",\"disambiguation\":\"\"}" })
    append("],\"aliases\":[")
    append(aliases.joinToString(",") { "{\"genreId\":\"${it.genreId}\",\"name\":\"${it.name}\",\"source\":\"${it.source}\"}" })
    append("],\"relations\":[")
    append(relations.joinToString(",") { "{\"type\":\"${it.type}\",\"source\":\"${it.source}\",\"target\":\"${it.target}\"}" })
    append("]}")
}
