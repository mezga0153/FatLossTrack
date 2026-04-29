package com.fatlosstrack.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.Instant

@Entity(tableName = "weight_entries")
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val valueKg: Double,
    val source: WeightSource = WeightSource.MANUAL,
    val createdAt: Instant = Instant.now(),
)

enum class WeightSource { MANUAL, HEALTH_CONNECT }

@Entity(tableName = "meal_entries")
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val description: String = "",
    val itemsJson: String? = null,
    val totalKcal: Int = 0,
    val totalProteinG: Int = 0,
    val totalCarbsG: Int = 0,
    val totalFatG: Int = 0,
    val coachNote: String? = null,
    val category: MealCategory = MealCategory.HOME,
    val mealType: MealType? = null,
    val hasAlcohol: Boolean = false,
    val photoUri: String? = null,
    val note: String? = null,
    val createdAt: Instant = Instant.now(),
)

enum class MealCategory { HOME, RESTAURANT, FAST_FOOD }

enum class MealType { BREAKFAST, BRUNCH, LUNCH, DINNER, SNACK }

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetKg: Double,
    val rateKgPerWeek: Double,
    val deadline: LocalDate,
    val dailyDeficitKcal: Int? = null,
    val createdAt: Instant = Instant.now(),
)

@Entity(tableName = "daily_logs")
data class DailyLog(
    @PrimaryKey val date: LocalDate,
    val weightKg: Double? = null,
    val steps: Int? = null,
    val sleepHours: Double? = null,
    val restingHr: Int? = null,
    val exercisesJson: String? = null,   // JSON array: [{"name":"Running","durationMin":30,"kcal":250}]
    val notes: String? = null,
    val offPlan: Boolean = false,
    val daySummary: String? = null,
    val synopsis: String? = null,
    val weightLocked: Boolean = false,
    val stepsLocked: Boolean = false,
    val bodyFatPct: Double? = null,
    val bodyWaterKg: Double? = null,
    val leanBodyMassKg: Double? = null,
    val boneMassKg: Double? = null,
    val bloodSugarMmol: Double? = null,  // fasting blood glucose in mmol/L
)

/**
 * Best available lean mass (kg) for this log entry.
 * Prefers the directly measured value from HC [leanBodyMassKg], then
 * derives it from weight + body fat %, returns null if neither is available.
 */
val DailyLog.measuredLeanMassKg: Double?
    get() = leanBodyMassKg
        ?: weightKg?.let { w -> bodyFatPct?.let { bf -> w * (1.0 - bf / 100.0) } }

@Entity(tableName = "insights")
data class Insight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val type: InsightType,
    val message: String,
    val dataJson: String? = null,
    val createdAt: Instant = Instant.now(),
)

enum class InsightType { PATTERN, TRADEOFF }

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,       // "user" or "assistant"
    val content: String,
    val imageUris: String? = null,   // comma-separated content:// URIs (user messages only)
    val createdAt: Instant = Instant.now(),
)

@Entity(tableName = "ai_usage")
data class AiUsageEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feature: String,        // "chat", "meal_text", "meal_photo", "meal_suggest", "meal_edit", "day_summary", "period_summary"
    val model: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val createdAt: Instant = Instant.now(),
)

@Entity(tableName = "bookmarked_meals")
data class BookmarkedMeal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val itemsJson: String? = null,
    val totalKcal: Int = 0,
    val totalProteinG: Int = 0,
    val totalCarbsG: Int = 0,
    val totalFatG: Int = 0,
    val category: MealCategory = MealCategory.HOME,
    val mealType: MealType? = null,
    val sortOrder: Int = 0,
    val createdAt: Instant = Instant.now(),
)
