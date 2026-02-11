package com.fatlosstrack.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        WeightEntry::class,
        MealEntry::class,
        Goal::class,
        DailyLog::class,
        Insight::class,
        ChatMessage::class,
        AiUsageEntry::class,
    ],
    version = 10,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class FatLossDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao
    abstract fun mealDao(): MealDao
    abstract fun goalDao(): GoalDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun insightDao(): InsightDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun aiUsageDao(): AiUsageDao
}
