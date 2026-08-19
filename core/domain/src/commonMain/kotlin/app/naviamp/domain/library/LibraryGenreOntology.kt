package app.naviamp.domain.library

import app.naviamp.domain.Genre
import app.naviamp.domain.smartplaylist.SmartPlaylistGenreOption

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

data class LibraryGenreOntologyAudit(
    val inventoryGenreCount: Int = 0,
    val exactMatchCount: Int = 0,
    val aliasMatchCount: Int = 0,
    val normalizedMatchCount: Int = 0,
    val unmatchedCount: Int = 0,
    val knownTrackCount: Long? = null,
    val matchedKnownTrackCount: Long? = null,
    val projectedRootCount: Int = 0,
    val largestSelectableGroupSize: Int = 0,
) {
    val matchedCount: Int
        get() = exactMatchCount + aliasMatchCount + normalizedMatchCount

    val nameMatchRatio: Double
        get() = if (inventoryGenreCount == 0) 0.0 else matchedCount.toDouble() / inventoryGenreCount

    val trackMatchRatio: Double?
        get() = knownTrackCount?.takeIf { it > 0L }?.let { total ->
            (matchedKnownTrackCount ?: 0L).toDouble() / total
        }

    /**
     * The hierarchy is a product win only when it covers the library and reduces the initial
     * choice set. Small, fragmented, or poorly matched inventories keep the flat genre browser.
     */
    val usefulForBrowsing: Boolean
        get() {
            if (inventoryGenreCount < MinimumUsefulGenreInventorySize) return false
            if ((trackMatchRatio ?: nameMatchRatio) < MinimumUsefulGenreMatchRatio) return false
            if (largestSelectableGroupSize < MinimumUsefulGenreGroupSize) return false
            val initialChoiceCount = projectedRootCount + unmatchedCount
            return initialChoiceCount.toDouble() / inventoryGenreCount <= MaximumUsefulRootChoiceRatio
        }
}

data class LibraryGenreOntologyProjection(
    val nodes: List<LibraryGenreOntologyNode> = emptyList(),
    val rootIds: List<String> = emptyList(),
    val unmatchedGenreNames: List<String> = emptyList(),
    val audit: LibraryGenreOntologyAudit = LibraryGenreOntologyAudit(),
) {
    /** Provider names are retained because playback APIs expect the server's vocabulary. */
    val selectableGenres: List<Genre>
        get() = nodes
            .flatMap(LibraryGenreOntologyNode::libraryGenreNames)
            .plus(unmatchedGenreNames)
            .distinctBy { it.lowercase() }
            .sortedBy(::normalizeGenreName)
            .map(::Genre)

    fun selectableGenresForSubtree(ontologyId: String): List<Genre> {
        val nodesById = nodes.associateBy(LibraryGenreOntologyNode::id)
        val pending = ArrayDeque(listOf(ontologyId))
        val visited = linkedSetOf<String>()
        val names = mutableListOf<String>()
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (!visited.add(id)) continue
            val node = nodesById[id] ?: continue
            names += node.libraryGenreNames
            pending.addAll(node.childIds)
        }
        return names
            .distinctBy { it.lowercase() }
            .sortedBy(::normalizeGenreName)
            .map(::Genre)
    }
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
    val projection = LibraryGenreOntologyProjection(
        nodes = nodes,
        rootIds = nodes.filter { it.parentIds.isEmpty() }.map(LibraryGenreOntologyNode::id),
        unmatchedGenreNames = inventory
            .filter { it.matchKind == LibraryGenreMatchKind.Unmatched }
            .map(LibraryGenreInventoryItem::sourceName)
            .sortedBy(::normalizeGenreName),
    )
    val knownTrackCounts = inventory.mapNotNull(LibraryGenreInventoryItem::trackCount)
    return projection.copy(
        audit = LibraryGenreOntologyAudit(
            inventoryGenreCount = inventory.size,
            exactMatchCount = inventory.count { it.matchKind == LibraryGenreMatchKind.Exact },
            aliasMatchCount = inventory.count { it.matchKind == LibraryGenreMatchKind.Alias },
            normalizedMatchCount = inventory.count { it.matchKind == LibraryGenreMatchKind.Normalized },
            unmatchedCount = inventory.count { it.matchKind == LibraryGenreMatchKind.Unmatched },
            knownTrackCount = knownTrackCounts.takeIf { it.isNotEmpty() }?.sumOf(Int::toLong),
            matchedKnownTrackCount = inventory
                .filter { it.matchKind != LibraryGenreMatchKind.Unmatched }
                .mapNotNull(LibraryGenreInventoryItem::trackCount)
                .takeIf { it.isNotEmpty() }
                ?.sumOf(Int::toLong),
            projectedRootCount = projection.rootIds.size,
            largestSelectableGroupSize = projection.nodes.maxOfOrNull { node ->
                projection.selectableGenresForSubtree(node.id).size
            } ?: 0,
        ),
    )
}

fun smartPlaylistGenreCatalog(
    ontologyGenres: List<GenreOntologyGenre>,
    inventory: List<LibraryGenreInventoryItem>,
): List<SmartPlaylistGenreOption> {
    val inventoryByGenreId = inventory
        .filter { it.matchedGenreId != null }
        .groupBy { requireNotNull(it.matchedGenreId) }
    return ontologyGenres
        .map { genre ->
            val matches = inventoryByGenreId[genre.id].orEmpty()
            SmartPlaylistGenreOption(
                canonicalName = genre.canonicalName,
                aliases = genre.aliases,
                libraryGenreNames = matches
                    .map(LibraryGenreInventoryItem::sourceName)
                    .distinctBy(::normalizeGenreName)
                    .sortedBy(::normalizeGenreName),
                trackCount = matches.mapNotNull(LibraryGenreInventoryItem::trackCount)
                    .takeIf(List<Int>::isNotEmpty)
                    ?.sum(),
            )
        }
        .sortedBy { normalizeGenreName(it.canonicalName) }
}

private fun List<LibraryGenreInventoryItem>?.sumKnownCounts(
    count: (LibraryGenreInventoryItem) -> Int?,
): Int? = this?.mapNotNull(count)?.takeIf { it.isNotEmpty() }?.sum()

private const val MinimumUsefulGenreInventorySize = 4
private const val MinimumUsefulGenreGroupSize = 2
private const val MinimumUsefulGenreMatchRatio = 0.60
private const val MaximumUsefulRootChoiceRatio = 0.80
