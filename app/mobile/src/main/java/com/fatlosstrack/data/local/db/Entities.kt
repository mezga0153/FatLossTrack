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
    val category: MealCategory,
    val hasAlcohol: Boolean = false,
    val kcalLow: Int? = null,
    val kcalHigh: Int? = null,
    val confidence: Confidence? = null,
    val photoUri: String? = null,
    val note: String? = null,
    val createdAt: Instant = Instant.now(),
)

enum class MealCategory { HOME, RESTAURANT, FAST_FOOD }
enum class Confidence { LOW, MEDIUM, HIGH }

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
    val offPlan: Boolean = false,
    val steps: Int? = null,
    val sleepHours: Double? = null,
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
