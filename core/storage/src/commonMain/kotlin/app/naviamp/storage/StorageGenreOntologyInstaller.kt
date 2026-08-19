package app.naviamp.storage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Installs versioned, application-owned ontology reference data for both new and upgraded stores. */
internal fun installBundledGenreOntology(database: NaviampStorageDatabase) {
    if (database.naviampStorageQueries.selectGenreOntologyMetadata()
            .executeAsOneOrNull()
            ?.payload_sha256 == BUNDLED_GENRE_ONTOLOGY_SHA256
    ) {
        return
    }
    installGenreOntologySnapshot(
        database = database,
        snapshotVersion = BUNDLED_GENRE_ONTOLOGY_SNAPSHOT,
        payloadSha256 = BUNDLED_GENRE_ONTOLOGY_SHA256,
        payloadJson = BUNDLED_GENRE_ONTOLOGY_JSON,
    )
}

internal fun installGenreOntologySnapshot(
    database: NaviampStorageDatabase,
    snapshotVersion: String,
    payloadSha256: String,
    payloadJson: String,
) {
    val queries = database.naviampStorageQueries
    if (queries.selectGenreOntologyMetadata().executeAsOneOrNull()?.payload_sha256 == payloadSha256) {
        return
    }

    val payload = Json.parseToJsonElement(payloadJson).jsonObject
    val genres = payload.getValue("genres").jsonArray
    val aliases = payload["aliases"]?.jsonArray.orEmpty()
    val relations = payload.getValue("relations").jsonArray
    queries.transaction {
        queries.clearGenreOntologyRelations()
        queries.clearGenreOntologyAliases()
        queries.clearGenreOntologyGenres()
        genres.forEach { element ->
            val genre = element.jsonObject
            queries.upsertGenreOntologyGenre(
                genre_id = genre.getValue("id").jsonPrimitive.content,
                canonical_name = genre.getValue("name").jsonPrimitive.content,
                disambiguation = genre["disambiguation"]?.jsonPrimitive?.content.orEmpty(),
            )
        }
        aliases.forEach { element ->
            val alias = element.jsonObject
            queries.upsertGenreOntologyAlias(
                genre_id = alias.getValue("genreId").jsonPrimitive.content,
                alias_name = alias.getValue("name").jsonPrimitive.content,
                alias_source = alias.getValue("source").jsonPrimitive.content,
            )
        }
        relations.forEach { element ->
            val relation = element.jsonObject
            queries.upsertGenreOntologyRelation(
                relation_type = relation.getValue("type").jsonPrimitive.content,
                source_genre_id = relation.getValue("source").jsonPrimitive.content,
                target_genre_id = relation.getValue("target").jsonPrimitive.content,
            )
        }
        queries.upsertGenreOntologyMetadata(
            source_name = payload.getValue("source").jsonPrimitive.content,
            source_url = payload.getValue("sourceUrl").jsonPrimitive.content,
            snapshot_version = snapshotVersion,
            payload_sha256 = payloadSha256,
        )
    }
}
