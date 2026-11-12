package br.com.leafcare

import br.com.leafcare.ml.PredictionPolicy
import org.junit.Assert.*
import org.junit.Test

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
}
