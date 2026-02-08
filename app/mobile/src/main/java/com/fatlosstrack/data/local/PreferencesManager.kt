package com.fatlosstrack.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
        private val KEY_CURRENT_WEIGHT = floatPreferencesKey("current_weight")
        private val KEY_GOAL_WEIGHT = floatPreferencesKey("goal_weight")
        private val KEY_WEEKLY_RATE = floatPreferencesKey("weekly_rate")
        private val KEY_AI_GUIDANCE = stringPreferencesKey("ai_guidance")
        private val KEY_COACH_TONE = stringPreferencesKey("coach_tone")
    }

    val openAiApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_OPENAI_API_KEY] ?: ""
    }

    val openAiModel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_OPENAI_MODEL] ?: "gpt-5.2"
    }

    val currentWeight: Flow<Float?> = context.dataStore.data.map { prefs ->
        prefs[KEY_CURRENT_WEIGHT]
    }

    val goalWeight: Flow<Float?> = context.dataStore.data.map { prefs ->
        prefs[KEY_GOAL_WEIGHT]
    }

    val weeklyRate: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_WEEKLY_RATE] ?: 0.5f
    }

    val aiGuidance: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_AI_GUIDANCE] ?: ""
    }

    val coachTone: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_COACH_TONE] ?: "honest"
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

    suspend fun setGoal(
        currentWeight: Float,
        goalWeight: Float,
        weeklyRate: Float,
        aiGuidance: String,
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CURRENT_WEIGHT] = currentWeight
            prefs[KEY_GOAL_WEIGHT] = goalWeight
            prefs[KEY_WEEKLY_RATE] = weeklyRate
            prefs[KEY_AI_GUIDANCE] = aiGuidance
        }
    }

    suspend fun setCoachTone(tone: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_COACH_TONE] = tone
        }
    }
}
