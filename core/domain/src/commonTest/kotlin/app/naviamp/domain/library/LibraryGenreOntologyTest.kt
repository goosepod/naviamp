package app.naviamp.domain.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryGenreOntologyTest {
    @Test
    fun matchesProviderGenreThroughOntologyAlias() {
        val inventory = matchLibraryGenres(
            sourceGenreNames = listOf("Rap"),
            ontologyGenres = listOf(
                GenreOntologyGenre("hip-hop", "Hip Hop", aliases = listOf("Rap")),
            ),
        )

        assertEquals("hip-hop", inventory.single().matchedGenreId)
        assertEquals(LibraryGenreMatchKind.Alias, inventory.single().matchKind)
        assertEquals("Rap", inventory.single().sourceName)
    }

    @Test
    fun matchesCanonicalNamesAfterPortableNormalization() {
        val inventory = matchLibraryGenres(
            sourceGenreNames = listOf("  Dream-Pop ", "JAZZ", "Server Only", "dream pop"),
            ontologyGenres = listOf(
                GenreOntologyGenre("dream-pop", "Dream Pop"),
                GenreOntologyGenre("jazz", "Jazz"),
            ),
        )

        assertEquals(listOf("Dream-Pop", "JAZZ", "Server Only"), inventory.map { it.sourceName })
        assertEquals(
            listOf(LibraryGenreMatchKind.Normalized, LibraryGenreMatchKind.Exact, LibraryGenreMatchKind.Unmatched),
            inventory.map { it.matchKind },
        )
        assertEquals(listOf("dream-pop", "jazz", null), inventory.map { it.matchedGenreId })
    }

    @Test
    fun projectionKeepsMatchedGenresAndAllAncestorsButHidesEmptyBranches() {
        val genres = listOf(
            GenreOntologyGenre("music", "Music"),
            GenreOntologyGenre("rock", "Rock"),
            GenreOntologyGenre("alternative", "Alternative Rock"),
            GenreOntologyGenre("dream-pop", "Dream Pop"),
            GenreOntologyGenre("jazz", "Jazz"),
        )
        val projection = projectLibraryGenreOntology(
            inventory = matchLibraryGenres(listOf("Dream-Pop", "Local Style"), genres),
            ontologyGenres = genres,
            parentRelations = listOf(
                GenreOntologyParentRelation("rock", "music"),
                GenreOntologyParentRelation("alternative", "rock"),
                GenreOntologyParentRelation("dream-pop", "alternative"),
                GenreOntologyParentRelation("dream-pop", "rock"),
                GenreOntologyParentRelation("jazz", "music"),
            ),
        )

        assertEquals(setOf("music", "rock", "alternative", "dream-pop"), projection.nodes.map { it.id }.toSet())
        assertFalse(projection.nodes.any { it.id == "jazz" })
        assertEquals(listOf("music"), projection.rootIds)
        assertEquals(listOf("alternative", "rock"), projection.nodes.single { it.id == "dream-pop" }.parentIds)
        assertTrue(projection.nodes.single { it.id == "dream-pop" }.directlyInLibrary)
        assertFalse(projection.nodes.single { it.id == "rock" }.directlyInLibrary)
        assertEquals(listOf("Dream-Pop", "Local Style"), projection.selectableGenres.map { it.name })
    }

    @Test
    fun auditEnablesAWellCoveredHierarchyAndExpandsGroupsToProviderNames() {
        val genres = listOf(
            GenreOntologyGenre("music", "Music"),
            GenreOntologyGenre("rock", "Rock"),
            GenreOntologyGenre("alternative", "Alternative Rock", aliases = listOf("Alt Rock")),
            GenreOntologyGenre("dream-pop", "Dream Pop"),
            GenreOntologyGenre("shoegaze", "Shoegaze"),
        )
        val inventory = matchLibraryGenres(
            listOf("Rock", "Alt Rock", "Dream-Pop", "Shoegaze", "Server Only"),
            genres,
        ).map { item -> item.copy(trackCount = if (item.matchedGenreId == null) 5 else 100) }
        val projection = projectLibraryGenreOntology(
            inventory = inventory,
            ontologyGenres = genres,
            parentRelations = listOf(
                GenreOntologyParentRelation("rock", "music"),
                GenreOntologyParentRelation("alternative", "rock"),
                GenreOntologyParentRelation("dream-pop", "alternative"),
                GenreOntologyParentRelation("shoegaze", "alternative"),
            ),
        )

        assertTrue(projection.audit.usefulForBrowsing)
        assertEquals(5, projection.audit.inventoryGenreCount)
        assertEquals(4, projection.audit.matchedCount)
        assertEquals(4, projection.audit.largestSelectableGroupSize)
        assertEquals(listOf("Alt Rock", "Dream-Pop", "Rock", "Shoegaze"), projection
            .selectableGenresForSubtree("rock")
            .map { it.name })
    }

    @Test
    fun auditRejectsFragmentedAndPoorlyMatchedLibraries() {
        val ontology = listOf(
            GenreOntologyGenre("rock", "Rock"),
            GenreOntologyGenre("jazz", "Jazz"),
        )
        val projection = projectLibraryGenreOntology(
            inventory = matchLibraryGenres(
                listOf("Rock", "Jazz", "Local One", "Local Two", "Local Three"),
                ontology,
            ),
            ontologyGenres = ontology,
            parentRelations = emptyList(),
        )

        assertFalse(projection.audit.usefulForBrowsing)
        assertEquals(2, projection.audit.matchedCount)
        assertEquals(3, projection.audit.unmatchedCount)
    }

    @Test
    fun smartPlaylistCatalogRetainsTheOntologyAndAddsLibraryAvailability() {
        val ontology = listOf(
            GenreOntologyGenre("hip-hop", "Hip Hop", aliases = listOf("Rap")),
            GenreOntologyGenre("jazz", "Jazz"),
        )
        val inventory = listOf(
            LibraryGenreInventoryItem("Rap", "rap", "hip-hop", LibraryGenreMatchKind.Alias, trackCount = 80),
            LibraryGenreInventoryItem("Hip-Hop", "hip hop", "hip-hop", LibraryGenreMatchKind.Normalized, trackCount = 25),
        )

        val catalog = smartPlaylistGenreCatalog(ontology, inventory)

        assertEquals(listOf("Hip Hop", "Jazz"), catalog.map { it.canonicalName })
        assertEquals(listOf("Hip-Hop", "Rap"), catalog.first().libraryGenreNames)
        assertEquals(105, catalog.first().trackCount)
        assertFalse(catalog.last().inLibrary)
    }
}
