package app.naviamp.tools.genreontology

import kotlin.test.Test
import kotlin.test.assertContains

class LibraryAuditTest {
    @Test
    fun comparesRepresentativeLibrariesAndSelectsTreeOrFlatBrowsing() {
        val ontology = OntologyPayload(
            source = "test",
            sourceUrl = "https://example.test",
            snapshot = "test-snapshot",
            inputManifestSha256 = "manifest",
            genres = listOf(
                GenreRecord("music", "Music"),
                GenreRecord("rock", "Rock"),
                GenreRecord("alternative", "Alternative Rock"),
                GenreRecord("dream-pop", "Dream Pop"),
                GenreRecord("shoegaze", "Shoegaze"),
                GenreRecord("jazz", "Jazz"),
            ),
            aliases = listOf(GenreAliasRecord("alternative", "Alt Rock", "musicbrainz")),
            relations = listOf(
                RelationRecord("subgenre", "rock", "music"),
                RelationRecord("subgenre", "alternative", "rock"),
                RelationRecord("subgenre", "dream-pop", "alternative"),
                RelationRecord("subgenre", "shoegaze", "alternative"),
                RelationRecord("subgenre", "jazz", "music"),
            ),
        )
        val report = renderLibraryAuditReport(
            ontology,
            libraries = listOf(
                library("Well tagged", "Rock", "Alt Rock", "Dream-Pop", "Shoegaze", "Local"),
                library("Fragmented", "Rock", "Jazz", "Local One", "Local Two", "Local Three"),
                library("Tiny", "Rock", "Jazz"),
            ),
        )

        assertContains(report, "| Well tagged | 5 | 4 | 80.0%")
        assertContains(report, "| Well tagged | 5 | 4 | 80.0% | 80.0% | 2 | 4 | Tree |")
        assertContains(report, "| Fragmented | 5 | 2 | 40.0% | 40.0% | 4 | 2 | Flat |")
        assertContains(report, "| Tiny | 2 | 2 | 100.0% | 100.0% | 1 | 2 | Flat |")
    }
}

private fun library(source: String, vararg names: String) = LibraryGenreAuditInput(
    source = source,
    genres = names.map { LibraryGenreAuditInputItem(it, trackCount = 10) },
)
