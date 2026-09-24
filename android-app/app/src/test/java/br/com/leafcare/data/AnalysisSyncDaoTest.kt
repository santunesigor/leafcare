package br.com.leafcare.data

import android.app.Application
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** v1 shape of the analyses table (pre-sync columns), managed by Room. */
@Entity(tableName = "analyses")
internal data class LegacyAnalysisEntity(
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
internal interface LegacyAnalysisDao {
    @Insert
    suspend fun insert(analysis: LegacyAnalysisEntity)
}

@Database(entities = [LegacyAnalysisEntity::class], version = 1, exportSchema = false)
internal abstract class LegacyAppDatabase : RoomDatabase() {
    abstract fun analysisDao(): LegacyAnalysisDao
}

/** v2 shape (pre-photo-sync columns), managed by Room. */
@Entity(tableName = "analyses")
internal data class LegacyV2AnalysisEntity(
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
    val syncStatus: SyncState = SyncState.SYNCED,
    val deletedAt: Long? = null,
)

@Dao
internal interface LegacyV2AnalysisDao {
    @Insert
    suspend fun insert(analysis: LegacyV2AnalysisEntity)
}

@Database(entities = [LegacyV2AnalysisEntity::class], version = 2, exportSchema = false)
internal abstract class LegacyV2AppDatabase : RoomDatabase() {
    abstract fun analysisDao(): LegacyV2AnalysisDao
}

/**
 * Room persistence tests (Robolectric): pending rows survive an app restart,
 * and the explicit v1 -> v2 migration preserves existing analyses with a
 * pending-upload default. No destructive migration anywhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnalysisSyncDaoTest {

    private lateinit var context: Application
    private val files = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        files.forEach { it.delete() }
    }

    private fun dbFile(name: String): File {
        val file = File(context.cacheDir, "$name.db")
        file.delete()
        files += file
        return file
    }

    private fun entity() = AnalysisEntity(
        id = "a-1",
        photoName = "a-1.img",
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

    @Test fun pendingRowSurvivesReopen() = runBlocking {
        val file = dbFile("sync-restart")
        Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .allowMainThreadQueries()
            .build()
            .also { it.analysisDao().insert(entity()) }
            .close()

        // Simulate an app restart: new database instance on the same file.
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .allowMainThreadQueries()
            .addMigrations(MIGRATION_1_2)
            .build()
        try {
            val row = reopened.analysisDao().get("a-1")
            assertNotNull(row)
            assertEquals(SyncState.PENDING_UPLOAD, row?.syncStatus)
            assertEquals("frog_eye", row?.classId)
        } finally {
            reopened.close()
        }
    }

    @Test fun migration1To2PreservesExistingAnalyses() = runBlocking {
        val file = dbFile("sync-migrate")

        // Existing v1 database with one analysis, created by Room itself.
        Room.databaseBuilder(context, LegacyAppDatabase::class.java, file.absolutePath)
            .allowMainThreadQueries()
            .build()
            .also { it.analysisDao().insert(legacyEntity()) }
            .close()

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .allowMainThreadQueries()
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        try {
            val row = migrated.analysisDao().get("a-1")
            assertNotNull(row)
            assertEquals("frog_eye", row?.classId)
            assertEquals("Olho-de-rã", row?.displayName)
            assertEquals(SyncState.PENDING_UPLOAD, row?.syncStatus)
            assertNull(row?.deletedAt)
        } finally {
            migrated.close()
        }
    }

    @Test fun migration2To3PreservesExistingAnalyses() = runBlocking {
        val file = dbFile("sync-migrate-photo")

        Room.databaseBuilder(context, LegacyV2AppDatabase::class.java, file.absolutePath)
            .allowMainThreadQueries()
            .build()
            .also {
                it.analysisDao().insert(
                    LegacyV2AnalysisEntity(
                        id = "a-1",
                        photoName = "a-1.img",
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
                        syncStatus = SyncState.SYNCED,
                        deletedAt = null
                    )
                )
            }
            .close()

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .allowMainThreadQueries()
            .addMigrations(MIGRATION_2_3)
            .build()
        try {
            val row = migrated.analysisDao().get("a-1")
            assertNotNull(row)
            assertEquals("frog_eye", row?.classId)
            assertEquals(SyncState.SYNCED, row?.syncStatus)
            assertEquals(PhotoSyncState.PENDING_UPLOAD, row?.photoSyncStatus)
        } finally {
            migrated.close()
        }
    }

    private fun legacyEntity() = LegacyAnalysisEntity(
        id = "a-1",
        photoName = "a-1.img",
        createdAt = 1_728_000_000_000,
        classId = "frog_eye",
        displayName = "Olho-de-rã",
        scientificName = "Cercospora nicotianae",
        confidence = 0.82f,
        top3Json = """[{"class_id":"frog_eye","confidence":0.82}]""",
        inconclusive = false,
        threshold = 0.7f,
        inferenceMs = 30.0,
        modelSha256 = "abc123"
    )
}
