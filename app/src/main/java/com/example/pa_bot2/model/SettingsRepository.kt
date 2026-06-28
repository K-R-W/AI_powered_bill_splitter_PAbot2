package com.example.pa_bot2.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
    private val IS_AUTHENTICATED = booleanPreferencesKey("is_authenticated")
    private val USER_EMAIL = stringPreferencesKey("user_email")
    private val GOOGLE_ACCESS_TOKEN = stringPreferencesKey("google_access_token")
    private val SPLITWISE_ACCESS_TOKEN = stringPreferencesKey("splitwise_access_token")
    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")

    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "system"
    }

    val defaultCurrencyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_CURRENCY] ?: "INR"
    }

    val googleAccessTokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[GOOGLE_ACCESS_TOKEN]
    }

    val splitwiseAccessTokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SPLITWISE_ACCESS_TOKEN]
    }

    val isAiEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[GOOGLE_ACCESS_TOKEN] != null
    }

    val isSplitwiseConnectedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SPLITWISE_ACCESS_TOKEN] != null
    }

    val isOnboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_ONBOARDING_COMPLETED] ?: false
    }

    val userEmailFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_EMAIL]
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun setDefaultCurrency(code: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_CURRENCY] = code
        }
    }

    suspend fun setGoogleAuth(email: String, accessToken: String?) {
        context.dataStore.edit { preferences ->
            preferences[IS_AUTHENTICATED] = accessToken != null
            preferences[USER_EMAIL] = email
            if (accessToken != null) {
                preferences[GOOGLE_ACCESS_TOKEN] = accessToken
            } else {
                preferences.remove(GOOGLE_ACCESS_TOKEN)
            }
        }
    }

    suspend fun setSplitwiseAuth(accessToken: String?) {
        context.dataStore.edit { preferences ->
            if (accessToken != null) {
                preferences[SPLITWISE_ACCESS_TOKEN] = accessToken
            } else {
                preferences.remove(SPLITWISE_ACCESS_TOKEN)
            }
        }
    }

    suspend fun logoutGoogle() {
        context.dataStore.edit { preferences ->
            preferences[IS_AUTHENTICATED] = false
            preferences.remove(USER_EMAIL)
            preferences.remove(GOOGLE_ACCESS_TOKEN)
        }
    }
}
