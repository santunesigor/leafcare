package br.com.leafcare.data

import androidx.room.*
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
)

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM analyses ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AnalysisEntity>>
    @Query("SELECT * FROM analyses WHERE id = :id")
    fun observe(id: String): Flow<AnalysisEntity?>
    @Query("SELECT * FROM analyses WHERE id = :id")
    suspend fun get(id: String): AnalysisEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(analysis: AnalysisEntity)
    @Query("DELETE FROM analyses WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [AnalysisEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() { abstract fun analysisDao(): AnalysisDao }
