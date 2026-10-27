package com.fatlosstrack.data.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTimingTest {

    private val zone = ZoneId.of("Europe/Ljubljana")
    private fun at(text: String): ZonedDateTime = LocalDateTime.parse(text).atZone(zone)

    private val threePm = 15 * 60

    // ── delayUntilNext ───────────────────────────────────────────────────

    @Test
    fun `delayUntilNext targets later today when the time hasn't passed`() {
        assertEquals(Duration.ofHours(6), ReminderTiming.delayUntilNext(at("2026-10-20T09:00"), threePm))
    }

    @Test
    fun `delayUntilNext targets tomorrow once the time has passed`() {
        assertEquals(Duration.ofHours(23), ReminderTiming.delayUntilNext(at("2026-10-20T16:00"), threePm))
    }

    @Test
    fun `delayUntilNext at exactly the reminder time waits a full day`() {
        // A worker running right on time must not re-trigger itself
        assertEquals(Duration.ofDays(1), ReminderTiming.delayUntilNext(at("2026-10-20T15:00"), threePm))
    }

    @Test
    fun `delayUntilNext keeps the wall-clock time across the autumn DST switch`() {
        // Clocks go back on 2026-10-25, so that day is 25 hours long
        val delay = ReminderTiming.delayUntilNext(at("2026-10-24T16:00"), threePm)
        assertEquals(Duration.ofHours(24), delay)
        assertEquals(at("2026-10-25T15:00"), at("2026-10-24T16:00").plus(delay))
    }

    @Test
    fun `delayUntilNext handles midnight`() {
        assertEquals(Duration.ofMinutes(30), ReminderTiming.delayUntilNext(at("2026-10-20T23:30"), 0))
    }

    // ── isOnTime ─────────────────────────────────────────────────────────

    @Test
    fun `isOnTime accepts runs at or shortly after the reminder time`() {
        assertTrue(ReminderTiming.isOnTime(at("2026-10-20T15:00"), threePm))
        assertTrue(ReminderTiming.isOnTime(at("2026-10-20T15:45"), threePm))
        assertTrue(ReminderTiming.isOnTime(at("2026-10-20T17:00"), threePm))
    }

    @Test
    fun `isOnTime rejects runs more than two hours late`() {
        assertFalse(ReminderTiming.isOnTime(at("2026-10-20T17:01"), threePm))
    }

    @Test
    fun `isOnTime rejects runs that are early or roll over into the next day`() {
        assertFalse(ReminderTiming.isOnTime(at("2026-10-20T14:00"), threePm))
        // 23:30 reminder flushed at 00:30 the next day belongs to yesterday
        assertFalse(ReminderTiming.isOnTime(at("2026-10-21T00:30"), 23 * 60 + 30))
    }
}
