package com.fatlosstrack.data.recap

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class WeeklyRecapSchedulerTest {

    private val zone = ZoneId.of("Europe/Ljubljana")
    private val sevenPm = 19 * 60

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(y, m, d, h, min), zone)

    @Test
    fun `later the same day when the time has not passed yet`() {
        // 2026-10-25 is a Sunday
        val next = WeeklyRecapScheduler.nextRun(at(2026, 10, 25, 10), DayOfWeek.SUNDAY, sevenPm)
        assertEquals(at(2026, 10, 25, 19), next)
    }

    @Test
    fun `next week when the time is now or has passed`() {
        assertEquals(
            at(2026, 11, 1, 19),
            WeeklyRecapScheduler.nextRun(at(2026, 10, 25, 19), DayOfWeek.SUNDAY, sevenPm),
        )
        assertEquals(
            at(2026, 11, 1, 19),
            WeeklyRecapScheduler.nextRun(at(2026, 10, 25, 21), DayOfWeek.SUNDAY, sevenPm),
        )
    }

    @Test
    fun `picks the coming weekday`() {
        // Wednesday → Monday of the following week
        val next = WeeklyRecapScheduler.nextRun(at(2026, 10, 21, 12), DayOfWeek.MONDAY, 8 * 60 + 30)
        assertEquals(at(2026, 10, 26, 8, 30), next)
    }

    @Test
    fun `keeps wall-clock time across a DST change`() {
        // Clocks go back on 2026-10-25 in Europe; a run a week earlier still lands on 19:00 local
        val next = WeeklyRecapScheduler.nextRun(at(2026, 10, 18, 19), DayOfWeek.SUNDAY, sevenPm)
        assertEquals(19, next.hour)
        assertEquals(at(2026, 10, 25, 19), next)
    }
}
