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
)

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
