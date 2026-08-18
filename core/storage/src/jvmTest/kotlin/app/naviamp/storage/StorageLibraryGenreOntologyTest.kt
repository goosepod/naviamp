package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.domain.Genre
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.library.LibraryGenreMatchKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StorageLibraryGenreOntologyTest {
    @Test
    fun persistsSeparateSourceInventoriesAndBuildsPrunedProjection() {
        withGenreStorage { database, sourceOne, sourceTwo ->
            val queries = database.naviampStorageQueries
            installTestOntology(queries, "sha-one")
            val mediaSources = StorageMediaSourceStore(queries, nowMillis = { 1L })
            val store = StorageLibraryIndexStore(queries, mediaSources, nowMillis = { 2L })

            store.replaceLibraryGenreInventory(
                sourceOne,
                listOf(Genre("Dream-Pop", albumCount = 12, trackCount = 345), Genre("Server Only")),
            )
            store.replaceLibraryGenreInventory(sourceTwo, listOf(Genre("Jazz")))

            val first = store.libraryGenreOntologyProjection(sourceOne)
            assertEquals(setOf("music", "rock", "dream-pop"), first.nodes.map { it.id }.toSet())
            assertFalse(first.nodes.any { it.id == "jazz" })
            assertEquals(listOf("Dream-Pop", "Server Only"), first.selectableGenres.map { it.name })
            assertEquals(LibraryGenreMatchKind.Unmatched, store.libraryGenreInventory(sourceOne).last().matchKind)
            assertEquals(12, first.nodes.single { it.id == "dream-pop" }.albumCount)
            assertEquals(345, first.nodes.single { it.id == "dream-pop" }.trackCount)
            assertEquals(listOf("Jazz"), store.libraryGenreOntologyProjection(sourceTwo).selectableGenres.map { it.name })
        }
    }

    @Test
    fun ontologyVersionChangeRematchesStoredProviderNames() {
        withGenreStorage { database, sourceOne, _ ->
            val queries = database.naviampStorageQueries
            installTestOntology(queries, "sha-one", includeLocalStyle = false)
            val mediaSources = StorageMediaSourceStore(queries, nowMillis = { 1L })
            val store = StorageLibraryIndexStore(queries, mediaSources, nowMillis = { 2L })
            store.replaceLibraryGenreInventory(sourceOne, listOf(Genre("Local Style")))
            assertEquals(LibraryGenreMatchKind.Unmatched, store.libraryGenreInventory(sourceOne).single().matchKind)

            installTestOntology(queries, "sha-two", includeLocalStyle = true)

            val rematched = store.libraryGenreInventory(sourceOne).single()
            assertEquals(LibraryGenreMatchKind.Exact, rematched.matchKind)
            assertEquals("local", rematched.matchedGenreId)
        }
    }

    @Test
    fun matchesStoredProviderGenreThroughPersistedAlias() {
        withGenreStorage { database, sourceOne, _ ->
            val queries = database.naviampStorageQueries
            installTestOntology(queries, "sha-alias")
            queries.upsertGenreOntologyAlias("rock", "Rock Music", "musicbrainz")
            val mediaSources = StorageMediaSourceStore(queries, nowMillis = { 1L })
            val store = StorageLibraryIndexStore(queries, mediaSources, nowMillis = { 2L })

            store.replaceLibraryGenreInventory(sourceOne, listOf(Genre("Rock Music")))

            val item = store.libraryGenreInventory(sourceOne).single()
            assertEquals(LibraryGenreMatchKind.Alias, item.matchKind)
            assertEquals("rock", item.matchedGenreId)
        }
    }
}

private fun withGenreStorage(block: (NaviampStorageDatabase, String, String) -> Unit) {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    try {
        NaviampStorageDatabase.Schema.create(driver)
        val database = NaviampStorageDatabase(driver)
        val mediaSources = StorageMediaSourceStore(database.naviampStorageQueries, nowMillis = { 1L })
        fun source(name: String) = mediaSources.upsertProviderMediaSource(
            ProviderMediaSourceConnection(name, "https://$name.example", "user", "token", "salt"),
            cacheNamespace = name,
            providerId = "navidrome",
        ).id
        block(database, source("one"), source("two"))
    } finally {
        driver.close()
    }
}

private fun installTestOntology(
    queries: NaviampStorageQueries,
    sha: String,
    includeLocalStyle: Boolean = false,
) {
    queries.transaction {
        queries.clearGenreOntologyRelations()
        queries.clearGenreOntologyAliases()
        queries.clearGenreOntologyGenres()
        listOf("music" to "Music", "rock" to "Rock", "dream-pop" to "Dream Pop", "jazz" to "Jazz")
            .plus(if (includeLocalStyle) listOf("local" to "Local Style") else emptyList())
            .forEach { (id, name) -> queries.upsertGenreOntologyGenre(id, name, "") }
        queries.upsertGenreOntologyRelation("subgenre", "rock", "music")
        queries.upsertGenreOntologyRelation("subgenre", "dream-pop", "rock")
        queries.upsertGenreOntologyRelation("subgenre", "jazz", "music")
        queries.upsertGenreOntologyMetadata("MusicBrainz", "https://musicbrainz.org", "test", sha)
    }
}
