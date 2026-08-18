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
}
