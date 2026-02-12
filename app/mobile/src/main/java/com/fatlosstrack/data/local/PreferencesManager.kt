package com.fatlosstrack.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
        private val KEY_START_WEIGHT = floatPreferencesKey("start_weight")
        private val KEY_GOAL_WEIGHT = floatPreferencesKey("goal_weight")
        private val KEY_WEEKLY_RATE = floatPreferencesKey("weekly_rate")
        private val KEY_AI_GUIDANCE = stringPreferencesKey("ai_guidance")
        private val KEY_COACH_TONE = stringPreferencesKey("coach_tone")
        private val KEY_HEIGHT_CM = intPreferencesKey("height_cm")
        private val KEY_START_DATE = stringPreferencesKey("start_date") // ISO format yyyy-MM-dd
        private val KEY_LANGUAGE = stringPreferencesKey("language") // "en" or "sl"
        private val KEY_SEX = stringPreferencesKey("sex") // "male", "female", or "yes"
        private val KEY_AGE = intPreferencesKey("age")
        private val KEY_ACTIVITY_LEVEL = stringPreferencesKey("activity_level") // "sedentary", "light", "moderate", "active"
        private val KEY_THEME_PRESET = stringPreferencesKey("theme_preset") // ThemePreset name
        private val KEY_LAST_BACKUP_TIME = stringPreferencesKey("last_backup_time") // ISO Instant
    }

    val openAiApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_OPENAI_API_KEY] ?: ""
    }

    val openAiModel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_OPENAI_MODEL] ?: "gpt-5-mini"
    }

    val startWeight: Flow<Float?> = context.dataStore.data.map { prefs ->
        prefs[KEY_START_WEIGHT]
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

    val heightCm: Flow<Int?> = context.dataStore.data.map { prefs ->
        prefs[KEY_HEIGHT_CM]
    }

    val startDate: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_START_DATE]
    }

    val language: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "en"
    }

    val sex: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SEX]
    }

    val age: Flow<Int?> = context.dataStore.data.map { prefs ->
        prefs[KEY_AGE]
    }

    val activityLevel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVITY_LEVEL] ?: "light"
    }

    val themePreset: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_PRESET] ?: "PURPLE_DARK"
    }

    val lastBackupTime: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_BACKUP_TIME]
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
        startWeight: Float,
        goalWeight: Float,
        weeklyRate: Float,
        aiGuidance: String,
        heightCm: Int?,
        startDate: String,
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_START_WEIGHT] = startWeight
            prefs[KEY_GOAL_WEIGHT] = goalWeight
            prefs[KEY_WEEKLY_RATE] = weeklyRate
            prefs[KEY_AI_GUIDANCE] = aiGuidance
            if (heightCm != null) prefs[KEY_HEIGHT_CM] = heightCm
            prefs[KEY_START_DATE] = startDate
        }
    }

    suspend fun setCoachTone(tone: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_COACH_TONE] = tone
        }
    }

    suspend fun setHeightCm(height: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HEIGHT_CM] = height
        }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LANGUAGE] = lang
        }
    }

    suspend fun setSex(sex: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SEX] = sex
        }
    }

    suspend fun setAge(age: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AGE] = age
        }
    }

    suspend fun setActivityLevel(level: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVITY_LEVEL] = level
        }
    }

    suspend fun setThemePreset(preset: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_PRESET] = preset
        }
    }

    suspend fun setLastBackupTime(time: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_BACKUP_TIME] = time
        }
    }
}
