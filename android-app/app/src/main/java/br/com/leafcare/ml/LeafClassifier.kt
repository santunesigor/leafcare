package br.com.leafcare.ml

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.security.MessageDigest
import kotlin.collections.emptyList

data class ClassifierResult(
    val predictions: List<Prediction>,
    val inferenceMs: Double,
    val modelHash: String
)

/**
 * Inicializa e valida modelo, metadata, classes e Interpreter uma única vez.
 * Repository usa Mutex para serializar chamadas a classify().
 */
class LeafClassifier(private val context: Context) {

    private lateinit var metadata: JSONObject
    private lateinit var classes: List<String>
    private lateinit var modelHash: String
    private lateinit var interpreter: Interpreter
    private lateinit var inputBuffer: ByteBuffer
    private lateinit var outputArray: Array<FloatArray>

    var defaultThreshold: Float = 0.70f
    var availabilityError: String? = null

    init {
        var meta: JSONObject? = null
        var cls: List<String>? = null
        var mHash: String? = null
        var interp: Interpreter? = null
        var inBuf: ByteBuffer? = null
        var outArr: Array<FloatArray>? = null
        var defThresh: Float = 0.70f
        var availErr: String? = null

        runCatching {
            // 1. Carregar e validar metadata
            meta = JSONObject(context.assets.open("model_metadata.json").bufferedReader().use { it.readText() })
            check(meta.optString("status") == "trained" && context.assets.list("")!!.contains("leafcare.tflite")) {
                "Modelo ainda não treinado. É necessário preparar o dataset completo e exportar o modelo para este aplicativo."
            }

            // 2. Carregar e validar classes
            val array = JSONArray(context.assets.open("classes.json").bufferedReader().use { it.readText() })
            cls = List(array.length()) { array.getString(it) }
            require(cls.size >= 3 && cls.distinct().size == cls.size) { "Lista de classes inválida." }

            fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            require(hash(cls.joinToString("\n").toByteArray(Charset.forName("UTF-8"))) == meta.getString("classes_sha256")) { "Ordem das classes inválida." }
            val listed = meta.getJSONArray("classes")
            require(cls == List(listed.length()) { listed.getString(it) }) { "Contrato de classes incompatível." }

            // 3. Validar contrato de pré-processamento e schema
            require(meta.getInt("schema_version") == 1 && meta.getString("resize") == "center_crop_bilinear_integer_v1")
            require(meta.getString("normalization") == "embedded_mobilenetv3_rescaling")

            // 4. Carregar modelo e validar hash
            val bytes = context.assets.open("leafcare.tflite").use { it.readBytes() }
            mHash = hash(bytes)
            require(mHash == meta.getString("model_sha256")) { "Modelo corrompido ou incompatível com os metadados." }

            // 5. Criar Interpreter e validar shapes/dtypes
            val modelBuffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
            interp = Interpreter(modelBuffer, Interpreter.Options().setNumThreads(2))

            require(interp.getInputTensor(0).shape().contentEquals(intArrayOf(1, 224, 224, 3))) { "Input shape inválido." }
            require(interp.getInputTensor(0).dataType() == DataType.FLOAT32) { "Input dtype inválido." }
            require(interp.getOutputTensor(0).shape().contentEquals(intArrayOf(1, cls.size))) { "Output shape incompatível com classes." }
            require(interp.getOutputTensor(0).dataType() == DataType.FLOAT32) { "Output dtype inválido." }

            // 6. Pré-alocar buffers de entrada/saída
            val inputSize = 1 * 224 * 224 * 3
            inBuf = ByteBuffer.allocateDirect(inputSize * 4).order(ByteOrder.nativeOrder())
            outArr = arrayOf(FloatArray(cls.size))

            defThresh = meta.optDouble("confidence_threshold", 0.70).toFloat()
        }.onFailure { availErr = it.message }
            .onSuccess {
                metadata = meta!!
                classes = cls!!
                modelHash = mHash!!
                interpreter = interp!!
                inputBuffer = inBuf!!
                outputArray = outArr!!
                defaultThreshold = defThresh
                availabilityError = availErr
            }
    }

    fun classify(bitmap: Bitmap): ClassifierResult {
        availabilityError?.let { error(it) }

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val rgb = PixelPreprocessor.toRgb(pixels, bitmap.width, bitmap.height)

        inputBuffer.rewind()
        inputBuffer.asFloatBuffer().put(rgb)

        val start = System.nanoTime()
        interpreter.run(inputBuffer, outputArray)
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        return ClassifierResult(PredictionPolicy.top3(outputArray[0], classes), elapsed, modelHash)
    }

    /** Fecha o Interpreter (chamado no shutdown do processo, se necessário). */
    fun close() {
        interpreter.close()
    }
}