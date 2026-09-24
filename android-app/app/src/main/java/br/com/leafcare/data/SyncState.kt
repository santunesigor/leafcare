package br.com.leafcare.data

import androidx.room.TypeConverter

/**
 * Local synchronization state of an analysis.
 *
 * - PENDING_UPLOAD: created locally, never confirmed remotely (covers LOCAL_ONLY).
 * - SYNCED: confirmed remotely via idempotent upsert on the local UUID.
 * - PENDING_DELETE: hidden from UI (tombstone); remote deletion still pending.
 * - ERROR: last attempt failed; retried later. Rows with a tombstone retry
 *   through the delete path (never re-upserted), so deletions can't resurrect.
 */
enum class SyncState {
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
 * Photo upload state, tracked separately from the analysis row state so an
 * analysis is never marked fully SYNCED before its photo is uploaded.
 * Survives app restarts via the Room column.
 *
 * REMOTE_ONLY marks rows imported by restore whose photo lives remotely and
 * was never downloaded (photo download is a later Phase 6 unit).
 */
enum class PhotoSyncState {
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
