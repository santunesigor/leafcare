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
 */
enum class PhotoSyncState {
    PENDING_UPLOAD,
    SYNCED,
    ERROR
}

class PhotoSyncStateConverter {
    @TypeConverter
    fun fromState(state: PhotoSyncState): String = state.name

    @TypeConverter
    fun toState(name: String): PhotoSyncState = PhotoSyncState.valueOf(name)
}
