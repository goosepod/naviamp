package app.naviamp.tools.genreontology

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDate
import java.util.ArrayDeque
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val BaseUrl = "https://musicbrainz.org"
private const val UserAgent =
    "NaviampGenreOntologyImporter/0.1 (https://forgejo.goosepod.lan/ursasmar/naviamp)"

private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

@Serializable
data class GenreRecord(
    val id: String,
    val name: String,
    val disambiguation: String = "",
)

@Serializable
data class RelationRecord(
    val type: String,
    val source: String,
    val target: String,
)

@Serializable
data class GenreAliasRecord(
    val genreId: String,
    val name: String,
    val source: String,
)

@Serializable
data class OntologyPayload(
    val source: String,
    val sourceUrl: String,
    val snapshot: String,
    val genres: List<GenreRecord>,
    val aliases: List<GenreAliasRecord>,
    val relations: List<RelationRecord>,
)

private val NaviampCompatibilityAliases = listOf(
    GenreAliasRecord(
        genreId = "52faa157-6bad-4d86-a0ab-d4dec7d2513c",
        name = "rap",
        source = "naviamp_compatibility",
    ),
)

data class AuditReport(
    val genreCount: Int,
    val aliasCount: Int,
    val musicBrainzAliasCount: Int,
    val compatibilityAliasCount: Int,
    val relationshipCount: Int,
    val relationshipCounts: Map<String, Int>,
    val unknownEndpointRelationshipCount: Int,
    val rootCount: Int,
    val isolatedCount: Int,
    val multipleParentCount: Int,
    val maximumParentCount: Int,
    val maximumDirectSubgenreCount: Int,
    val cycleComponents: List<List<String>>,
    val selfCycles: List<String>,
    val maximumCondensedDepth: Int,
)

private data class Options(
    val cacheDirectory: Path = Path.of(".tmp/genre-ontology/musicbrainz"),
    val jsonOutput: Path = Path.of(".tmp/genre-ontology/ontology.json"),
    val kotlinOutput: Path = Path.of(
        "core/storage/src/commonMain/kotlin/app/naviamp/storage/GeneratedGenreOntologySnapshot.kt",
    ),
    val reportOutput: Path = Path.of("docs/genre-ontology-import-audit.md"),
    val snapshot: String = LocalDate.now().toString(),
    val delayMillis: Long = 1_050,
    val maxPages: Int? = null,
)

fun main(arguments: Array<String>) {
    val options = parseOptions(arguments)
    options.cacheDirectory.createDirectories()
    val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    val requestPacer = RequestPacer(options.delayMillis)
    val genres = fetchGenres(client, options, requestPacer)
    val aliases = fetchAliases(client, genres, options, requestPacer)
    val relationships = fetchRelationships(client, genres, options, requestPacer)
    val report = auditOntology(genres, relationships, aliases)

    if (options.maxPages != null && options.maxPages < genres.size) {
        println("Partial audit requested; bundled snapshot was not replaced.")
        println(report.asConsoleText())
        return
    }
    check(report.unknownEndpointRelationshipCount == 0) {
        "Refusing to generate a snapshot with ${report.unknownEndpointRelationshipCount} unknown relationship endpoints"
    }

    val payload = OntologyPayload(
        source = "MusicBrainz CC0 core genre ontology",
        sourceUrl = "https://musicbrainz.org/",
        snapshot = options.snapshot,
        genres = genres.sortedWith(compareBy<GenreRecord> { it.name.lowercase() }.thenBy { it.id }),
        aliases = aliases.sortedWith(compareBy(GenreAliasRecord::genreId, { it.name.lowercase() }, GenreAliasRecord::source)),
        relations = relationships.sortedWith(compareBy(RelationRecord::type, RelationRecord::source, RelationRecord::target)),
    )
    val payloadJson = json.encodeToString(payload)
    val checksum = sha256(payloadJson)
    options.jsonOutput.parent?.createDirectories()
    options.jsonOutput.writeText("$payloadJson\n", StandardCharsets.UTF_8)
    writeKotlinSnapshot(options.kotlinOutput, options.snapshot, checksum, payloadJson)
    writeAuditReport(options.reportOutput, options.snapshot, checksum, report)
    println(report.asConsoleText())
}

private fun fetchAliases(
    client: HttpClient,
    genres: List<GenreRecord>,
    options: Options,
    requestPacer: RequestPacer,
): Set<GenreAliasRecord> {
    val aliasesDirectory = options.cacheDirectory.resolve("alias-pages").also(Path::createDirectories)
    val selected = options.maxPages?.let(genres::take) ?: genres
    val aliases = linkedSetOf<GenreAliasRecord>()
    var fetched = 0
    selected.forEachIndexed { index, genre ->
        val cache = aliasesDirectory.resolve("${genre.id}.html")
        val response = if (cache.exists()) {
            cache.readText()
        } else {
            requestText(client, "$BaseUrl/genre/${genre.id}/aliases", requestPacer)
                .also { cache.writeText(it) }
                .also { fetched += 1 }
        }
        parseAliases(response).forEach { name ->
            if (name.isNotEmpty() && !name.equals(genre.name, ignoreCase = true)) {
                aliases += GenreAliasRecord(genre.id, name, "musicbrainz")
            }
        }
        val processed = index + 1
        if (processed == selected.size || processed % 50 == 0) {
            println("Processed aliases for $processed/${selected.size} genres ($fetched fetched, ${aliases.size} aliases)")
        }
    }
    aliases += NaviampCompatibilityAliases.filter { alias -> genres.any { it.id == alias.genreId } }
    return aliases
}

private fun parseOptions(arguments: Array<String>): Options {
    var options = Options()
    var index = 0
    while (index < arguments.size) {
        val flag = arguments[index]
        fun value(): String = arguments.getOrNull(++index) ?: error("Missing value after $flag")
        options = when (flag) {
            "--cache-dir" -> options.copy(cacheDirectory = Path.of(value()))
            "--json-output" -> options.copy(jsonOutput = Path.of(value()))
            "--kotlin-output" -> options.copy(kotlinOutput = Path.of(value()))
            "--report-output" -> options.copy(reportOutput = Path.of(value()))
            "--snapshot" -> options.copy(snapshot = value())
            "--delay-millis" -> options.copy(delayMillis = value().toLong())
            "--max-pages" -> options.copy(maxPages = value().toInt())
            else -> when {
                flag.startsWith("--snapshot=") -> options.copy(snapshot = flag.substringAfter('='))
                else -> error("Unknown option: $flag")
            }
        }
        index += 1
    }
    require(options.delayMillis >= 1_000) { "MusicBrainz release imports must wait at least 1000 ms per request" }
    require(options.maxPages == null || options.maxPages > 0) { "--max-pages must be positive" }
    return options
}

private fun fetchGenres(client: HttpClient, options: Options, requestPacer: RequestPacer): List<GenreRecord> {
    val cache = options.cacheDirectory.resolve("genres.json")
    if (cache.exists()) {
        return json.decodeFromString(ListSerializer(GenreRecord.serializer()), cache.readText())
    }

    val genres = mutableListOf<GenreRecord>()
    var offset = 0
    while (true) {
        val response = requestText(client, "$BaseUrl/ws/2/genre/all?fmt=json&limit=100&offset=$offset", requestPacer)
        val payload = json.parseToJsonElement(response).jsonObject
        val page = payload.getValue("genres").jsonArray.map { element ->
            val item = element.jsonObject
            GenreRecord(
                id = item.getValue("id").jsonPrimitive.content.lowercase(),
                name = item.getValue("name").jsonPrimitive.content,
                disambiguation = item["disambiguation"]?.jsonPrimitive?.content.orEmpty(),
            )
        }
        genres += page
        offset += page.size
        if (offset >= payload.getValue("genre-count").jsonPrimitive.content.toInt()) break
    }
    cache.writeText(json.encodeToString(ListSerializer(GenreRecord.serializer()), genres) + "\n")
    return genres
}

private fun fetchRelationships(
    client: HttpClient,
    genres: List<GenreRecord>,
    options: Options,
    requestPacer: RequestPacer,
): Set<RelationRecord> {
    val pagesDirectory = options.cacheDirectory.resolve("pages").also(Path::createDirectories)
    val selected = options.maxPages?.let(genres::take) ?: genres
    val relationships = linkedSetOf<RelationRecord>()
    var fetched = 0
    selected.forEachIndexed { index, genre ->
        val cache = pagesDirectory.resolve("${genre.id}.html")
        val page = if (cache.exists()) {
            cache.readText()
        } else {
            requestText(client, "$BaseUrl/genre/${genre.id}", requestPacer)
                .also { cache.writeText(it) }
                .also { fetched += 1 }
        }
        relationships += parseRelationships(page, genre.id)
        val processed = index + 1
        if (processed == selected.size || processed % 50 == 0) {
            println(
                "Processed $processed/${selected.size} genre pages " +
                    "($fetched fetched, ${relationships.size} relationships)",
            )
        }
    }
    return relationships
}

private fun requestText(client: HttpClient, url: String, requestPacer: RequestPacer): String {
    var lastFailure: Throwable? = null
    repeat(4) { attempt ->
        try {
            requestPacer.beforeRequest()
            val request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", UserAgent)
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            check(response.statusCode() in 200..299) { "$url returned HTTP ${response.statusCode()}" }
            return response.body()
        } catch (failure: Exception) {
            lastFailure = failure
            if (attempt < 3) Thread.sleep(1_000L shl attempt)
        }
    }
    error("Failed to fetch $url: ${lastFailure?.message}")
}

private class RequestPacer(private val minimumIntervalMillis: Long) {
    private var lastRequestStartedAtNanos: Long? = null

    fun beforeRequest() {
        lastRequestStartedAtNanos?.let { lastStarted ->
            val elapsedMillis = (System.nanoTime() - lastStarted) / 1_000_000
            val remainingMillis = minimumIntervalMillis - elapsedMillis
            if (remainingMillis > 0) Thread.sleep(remainingMillis)
        }
        lastRequestStartedAtNanos = System.nanoTime()
    }
}

private val rowRegex = Regex("""<tr><th>([^<]+):</th><td[^>]*>(.*?)</td></tr>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val genreLinkRegex = Regex(
    """href="/genre/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"[^>]*><bdi>(.*?)</bdi>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val aliasRowRegex = Regex(
    """<tr[^>]*>\s*<td[^>]*>\s*<bdi>(.*?)</bdi>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

fun parseAliases(page: String): List<String> = aliasRowRegex.findAll(page)
    .map { match -> decodeBasicHtml(match.groupValues[1]).trim() }
    .filter(String::isNotEmpty)
    .distinctBy(String::lowercase)
    .toList()

fun parseRelationships(page: String, currentId: String): Set<RelationRecord> = buildSet {
    rowRegex.findAll(page).forEach { row ->
        val label = decodeBasicHtml(row.groupValues[1]).trim().lowercase()
        val (type, currentIsSource) = when (label) {
            "subgenre of" -> "subgenre" to true
            "subgenres" -> "subgenre" to false
            "fusion of" -> "fusion_of" to true
            "has fusion genres" -> "fusion_of" to false
            "influenced by" -> "influenced_by" to true
            "influenced genres" -> "influenced_by" to false
            else -> return@forEach
        }
        genreLinkRegex.findAll(row.groupValues[2]).forEach { link ->
            val target = link.groupValues[1].lowercase()
            add(
                if (currentIsSource) RelationRecord(type, currentId, target)
                else RelationRecord(type, target, currentId),
            )
        }
    }
}

private fun decodeBasicHtml(value: String): String = value
    .replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")

fun auditOntology(
    genres: List<GenreRecord>,
    relationships: Set<RelationRecord>,
    aliases: Set<GenreAliasRecord> = emptySet(),
): AuditReport {
    val ids = genres.mapTo(hashSetOf(), GenreRecord::id)
    val valid = relationships.filterTo(hashSetOf()) { it.source in ids && it.target in ids }
    val subgenreEdges = valid.filter { it.type == "subgenre" }.mapTo(hashSetOf()) { it.source to it.target }
    val parentCounts = subgenreEdges.groupingBy(Pair<String, String>::first).eachCount()
    val childCounts = subgenreEdges.groupingBy(Pair<String, String>::second).eachCount()
    val components = stronglyConnectedComponents(ids, subgenreEdges)
    val selfCycles = subgenreEdges.filter { it.first == it.second }.map(Pair<String, String>::first).sorted()
    val cycles = components.filter { it.size > 1 }
    val connected = subgenreEdges.flatMapTo(hashSetOf()) { listOf(it.first, it.second) }
    val componentByNode = buildMap {
        components.forEachIndexed { componentIndex, component ->
            component.forEach { put(it, componentIndex) }
        }
    }
    val graph = mutableMapOf<Int, MutableSet<Int>>()
    val indegree = IntArray(components.size)
    subgenreEdges.forEach { (source, target) ->
        val sourceComponent = componentByNode.getValue(source)
        val targetComponent = componentByNode.getValue(target)
        if (sourceComponent != targetComponent && graph.getOrPut(sourceComponent, ::linkedSetOf).add(targetComponent)) {
            indegree[targetComponent] += 1
        }
    }
    val queue = ArrayDeque<Int>()
    val depths = IntArray(components.size)
    indegree.indices.filterTo(queue) { indegree[it] == 0 }
    while (queue.isNotEmpty()) {
        val component = queue.removeFirst()
        graph[component].orEmpty().forEach { target ->
            depths[target] = maxOf(depths[target], depths[component] + 1)
            indegree[target] -= 1
            if (indegree[target] == 0) queue.addLast(target)
        }
    }
    return AuditReport(
        genreCount = genres.size,
        aliasCount = aliases.size,
        musicBrainzAliasCount = aliases.count { it.source == "musicbrainz" },
        compatibilityAliasCount = aliases.count { it.source == "naviamp_compatibility" },
        relationshipCount = valid.size,
        relationshipCounts = valid.groupingBy(RelationRecord::type).eachCount().toSortedMap(),
        unknownEndpointRelationshipCount = relationships.size - valid.size,
        rootCount = ids.count { parentCounts[it] == null },
        isolatedCount = ids.count { it !in connected },
        multipleParentCount = parentCounts.count { it.value > 1 },
        maximumParentCount = parentCounts.maxOfOrNull(Map.Entry<String, Int>::value) ?: 0,
        maximumDirectSubgenreCount = childCounts.maxOfOrNull(Map.Entry<String, Int>::value) ?: 0,
        cycleComponents = cycles,
        selfCycles = selfCycles,
        maximumCondensedDepth = depths.maxOrNull() ?: 0,
    )
}

private fun stronglyConnectedComponents(
    nodes: Set<String>,
    edges: Set<Pair<String, String>>,
): List<List<String>> {
    val adjacency = edges.groupBy({ it.first }, { it.second })
    var nextIndex = 0
    val indices = mutableMapOf<String, Int>()
    val lowLinks = mutableMapOf<String, Int>()
    val stack = ArrayDeque<String>()
    val onStack = hashSetOf<String>()
    val components = mutableListOf<List<String>>()

    fun visit(node: String) {
        indices[node] = nextIndex
        lowLinks[node] = nextIndex
        nextIndex += 1
        stack.addLast(node)
        onStack += node
        adjacency[node].orEmpty().forEach { target ->
            if (target !in indices) {
                visit(target)
                lowLinks[node] = minOf(lowLinks.getValue(node), lowLinks.getValue(target))
            } else if (target in onStack) {
                lowLinks[node] = minOf(lowLinks.getValue(node), indices.getValue(target))
            }
        }
        if (lowLinks[node] == indices[node]) {
            val component = mutableListOf<String>()
            do {
                val member = stack.removeLast()
                onStack -= member
                component += member
            } while (member != node)
            components += component.sorted()
        }
    }

    nodes.sorted().forEach { if (it !in indices) visit(it) }
    return components
}

private fun writeKotlinSnapshot(path: Path, snapshot: String, checksum: String, payloadJson: String) {
    require("\"\"\"" !in payloadJson) { "Snapshot contains a Kotlin raw-string terminator" }
    path.parent?.createDirectories()
    path.writeText(buildString {
        appendLine("// Generated by tools/genre-ontology. Do not edit.")
        appendLine("package app.naviamp.storage")
        appendLine()
        appendLine("internal const val BUNDLED_GENRE_ONTOLOGY_SNAPSHOT = \"$snapshot\"")
        appendLine("internal const val BUNDLED_GENRE_ONTOLOGY_SHA256 = \"$checksum\"")
        appendLine("internal val BUNDLED_GENRE_ONTOLOGY_JSON: String = buildString(${payloadJson.length}) {")
        payloadJson.chunked(12_000).forEach { chunk ->
            append("    append(\"\"\"")
            append(chunk.replace("\$", "\${'\$'}"))
            appendLine("\"\"\")")
        }
        appendLine("}")
    })
}

private fun writeAuditReport(path: Path, snapshot: String, checksum: String, report: AuditReport) {
    val cycleLines = (report.cycleComponents.map { "- `${it.joinToString()}`" } + report.selfCycles.map { "- `$it` (self-cycle)" })
        .joinToString("\n")
    path.parent?.createDirectories()
    path.writeText(
        """# Genre ontology import audit

MusicBrainz snapshot: `$snapshot`
Generated payload SHA-256: `$checksum`

## Summary

- Genres: ${report.genreCount}
- Aliases: ${report.aliasCount} (${report.musicBrainzAliasCount} MusicBrainz, ${report.compatibilityAliasCount} Naviamp compatibility)
- Relationships: ${report.relationshipCount}
- Subgenre relationships: ${report.relationshipCounts["subgenre"] ?: 0}
- Fusion relationships: ${report.relationshipCounts["fusion_of"] ?: 0}
- Influence relationships: ${report.relationshipCounts["influenced_by"] ?: 0}
- Roots in the subgenre projection: ${report.rootCount}
- Genres isolated from the subgenre projection: ${report.isolatedCount}
- Genres with multiple parents: ${report.multipleParentCount}
- Maximum parent count: ${report.maximumParentCount}
- Maximum direct subgenre count: ${report.maximumDirectSubgenreCount}
- Cycle components: ${report.cycleComponents.size + report.selfCycles.size}
- Genres participating in cycles: ${report.cycleComponents.sumOf(List<String>::size) + report.selfCycles.size}
- Maximum depth after condensing cycles: ${report.maximumCondensedDepth}
- Relationships with unknown endpoints: ${report.unknownEndpointRelationshipCount}

## Interpretation

The stored ontology is a graph, not a tree. The product UI must choose a deterministic preferred
parent for multiple-parent genres and must protect recursive traversal from cycles. Fusion and
influence edges are retained for future discovery features but are not part of descendant expansion
for Genre Mix playback.
${if (cycleLines.isEmpty()) "" else "\n## Cycle IDs\n\n$cycleLines\n"}
""",
    )
}

private fun AuditReport.asConsoleText(): String = buildString {
    appendLine("Genres: $genreCount")
    appendLine("Aliases: $aliasCount ($musicBrainzAliasCount MusicBrainz, $compatibilityAliasCount Naviamp compatibility)")
    appendLine("Relationships: $relationshipCount $relationshipCounts")
    appendLine("Roots: $rootCount; isolated: $isolatedCount; multiple parents: $multipleParentCount")
    appendLine("Cycles: ${cycleComponents.size + selfCycles.size}; maximum condensed depth: $maximumCondensedDepth")
    append("Unknown endpoints: $unknownEndpointRelationshipCount")
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
