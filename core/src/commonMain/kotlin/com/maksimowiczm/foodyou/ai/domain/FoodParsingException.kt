package com.maksimowiczm.foodyou.ai.domain

/**
 * Failure modes of [GeminiClient].
 *
 * Kept granular on purpose: the UI maps each case to its own message, because collapsing them hides
 * the one fact the user needs in order to act.
 */
sealed class FoodParsingException(message: String) : Exception(message) {

    /** No API key configured. */
    data object ApiKeyMissing : FoodParsingException("Gemini API key is not configured")

    /** The key was refused: HTTP 401/403, or a 400 carrying a key-related status. */
    data class ApiKeyRejected(val status: String?) :
        FoodParsingException("Gemini rejected the API key (status=$status)")

    /** The requested model is retired or not served to this project: HTTP 404. */
    data class ModelUnavailable(val model: String) :
        FoodParsingException("Gemini model '$model' is not available to this API key")

    /** HTTP 429 — free-tier quota or rate limit. */
    data class QuotaExceeded(val detail: String?) :
        FoodParsingException("Gemini quota exhausted (detail=$detail)")

    /** HTTP 5xx, including 503 for models under heavy load. */
    data class ServiceUnavailable(val status: Int, val detail: String?) :
        FoodParsingException("Gemini service unavailable (status=$status, detail=$detail)")

    /** No response at all: DNS failure, refused connection, or timeout. */
    data class NetworkUnavailable(val detail: String?) :
        FoodParsingException("Gemini unreachable (detail=$detail)")

    /** HTTP 400 that is not key-related — a malformed or unsupported request. */
    data class InvalidRequest(val status: String?, val detail: String?) :
        FoodParsingException("Gemini rejected the request (status=$status, detail=$detail)")

    /** A 2xx response whose payload could not be understood. */
    data class MalformedResponse(val detail: String?) :
        FoodParsingException("Gemini returned an unexpected payload ($detail)")
}

/**
 * Normalizes any throwable into a [FoodParsingException] so the UI never has to guess.
 *
 * Transport problems surface as [FoodParsingException.NetworkUnavailable]; anything already
 * classified by the client is passed through unchanged.
 */
fun Throwable.asFoodParsingException(): FoodParsingException =
    this as? FoodParsingException ?: FoodParsingException.NetworkUnavailable(detail = message)
