package com.maksimowiczm.foodyou.preferences.domain

/**
 * Gemini-backed food parsing settings.
 *
 * @property geminiApiKey decrypted API key, or `null` when unset
 * @property geminiModel model id without the `models/` prefix, or `null` to use the default
 */
data class AiPreferences(val geminiApiKey: String?, val geminiModel: String?) : UserPreferences {

    /** Whether food parsing is usable. */
    val isGeminiEnabled: Boolean
        get() = !geminiApiKey.isNullOrBlank()
}
