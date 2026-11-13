package br.com.leafcare.ui

import br.com.leafcare.data.AnalysisEntity
import org.junit.Assert.*
import org.junit.Test

/** Inconclusive photos stay local: they never get a training authorization. */
class TrainingEligibilityTest {

    private fun analysis(confidence: Float, inconclusive: Boolean) = AnalysisEntity(
        id = "a", photoName = "a.img", createdAt = 0L, classId = "c", displayName = "d",
        scientificName = "s", confidence = confidence, top3Json = "[]", inconclusive = inconclusive,
        threshold = 0.6f, inferenceMs = 1.0, modelSha256 = "h"
    )

    @Test fun conclusiveAnalysisIsEligible() {
        assertTrue(isTrainingEligible(analysis(0.9f, inconclusive = false)))
    }

    @Test fun inconclusiveOrMissingAnalysisIsNotEligible() {
        assertFalse(isTrainingEligible(analysis(0.4f, inconclusive = true)))
        assertFalse(isTrainingEligible(analysis(0.9f, inconclusive = true)))
        assertFalse(isTrainingEligible(analysis(0.4f, inconclusive = false)))
        assertFalse(isTrainingEligible(null))
    }
}
