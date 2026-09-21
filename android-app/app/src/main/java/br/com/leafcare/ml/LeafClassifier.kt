package br.com.leafcare.ml

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

data class ClassifierResult(val predictions: List<Prediction>, val inferenceMs: Double, val modelHash: String)

/** Um Interpreter por análise, fechado com use; repositório serializa chamadas. */
class LeafClassifier(private val context: Context) {
    private val meta: JSONObject get() = JSONObject(context.assets.open("model_metadata.json").bufferedReader().use { it.readText() })
    val defaultThreshold: Float get() = meta.optDouble("confidence_threshold", 0.70).toFloat()
    fun availabilityError(): String? = runCatching {
        check(meta.optString("status") == "trained" && context.assets.list("")!!.contains("leafcare.tflite")) {
            "Modelo ainda não treinado. É necessário preparar o dataset completo e exportar o modelo para este aplicativo."
        }
    }.exceptionOrNull()?.message

    fun classify(bitmap: Bitmap): ClassifierResult {
        availabilityError()?.let { error(it) }
        val metadata = meta
        val array = JSONArray(context.assets.open("classes.json").bufferedReader().use { it.readText() })
        val classes = List(array.length()) { array.getString(it) }
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(hash(classes.joinToString("\n").toByteArray(Charsets.UTF_8)) == metadata.getString("classes_sha256")) { "Ordem das classes inválida." }
        val listed = metadata.getJSONArray("classes")
        require(classes == List(listed.length()) { listed.getString(it) }) { "Contrato de classes incompatível." }
        require(metadata.getInt("schema_version") == 1 && metadata.getString("resize") == "center_crop_bilinear_integer_v1")
        require(metadata.getString("normalization") == "embedded_mobilenetv3_rescaling")
        val bytes = context.assets.open("leafcare.tflite").use { it.readBytes() }
        val modelHash = hash(bytes)
        require(modelHash == metadata.getString("model_sha256")) { "Modelo corrompido ou incompatível com os metadados." }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val rgb = PixelPreprocessor.toRgb(pixels, bitmap.width, bitmap.height)
        val input = ByteBuffer.allocateDirect(rgb.size * 4).order(ByteOrder.nativeOrder())
        input.asFloatBuffer().put(rgb)
        val output = arrayOf(FloatArray(classes.size))
        Interpreter(model, Interpreter.Options().setNumThreads(2)).use { interpreter ->
            require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1, 224, 224, 3)))
            require(interpreter.getInputTensor(0).dataType() == DataType.FLOAT32)
            require(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, classes.size)))
            require(interpreter.getOutputTensor(0).dataType() == DataType.FLOAT32)
            val start = System.nanoTime()
            interpreter.run(input, output)
            val elapsed = (System.nanoTime() - start) / 1_000_000.0
            return ClassifierResult(PredictionPolicy.top3(output[0], classes), elapsed, modelHash)
        }
    }
}
