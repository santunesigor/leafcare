package br.com.leafcare.data

import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Maps a local [AnalysisEntity] to a `public.analyses` row.
 * The local UUID is reused as the remote id (upsert onConflict = id).
 */
internal fun AnalysisEntity.toRemoteJson(userId: String, appVersion: String): JsonObject =
    buildJsonObject {
        put("id", id)
        put("user_id", userId)
        put("created_at", Instant.fromEpochMilliseconds(createdAt).toString())
        put("class_id", classId)
        put("display_name", displayName)
        put("scientific_name", scientificName)
        put("confidence", confidence)
        put("top3", Json.parseToJsonElement(top3Json))
        put("inconclusive", inconclusive)
        put("threshold", threshold)
        put("inference_ms", inferenceMs)
        put("model_sha256", modelSha256)
        put("app_version", appVersion)
        put("photo_path", photoName)
    }

/** Narrow seam over the remote calls the sync engine needs (testable without network). */
internal interface AnalysisSyncApi {
    /** Idempotent insert-or-update keyed by the analysis UUID. */
    suspend fun upsertAnalysis(row: JsonObject)

    /** Tombstone a remote row; no photo upload in this phase. */
    suspend fun markRemoteDeleted(id: String, deletedAtIso: String)
}

/** Production [AnalysisSyncApi] on the authenticated supabase-kt 2.1.0 client. */
internal class PostgrestAnalysisSyncApi(postgrest: Postgrest) : AnalysisSyncApi {

    private val table = postgrest["analyses"]

    override suspend fun upsertAnalysis(row: JsonObject) {
        table.upsert(row, onConflict = "id")
    }

    override suspend fun markRemoteDeleted(id: String, deletedAtIso: String) {
        table.update({
            set("deleted_at", deletedAtIso)
        }) {
            filter {
                eq("id", id)
            }
        }
    }
}
