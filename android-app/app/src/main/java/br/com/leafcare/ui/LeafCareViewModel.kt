package br.com.leafcare.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.leafcare.LeafCareApplication
import br.com.leafcare.data.AnalysisEntity
import br.com.leafcare.data.DiseaseInfo
import br.com.leafcare.data.TrainingConsentEntity
import br.com.leafcare.data.TrainingPhotoAuthorizationEntity
import br.com.leafcare.data.TRAINING_TERMS_VERSION
import br.com.leafcare.data.TRAINING_UPLOADS_ENABLED
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.io.File

data class UiState(val busy: Boolean = false, val error: String? = null)

class LeafCareViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LeafCareApplication
    private val repository = app.repository
    private val catalog = app.catalog

    val analyses = repository.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val consentDao = app.database.trainingConsentDao()
    private val _trainingConsent = MutableStateFlow<br.com.leafcare.data.TrainingConsentEntity?>(null)
    val trainingConsent = _trainingConsent.asStateFlow()
    private var localConsentDecision = false
    private var consentUserId: String? = null
    private var consentObserver: Job? = null
    fun currentConsentUserId(): String? = consentUserId
    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()
    private val navigation = Channel<String>(Channel.BUFFERED)
    val results = navigation.receiveAsFlow()

    fun bindTrainingConsent(userId: String?) {
        if (consentUserId == userId) return
        consentObserver?.cancel()
        consentUserId = userId
        localConsentDecision = false
        _trainingConsent.value = null
        if (userId != null) consentObserver = viewModelScope.launch {
            consentDao.observe(userId).collect { persisted ->
                if (!localConsentDecision && consentUserId == userId) _trainingConsent.value = persisted
            }
        }
    }

    fun error(message: String) { _ui.update { it.copy(error = message) } }
    fun clearError() { _ui.update { it.copy(error = null) } }

    fun setTrainingConsent(granted: Boolean) {
        if (granted && !TRAINING_UPLOADS_ENABLED) return
        val userId = consentUserId ?: return
        localConsentDecision = true
        val decision = TrainingConsentEntity(userId = userId, termsVersion = TRAINING_TERMS_VERSION, granted = granted, decidedAt = System.currentTimeMillis())
        _trainingConsent.value = decision
        viewModelScope.launch {
            consentDao.save(decision)
            localConsentDecision = false
            if (!granted) consentDao.revokeAllPhotos(userId)
            app.scheduleSync()
        }
    }

    fun analyze(uri: Uri, temporary: File? = null, authorizeTraining: Boolean = false) {
        if (_ui.value.busy) { temporary?.delete(); return }
        _ui.value = UiState(busy = true)
        viewModelScope.launch {
            try {
                val id = repository.analyze(uri)
                if (TRAINING_UPLOADS_ENABLED && authorizeTraining && trainingConsent.value?.userId == consentUserId && trainingConsent.value?.granted == true) {
                    val userId = requireNotNull(consentUserId)
                    consentDao.savePhoto(TrainingPhotoAuthorizationEntity(userId, id, TRAINING_TERMS_VERSION, true, System.currentTimeMillis()))
                }
                navigation.send(id)
                // Room first, UI immediately; sync follows in background.
                app.scheduleSync()
            }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { this@LeafCareViewModel.error(error.message ?: "Não foi possível analisar a imagem.") }
            finally { temporary?.delete(); _ui.update { it.copy(busy = false) } }
        }
    }

    fun delete(id: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.delete(id)
                app.scheduleSync()
                onDeleted()
            }
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
