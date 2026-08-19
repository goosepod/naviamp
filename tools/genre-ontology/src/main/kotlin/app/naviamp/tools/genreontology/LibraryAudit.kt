package app.naviamp.tools.genreontology

import app.naviamp.domain.library.GenreOntologyGenre
import app.naviamp.domain.library.GenreOntologyParentRelation
import app.naviamp.domain.library.LibraryGenreInventoryItem
import app.naviamp.domain.library.matchLibraryGenres
import app.naviamp.domain.library.normalizeGenreName
import app.naviamp.domain.library.projectLibraryGenreOntology
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LibraryGenreAuditInput(
    val source: String,
    val genres: List<LibraryGenreAuditInputItem>,
)

@Serializable
data class LibraryGenreAuditInputItem(
    val name: String,
    val albumCount: Int? = null,
    val trackCount: Int? = null,
)

private val auditJson = Json { ignoreUnknownKeys = true }

fun main(arguments: Array<String>) {
    var ontologyPath: Path? = null
    var ontologyKotlinPath: Path? = null
    var databasePath: Path? = null
    var outputPath = Path.of(".tmp/genre-ontology/library-audit.md")
    val libraryPaths = mutableListOf<Path>()
    var index = 0
    while (index < arguments.size) {
        val flag = arguments[index]
        fun pathValue(): String {
            val parts = mutableListOf<String>()
            while (arguments.getOrNull(index + 1)?.startsWith("--") == false) {
                parts += arguments[++index]
            }
            return parts.joinToString(" ").takeIf(String::isNotEmpty)
                ?: error("Missing value after $flag")
        }
        when (flag) {
            "--ontology" -> ontologyPath = Path.of(pathValue())
            "--ontology-kotlin" -> ontologyKotlinPath = Path.of(pathValue())
            "--database" -> databasePath = Path.of(pathValue())
            "--library" -> libraryPaths.add(Path.of(pathValue()))
            "--output" -> outputPath = Path.of(pathValue())
            else -> error("Unknown option: $flag")
        }
        index += 1
    }
    require(libraryPaths.isNotEmpty() || databasePath != null) {
        "Provide at least one --library genre inventory JSON file or --database"
    }
    val ontologyJson = when {
        ontologyPath != null -> ontologyPath.readText()
        ontologyKotlinPath != null -> readGeneratedOntologyJson(ontologyKotlinPath)
        else -> error("Provide --ontology JSON or --ontology-kotlin generated snapshot")
    }
    val ontology = auditJson.decodeFromString<OntologyPayload>(ontologyJson)
    val inputs = libraryPaths.map { path -> auditJson.decodeFromString<LibraryGenreAuditInput>(path.readText()) } +
        databasePath?.let(::loadLibraryAuditsFromDatabase).orEmpty()
    val report = renderLibraryAuditReport(ontology, inputs)
    outputPath.parent?.createDirectories()
    outputPath.writeText(report)
    println(report)
}

fun readGeneratedOntologyJson(path: Path): String = Regex(
    "append\\(\\\"\\\"\\\"(.*?)\\\"\\\"\\\"\\)",
    RegexOption.DOT_MATCHES_ALL,
).findAll(path.readText()).joinToString("") { it.groupValues[1] }
    .takeIf(String::isNotEmpty)
    ?: error("No generated ontology payload chunks found in $path")

fun loadLibraryAuditsFromDatabase(path: Path): List<LibraryGenreAuditInput> {
    val countsBySource = linkedMapOf<String, MutableMap<String, Int>>()
    val namesBySource = linkedMapOf<String, MutableMap<String, String>>()
    val databaseUrl = "jdbc:sqlite:file:${path.toAbsolutePath()}?mode=ro&immutable=1"
    DriverManager.getConnection(databaseUrl).use { connection ->
        connection.prepareStatement(
            "SELECT source_id, genre_names FROM library_track " +
                "WHERE genre_names IS NOT NULL AND genre_names != ''",
        ).use { statement ->
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    val source = rows.getString(1)
                    val counts = countsBySource.getOrPut(source, ::linkedMapOf)
                    val names = namesBySource.getOrPut(source, ::linkedMapOf)
                    rows.getString(2).split('\u001f')
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .distinctBy(::normalizeGenreName)
                        .forEach { name ->
                            val normalized = normalizeGenreName(name)
                            names.putIfAbsent(normalized, name)
                            counts[normalized] = counts.getOrDefault(normalized, 0) + 1
                        }
                }
            }
        }
    }
    return countsBySource.keys.sorted().mapIndexed { index, source ->
        LibraryGenreAuditInput(
            source = "Local source ${index + 1}",
            genres = countsBySource.getValue(source).map { (normalized, count) ->
                LibraryGenreAuditInputItem(
                    name = namesBySource.getValue(source).getValue(normalized),
                    trackCount = count,
                )
            },
        )
    }
}

fun renderLibraryAuditReport(
    ontology: OntologyPayload,
    libraries: List<LibraryGenreAuditInput>,
): String {
    val aliasesByGenre = ontology.aliases.groupBy({ it.genreId }, { it.name })
    val genres = ontology.genres.map { genre ->
        GenreOntologyGenre(genre.id, genre.name, aliasesByGenre[genre.id].orEmpty())
    }
    val parents = ontology.relations
        .filter { it.type == "subgenre" }
        .map { GenreOntologyParentRelation(it.source, it.target) }
    return buildString {
        appendLine("# Naviamp library genre ontology audit")
        appendLine()
        appendLine("Ontology: `${ontology.snapshot}` (`${ontology.inputManifestSha256.ifBlank { "legacy-unmanifested" }}`)")
        appendLine()
        appendLine("| Source | Genres | Matched | Name coverage | Track coverage | Initial choices | Largest group | Browser |")
        appendLine("|---|---:|---:|---:|---:|---:|---:|---|")
        libraries.forEach { library ->
            val inputByName = library.genres.associateBy { normalizeGenreName(it.name) }
            val inventory = matchLibraryGenres(library.genres.map { it.name }, genres).map { item ->
                item.withCounts(inputByName[item.normalizedName])
            }
            val audit = projectLibraryGenreOntology(inventory, genres, parents).audit
            val initialChoices = audit.projectedRootCount + audit.unmatchedCount
            appendLine(
                "| ${library.source.escapeTable()} | ${audit.inventoryGenreCount} | ${audit.matchedCount} | " +
                    "${audit.nameMatchRatio.percent()} | ${audit.trackMatchRatio?.percent() ?: "n/a"} | " +
                    "$initialChoices | ${audit.largestSelectableGroupSize} | " +
                    if (audit.usefulForBrowsing) "Tree |" else "Flat |",
            )
        }
    }
}

private fun LibraryGenreInventoryItem.withCounts(input: LibraryGenreAuditInputItem?): LibraryGenreInventoryItem =
    copy(albumCount = input?.albumCount, trackCount = input?.trackCount)

private fun Double.percent(): String = "${(this * 1_000).toInt() / 10.0}%"

private fun String.escapeTable(): String = replace("|", "\\|")
