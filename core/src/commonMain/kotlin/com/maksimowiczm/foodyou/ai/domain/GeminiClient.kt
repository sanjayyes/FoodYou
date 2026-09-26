package com.maksimowiczm.foodyou.ai.domain

/**
 * Model used when the user has not explicitly chosen one.
 *
 * This is the single source of truth for the default: the settings screen labels it and
 * [FoodParsingService] falls back to it, so the two can never disagree.
 */
const val DEFAULT_GEMINI_MODEL: String = "gemini-2.5-flash"

/** Transport for the Gemini generative API. */
interface GeminiClient {

    /**
     * Parses a free-form food description into structured nutrition entries.
     *
     * @param description natural language description, e.g. "2 medium eggs and a slice of toast"
     * @param apiKey Gemini API key
     * @param model model id, without the `models/` prefix
     * @throws FoodParsingException on any failure
     */
    suspend fun parseFood(
        description: String,
        apiKey: String,
        model: String,
    ): List<ParsedFoodEntry>

    /**
     * Lists model ids that support content generation, without the `models/` prefix.
     *
     * @param apiKey Gemini API key
     * @throws FoodParsingException on any failure
     */
    suspend fun listModels(apiKey: String): List<String>
}
