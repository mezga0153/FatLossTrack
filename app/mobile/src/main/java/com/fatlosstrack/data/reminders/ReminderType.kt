package com.fatlosstrack.data.reminders

/**
 * The daily logging reminders. Each one runs once a day at a user-set time
 * and only notifies when its condition holds (see [ReminderWorker]).
 *
 * [id] is part of the DataStore keys and WorkManager unique-work names — don't rename it.
 */
enum class ReminderType(
    val id: String,
    /** Default time of day, in minutes after midnight. */
    val defaultMinutes: Int,
    val notificationId: Int,
) {
    /** Nothing logged for today yet. */
    MEAL_GAP("meal_gap", 15 * 60, 3601),

    /** No weight for today yet (manual or synced from Health Connect). */
    WEIGH_IN("weigh_in", 9 * 60, 3602),

    /** Calories (carbs in diabetes mode) left of today's target. */
    EVENING_BUDGET("evening_budget", 18 * 60, 3603);

    val workName: String get() = "reminder_$id"

    companion object {
        fun fromId(id: String?): ReminderType? = entries.firstOrNull { it.id == id }
    }
}
