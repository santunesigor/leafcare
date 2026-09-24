package br.com.leafcare.data

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

/**
 * Remote mapping tests: the local UUID and every synced field must land on
 * the `public.analyses` row. Pure functions, no Android framework.
 */
class SyncMappingTest {

    private fun entity() = AnalysisEntity(
        id = "550e8400-e29b-41d4-a716-446655440000",
        photoName = "550e8400-e29b-41d4-a716-446655440000.img",
        createdAt = 1_728_000_000_000,
        classId = "frog_eye",
        displayName = "Olho-de-rã",
        scientificName = "Cercospora nicotianae",
        confidence = 0.82f,
        top3Json = """[{"class_id":"frog_eye","confidence":0.82}]""",
        inconclusive = false,
        threshold = 0.7f,
        inferenceMs = 30.0,
        modelSha256 = "abc123",
        syncStatus = SyncState.PENDING_UPLOAD,
        deletedAt = null
    )

    @Test fun sameUuidAndOwnership() {
        val row = entity().toRemoteJson("user-1", "0.3.0")

        assertEquals("550e8400-e29b-41d4-a716-446655440000", row.getValue("id").jsonPrimitive.content)
        assertEquals("user-1", row.getValue("user_id").jsonPrimitive.content)
        assertEquals("0.3.0", row.getValue("app_version").jsonPrimitive.content)
        assertEquals(
            "550e8400-e29b-41d4-a716-446655440000.img",
            row.getValue("photo_path").jsonPrimitive.content
        )
    }

    @Test fun timestampsAndPayloadRoundTrip() {        val row = entity().toRemoteJson("user-1", "0.3.0")

        assertEquals(
            1_728_000_000_000,
            Instant.parse(row.getValue("created_at").jsonPrimitive.content).toEpochMilliseconds()
        )
        assertEquals("frog_eye", row.getValue("class_id").jsonPrimitive.content)
        assertEquals("Olho-de-rã", row.getValue("display_name").jsonPrimitive.content)
        assertEquals("Cercospora nicotianae", row.getValue("scientific_name").jsonPrimitive.content)
        assertEquals(0.82f, row.getValue("confidence").jsonPrimitive.float)
        assertEquals(false, row.getValue("inconclusive").jsonPrimitive.boolean)
        assertEquals(0.7f, row.getValue("threshold").jsonPrimitive.float)
        assertEquals(30.0, row.getValue("inference_ms").jsonPrimitive.double, 0.0)
        assertEquals("abc123", row.getValue("model_sha256").jsonPrimitive.content)

        val top3 = row.getValue("top3")
        assertTrue(top3 is JsonArray)
        assertEquals("frog_eye", (top3 as JsonArray)[0].jsonObject.getValue("class_id").jsonPrimitive.content)
    }

    @Test fun remoteRowMapsToLocalEntity() {
        val row = buildJsonObject {
            put("id", "r-1")
            put("user_id", "user-1")
            put("created_at", "2024-09-23T12:00:00Z")
            put("class_id", "frog_eye")
            put("display_name", "Olho-de-rã")
            put("scientific_name", "Cercospora nicotianae")
            put("confidence", 0.82)
            put("top3", buildJsonArray {
                addJsonObject {
                    put("class_id", "frog_eye")
                    put("confidence", 0.82)
                }
            })
            put("inconclusive", false)
            put("threshold", 0.7)
            put("inference_ms", 30.0)
            put("model_sha256", "abc123")
            put("app_version", "0.3.0")
            put("photo_path", "user-1/r-1.jpg")
            put("deleted_at", JsonNull)
        }

        val entity = row.toAnalysisEntity()

        assertEquals("r-1", entity.id)
        assertEquals("r-1.img", entity.photoName)
        assertEquals(
            Instant.parse("2024-09-23T12:00:00Z").toEpochMilliseconds(),
            entity.createdAt
        )
        assertEquals("frog_eye", entity.classId)
        assertEquals(0.82f, entity.confidence)
        assertNull(entity.deletedAt)
        assertEquals(SyncState.SYNCED, entity.syncStatus)
        assertEquals(PhotoSyncState.REMOTE_ONLY, entity.photoSyncStatus)
        // top3 stays a valid JSON array string, as produced locally.
        assertTrue(Json.parseToJsonElement(entity.top3Json) is JsonArray)
    }

    @Test fun remoteTombstoneParsesDeletedAt() {
        val row = buildJsonObject {
            put("id", "r-1")
            put("user_id", "user-1")
            put("created_at", "2024-09-23T12:00:00Z")
            put("class_id", "frog_eye")
            put("display_name", "Olho-de-rã")
            put("scientific_name", "Cercospora nicotianae")
            put("confidence", 0.82)
            put("top3", buildJsonArray { })
            put("inconclusive", false)
            put("threshold", 0.7)
            put("inference_ms", 30.0)
            put("model_sha256", "abc123")
            put("app_version", "0.3.0")
            put("photo_path", JsonNull)
            put("deleted_at", "2024-09-24T12:00:00Z")
        }

        val entity = row.toAnalysisEntity()

        assertEquals(
            Instant.parse("2024-09-24T12:00:00Z").toEpochMilliseconds(),
            entity.deletedAt
        )
    }
}
