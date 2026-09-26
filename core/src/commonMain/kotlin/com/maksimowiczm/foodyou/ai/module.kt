package com.maksimowiczm.foodyou.ai

import com.maksimowiczm.foodyou.ai.domain.FoodParsingService
import com.maksimowiczm.foodyou.ai.domain.GeminiClient
import com.maksimowiczm.foodyou.ai.infrastructure.gemini.GeminiApiClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.onClose

private val httpClientQualifier = named("GeminiHttpClient")

internal fun Module.ai() {
    single(httpClientQualifier) {
            HttpClient {
                install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS }
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        }
        .onClose { it?.close() }

    factory { GeminiApiClient(client = get(httpClientQualifier), logger = get()) }
        .bind<GeminiClient>()

    factoryOf(::FoodParsingService)
}

private const val REQUEST_TIMEOUT_MILLIS = 60_000L
