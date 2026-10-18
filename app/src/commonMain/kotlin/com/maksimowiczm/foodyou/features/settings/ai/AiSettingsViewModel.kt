package com.maksimowiczm.foodyou.features.settings.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.ai.domain.FoodParsingException
import com.maksimowiczm.foodyou.ai.domain.FoodParsingService
import com.maksimowiczm.foodyou.ai.domain.asFoodParsingException
import com.maksimowiczm.foodyou.preferences.domain.AiPreferences
import com.maksimowiczm.foodyou.preferences.domain.UserPreferencesRepository
import com.maksimowiczm.foodyou.preferences.domain.observe
import com.maksimowiczm.foodyou.preferences.domain.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Backing state for [AiSettingsScreen].
 *
 * Holds only the API key. The model used to be selectable here, which was removed: Google's
 * `listModels` advertises dated snapshots and preview models that do not serve `generateContent`,
 * so any stored choice locked the feature into a permanently failing state.
 */
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

    private val _connectionState = MutableStateFlow<AiConnectionState>(AiConnectionState.Idle)
    val connectionState: StateFlow<AiConnectionState> = _connectionState.asStateFlow()

    /** Persists [apiKey]; a blank value clears the stored key. */
    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim().takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            preferencesRepository.update<AiPreferences> { it.copy(geminiApiKey = trimmed) }
            // A previous result described the old key, so it no longer applies.
            _connectionState.value = AiConnectionState.Idle
        }
    }

    /**
     * Probes the stored key against the live API so the user can confirm it works before relying on
     * it. The model list is used only as a credential and reachability check — its contents are
     * ignored, because they are not a list of usable choices.
     */
    fun testConnection() {
        val key = preferences.value.geminiApiKey?.trim()
        if (key.isNullOrEmpty()) {
            _connectionState.value = AiConnectionState.Failed(FoodParsingException.ApiKeyMissing)
            return
        }

        _connectionState.value = AiConnectionState.Checking

        viewModelScope.launch {
            _connectionState.value =
                runCatching { foodParsingService.models(key) }
                    .fold(
                        onSuccess = { AiConnectionState.Connected },
                        onFailure = {
                            // Never let a raw throwable reach the UI, otherwise the user sees a
                            // generic message and the real cause is unrecoverable.
                            AiConnectionState.Failed(it.asFoodParsingException())
                        },
                    )
        }
    }
}

/** Outcome of the on-demand connection test. */
internal sealed interface AiConnectionState {
    /** No test has been requested since the key last changed. */
    data object Idle : AiConnectionState

    /** A probe is in flight. */
    data object Checking : AiConnectionState

    /** The stored key reached Gemini. */
    data object Connected : AiConnectionState

    /** The probe failed; [error] says why. */
    data class Failed(val error: FoodParsingException) : AiConnectionState
}
