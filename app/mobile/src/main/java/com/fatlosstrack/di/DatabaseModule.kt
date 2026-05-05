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

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS ai_usage (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, feature TEXT NOT NULL, model TEXT NOT NULL, promptTokens INTEGER NOT NULL, completionTokens INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN imageUris TEXT DEFAULT NULL")
        }
    }

    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE bookmarked_meals ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE daily_logs ADD COLUMN bodyFatPct REAL DEFAULT NULL")
            db.execSQL("ALTER TABLE daily_logs ADD COLUMN bodyWaterKg REAL DEFAULT NULL")
            db.execSQL("ALTER TABLE daily_logs ADD COLUMN leanBodyMassKg REAL DEFAULT NULL")
            db.execSQL("ALTER TABLE daily_logs ADD COLUMN boneMassKg REAL DEFAULT NULL")
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE daily_logs ADD COLUMN weightLocked INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE daily_logs ADD COLUMN stepsLocked INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE daily_logs ADD COLUMN synopsis TEXT DEFAULT NULL")
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS bookmarked_meals (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "description TEXT NOT NULL DEFAULT '', " +
                    "itemsJson TEXT DEFAULT NULL, " +
                    "totalKcal INTEGER NOT NULL DEFAULT 0, " +
                    "totalProteinG INTEGER NOT NULL DEFAULT 0, " +
                    "totalCarbsG INTEGER NOT NULL DEFAULT 0, " +
                    "totalFatG INTEGER NOT NULL DEFAULT 0, " +
                    "category TEXT NOT NULL DEFAULT 'HOME', " +
                    "mealType TEXT DEFAULT NULL, " +
                    "createdAt INTEGER NOT NULL)"
            )
        }
    }

    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE daily_logs ADD COLUMN bloodSugarMmol REAL DEFAULT NULL")
        }
    }

    private val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE meal_entries ADD COLUMN loggedAt INTEGER DEFAULT NULL")
        }
    }

    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS blood_glucose_entries (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "timestamp INTEGER NOT NULL, " +
                    "valueMmolL REAL NOT NULL, " +
                    "source TEXT NOT NULL DEFAULT 'HC')"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FatLossDatabase {
        return Room.databaseBuilder(
            context,
            FatLossDatabase::class.java,
            "fatloss_track.db"
        ).addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18).build()
    }

    @Provides fun provideWeightDao(db: FatLossDatabase): WeightDao = db.weightDao()
    @Provides fun provideMealDao(db: FatLossDatabase): MealDao = db.mealDao()
    @Provides fun provideGoalDao(db: FatLossDatabase): GoalDao = db.goalDao()
    @Provides fun provideDailyLogDao(db: FatLossDatabase): DailyLogDao = db.dailyLogDao()
    @Provides fun provideInsightDao(db: FatLossDatabase): InsightDao = db.insightDao()
    @Provides fun provideChatMessageDao(db: FatLossDatabase): ChatMessageDao = db.chatMessageDao()
    @Provides fun provideAiUsageDao(db: FatLossDatabase): AiUsageDao = db.aiUsageDao()
    @Provides fun provideBookmarkedMealDao(db: FatLossDatabase): BookmarkedMealDao = db.bookmarkedMealDao()
    @Provides fun provideBloodGlucoseDao(db: FatLossDatabase): BloodGlucoseDao = db.bloodGlucoseDao()

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
