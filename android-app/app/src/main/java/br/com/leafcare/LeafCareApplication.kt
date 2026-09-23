package br.com.leafcare

import android.app.Application
import androidx.room.Room
import br.com.leafcare.auth.SupabaseClientHolder
import br.com.leafcare.data.AnalysisRepository
import br.com.leafcare.data.AppDatabase
import br.com.leafcare.data.DiseaseCatalog
import br.com.leafcare.ml.LeafClassifier

class LeafCareApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "leafcare.db").build()
    }
    val catalog by lazy { DiseaseCatalog(this) }
    val repository by lazy { AnalysisRepository(this, database.analysisDao(), catalog, LeafClassifier(this)) }

    // Supabase client initialized once for the application lifecycle
    val supabaseClientHolder by lazy { SupabaseClientHolder(this) }
}
