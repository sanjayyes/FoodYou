package com.maksimowiczm.foodyou.ai.domain

import co.touchlab.kermit.Logger

/**
 * Turns free-form food descriptions into structured [ParsedFoodEntry] values.
 *
 * Owns policy — credential validation, model fallback, logging — while the Gemini wire contract and
 * payload decoding live in [GeminiClient].
 */
class FoodParsingService(
    private val geminiClient: GeminiClient,
    logger: Logger,
) {
    private val logger = logger.withTag(TAG)

    /**
     * Parses [description] into zero or more entries.
     *
     * @param model preferred model id; falls back to [DEFAULT_GEMINI_MODEL] when absent or blank
     * @throws FoodParsingException when the API key is missing or the request fails
     */
    suspend fun parse(
        description: String,
        apiKey: String,
        model: String? = null,
    ): List<ParsedFoodEntry> {
        val key = apiKey.trim()
        if (key.isEmpty()) throw FoodParsingException.ApiKeyMissing

        val trimmed = description.trim()
        if (trimmed.isEmpty()) return emptyList()

        val entries =
            geminiClient.parseFood(
                description = trimmed,
                apiKey = key,
                model = model?.takeIf { it.isNotBlank() } ?: DEFAULT_GEMINI_MODEL,
            )

        logger.d { "Parsed ${entries.size} entries from \"$trimmed\"" }
        return entries
    }

    /** Lists the model ids available to [apiKey]. */
    suspend fun models(apiKey: String): List<String> {
        val key = apiKey.trim()
        if (key.isEmpty()) throw FoodParsingException.ApiKeyMissing
        return geminiClient.listModels(key)
    }

    private companion object {
        const val TAG = "FoodParsing"
    }
}
