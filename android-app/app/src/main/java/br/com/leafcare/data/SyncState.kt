package br.com.leafcare.data

import androidx.room.TypeConverter

/**
 * Local synchronization state of an analysis.
 *
 * - PENDING_UPLOAD: created locally, never confirmed remotely.
 * - LOCAL_ONLY: excluded permanently by the server resume cutoff.
 * - SYNCED: confirmed remotely via idempotent upsert on the local UUID.
 * - PENDING_DELETE: hidden from UI (tombstone); remote deletion still pending.
 * - ERROR: last attempt failed; retried later. Rows with a tombstone retry
 *   through the delete path (never re-upserted), so deletions can't resurrect.
 */
enum class SyncState {
    LOCAL_ONLY,
    PENDING_UPLOAD,
    SYNCED,
    PENDING_DELETE,
    ERROR
}

class SyncStateConverter {
    @TypeConverter
    fun fromState(state: SyncState): String = state.name

    @TypeConverter
    fun toState(name: String): SyncState = SyncState.valueOf(name)
}

/**
 * Photo state, tracked separately from the analysis row state.
 * Survives app restarts via the Room column.
 *
 * REMOTE_ONLY marks rows imported by restore whose photo lives remotely and
 * has not been downloaded yet. LOCAL_ONLY has no confirmed remote photo.
 */
enum class PhotoSyncState {
    LOCAL_ONLY,
    PENDING_UPLOAD,
    SYNCED,
    ERROR,
    REMOTE_ONLY
}

class PhotoSyncStateConverter {
    @TypeConverter
    fun fromState(state: PhotoSyncState): String = state.name

    @TypeConverter
    fun toState(name: String): PhotoSyncState = PhotoSyncState.valueOf(name)
}
