package br.com.leafcare

import android.app.Application
import androidx.room.Room
import androidx.work.Configuration
import br.com.leafcare.auth.SupabaseClientHolder
import br.com.leafcare.data.AnalysisRepository
import br.com.leafcare.data.AppDatabase
import br.com.leafcare.data.DiseaseCatalog
import br.com.leafcare.data.MIGRATION_1_2
import br.com.leafcare.data.requestAnalysisSync
import br.com.leafcare.ml.LeafClassifier

class LeafCareApplication : Application(), Configuration.Provider {
    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "leafcare.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }
    val catalog by lazy { DiseaseCatalog(this) }
    val repository by lazy { AnalysisRepository(this, database.analysisDao(), catalog, LeafClassifier(this)) }

    // Supabase client initialized once for the application lifecycle
    val supabaseClientHolder by lazy { SupabaseClientHolder(this) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        // Flush analyses pending from before a restart.
        scheduleSync()
    }

    /** Schedules a sync pass for locally changed analyses. */
    fun scheduleSync() {
        requestAnalysisSync(this)
    }
}
