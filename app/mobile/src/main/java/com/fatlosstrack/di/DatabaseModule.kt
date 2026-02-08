package com.fatlosstrack.di

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FatLossDatabase {
        return Room.databaseBuilder(
            context,
            FatLossDatabase::class.java,
            "fatloss_track.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides fun provideWeightDao(db: FatLossDatabase): WeightDao = db.weightDao()
    @Provides fun provideMealDao(db: FatLossDatabase): MealDao = db.mealDao()
    @Provides fun provideGoalDao(db: FatLossDatabase): GoalDao = db.goalDao()
    @Provides fun provideDailyLogDao(db: FatLossDatabase): DailyLogDao = db.dailyLogDao()
    @Provides fun provideInsightDao(db: FatLossDatabase): InsightDao = db.insightDao()

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
