package com.maksimowiczm.foodyou.features.settings.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.ai.domain.DEFAULT_GEMINI_MODEL
import com.maksimowiczm.foodyou.ai.domain.FoodParsingException
import com.maksimowiczm.foodyou.ai.domain.FoodParsingService
import com.maksimowiczm.foodyou.preferences.domain.AiPreferences
import com.maksimowiczm.foodyou.preferences.domain.UserPreferencesRepository
import com.maksimowiczm.foodyou.preferences.domain.observe
import com.maksimowiczm.foodyou.preferences.domain.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal data class AiSettingsUiState(
    val models: List<String> = emptyList(),
    val isLoadingModels: Boolean = false,
    val error: FoodParsingException? = null,
)

internal class AiSettingsViewModel(
    private val foodParsingService: FoodParsingService,
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    /**
     * Seeded synchronously so the API key field has its value on first composition rather than
     * flashing empty.
     */
    val preferences: StateFlow<AiPreferences> =
        preferencesRepository
            .observe<AiPreferences>()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue =
                    runBlocking { preferencesRepository.observe<AiPreferences>().first() },
            )

    private val _uiState = MutableStateFlow(AiSettingsUiState())
    val uiState: StateFlow<AiSettingsUiState> = _uiState.asStateFlow()

    /** The model in effect, falling back to [DEFAULT_GEMINI_MODEL] when unset. */
    val selectedModel: StateFlow<String> =
        preferences
            .map { it.geminiModel ?: DEFAULT_GEMINI_MODEL }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = DEFAULT_GEMINI_MODEL,
            )

    /**
     * Persists [apiKey]; a blank value clears the stored key.
     *
     * Reloads the model list with the new key before returning, so callers must not trigger a
     * separate refresh — the persisted value is not visible synchronously.
     */
    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim().takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            preferencesRepository.update<AiPreferences> { it.copy(geminiApiKey = trimmed) }
            loadModels(trimmed)
        }
    }

    fun selectModel(model: String) {
        viewModelScope.launch {
            preferencesRepository.update<AiPreferences> { it.copy(geminiModel = model) }
        }
    }

    /** Reloads the model list for the currently stored key. */
    fun refreshModels() {
        viewModelScope.launch { loadModels(preferences.value.geminiApiKey) }
    }

    private suspend fun loadModels(apiKey: String?) {
        if (apiKey.isNullOrBlank()) {
            _uiState.update { it.copy(models = emptyList(), isLoadingModels = false, error = null) }
            return
        }

        _uiState.update { it.copy(isLoadingModels = true, error = null) }

        _uiState.update { state ->
            try {
                state.copy(
                    isLoadingModels = false,
                    models = foodParsingService.models(apiKey),
                    error = null,
                )
            } catch (e: FoodParsingException) {
                state.copy(isLoadingModels = false, error = e)
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
