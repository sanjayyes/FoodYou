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
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
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
        logger.d { "parseFood: model=$model descriptionLength=${description.length}" }

        val response =
            try {
                client.post("$BASE_URL/models/$model:$GENERATE_CONTENT_METHOD") {
                    header(API_KEY_HEADER, apiKey)
                    // Required: ContentNegotiation only selects a converter for a body when a
                    // Content-Type is present. Without this the request fails to serialize.
                    contentType(ContentType.Application.Json)
                    setBody(buildRequest(description, model))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.e(e) { "parseFood: request failed before any response (model=$model)" }
                throw FoodParsingException.NetworkUnavailable(detail = e.message)
            }

        response.throwIfFailure(model)

        val payload =
            try {
                response.body<GeminiGenerateResponse>()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.e(e) { "parseFood: could not read response envelope" }
                throw FoodParsingException.MalformedResponse(detail = e.message)
            }

        val text =
            payload.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: run {
                    logger.e { "parseFood: response carried no candidates" }
                    throw FoodParsingException.MalformedResponse(detail = "no candidates")
                }

        val foods =
            try {
                json.decodeFromString(ListSerializer(GeminiParsedFood.serializer()), text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.e(e) { "parseFood: could not decode model payload" }
                throw FoodParsingException.MalformedResponse(detail = e.message)
            }

        logger.d { "parseFood: response decoded into ${foods.size} raw foods (model=$model)" }

        val entries =
            try {
                foods.filter { it.name.isNotBlank() }.map { it.toDomain() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.e(e) { "parseFood: mapping model output onto the food domain failed" }
                throw FoodParsingException.MalformedResponse(detail = e.message)
            }

        logger.d { "parseFood: decoded ${entries.size} entries (model=$model)" }
        return entries
    }

    override suspend fun listModels(apiKey: String): List<String> {
        val response =
            try {
                client.get("$BASE_URL/models") { header(API_KEY_HEADER, apiKey) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.e(e) { "listModels: request failed before any response" }
                throw FoodParsingException.NetworkUnavailable(detail = e.message)
            }

        response.throwIfFailure(model = MODEL_NAME_PREFIX)

        val models =
            try {
                response.body<GeminiModelsResponse>()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.e(e) { "listModels: could not read response envelope" }
                throw FoodParsingException.MalformedResponse(detail = e.message)
            }

        return models.models
            .filter { GENERATE_CONTENT_METHOD in it.supportedGenerationMethods }
            .map { it.name.removePrefix(MODEL_NAME_PREFIX) }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .also { logger.d { "listModels: ${it.size} generation-capable models" } }
    }

    private suspend fun HttpResponse.throwIfFailure(model: String) {
        val responseStatus = status
        if (responseStatus.isSuccess()) return

        val code = responseStatus.value
        val error = runCatching {
            json.decodeFromString(GeminiErrorResponse.serializer(), bodyAsText())
        }
            .getOrNull()
            ?.error

        val failure =
            classifyGeminiFailure(
                httpStatus = code,
                errorStatus = error?.status,
                detail = error?.message,
                model = model,
            )

        logger.e { "Gemini request failed: ${failure.diagnose()}" }
        throw failure
    }

    /**
     * Builds the request body for [description].
     *
     * Exposed so the exact payload can be asserted in tests: the wire shape is a contract with the
     * model, and a change here silently degrades parsing quality.
     */
    internal fun buildRequest(description: String, model: String): GeminiGenerateRequest =
        GeminiGenerateRequest(
            contents =
                listOf(GeminiContent(parts = listOf(GeminiPart(text = buildPrompt(description))))),
            generationConfig =
                GeminiGenerationConfig(responseMimeType = JSON_MIME_TYPE, temperature = 0.0),
        )

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
