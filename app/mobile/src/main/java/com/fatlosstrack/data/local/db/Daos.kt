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

@Dao
interface AiUsageDao {
    @Insert
    suspend fun insert(entry: AiUsageEntry)

    @Query("SELECT * FROM ai_usage ORDER BY createdAt DESC")
    fun getAllUsage(): Flow<List<AiUsageEntry>>

    @Query("SELECT SUM(promptTokens) FROM ai_usage")
    fun totalPromptTokens(): Flow<Int?>

    @Query("SELECT SUM(completionTokens) FROM ai_usage")
    fun totalCompletionTokens(): Flow<Int?>

    @Query("SELECT COUNT(*) FROM ai_usage")
    fun totalRequests(): Flow<Int>

    @Query("SELECT feature, SUM(promptTokens) as promptTokens, SUM(completionTokens) as completionTokens, COUNT(*) as requests FROM ai_usage GROUP BY feature ORDER BY (SUM(promptTokens) + SUM(completionTokens)) DESC")
    fun usageByFeature(): Flow<List<FeatureUsageSummary>>

    @Query("SELECT model, SUM(promptTokens) as promptTokens, SUM(completionTokens) as completionTokens, COUNT(*) as requests FROM ai_usage GROUP BY model ORDER BY (SUM(promptTokens) + SUM(completionTokens)) DESC")
    fun usageByModel(): Flow<List<ModelUsageSummary>>

    @Query("SELECT date(createdAt/1000, 'unixepoch', 'localtime') as day, SUM(promptTokens) as promptTokens, SUM(completionTokens) as completionTokens, COUNT(*) as requests FROM ai_usage WHERE createdAt >= :sinceMillis GROUP BY day ORDER BY day ASC")
    fun usageByDay(sinceMillis: Long): Flow<List<DailyUsageSummary>>

    @Query("SELECT date(createdAt/1000, 'unixepoch', 'localtime') as day, model, SUM(promptTokens) as promptTokens, SUM(completionTokens) as completionTokens FROM ai_usage WHERE createdAt >= :sinceMillis GROUP BY day, model ORDER BY day ASC")
    fun usageByDayAndModel(sinceMillis: Long): Flow<List<DailyModelUsage>>

    @Query("DELETE FROM ai_usage")
    suspend fun clearAll()
}

data class FeatureUsageSummary(
    val feature: String,
    val promptTokens: Long,
    val completionTokens: Long,
    val requests: Int,
)

data class ModelUsageSummary(
    val model: String,
    val promptTokens: Long,
    val completionTokens: Long,
    val requests: Int,
)

data class DailyUsageSummary(
    val day: String,            // "YYYY-MM-DD"
    val promptTokens: Long,
    val completionTokens: Long,
    val requests: Int,
)

data class DailyModelUsage(
    val day: String,
    val model: String,
    val promptTokens: Long,
    val completionTokens: Long,
)
