package com.maksimowiczm.foodyou.preferences.infrastructure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maksimowiczm.foodyou.common.extension.set
import com.maksimowiczm.foodyou.common.infrastructure.crypto.SoftwareEncrypted
import com.maksimowiczm.foodyou.common.infrastructure.crypto.decryptString
import com.maksimowiczm.foodyou.common.infrastructure.crypto.encryptString
import com.maksimowiczm.foodyou.preferences.domain.AiPreferences

/** Stores [AiPreferences], encrypting the API key at rest via the platform key store. */
class AiProvider(dataStore: DataStore<Preferences>) : DataStoreProvider<AiPreferences>(dataStore) {
    override val key = AiPreferences::class.qualifiedName!!

    override fun Preferences.map(): AiPreferences =
        AiPreferences(
            geminiApiKey = this[apiKey]?.let { SoftwareEncrypted(it).decryptString() },
            geminiModel = this[model],
        )

    override fun MutablePreferences.apply(value: AiPreferences) {
        this[apiKey] = value.geminiApiKey?.let { SoftwareEncrypted.encryptString(it).data }
        this[model] = value.geminiModel
    }

    private companion object {
        private val apiKey = byteArrayPreferencesKey("ai:gemini_api_key")
        private val model = stringPreferencesKey("ai:gemini_model")
    }
}
