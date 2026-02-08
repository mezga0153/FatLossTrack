package com.fatlosstrack.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        private val KEY_OPENAI_MODEL = stringPreferencesKey("openai_model")
    }

    val openAiApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_OPENAI_API_KEY] ?: ""
    }

    val openAiModel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_OPENAI_MODEL] ?: "gpt-5.2"
    }

    suspend fun setOpenAiApiKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_OPENAI_API_KEY] = key
        }
    }

    suspend fun setOpenAiModel(model: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_OPENAI_MODEL] = model
        }
    }
}
