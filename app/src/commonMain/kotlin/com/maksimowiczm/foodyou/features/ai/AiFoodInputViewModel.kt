package com.maksimowiczm.foodyou.features.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.maksimowiczm.foodyou.ai.domain.DEFAULT_GEMINI_MODEL
import com.maksimowiczm.foodyou.ai.domain.FoodParsingException
import com.maksimowiczm.foodyou.ai.domain.FoodParsingService
import com.maksimowiczm.foodyou.ai.domain.ParsedFoodEntry
import com.maksimowiczm.foodyou.ai.domain.asFoodParsingException
import com.maksimowiczm.foodyou.preferences.domain.AiPreferences
import com.maksimowiczm.foodyou.preferences.domain.UserPreferencesRepository
import com.maksimowiczm.foodyou.preferences.domain.observe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the AI food input dialog. */
sealed interface AiFoodInputUiState {
    data object Input : AiFoodInputUiState

    data object Loading : AiFoodInputUiState

    data class Success(val entries: List<ParsedFoodEntry>) : AiFoodInputUiState

    data class Error(val error: FoodParsingException) : AiFoodInputUiState
}

internal class AiFoodInputViewModel(
    private val foodParsingService: FoodParsingService,
    logger: Logger,
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val logger = logger.withTag(TAG)

    private val _uiState = MutableStateFlow<AiFoodInputUiState>(AiFoodInputUiState.Input)
    val uiState: StateFlow<AiFoodInputUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val preferences =
        preferencesRepository
            .observe<AiPreferences>()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = null,
            )

    fun onQueryChange(value: String) {
        _query.value = value
        if (_uiState.value is AiFoodInputUiState.Error) _uiState.value = AiFoodInputUiState.Input
    }

    fun parse() {
        val description = _query.value.trim()
        if (description.isEmpty() || _uiState.value is AiFoodInputUiState.Loading) return

        _uiState.value = AiFoodInputUiState.Loading

        viewModelScope.launch {
            val settings =
                preferences.value ?: preferencesRepository.observe<AiPreferences>().first()

            val apiKey = settings.geminiApiKey
            if (apiKey.isNullOrBlank()) {
                _uiState.value = AiFoodInputUiState.Error(FoodParsingException.ApiKeyMissing)
                return@launch
            }

            _uiState.value =
                runCatching {
                        foodParsingService.parse(
                            description = description,
                            apiKey = apiKey,
                            model = DEFAULT_GEMINI_MODEL,
                        )
                    }
                    .fold(
                        onSuccess = { AiFoodInputUiState.Success(it) },
                        onFailure = { throwable ->
                            // Never let a raw throwable escape silently: without this the UI
                            // shows a generic message and the real cause is unrecoverable.
                            logger.e(throwable) {
                                "parse failed: ${throwable::class.simpleName}: ${throwable.message}"
                            }
                            AiFoodInputUiState.Error(throwable.asFoodParsingException())
                        },
                    )
        }
    }

    fun consume() {
        _uiState.update { AiFoodInputUiState.Input }
    }

    private companion object {
        const val TAG = "AiFoodInput"
    }

    /** Clears any previous session so the dialog always opens on a blank prompt. */
    fun reset() {
        _query.value = ""
        _uiState.value = AiFoodInputUiState.Input
    }
}
