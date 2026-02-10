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

    @Query("SELECT * FROM weight_entries ORDER BY date ASC")
    fun getAllEntries(): Flow<List<WeightEntry>>
}

@Dao
interface MealDao {
    @Insert
    suspend fun insert(entry: MealEntry): Long

    @Query("SELECT * FROM meal_entries WHERE date = :date ORDER BY createdAt ASC")
    fun getMealsForDate(date: LocalDate): Flow<List<MealEntry>>

    @Query("SELECT * FROM meal_entries WHERE date >= :since ORDER BY date DESC, createdAt DESC")
    fun getMealsSince(since: LocalDate): Flow<List<MealEntry>>

    @Query("SELECT * FROM meal_entries ORDER BY date DESC, createdAt DESC")
    fun getAllMeals(): Flow<List<MealEntry>>

    @Update
    suspend fun update(entry: MealEntry)

    @Delete
    suspend fun delete(entry: MealEntry)
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
    suspend fun upsert(log: DailyLog)

    @Query("SELECT * FROM daily_logs WHERE date = :date")
    suspend fun getForDate(date: LocalDate): DailyLog?

    @Query("SELECT * FROM daily_logs WHERE date >= :since ORDER BY date DESC")
    fun getLogsSince(since: LocalDate): Flow<List<DailyLog>>

    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    fun getAllLogs(): Flow<List<DailyLog>>
}

@Dao
interface InsightDao {
    @Insert
    suspend fun insert(insight: Insight)

    @Query("SELECT * FROM insights ORDER BY date DESC, createdAt DESC")
    fun getAllInsights(): Flow<List<Insight>>
}

@Dao
interface ChatMessageDao {
    @Insert
    suspend fun insert(message: ChatMessage): Long

    @Delete
    suspend fun delete(message: ChatMessage)

    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY createdAt DESC LIMIT :count")
    suspend fun getRecentMessages(count: Int): List<ChatMessage>

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}
