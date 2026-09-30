package br.com.leafcare.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "analyses")
data class AnalysisEntity(
    @PrimaryKey val id: String,
    val photoName: String,
    val createdAt: Long,
    val classId: String,
    val displayName: String,
    val scientificName: String,
    val confidence: Float,
    val top3Json: String,
    val inconclusive: Boolean,
    val threshold: Float,
    val inferenceMs: Double,
    val modelSha256: String,
    // Sync fields (v2). Tombstoned rows (deletedAt != null) are hidden from UI
    // but kept until the remote deletion is confirmed.
    val syncStatus: SyncState = SyncState.PENDING_UPLOAD,
    val deletedAt: Long? = null,
    // Photo upload state (v3), tracked separately: an analysis row is never
    // marked SYNCED before its photo upload, and vice versa.
    val photoSyncStatus: PhotoSyncState = PhotoSyncState.PENDING_UPLOAD,
)

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM analyses WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AnalysisEntity>>
    @Query("SELECT * FROM analyses WHERE id = :id")
    fun observe(id: String): Flow<AnalysisEntity?>
    @Query("SELECT * FROM analyses WHERE id = :id")
    suspend fun get(id: String): AnalysisEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(analysis: AnalysisEntity)
    @Query("DELETE FROM analyses WHERE id = :id")
    suspend fun delete(id: String)

    // Sync queue: failed uploads retry through upsert; failed deletes retry
    // through the tombstone path (deletedAt preserved, never re-upserted).
    @Query("SELECT * FROM analyses WHERE syncStatus IN ('PENDING_UPLOAD', 'ERROR') AND deletedAt IS NULL")
    suspend fun getPendingUploads(): List<AnalysisEntity>

    @Query("SELECT * FROM analyses WHERE syncStatus = 'PENDING_DELETE' OR (syncStatus = 'ERROR' AND deletedAt IS NOT NULL)")
    suspend fun getPendingDeletes(): List<AnalysisEntity>

    @Query("UPDATE analyses SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE analyses SET syncStatus = 'ERROR' WHERE id = :id")
    suspend fun markError(id: String)

    @Query("UPDATE analyses SET syncStatus = 'PENDING_DELETE', deletedAt = :deletedAt WHERE id = :id")
    suspend fun markDeleted(id: String, deletedAt: Long)

    // Photo queue: only for analyses already confirmed remotely, never for
    // tombstones (their remote photo is removed through the delete path).
    @Query("SELECT * FROM analyses WHERE photoSyncStatus IN ('PENDING_UPLOAD', 'ERROR') AND deletedAt IS NULL AND syncStatus = 'SYNCED'")
    suspend fun getPendingPhotoUploads(): List<AnalysisEntity>

    @Query("UPDATE analyses SET photoSyncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markPhotoSynced(id: String)

    @Query("UPDATE analyses SET photoSyncStatus = 'ERROR' WHERE id = :id")
    suspend fun markPhotoError(id: String)

    // Photo download queue: remote-only rows whose analysis is visible.
    // Tombstones never download; missing states stay retryable downstream.
    @Query("SELECT * FROM analyses WHERE photoSyncStatus = 'REMOTE_ONLY' AND deletedAt IS NULL")
    suspend fun getPendingPhotoDownloads(): List<AnalysisEntity>

    @Query("SELECT COUNT(*) FROM analyses")
    suspend fun count(): Int

    @Query("DELETE FROM analyses")
    suspend fun deleteAll()
}

/** v1 -> v2: sync columns. Existing rows default to pending upload. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE analyses ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'PENDING_UPLOAD'")
        db.execSQL("ALTER TABLE analyses ADD COLUMN deletedAt INTEGER")
    }
}

/** v2 -> v3: photo upload state. Existing rows default to pending photo upload. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE analyses ADD COLUMN photoSyncStatus TEXT NOT NULL DEFAULT 'PENDING_UPLOAD'")
    }
}

@Database(entities = [AnalysisEntity::class], version = 3, exportSchema = true)
@TypeConverters(SyncStateConverter::class, PhotoSyncStateConverter::class)
abstract class AppDatabase : RoomDatabase() { abstract fun analysisDao(): AnalysisDao }
