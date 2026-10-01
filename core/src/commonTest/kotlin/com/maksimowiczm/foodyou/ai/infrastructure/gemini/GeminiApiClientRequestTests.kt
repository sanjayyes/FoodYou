package com.maksimowiczm.foodyou.ai.infrastructure.gemini

import co.touchlab.kermit.Logger
import com.maksimowiczm.foodyou.ai.domain.FoodParsingException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Exercises the client against a mock engine.
 *
 * These exist because the real failure was invisible to compilation and to unit tests of the
 * mapping layer: the request was never serialized, so it was never sent, so nothing downstream ever
 * ran. Only a test that puts a real body through the real client can catch that.
 */
class GeminiApiClientRequestTests {

    @Test
    fun request_body_should_be_serialized_to_json() = runTest {
        val captured = mutableListOf<String>()
        val client = mockClient(captured) { respondOk(envelope) }

        val entries =
            GeminiApiClient(client, Logger.Companion)
                .parseFood(
                    description = "2 eggs",
                    apiKey = "key",
                    model = "gemini-flash-lite-latest",
                )

        assertEquals(1, captured.size, "the request should have been sent exactly once")
        // TextContent proves ContentNegotiation serialized the body rather than leaving it opaque.
        assertTrue(
            captured.single().startsWith("TextContent[application/json]"),
            "body should be serialized by ContentNegotiation, was: ${captured.single()}",
        )
        assertTrue(
            captured.single().contains(""""contents""""),
            "body should carry the contents field, was: ${captured.single()}",
        )
        assertEquals("Egg", entries.single().foodName)
    }

    @Test
    fun api_key_should_be_sent_as_a_header() = runTest {
        var seenKey: String? = null
        val client =
            mockClient(mutableListOf()) { request ->
                seenKey = request.headers["x-goog-api-key"]
                respondOk(envelope)
            }

        GeminiApiClient(client, Logger.Companion)
            .parseFood(description = "2 eggs", apiKey = "secret-key", model = "m")

        assertEquals("secret-key", seenKey)
    }

    @Test
    fun a_retired_model_should_surface_as_model_unavailable() = runTest {
        val client = mockClient(mutableListOf()) { respondError(HttpStatusCode.NotFound) }

        val failure =
            assertFailsWith<FoodParsingException> {
                GeminiApiClient(client, Logger.Companion)
                    .parseFood(description = "2 eggs", apiKey = "key", model = "gemini-2.5-flash")
            }

        assertIs<FoodParsingException.ModelUnavailable>(failure)
        assertEquals("gemini-2.5-flash", failure.model)
    }

    @Test
    fun quota_exhaustion_should_surface_as_quota_exceeded() = runTest {
        val client = mockClient(mutableListOf()) { respondError(HttpStatusCode.TooManyRequests) }

        val failure =
            assertFailsWith<FoodParsingException> {
                GeminiApiClient(client, Logger.Companion)
                    .parseFood(description = "2 eggs", apiKey = "key", model = "m")
            }

        assertIs<FoodParsingException.QuotaExceeded>(failure)
    }

    @Test
    fun list_models_should_strip_the_prefix_and_keep_generation_capable() = runTest {
        val client =
            mockClient(mutableListOf()) {
                respondOk(
                    buildJsonObject {
                        put(
                            "models",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("name", "models/a")
                                        put(
                                            "supportedGenerationMethods",
                                            buildJsonArray {
                                                add(JsonPrimitive("generateContent"))
                                            },
                                        )
                                    }
                                )
                                add(
                                    buildJsonObject {
                                        put("name", "models/b")
                                        put(
                                            "supportedGenerationMethods",
                                            buildJsonArray { add(JsonPrimitive("countTokens")) },
                                        )
                                    }
                                )
                            },
                        )
                    }
                        .toString()
                )
            }

        val models = GeminiApiClient(client, Logger.Companion).listModels(apiKey = "key")

        assertEquals(listOf("a"), models)
    }

    private val jsonHeaders = headersOf("Content-Type", ContentType.Application.Json.toString())

    private fun MockRequestHandleScope.respondOk(body: String): HttpResponseData =
        respond(body, HttpStatusCode.OK, jsonHeaders)

    /** The model replies with a JSON array carried inside a text field. */
    private val envelope: String = buildJsonObject {
        put(
            "candidates",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put(
                            "content",
                            buildJsonObject {
                                put(
                                    "parts",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put(
                                                    "text",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("name", "Egg")
                                                                put(
                                                                    "proteinsGrams",
                                                                    12.6,
                                                                )
                                                                put(
                                                                    "carbohydratesGrams",
                                                                    0.7,
                                                                )
                                                                put("fatsGrams", 9.5)
                                                            }
                                                        )
                                                    }
                                                        .toString(),
                                                )
                                            }
                                        )
                                    },
                                )
                            },
                        )
                    }
                )
            },
        )
    }
        .toString()

    private fun mockClient(
        captured: MutableList<String>,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient =
        HttpClient(
            MockEngine { request ->
                captured += request.body.toString()
                handler(request)
            }
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
}
