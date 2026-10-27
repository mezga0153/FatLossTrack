package com.fatlosstrack.data.reminders

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/** Pure time math for daily reminders, kept separate so it can be unit-tested. */
object ReminderTiming {

    /**
     * Runs this late are dropped instead of notifying, e.g. work that was
     * queued while the phone was off and flushes after an evening reboot.
     */
    val MAX_LATENESS: Duration = Duration.ofHours(2)

    fun timeOf(minutes: Int): LocalTime = LocalTime.of(minutes / 60, minutes % 60)

    /**
     * Time from [now] until the next occurrence of [minutes] after midnight.
     * Always strictly in the future, so a run never re-triggers itself.
     * Uses zoned times so a DST switch doesn't shift the reminder by an hour.
     */
    fun delayUntilNext(now: ZonedDateTime, minutes: Int): Duration {
        var next = now.toLocalDate().atTime(timeOf(minutes)).atZone(now.zone)
        if (!next.isAfter(now)) {
            next = now.toLocalDate().plusDays(1).atTime(timeOf(minutes)).atZone(now.zone)
        }
        return Duration.between(now, next)
    }

    /** True when [now] is at or shortly after today's [minutes] (within [MAX_LATENESS]). */
    fun isOnTime(now: ZonedDateTime, minutes: Int): Boolean {
        val target = now.toLocalDate().atTime(timeOf(minutes)).atZone(now.zone)
        // One minute of slack for clock adjustments between scheduling and running
        return !now.isBefore(target.minusMinutes(1)) && !now.isAfter(target.plus(MAX_LATENESS))
    }
}
