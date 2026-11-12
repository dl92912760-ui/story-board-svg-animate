package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.HistoriaAiService
import com.example.data.model.HistoryProduction
import com.example.data.repository.HistoriaRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ArchiveUiState {
    data object Loading : ArchiveUiState
    data class Success(val items: List<HistoryProduction>) : ArchiveUiState
    data class Error(val message: String) : ArchiveUiState
}

sealed interface GenerationUiState {
    data object Idle : GenerationUiState
    data class Generating(val statusMessage: String) : GenerationUiState
    data class Error(val message: String) : GenerationUiState
}

data class StudioFormState(
    val prompt: String = "",
    val generationType: String = "video", // "video" | "svg"
    val backgroundTheme: String = "dark", // "light" | "dark"
    val visualStyle: String = "Engraved Vector",
    val aspectRatio: String = "16:9",
    val sceneCount: Int = 4
)

class HistoriaViewModel(
    private val repository: HistoriaRepository,
    private val aiService: HistoriaAiService,
    private val currentUserId: String
) : ViewModel() {

    val archiveState: StateFlow<ArchiveUiState> = repository.observeHistories(currentUserId)
        .map<List<HistoryProduction>, ArchiveUiState> { ArchiveUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing histories", rootError)
            emit(ArchiveUiState.Error(rootError.message ?: "Failed to load historical archive."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ArchiveUiState.Loading
        )

    private val _generationState = MutableStateFlow<GenerationUiState>(GenerationUiState.Idle)
    val generationState: StateFlow<GenerationUiState> = _generationState.asStateFlow()

    private val _rethemeInProgress = MutableStateFlow(false)
    val rethemeInProgress: StateFlow<Boolean> = _rethemeInProgress.asStateFlow()

    private val _studioForm = MutableStateFlow(StudioFormState())
    val studioForm: StateFlow<StudioFormState> = _studioForm.asStateFlow()

    private val _lastCreatedId = MutableStateFlow<String?>(null)
    val lastCreatedId: StateFlow<String?> = _lastCreatedId.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun updatePrompt(value: String) {
        _studioForm.update { it.copy(prompt = value) }
    }

    fun updateGenerationType(type: String) {
        _studioForm.update { it.copy(generationType = type) }
    }

    fun updateBackgroundTheme(theme: String) {
        _studioForm.update { it.copy(backgroundTheme = theme) }
    }

    fun updateVisualStyle(style: String) {
        _studioForm.update { it.copy(visualStyle = style) }
    }

    fun updateAspectRatio(ratio: String) {
        _studioForm.update { it.copy(aspectRatio = ratio) }
    }

    fun updateSceneCount(count: Int) {
        _studioForm.update { it.copy(sceneCount = count.coerceIn(3, 6)) }
    }

    fun clearLastCreatedId() {
        _lastCreatedId.value = null
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun clearGenerationError() {
        if (_generationState.value is GenerationUiState.Error) {
            _generationState.value = GenerationUiState.Idle
        }
    }

    fun generateProduction() {
        val form = _studioForm.value
        val trimmedPrompt = form.prompt.trim()
        if (trimmedPrompt.isBlank()) {
            _generationState.value = GenerationUiState.Error("Enter a historical topic or scene prompt first.")
            return
        }

        viewModelScope.launch {
            val status = if (form.generationType == "video") {
                "Directing ${form.sceneCount} animated SVG scenes with Gemini…"
            } else {
                "Illustrating animated SVG artwork with Gemini…"
            }
            _generationState.value = GenerationUiState.Generating(status)
            try {
                val generated = if (form.generationType == "video") {
                    aiService.generateHistoriaVideo(
                        prompt = trimmedPrompt,
                        visualStyle = form.visualStyle,
                        backgroundTheme = form.backgroundTheme,
                        aspectRatio = form.aspectRatio,
                        sceneCount = form.sceneCount
                    )
                } else {
                    aiService.generateHistoriaSvg(
                        prompt = trimmedPrompt,
                        visualStyle = form.visualStyle,
                        backgroundTheme = form.backgroundTheme,
                        aspectRatio = form.aspectRatio
                    )
                }

                val saveResult = repository.createHistory(currentUserId, generated)
                if (saveResult.isSuccess) {
                    val createdId = saveResult.getOrThrow()
                    _generationState.value = GenerationUiState.Idle
                    _lastCreatedId.value = createdId
                } else {
                    val err = saveResult.exceptionOrNull()
                    _generationState.value = GenerationUiState.Error(
                        err?.localizedMessage ?: "Generated content could not be saved to Firestore."
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini generation timed out", e)
                _generationState.value = GenerationUiState.Error(
                    "Generation timed out after 60 seconds. Try a shorter prompt or fewer scenes."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini generation failed", e)
                _generationState.value = GenerationUiState.Error(
                    e.localizedMessage ?: "Failed to generate with Gemini."
                )
            }
        }
    }

    fun rethemeProduction(production: HistoryProduction, targetTheme: String) {
        if (production.id.isBlank() || _rethemeInProgress.value) return
        viewModelScope.launch {
            _rethemeInProgress.value = true
            try {
                val updatedScenes = aiService.rethemeProduction(production, targetTheme)
                val totalDuration = updatedScenes.sumOf { it.durationSeconds }.coerceAtLeast(1L)
                val result = repository.updateScenesTheme(
                    userId = currentUserId,
                    historyId = production.id,
                    backgroundTheme = targetTheme,
                    scenes = updatedScenes,
                    totalDurationSeconds = totalDuration
                )
                if (result.isSuccess) {
                    _actionMessage.value = "Adapted SVG palette to ${targetTheme.replaceFirstChar { it.uppercase() }} mode."
                } else {
                    _actionMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to update scenes."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Retheme timed out", e)
                _actionMessage.value = "Re-theming timed out. Please try again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Retheme failed", e)
                _actionMessage.value = e.localizedMessage ?: "Failed to adapt SVG theme."
            } finally {
                _rethemeInProgress.value = false
            }
        }
    }

    fun toggleFavorite(production: HistoryProduction) {
        if (production.id.isBlank()) return
        viewModelScope.launch {
            val res = repository.toggleFavorite(
                userId = currentUserId,
                historyId = production.id,
                favorite = !production.favorite
            )
            if (res.isFailure) {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update favorite."
            }
        }
    }

    fun deleteHistory(historyId: String) {
        if (historyId.isBlank()) return
        viewModelScope.launch {
            val res = repository.deleteHistory(currentUserId, historyId)
            if (res.isSuccess) {
                _actionMessage.value = "Removed from your Historia archive."
            } else {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to delete item."
            }
        }
    }

    private companion object {
        const val TAG = "HistoriaVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
