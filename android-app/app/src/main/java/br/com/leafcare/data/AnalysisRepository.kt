package br.com.leafcare.data

import android.content.Context
import android.net.Uri
import br.com.leafcare.ml.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class AnalysisRepository(
    private val context: Context, private val dao: AnalysisDao,
    private val catalog: DiseaseCatalog, private val classifier: LeafClassifier,
) {
    private val photos = File(context.filesDir, "photos").apply { mkdirs() }
    private val mutex = Mutex()
    val all = dao.observeAll()
    fun observe(id: String) = dao.observe(id)
    fun photo(name: String): File {
        require(File(name).name == name)
        return File(photos, name)
    }
    fun modelError() = classifier.availabilityError
    fun threshold(): Float = classifier.defaultThreshold

    suspend fun analyze(uri: Uri): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            classifier.availabilityError?.let { throw IllegalStateException(it) }
            val id = UUID.randomUUID().toString()
            val file = photo("$id.img")
            var committed = false
            try {
                val source = if (uri.scheme == "file") File(requireNotNull(uri.path)).inputStream()
                    else requireNotNull(context.contentResolver.openInputStream(uri)) { "A foto não está mais disponível." }
                source.use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 30L * 1024 * 1024) { "Arquivo acima de 30 MB. Escolha uma cópia menor." }
                        output.write(buffer, 0, count)
                    }
                } }
                val bitmap = ImageDecoder.decode(file)
                val result = try { classifier.classify(bitmap) } finally { bitmap.recycle() }
                val top = result.predictions.first()
                val disease = catalog.get(top.classId)
                val threshold = threshold()
                val json = JSONArray().apply { result.predictions.forEach { p ->
                    put(JSONObject().put("class_id", p.classId).put("confidence", p.confidence.toDouble())
                        .put("name", catalog.get(p.classId).name))
                } }
                val entity = AnalysisEntity(id, file.name, System.currentTimeMillis(), top.classId,
                    disease.name, disease.scientificName, top.confidence, json.toString(),
                    PredictionPolicy.inconclusive(top.confidence, threshold), threshold, result.inferenceMs, result.modelHash)
                // A conclusão da gravação permanece consistente mesmo ao sair da tela.
                withContext(NonCancellable) { dao.insert(entity); committed = true }
                id
            } finally {
                if (!committed) file.delete()
            }
        }
    }

    suspend fun delete(id: String) = mutex.withLock {
        withContext(Dispatchers.IO + NonCancellable) {
            val row = dao.get(id) ?: return@withContext
            // Tombstone: hidden from UI immediately, remote deletion synced later.
            // The photo is removed only after the remote tombstone is confirmed.
            if (row.deletedAt == null) {
                dao.markDeleted(id, System.currentTimeMillis())
            }
        }
    }

    /** Removes every local row and photo file (account switch isolation). */
    suspend fun clearAllLocal() = mutex.withLock {
        withContext(Dispatchers.IO + NonCancellable) {
            dao.deleteAll()
            photos.listFiles()?.forEach { file ->
                if (file.isFile) file.delete()
            }
        }
    }

    suspend fun hasLocalRows(): Boolean = dao.count() > 0
}

/**
 * Decides whether local data must be wiped on authentication.
 *
 * Room rows carry no owner column, so rows of a previous account must never
 * be shown to (or synced as) a different account. Same-account re-login keeps
 * everything (offline-first preserved); logout alone never wipes.
 */
internal fun shouldWipeForAccountSwitch(
    storedUserId: String?,
    newUserId: String,
    hasLocalRows: Boolean,
): Boolean = hasLocalRows && (storedUserId == null || storedUserId != newUserId)
