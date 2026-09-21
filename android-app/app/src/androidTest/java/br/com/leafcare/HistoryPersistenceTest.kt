package br.com.leafcare

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.leafcare.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HistoryPersistenceTest {
    @Test fun insertReopenAndDelete() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "persistence-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        val example = AnalysisEntity("test", "test.img", 123L, "fixture", "Exemplo de teste", "Teste",
            .7f, "[]", false, .7f, 2.0, "test-only")
        var db = open()
        try {
            db.analysisDao().insert(example)
            db.close()
            db = open()
            assertEquals(example, db.analysisDao().get("test"))
            db.analysisDao().delete("test")
            assertNull(db.analysisDao().get("test"))
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
