package com.maksimowiczm.foodyou.ai.infrastructure.gemini

import co.touchlab.kermit.Logger
import com.maksimowiczm.foodyou.ai.domain.FoodParsingException
import com.maksimowiczm.foodyou.ai.domain.GeminiClient
import com.maksimowiczm.foodyou.ai.domain.ParsedFoodEntry
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
private const val API_KEY_HEADER = "x-goog-api-key"
private const val JSON_MIME_TYPE = "application/json"
private const val MODEL_NAME_PREFIX = "models/"
private const val GENERATE_CONTENT_METHOD = "generateContent"

/**
 * [GeminiClient] backed by the public Gemini REST API.
 *
 * Owns the wire contract, including the prompt, because the response shape the prompt demands is
 * part of that contract.
 */
internal class GeminiApiClient(
    private val client: HttpClient,
    logger: Logger,
) : GeminiClient {
    private val logger = logger.withTag(TAG)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun parseFood(
        description: String,
        apiKey: String,
        model: String,
    ): List<ParsedFoodEntry> {
        val response =
            client.post("$BASE_URL/models/$model:$GENERATE_CONTENT_METHOD") {
                header(API_KEY_HEADER, apiKey)
                setBody(
                    GeminiGenerateRequest(
                        contents =
                            listOf(
                                GeminiContent(
                                    parts = listOf(GeminiPart(text = buildPrompt(description)))
                                )
                            ),
                        generationConfig =
                            GeminiGenerationConfig(
                                responseMimeType = JSON_MIME_TYPE,
                                temperature = 0.0,
                            ),
                    )
                )
            }

        response.throwIfFailure()

        val payload = response.body<GeminiGenerateResponse>()
        val text =
            payload.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw FoodParsingException.MalformedResponse(
                    detail = "no candidates in response"
                )

        val foods =
            try {
                json.decodeFromString(ListSerializer(GeminiParsedFood.serializer()), text)
            } catch (e: SerializationException) {
                throw FoodParsingException.MalformedResponse(detail = e.message)
            }

        return foods
            .filter { it.name.isNotBlank() }
            .map { it.toDomain() }
            .also { logger.d { "Decoded ${it.size} foods for model $model" } }
    }

    override suspend fun listModels(apiKey: String): List<String> {
        val response = client.get("$BASE_URL/models") { header(API_KEY_HEADER, apiKey) }

        response.throwIfFailure()

        return response
            .body<GeminiModelsResponse>()
            .models
            .filter { GENERATE_CONTENT_METHOD in it.supportedGenerationMethods }
            .map { it.name.removePrefix(MODEL_NAME_PREFIX) }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    private suspend fun HttpResponse.throwIfFailure() {
        val status = status
        if (status.isSuccess()) return

        val code = status.value
        val detail = runCatching {
            json.decodeFromString(
                GeminiErrorResponse.serializer(),
                bodyAsText(),
            )
        }
            .getOrNull()
            ?.error
            ?.message

        logger.e { "Gemini request failed with $code: $detail" }

        if (code == 400 || code == 401 || code == 403) {
            throw FoodParsingException.ApiKeyRejected(status = detail)
        }
        throw FoodParsingException.RequestFailed(status = code, detail = detail)
    }

    /**
     * Builds the instruction sent to the model.
     *
     * Internal rather than private so the contract can be asserted in tests.
     */
    internal fun buildPrompt(description: String): String =
        """
        You are a nutrition database. Convert the food description below into structured data.

        Description: "${description.trim().replace("\"", "'")}"

        Reply with a JSON array. Each element describes one food:
        {
          "name": string,
          "portionWeightGrams": number or null,
          "portionVolumeMilliliters": number or null,
          "energyKilocalories": number or null,
          "proteinsGrams": number,
          "carbohydratesGrams": number,
          "fatsGrams": number,
          "sugarsGrams": number or null,
          "dietaryFiberGrams": number or null,
          "saltGrams": number or null
        }

        Rules:
        - Nutrition values are per 100 grams, or per 100 milliliters for liquids. Never per portion.
        - portionWeightGrams and portionVolumeMilliliters cover the whole described portion.
          Set exactly one of them; leave the other null.
        - Use null for a value you genuinely do not know. Never invent numbers.
        - Split the description into one element per distinct food.
        - Reply with an empty array if it names no food.
        """
            .trimIndent()

    private companion object {
        const val TAG = "Gemini"
    }
}
