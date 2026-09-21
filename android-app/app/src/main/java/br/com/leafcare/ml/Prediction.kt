package br.com.leafcare.ml

data class Prediction(val classId: String, val confidence: Float)

object PredictionPolicy {
    fun top3(scores: FloatArray, classes: List<String>): List<Prediction> {
        require(classes.size >= 3 && scores.size == classes.size && classes.distinct().size == classes.size)
        require(scores.all { it.isFinite() && it in 0f..1.0001f } && kotlin.math.abs(scores.sum() - 1f) <= 0.01f) {
            "O modelo retornou probabilidades inválidas."
        }
        return scores.indices.sortedWith(compareByDescending<Int> { scores[it] }.thenBy { it })
            .take(3).map { Prediction(classes[it], scores[it]) }
    }
    fun inconclusive(confidence: Float, threshold: Float): Boolean {
        require(threshold in 0f..1f)
        return confidence < threshold
    }
}
