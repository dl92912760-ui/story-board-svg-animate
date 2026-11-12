package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "historia_prefs")

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

class ThemePreferences(private val context: Context) {
    private val themeKey = stringPreferencesKey("app_theme_mode")
    private val customApiKeyPref = stringPreferencesKey("custom_gemini_api_key")
    private val customApiModelPref = stringPreferencesKey("custom_gemini_model_name")

    val themeMode: Flow<AppThemeMode> = context.themeDataStore.data.map { prefs ->
        val raw = prefs[themeKey] ?: AppThemeMode.SYSTEM.name
        runCatching { AppThemeMode.valueOf(raw) }.getOrDefault(AppThemeMode.SYSTEM)
    }

    val customApiKey: Flow<String> = context.themeDataStore.data.map { prefs ->
        prefs[customApiKeyPref].orEmpty()
    }

    val customApiModel: Flow<String> = context.themeDataStore.data.map { prefs ->
        prefs[customApiModelPref].orEmpty()
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.themeDataStore.edit { prefs ->
            prefs[themeKey] = mode.name
        }
    }

    suspend fun setCustomApiConfig(apiKey: String, modelName: String) {
        context.themeDataStore.edit { prefs ->
            prefs[customApiKeyPref] = apiKey.trim()
            prefs[customApiModelPref] = modelName.trim()
        }
    }

    suspend fun clearCustomApiConfig() {
        context.themeDataStore.edit { prefs ->
            prefs.remove(customApiKeyPref)
            prefs.remove(customApiModelPref)
        }
    }
}
