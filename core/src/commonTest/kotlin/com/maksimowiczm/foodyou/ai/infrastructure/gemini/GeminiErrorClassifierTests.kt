package com.maksimowiczm.foodyou.ai.infrastructure.gemini

import com.maksimowiczm.foodyou.ai.domain.FoodParsingException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GeminiErrorClassifierTests {

    @Test
    fun not_found_should_mean_the_model_is_unavailable() {
        // A retired model answers 404 with an explanatory body; that must not read as a network
        // problem, which is what previously hid the real cause.
        val failure = classify(404, "NOT_FOUND", "no longer available to new users")

        assertIs<FoodParsingException.ModelUnavailable>(failure)
        assertEquals("gemini-2.5-flash", failure.model)
    }

    @Test
    fun too_many_requests_should_mean_quota_exhausted() {
        assertIs<FoodParsingException.QuotaExceeded>(classify(429, "RESOURCE_EXHAUSTED", "quota"))
    }

    @Test
    fun unauthorized_and_forbidden_should_mean_the_key_was_rejected() {
        assertIs<FoodParsingException.ApiKeyRejected>(classify(401, "UNAUTHENTICATED", null))
        assertIs<FoodParsingException.ApiKeyRejected>(classify(403, "PERMISSION_DENIED", null))
    }

    @Test
    fun key_status_on_a_400_should_still_mean_the_key_was_rejected() {
        assertIs<FoodParsingException.ApiKeyRejected>(classify(400, "API_KEY_INVALID", null))
    }

    @Test
    fun malformed_payload_on_a_400_should_mean_invalid_request_not_a_bad_key() {
        val failure = classify(400, "INVALID_ARGUMENT", "Invalid JSON payload received")

        assertIs<FoodParsingException.InvalidRequest>(failure)
        assertEquals("INVALID_ARGUMENT", failure.status)
        assertEquals("Invalid JSON payload received", failure.detail)
    }

    @Test
    fun server_errors_should_mean_the_service_is_unavailable() {
        assertIs<FoodParsingException.ServiceUnavailable>(
            classify(503, "UNAVAILABLE", "high demand")
        )
        assertIs<FoodParsingException.ServiceUnavailable>(classify(500, "INTERNAL", null))
    }

    @Test
    fun failure_diagnosis_should_name_the_case_and_carry_the_detail() {
        val failure = classify(503, "UNAVAILABLE", "high demand")

        assertEquals(
            "ServiceUnavailable: Gemini service unavailable (status=503, detail=high demand)",
            failure.diagnose(),
        )
    }

    private fun classify(http: Int, status: String?, detail: String?) =
        classifyGeminiFailure(
            httpStatus = http,
            errorStatus = status,
            detail = detail,
            model = "gemini-2.5-flash",
        )
}
