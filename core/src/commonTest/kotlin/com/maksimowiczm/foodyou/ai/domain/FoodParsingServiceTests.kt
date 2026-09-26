package com.maksimowiczm.foodyou.ai.domain

import co.touchlab.kermit.Logger
import com.maksimowiczm.foodyou.common.domain.food.AbsoluteQuantity
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.grams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FoodParsingServiceTests {

    private val client = FakeGeminiClient()
    private val service = FoodParsingService(client, Logger.Companion)

    @Test
    fun blank_api_key_should_be_rejected_before_calling_the_api() = runTest {
        assertFailsWith<FoodParsingException.ApiKeyMissing> {
            service.parse(description = "two eggs", apiKey = "   ")
        }
        assertEquals(0, client.parseCalls)
    }

    @Test
    fun blank_description_should_short_circuit() = runTest {
        assertTrue(service.parse(description = "   ", apiKey = "key").isEmpty())
        assertEquals(0, client.parseCalls)
    }

    @Test
    fun missing_model_should_fall_back_to_the_default() = runTest {
        service.parse(description = "two eggs", apiKey = "key", model = null)

        assertEquals(DEFAULT_GEMINI_MODEL, client.lastModel)
    }

    @Test
    fun blank_model_should_fall_back_to_the_default() = runTest {
        service.parse(description = "two eggs", apiKey = "key", model = "  ")

        assertEquals(DEFAULT_GEMINI_MODEL, client.lastModel)
    }

    @Test
    fun explicit_model_should_be_forwarded() = runTest {
        service.parse(description = "two eggs", apiKey = "key", model = "gemini-2.5-pro")

        assertEquals("gemini-2.5-pro", client.lastModel)
    }

    @Test
    fun api_key_should_be_trimmed_before_use() = runTest {
        service.parse(description = "two eggs", apiKey = "  key  ")

        assertEquals("key", client.lastApiKey)
    }

    @Test
    fun parsed_entries_should_be_returned_untouched() = runTest {
        val entries = service.parse(description = "two eggs", apiKey = "key")

        assertEquals(1, entries.size)
        assertEquals("Egg", entries.single().foodName)
    }

    @Test
    fun listing_models_should_reject_a_blank_api_key() = runTest {
        assertFailsWith<FoodParsingException.ApiKeyMissing> { service.models(apiKey = "") }
        assertEquals(0, client.modelCalls)
    }

    @Test
    fun listing_models_should_be_forwarded() = runTest {
        val models = service.models(apiKey = " key ")

        assertEquals(listOf("gemini-2.5-flash", "gemini-2.5-pro"), models)
        assertEquals("key", client.lastApiKey)
    }

    private class FakeGeminiClient : GeminiClient {
        var parseCalls = 0
        var modelCalls = 0
        var lastModel: String? = null
        var lastApiKey: String? = null

        override suspend fun parseFood(
            description: String,
            apiKey: String,
            model: String,
        ): List<ParsedFoodEntry> {
            parseCalls++
            lastModel = model
            lastApiKey = apiKey
            return listOf(
                ParsedFoodEntry(
                    foodName = "Egg",
                    portion = AbsoluteQuantity.Weight(100.0.grams),
                    nutritionFacts = NutritionFacts(),
                )
            )
        }

        override suspend fun listModels(apiKey: String): List<String> {
            modelCalls++
            lastApiKey = apiKey
            return listOf("gemini-2.5-flash", "gemini-2.5-pro")
        }
    }
}
