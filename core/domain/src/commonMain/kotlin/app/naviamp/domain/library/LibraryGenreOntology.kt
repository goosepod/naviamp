package app.naviamp.domain.library

import app.naviamp.domain.Genre

enum class LibraryGenreMatchKind {
    Exact,
    Alias,
    Normalized,
    Unmatched,
}

data class LibraryGenreInventoryItem(
    val sourceName: String,
    val normalizedName: String,
    val matchedGenreId: String?,
    val matchKind: LibraryGenreMatchKind,
    val albumCount: Int? = null,
    val trackCount: Int? = null,
)

data class GenreOntologyGenre(
    val id: String,
    val canonicalName: String,
    val aliases: List<String> = emptyList(),
)

/** A child-to-parent ontology relationship. */
data class GenreOntologyParentRelation(
    val childGenreId: String,
    val parentGenreId: String,
)

data class LibraryGenreOntologyNode(
    val id: String,
    val canonicalName: String,
    val libraryGenreNames: List<String>,
    val parentIds: List<String>,
    val childIds: List<String>,
    val albumCount: Int? = null,
    val trackCount: Int? = null,
) {
    val directlyInLibrary: Boolean
        get() = libraryGenreNames.isNotEmpty()
}

data class LibraryGenreOntologyProjection(
    val nodes: List<LibraryGenreOntologyNode> = emptyList(),
    val rootIds: List<String> = emptyList(),
    val unmatchedGenreNames: List<String> = emptyList(),
) {
    /** Provider names are retained because playback APIs expect the server's vocabulary. */
    val selectableGenres: List<Genre>
        get() = nodes
            .flatMap(LibraryGenreOntologyNode::libraryGenreNames)
            .plus(unmatchedGenreNames)
            .distinctBy(::normalizeGenreName)
            .sortedBy(::normalizeGenreName)
            .map(::Genre)
}

fun normalizeGenreName(name: String): String = buildString {
    var needsSeparator = false
    name.trim().lowercase().forEach { character ->
        when {
            character.isLetterOrDigit() -> {
                if (needsSeparator && isNotEmpty()) append(' ')
                append(character)
                needsSeparator = false
            }
            else -> needsSeparator = isNotEmpty()
        }
    }
}

fun matchLibraryGenres(
    sourceGenreNames: List<String>,
    ontologyGenres: List<GenreOntologyGenre>,
): List<LibraryGenreInventoryItem> {
    data class Candidate(val genre: GenreOntologyGenre, val matchedName: String, val isAlias: Boolean)
    val candidatesByNormalizedName = ontologyGenres
        .flatMap { genre ->
            listOf(Candidate(genre, genre.canonicalName, false)) +
                genre.aliases.map { alias -> Candidate(genre, alias, true) }
        }
        .sortedWith(compareBy<Candidate>({ normalizeGenreName(it.matchedName) }, { it.isAlias }, { it.genre.id }))
        .groupBy { normalizeGenreName(it.matchedName) }
    return sourceGenreNames
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy(::normalizeGenreName)
        .sortedBy(::normalizeGenreName)
        .map { sourceName ->
            val normalized = normalizeGenreName(sourceName)
            val candidates = candidatesByNormalizedName[normalized].orEmpty()
            val exact = candidates.firstOrNull {
                !it.isAlias && it.matchedName.equals(sourceName, ignoreCase = true)
            }
            val exactAlias = candidates.firstOrNull {
                it.isAlias && it.matchedName.equals(sourceName, ignoreCase = true)
            }
            val match = exact ?: exactAlias ?: candidates.firstOrNull()
            LibraryGenreInventoryItem(
                sourceName = sourceName,
                normalizedName = normalized,
                matchedGenreId = match?.genre?.id,
                matchKind = when {
                    exact != null -> LibraryGenreMatchKind.Exact
                    exactAlias != null -> LibraryGenreMatchKind.Alias
                    match != null -> LibraryGenreMatchKind.Normalized
                    else -> LibraryGenreMatchKind.Unmatched
                },
            )
        }
}

fun projectLibraryGenreOntology(
    inventory: List<LibraryGenreInventoryItem>,
    ontologyGenres: List<GenreOntologyGenre>,
    parentRelations: List<GenreOntologyParentRelation>,
): LibraryGenreOntologyProjection {
    val genresById = ontologyGenres.associateBy(GenreOntologyGenre::id)
    val parentIdsByChild = parentRelations
        .filter { it.childGenreId in genresById && it.parentGenreId in genresById }
        .groupBy(GenreOntologyParentRelation::childGenreId, GenreOntologyParentRelation::parentGenreId)
    val includedIds = inventory.mapNotNullTo(linkedSetOf(), LibraryGenreInventoryItem::matchedGenreId)
    val pending = ArrayDeque(includedIds)
    while (pending.isNotEmpty()) {
        parentIdsByChild[pending.removeFirst()].orEmpty().forEach { parentId ->
            if (includedIds.add(parentId)) pending.addLast(parentId)
        }
    }

    val includedRelations = parentRelations.filter {
        it.childGenreId in includedIds && it.parentGenreId in includedIds
    }
    val includedParentsByChild = includedRelations
        .groupBy(GenreOntologyParentRelation::childGenreId, GenreOntologyParentRelation::parentGenreId)
    val includedChildrenByParent = includedRelations
        .groupBy(GenreOntologyParentRelation::parentGenreId, GenreOntologyParentRelation::childGenreId)
    val sourceNamesByGenreId = inventory
        .filter { it.matchedGenreId != null }
        .groupBy { requireNotNull(it.matchedGenreId) }
        .mapValues { (_, matches) -> matches.map(LibraryGenreInventoryItem::sourceName).sortedBy(::normalizeGenreName) }
    val inventoryByGenreId = inventory.filter { it.matchedGenreId != null }
        .groupBy { requireNotNull(it.matchedGenreId) }
    val nodes = includedIds.mapNotNull(genresById::get)
        .sortedWith(compareBy({ normalizeGenreName(it.canonicalName) }, GenreOntologyGenre::id))
        .map { genre ->
            LibraryGenreOntologyNode(
                id = genre.id,
                canonicalName = genre.canonicalName,
                libraryGenreNames = sourceNamesByGenreId[genre.id].orEmpty(),
                parentIds = includedParentsByChild[genre.id].orEmpty().distinct().sorted(),
                childIds = includedChildrenByParent[genre.id].orEmpty().distinct().sorted(),
                albumCount = inventoryByGenreId[genre.id].sumKnownCounts(LibraryGenreInventoryItem::albumCount),
                trackCount = inventoryByGenreId[genre.id].sumKnownCounts(LibraryGenreInventoryItem::trackCount),
            )
        }
    return LibraryGenreOntologyProjection(
        nodes = nodes,
        rootIds = nodes.filter { it.parentIds.isEmpty() }.map(LibraryGenreOntologyNode::id),
        unmatchedGenreNames = inventory
            .filter { it.matchKind == LibraryGenreMatchKind.Unmatched }
            .map(LibraryGenreInventoryItem::sourceName)
            .sortedBy(::normalizeGenreName),
    )
}

private fun List<LibraryGenreInventoryItem>?.sumKnownCounts(
    count: (LibraryGenreInventoryItem) -> Int?,
): Int? = this?.mapNotNull(count)?.takeIf { it.isNotEmpty() }?.sum()
