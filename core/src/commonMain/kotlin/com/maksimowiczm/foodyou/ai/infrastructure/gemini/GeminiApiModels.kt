package com.maksimowiczm.foodyou.ai.infrastructure.gemini

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable internal data class GeminiContent(val parts: List<GeminiPart> = emptyList())

@Serializable internal data class GeminiPart(val text: String = "")

@Serializable
internal data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Double? = null,
)

@Serializable
internal data class GeminiGenerateResponse(val candidates: List<GeminiCandidate> = emptyList())

@Serializable internal data class GeminiCandidate(val content: GeminiContent? = null)

@Serializable internal data class GeminiModelsResponse(val models: List<GeminiModel> = emptyList())

@Serializable
internal data class GeminiModel(
    val name: String = "",
    val supportedGenerationMethods: List<String> = emptyList(),
)

@Serializable internal data class GeminiErrorResponse(val error: GeminiErrorBody? = null)

@Serializable
internal data class GeminiErrorBody(
    val message: String? = null,
    @SerialName("status") val status: String? = null,
)

/**
 * One food as returned by the model.
 *
 * Every nutrition value is per 100 g (or per 100 ml for liquids), matching the convention of
 * [com.maksimowiczm.foodyou.common.domain.food.NutritionFacts].
 */
@Serializable
internal data class GeminiParsedFood(
    val name: String = "",
    val portionWeightGrams: Double? = null,
    val portionVolumeMilliliters: Double? = null,
    val energyKilocalories: Double? = null,
    val proteinsGrams: Double = 0.0,
    val carbohydratesGrams: Double = 0.0,
    val fatsGrams: Double = 0.0,
    val sugarsGrams: Double? = null,
    val dietaryFiberGrams: Double? = null,
    val saltGrams: Double? = null,
)
