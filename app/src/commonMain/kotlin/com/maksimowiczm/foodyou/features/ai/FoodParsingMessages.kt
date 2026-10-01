package com.maksimowiczm.foodyou.features.ai

import androidx.compose.runtime.Composable
import com.maksimowiczm.foodyou.ai.domain.FoodParsingException
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.error_ai_api_key_missing
import foodyou.app.generated.resources.error_ai_api_key_rejected
import foodyou.app.generated.resources.error_ai_invalid_request
import foodyou.app.generated.resources.error_ai_malformed_response
import foodyou.app.generated.resources.error_ai_model_unavailable
import foodyou.app.generated.resources.error_ai_network_unavailable
import foodyou.app.generated.resources.error_ai_quota_exceeded
import foodyou.app.generated.resources.error_ai_service_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * Maps a failure to a message that tells the user what to do about it.
 *
 * Shared by the food input dialog and the settings screen so both describe the same problem the
 * same way.
 */
@Composable
internal fun FoodParsingException.asMessage(): String =
    when (this) {
        FoodParsingException.ApiKeyMissing -> stringResource(Res.string.error_ai_api_key_missing)
        is FoodParsingException.ApiKeyRejected ->
            stringResource(Res.string.error_ai_api_key_rejected)
        is FoodParsingException.ModelUnavailable ->
            stringResource(Res.string.error_ai_model_unavailable)
        is FoodParsingException.QuotaExceeded -> stringResource(Res.string.error_ai_quota_exceeded)
        is FoodParsingException.ServiceUnavailable ->
            stringResource(Res.string.error_ai_service_unavailable)
        is FoodParsingException.NetworkUnavailable ->
            stringResource(Res.string.error_ai_network_unavailable)
        is FoodParsingException.InvalidRequest ->
            stringResource(Res.string.error_ai_invalid_request)
        is FoodParsingException.MalformedResponse ->
            stringResource(Res.string.error_ai_malformed_response)
    }
