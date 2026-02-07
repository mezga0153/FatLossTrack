package com.fatlosstrack.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface WeightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WeightEntry)

    @Query("SELECT * FROM weight_entries WHERE date >= :since ORDER BY date ASC")
    fun getEntriesSince(since: LocalDate): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_entries ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(): WeightEntry?

    @Query("SELECT * FROM weight_entries ORDER BY date DESC LIMIT :count")
    suspend fun getLastN(count: Int): List<WeightEntry>
}

@Dao
interface MealDao {
    @Insert
    suspend fun insert(entry: MealEntry)

    @Query("SELECT * FROM meal_entries WHERE date = :date ORDER BY createdAt ASC")
    fun getMealsForDate(date: LocalDate): Flow<List<MealEntry>>

    @Query("SELECT * FROM meal_entries WHERE date >= :since ORDER BY date ASC")
    fun getMealsSince(since: LocalDate): Flow<List<MealEntry>>
}

@Dao
interface GoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: Goal)

    @Query("SELECT * FROM goals ORDER BY createdAt DESC LIMIT 1")
    fun getCurrentGoal(): Flow<Goal?>
}

@Dao
interface DailyLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: DailyLog)

    @Query("SELECT * FROM daily_logs WHERE date >= :since ORDER BY date ASC")
    fun getLogsSince(since: LocalDate): Flow<List<DailyLog>>
}

@Dao
interface InsightDao {
    @Insert
    suspend fun insert(insight: Insight)

    @Query("SELECT * FROM insights ORDER BY date DESC, createdAt DESC")
    fun getAllInsights(): Flow<List<Insight>>
}
