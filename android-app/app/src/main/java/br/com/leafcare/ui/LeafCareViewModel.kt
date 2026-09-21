package br.com.leafcare.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.leafcare.LeafCareApplication
import br.com.leafcare.data.AnalysisEntity
import br.com.leafcare.data.DiseaseInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class UiState(val busy: Boolean = false, val error: String? = null)

class LeafCareViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LeafCareApplication
    private val repository = app.repository
    private val catalog = app.catalog

    val analyses = repository.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()
    private val navigation = Channel<String>(Channel.BUFFERED)
    val results = navigation.receiveAsFlow()
    val threshold = MutableStateFlow(repository.threshold())

    fun setThreshold(value: Float) { repository.setThreshold(value); threshold.value = value }
    fun error(message: String) { _ui.update { it.copy(error = message) } }
    fun clearError() { _ui.update { it.copy(error = null) } }

    fun analyze(uri: Uri, temporary: File? = null) {
        if (_ui.value.busy) { temporary?.delete(); return }
        _ui.value = UiState(busy = true)
        viewModelScope.launch {
            try { navigation.send(repository.analyze(uri)) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { this@LeafCareViewModel.error(error.message ?: "Não foi possível analisar a imagem.") }
            finally { temporary?.delete(); _ui.update { it.copy(busy = false) } }
        }
    }

    fun delete(id: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            try { repository.delete(id); onDeleted() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { this@LeafCareViewModel.error(error.message ?: "Falha ao excluir a análise.") }
        }
    }

    // Encapsulated repository/catalog access
    fun getPhoto(name: String): File = repository.photo(name)
    fun getModelError(): String? = repository.modelError()
    fun observeAnalysis(id: String) = repository.observe(id)
    fun getDiseaseInfo(classId: String): DiseaseInfo = catalog.get(classId)
}
