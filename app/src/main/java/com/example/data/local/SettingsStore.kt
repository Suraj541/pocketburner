package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pocketburner_settings")

data class SecurityAndTransportSettings(
    val encryptionEnabled: Boolean = false,
    val preSharedKey: String = "pocketburner-iot-key",
    val allowSelfSignedTls: Boolean = true,
    val defaultBaudRate: Int = 115200,
    val selectedBoardId: String = "CoreS3",
    val aiProvider: String = "OPENROUTER",
    val aiApiKey: String = "",
    val aiCustomBaseUrl: String = "https://openrouter.ai/api/v1",
    val aiSelectedModelId: String = "",
    val aiFreeOnlyFilter: Boolean = true,
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val ENCRYPTION_ENABLED = booleanPreferencesKey("encryption_enabled")
        val PRE_SHARED_KEY = stringPreferencesKey("pre_shared_key")
        val ALLOW_SELF_SIGNED_TLS = booleanPreferencesKey("allow_self_signed_tls")
        val DEFAULT_BAUD = intPreferencesKey("default_baud")
        val SELECTED_BOARD_ID = stringPreferencesKey("selected_board_id")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_CUSTOM_BASE_URL = stringPreferencesKey("ai_custom_base_url")
        val AI_SELECTED_MODEL_ID = stringPreferencesKey("ai_selected_model_id")
        val AI_FREE_ONLY_FILTER = booleanPreferencesKey("ai_free_only_filter")
    }

    val settings: Flow<SecurityAndTransportSettings> = context.dataStore.data.map { prefs ->
        SecurityAndTransportSettings(
            encryptionEnabled = prefs[Keys.ENCRYPTION_ENABLED] ?: false,
            preSharedKey = prefs[Keys.PRE_SHARED_KEY] ?: "pocketburner-iot-key",
            allowSelfSignedTls = prefs[Keys.ALLOW_SELF_SIGNED_TLS] ?: true,
            defaultBaudRate = prefs[Keys.DEFAULT_BAUD] ?: 115200,
            selectedBoardId = prefs[Keys.SELECTED_BOARD_ID] ?: "CoreS3",
            aiProvider = prefs[Keys.AI_PROVIDER] ?: "OPENROUTER",
            aiApiKey = prefs[Keys.AI_API_KEY] ?: "",
            aiCustomBaseUrl = prefs[Keys.AI_CUSTOM_BASE_URL] ?: "https://openrouter.ai/api/v1",
            aiSelectedModelId = prefs[Keys.AI_SELECTED_MODEL_ID] ?: "",
            aiFreeOnlyFilter = prefs[Keys.AI_FREE_ONLY_FILTER] ?: true,
        )
    }

    suspend fun setEncryptionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ENCRYPTION_ENABLED] = enabled }
    }

    suspend fun setPreSharedKey(psk: String) {
        context.dataStore.edit { it[Keys.PRE_SHARED_KEY] = psk }
    }

    suspend fun setAllowSelfSignedTls(allow: Boolean) {
        context.dataStore.edit { it[Keys.ALLOW_SELF_SIGNED_TLS] = allow }
    }

    suspend fun setDefaultBaudRate(baud: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_BAUD] = baud }
    }

    suspend fun setSelectedBoardId(boardId: String) {
        context.dataStore.edit { it[Keys.SELECTED_BOARD_ID] = boardId }
    }

    suspend fun setAiProvider(provider: String, defaultBaseUrl: String) {
        context.dataStore.edit {
            it[Keys.AI_PROVIDER] = provider
            it[Keys.AI_CUSTOM_BASE_URL] = defaultBaseUrl
        }
    }

    suspend fun setAiApiKey(apiKey: String) {
        context.dataStore.edit { it[Keys.AI_API_KEY] = apiKey }
    }

    suspend fun setAiCustomBaseUrl(baseUrl: String) {
        context.dataStore.edit { it[Keys.AI_CUSTOM_BASE_URL] = baseUrl }
    }

    suspend fun setAiSelectedModelId(modelId: String) {
        context.dataStore.edit { it[Keys.AI_SELECTED_MODEL_ID] = modelId }
    }

    suspend fun setAiFreeOnlyFilter(freeOnly: Boolean) {
        context.dataStore.edit { it[Keys.AI_FREE_ONLY_FILTER] = freeOnly }
    }
}
