package app.naviamp.domain.smartplaylist

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray

/** Read-only results deliberately expose no draft or definition that can be sent to a save API. */
sealed interface SmartPlaylistEditingResult {
    data class Editable(val definition: SmartPlaylistDefinition, val originalJson: String) : SmartPlaylistEditingResult
    data class ReadOnly(val originalJson: String) : SmartPlaylistEditingResult
    data class Unavailable(val reason: SmartPlaylistDefinitionUnavailable = SmartPlaylistDefinitionUnavailable.Missing) : SmartPlaylistEditingResult
}

enum class SmartPlaylistDefinitionUnavailable { Missing, PermissionDenied, NotFound }

private val InspectionJson = Json { prettyPrint = true }

fun inspectSmartPlaylistJson(json: String): SmartPlaylistEditingResult = try {
    inspectSmartPlaylistObject(InspectionJson.parseToJsonElement(json).jsonObject, json).let {
        if (it is SmartPlaylistEditingResult.Unavailable) SmartPlaylistEditingResult.ReadOnly(json) else it
    }
} catch (_: IllegalArgumentException) {
    SmartPlaylistEditingResult.ReadOnly(json)
}

fun inspectSmartPlaylistObject(
    original: JsonObject,
    originalJson: String = InspectionJson.encodeToString(JsonObject.serializer(), original),
    mapDefinition: (SmartPlaylistDefinition) -> SmartPlaylistDefinition = { it },
): SmartPlaylistEditingResult {
    return try {
        val root = original["data"]?.jsonObject ?: original
        val rules = root["rules"]?.takeUnless { it == JsonNull }?.jsonObject ?: root
        if ("all" !in rules && "any" !in rules) return if (root["rules"] != null && root["rules"] != JsonNull)
            SmartPlaylistEditingResult.ReadOnly(originalJson) else SmartPlaylistEditingResult.Unavailable()
        require(("all" in rules) != ("any" in rules))
        requireUnambiguousRules(rules)
        for (key in listOf("sort", "order", "refreshDelay")) rules[key]?.let { require(it.jsonPrimitive.isString) }
        root["public"]?.let { require(!it.jsonPrimitive.isString && it.jsonPrimitive.booleanOrNull != null) }
        val supported = setOf("all", "any", "sort", "order", "limit", "limitPercent", "refreshDelay")
        val metadata = if (rules === root) setOf("name", "comment", "public") else emptySet()
        require(rules.keys.all { it in supported || it in metadata })
        // Reject options older parsers would otherwise silently ignore or truncate.
        for (key in listOf("limit", "limitPercent")) rules[key]?.let { raw ->
            val primitive = raw.jsonPrimitive
            val number = primitive.longOrNull
            require(!primitive.isString && number != null && number in 1..Int.MAX_VALUE.toLong())
        }
        val definition = mapDefinition(SmartPlaylistDefinition.fromJsonObject(root))
        require(effectiveOrderIsPreserved(rules, definition))
        val draft = SmartPlaylistDraft.fromDefinition(definition)
        require(canonicalRules(definition.rules) == canonicalRules(draft.toDefinition().rules))
        SmartPlaylistEditingResult.Editable(definition, originalJson)
    } catch (_: IllegalArgumentException) {
        SmartPlaylistEditingResult.ReadOnly(originalJson)
    } catch (_: NoSuchElementException) {
        SmartPlaylistEditingResult.ReadOnly(originalJson)
    }
}

private fun canonicalRules(rules: List<SmartPlaylistRule>): List<String> = rules.map { rule ->
    when (rule) {
        is SmartPlaylistGroup -> rule.match.name + canonicalRules(rule.rules).joinToString()
        else -> rule.toJsonElement().toString()
    }
}.sorted()

fun SmartPlaylistDefinition.editingResult(): SmartPlaylistEditingResult =
    inspectSmartPlaylistObject(toJsonElement(), mapDefinition = { this })

// Also detects older provider parsers that ignore a global order instead of applying it.
private fun effectiveOrderIsPreserved(rules: JsonObject, definition: SmartPlaylistDefinition): Boolean {
    val order = rules["order"]?.jsonPrimitive?.content?.trim()?.lowercase() ?: return true
    if (order !in setOf("", "asc", "desc")) return false
    val fields = rules["sort"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "title"
    val effective = fields.split(',').mapNotNull { raw ->
        val part = raw.trim()
        if (part.isBlank()) null else SmartPlaylistSort(part.removePrefix("+").removePrefix("-").trim().lowercase(),
            part.startsWith("-") != (order == "desc"))
    }
    val actual = definition.sort.ifEmpty { listOf(SmartPlaylistSort("title")) }
    return effective == actual
}

private fun requireUnambiguousRules(root: JsonObject) {
    val pending = mutableListOf(root)
    while (pending.isNotEmpty()) {
        val group = pending.removeAt(pending.lastIndex)
        val key = if ("all" in group) "all" else "any"
        for (element in group.getValue(key).jsonArray) {
            val rule = element.jsonObject
            require(rule.size == 1)
            if ("all" in rule || "any" in rule) pending += rule
        }
    }
}
