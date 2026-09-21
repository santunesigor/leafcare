package br.com.leafcare

import br.com.leafcare.ml.PredictionPolicy
import br.com.leafcare.ml.PixelPreprocessor
import org.junit.Assert.*
import org.junit.Test
import java.util.Properties
import java.security.MessageDigest

class PredictionPolicyTest {
    @Test fun top3PreservesScoresAndStableTies() {
        val results = PredictionPolicy.top3(floatArrayOf(.1f, .4f, .4f, .1f), listOf("a", "b", "c", "d"))
        assertEquals(listOf("b", "c", "a"), results.map { it.classId })
        assertEquals(.4f, results.first().confidence, 0f)
    }
    @Test fun thresholdBoundary() {
        assertFalse(PredictionPolicy.inconclusive(.7f, .7f))
        assertTrue(PredictionPolicy.inconclusive(.699f, .7f))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidScoresFail() {
        PredictionPolicy.top3(floatArrayOf(Float.NaN, .5f, .5f), listOf("a", "b", "c"))
    }
    @Test fun pixelContractMatchesPythonGoldenFile() {
        val p = Properties().apply { PredictionPolicyTest::class.java.getResourceAsStream("/preprocess_golden.properties")!!.use { load(it) } }
        val width = p.getProperty("width").toInt()
        val height = p.getProperty("height").toInt()
        val pixels = p.getProperty("argb_hex").split(",").map { it.toLong(16).toInt() }.toIntArray()
        val values = PixelPreprocessor.toRgb(pixels, width, height)
        val digest = MessageDigest.getInstance("SHA-256").digest(ByteArray(values.size) { values[it].toInt().toByte() })
            .joinToString("") { "%02x".format(it) }
        assertEquals(p.getProperty("resized_rgb_sha256"), digest)
    }
}
