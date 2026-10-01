package com.maksimowiczm.foodyou.ai.infrastructure.gemini

import com.maksimowiczm.foodyou.ai.domain.FoodParsingException

/**
 * HTTP statuses that mean "your key is the problem", as opposed to "your request is the problem".
 *
 * Google reports these in the error body's `status` field, which is the only reliable way to tell a
 * malformed 400 from a rejected key.
 */
private val API_KEY_ERROR_STATUSES =
    setOf("API_KEY_INVALID", "PERMISSION_DENIED", "UNAUTHENTICATED")

/**
 * Maps a failed Gemini response onto the specific reason it failed.
 *
 * Internal and pure so the mapping can be asserted without an HTTP client.
 */
internal fun classifyGeminiFailure(
    httpStatus: Int,
    errorStatus: String?,
    detail: String?,
    model: String,
): FoodParsingException =
    when {
        httpStatus == 404 -> FoodParsingException.ModelUnavailable(model)
        httpStatus == 429 -> FoodParsingException.QuotaExceeded(detail)
        httpStatus == 401 || httpStatus == 403 -> FoodParsingException.ApiKeyRejected(errorStatus)
        httpStatus == 400 && errorStatus in API_KEY_ERROR_STATUSES ->
            FoodParsingException.ApiKeyRejected(errorStatus)
        httpStatus == 400 -> FoodParsingException.InvalidRequest(errorStatus, detail)
        else -> FoodParsingException.ServiceUnavailable(httpStatus, detail)
    }

/** Single-line description of a failure, for logs. */
internal fun FoodParsingException.diagnose(): String = "${this::class.simpleName}: $message"
