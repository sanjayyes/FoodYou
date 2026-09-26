package com.maksimowiczm.foodyou.ai.domain

/** Failure modes of [GeminiClient], mapped by the UI layer onto localized messages. */
sealed class FoodParsingException(message: String) : Exception(message) {
    data object ApiKeyMissing : FoodParsingException("Gemini API key is not configured")

    data class ApiKeyRejected(val status: String?) :
        FoodParsingException("Gemini rejected the API key (status=$status)")

    data class RequestFailed(val status: Int, val detail: String?) :
        FoodParsingException("Gemini request failed (status=$status, detail=$detail)")

    data class MalformedResponse(val detail: String?) :
        FoodParsingException("Gemini returned an unexpected payload ($detail)")
}
