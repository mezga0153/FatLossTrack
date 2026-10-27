package com.fatlosstrack.data.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fatlosstrack.R
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.WeightDao
import com.fatlosstrack.domain.TdeeCalculator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * One daily run of a [ReminderType]. Queues tomorrow's run, then checks the
 * logs for today and posts a notification only if the reminder still applies.
 * Reads Room + DataStore only — no AI calls, no network.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val preferencesManager: PreferencesManager,
    private val mealDao: MealDao,
    private val dailyLogDao: DailyLogDao,
    private val weightDao: WeightDao,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val appLogger: AppLogger,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_TYPE = "reminder_type"
    }

    /** Title + body of a reminder notification. */
    private data class Message(val title: String, val text: String)

    override suspend fun doWork(): Result {
        val type = ReminderType.fromId(inputData.getString(KEY_TYPE)) ?: return Result.success()
        if (!preferencesManager.reminderEnabled(type.id).first()) return Result.success()

        // Queue tomorrow's run first so a failure below can't break the daily chain
        scheduler.scheduleNextRun(type)

        val minutes = preferencesManager.reminderMinutes(type.id, type.defaultMinutes).first()
        if (!ReminderTiming.isOnTime(ZonedDateTime.now(), minutes)) {
            appLogger.log("REMINDER", "${type.id}: skipped, ran too late")
            return Result.success()
        }

        try {
            // No goal yet means onboarding isn't done — nothing to remind about
            if (preferencesManager.startDate.first() == null) return Result.success()

            val message = buildMessage(type, LocalDate.now())
            if (message == null) {
                appLogger.log("REMINDER", "${type.id}: nothing to remind")
            } else {
                notifier.show(type, message.title, message.text)
                appLogger.log("REMINDER", "${type.id}: notified — ${message.title}")
            }
        } catch (e: Exception) {
            appLogger.error("REMINDER", "${type.id} failed", e)
        }
        return Result.success()
    }

    /** Returns the notification for [type] on [today], or null when it doesn't apply. */
    private suspend fun buildMessage(type: ReminderType, today: LocalDate): Message? = when (type) {
        ReminderType.MEAL_GAP -> {
            val meals = mealDao.getMealsForDate(today).first()
            if (meals.isNotEmpty()) {
                null
            } else {
                Message(
                    applicationContext.getString(R.string.notif_meal_gap_title),
                    applicationContext.getString(R.string.notif_meal_gap_text),
                )
            }
        }

        ReminderType.WEIGH_IN -> {
            // Health Connect weights land in both tables on sync, manual ones in daily_logs
            val hasWeight = dailyLogDao.getForDate(today)?.weightKg != null ||
                weightDao.getEntriesSince(today).first().any { it.date == today }
            if (hasWeight) {
                null
            } else {
                Message(
                    applicationContext.getString(R.string.notif_weigh_in_title),
                    applicationContext.getString(R.string.notif_weigh_in_text),
                )
            }
        }

        ReminderType.EVENING_BUDGET -> buildBudgetMessage(today)
    }

    /**
     * "640 kcal left" (or carbs in diabetes mode). Skipped when nothing is logged
     * yet — a full budget would be misleading, and the meal reminder covers it.
     */
    private suspend fun buildBudgetMessage(today: LocalDate): Message? {
        val meals = mealDao.getMealsForDate(today).first()
        if (meals.isEmpty()) return null

        val ctx = applicationContext
        if (preferencesManager.goalType.first() == "diabetes") {
            val limit = preferencesManager.maxCarbsPerDay.first()
            val eaten = meals.sumOf { it.totalCarbsG }
            val left = limit - eaten
            val title = if (left >= 0) {
                ctx.getString(R.string.notif_budget_carbs_left, left)
            } else {
                ctx.getString(R.string.notif_budget_carbs_over, -left)
            }
            return Message(title, ctx.getString(R.string.notif_budget_carbs_eaten, eaten, limit))
        }

        val target = TdeeCalculator.dailyTargetOrNull(
            weightKg = preferencesManager.startWeight.first(),
            heightCm = preferencesManager.heightCm.first(),
            age = preferencesManager.age.first(),
            sex = preferencesManager.sex.first(),
            activityLevel = preferencesManager.activityLevel.first(),
            weeklyRateKg = preferencesManager.weeklyRate.first(),
        ) ?: return null // profile incomplete — no target to compare against
        val eaten = meals.sumOf { it.totalKcal }
        val left = target - eaten
        val title = if (left >= 0) {
            ctx.getString(R.string.notif_budget_kcal_left, left)
        } else {
            ctx.getString(R.string.notif_budget_kcal_over, -left)
        }
        return Message(title, ctx.getString(R.string.notif_budget_kcal_eaten, eaten, target))
    }
}
