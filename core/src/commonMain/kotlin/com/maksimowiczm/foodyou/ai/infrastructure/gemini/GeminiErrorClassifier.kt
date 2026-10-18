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
 * Whether [detail] accuses the API key rather than the request.
 *
 * Google answers a malformed key with 400 INVALID_ARGUMENT — verified against the live API, where
 * the body is `status=INVALID_ARGUMENT` and the message is "API key not valid. Please pass a valid
 * API key." The status field is therefore useless here and the message is the only signal. Without
 * this the UI tells the user to try a different model, sending them after the wrong problem.
 *
 * Requires both a mention of the key and an accusation, so a 400 that merely names an `api_key`
 * field stays an InvalidRequest.
 */
private fun blamesApiKey(detail: String?): Boolean {
    if (detail == null) return false
    val normalized = detail.lowercase()
    return "api key" in normalized &&
        listOf("not valid", "invalid", "incorrect", "unauthorized", "not authorized").any {
            it in normalized
        }
}

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
        httpStatus == 400 && blamesApiKey(detail) ->
            FoodParsingException.ApiKeyRejected(errorStatus)
        httpStatus == 400 -> FoodParsingException.InvalidRequest(errorStatus, detail)
        else -> FoodParsingException.ServiceUnavailable(httpStatus, detail)
    }

/** Single-line description of a failure, for logs. */
internal fun FoodParsingException.diagnose(): String = "${this::class.simpleName}: $message"
