package com.fatlosstrack.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fatlosstrack.data.local.db.*
import com.fatlosstrack.data.health.HealthConnectManager
import com.fatlosstrack.data.local.AppLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE daily_logs ADD COLUMN daySummary TEXT DEFAULT NULL")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE meal_entries ADD COLUMN totalProteinG INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS chat_messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE meal_entries ADD COLUMN totalCarbsG INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE meal_entries ADD COLUMN totalFatG INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FatLossDatabase {
        return Room.databaseBuilder(
            context,
            FatLossDatabase::class.java,
            "fatloss_track.db"
        ).addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build()
    }

    @Provides fun provideWeightDao(db: FatLossDatabase): WeightDao = db.weightDao()
    @Provides fun provideMealDao(db: FatLossDatabase): MealDao = db.mealDao()
    @Provides fun provideGoalDao(db: FatLossDatabase): GoalDao = db.goalDao()
    @Provides fun provideDailyLogDao(db: FatLossDatabase): DailyLogDao = db.dailyLogDao()
    @Provides fun provideInsightDao(db: FatLossDatabase): InsightDao = db.insightDao()
    @Provides fun provideChatMessageDao(db: FatLossDatabase): ChatMessageDao = db.chatMessageDao()

    @Provides
    @Singleton
    fun provideHealthConnectManager(@ApplicationContext context: Context, appLogger: AppLogger): HealthConnectManager {
        return HealthConnectManager(context, appLogger)
    }

    @Provides
    @Singleton
    fun provideAppLogger(@ApplicationContext context: Context): AppLogger {
        return AppLogger(context)
    }
}
