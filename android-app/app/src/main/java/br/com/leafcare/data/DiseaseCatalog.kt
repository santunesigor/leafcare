package br.com.leafcare.data

import android.content.Context
import org.json.JSONObject

data class DiseaseInfo(
    val name: String, val scientificName: String, val description: String,
    val symptoms: List<String>, val favorableConditions: String, val guidance: String,
    val reviewed: Boolean,
    val appearance: String = "", val affectedRegion: String = "", val symptomEvolution: String = "",
    val referenceImages: List<String> = emptyList(),
)

class DiseaseCatalog(context: Context) {
    private val references = context.assets.list("references")?.toList().orEmpty()
    private val data = JSONObject(context.assets.open("diseases.json").bufferedReader().use { it.readText() })
        .getJSONObject("classes")

    fun get(id: String): DiseaseInfo {
        val entry = data.optJSONObject(id)
        val symptoms = entry?.optJSONArray("symptoms")
        return DiseaseInfo(
            entry?.optString("name", id) ?: id,
            entry?.optString("scientific_name", "Não informado no catálogo local") ?: "Não informado no catálogo local",
            entry?.optString("description") ?: "Conteúdo explicativo ainda não disponível para esta classe.",
            if (symptoms != null) List(symptoms.length()) { symptoms.getString(it) } else listOf("Procure avaliação profissional."),
            entry?.optString("favorable_conditions") ?: "Dependem da causa e de avaliação técnica.",
            entry?.optString("guidance") ?: "Registre outras folhas e procure orientação profissional.",
            entry?.optBoolean("reviewed", false) ?: false,
            entry?.optString("appearance") ?: "",
            entry?.optString("affected_region") ?: "",
            entry?.optString("symptom_evolution") ?: "",
            references.filter { it.startsWith(id + "_") }.take(3).map { "file:///android_asset/references/$it" },
        )
    }
}
