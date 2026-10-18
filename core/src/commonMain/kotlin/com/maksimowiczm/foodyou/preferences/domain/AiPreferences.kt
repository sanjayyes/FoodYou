package com.maksimowiczm.foodyou.preferences.domain

/**
 * Gemini-backed food parsing settings.
 *
 * The model is deliberately not configurable: see
 * [com.maksimowiczm.foodyou.ai.domain.DEFAULT_GEMINI_MODEL]. `listModels` advertises ids Google no
 * longer serves, so persisting a user choice only produced permanently broken parsing.
 *
 * @property geminiApiKey decrypted API key, or `null` when unset
 */
data class AiPreferences(val geminiApiKey: String?) : UserPreferences {

    /** Whether food parsing is usable. */
    val isGeminiEnabled: Boolean
        get() = !geminiApiKey.isNullOrBlank()
}
