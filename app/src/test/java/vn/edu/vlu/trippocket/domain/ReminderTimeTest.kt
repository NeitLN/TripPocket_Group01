package vn.edu.vlu.trippocket.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** I4: when a reminder fires, from floating local time (docs/ARCHITECTURE.md "Thời gian"). Owner: Bảo. */
class ReminderTimeTest {

    private val vietnam = ZoneId.of("Asia/Ho_Chi_Minh")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val newYork = ZoneId.of("America/New_York")
    private val start = LocalDate.of(2026, 11, 3)

    private fun millis(at: LocalDateTime, zone: ZoneId) = at.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun dayIndexAndStartTime_minusTheLeadTime() {
        // Day 2 (index 1) at 09:00, 30 minutes before.
        val trigger = ReminderTime.triggerAtMillis(start.toEpochDay(), 1, 9 * 60, 30, vietnam)

        assertEquals(millis(LocalDateTime.of(2026, 11, 4, 8, 30), vietnam), trigger)
    }

    @Test
    fun noLeadTime_firesAtTheStartTime() {
        val trigger = ReminderTime.triggerAtMillis(start.toEpochDay(), 0, 14 * 60 + 45, 0, vietnam)

        assertEquals(millis(LocalDateTime.of(2026, 11, 3, 14, 45), vietnam), trigger)
    }

    @Test
    fun leadTimeCrossesMidnight_firesOnThePreviousEvening() {
        // A 00:15 night bus on day 3, reminded 45 minutes before: 23:30 on day 2.
        val trigger = ReminderTime.triggerAtMillis(start.toEpochDay(), 2, 15, 45, vietnam)

        assertEquals(millis(LocalDateTime.of(2026, 11, 4, 23, 30), vietnam), trigger)
    }

    @Test
    fun leadTimeBeforeTheFirstDay_firesTheDayBeforeTheTrip() {
        val trigger = ReminderTime.triggerAtMillis(start.toEpochDay(), 0, 6 * 60, 24 * 60, vietnam)

        assertEquals(millis(LocalDateTime.of(2026, 11, 2, 6, 0), vietnam), trigger)
    }

    @Test
    fun anotherTimeZone_keepsTheWallClockTime() {
        // Floating time: 09:00 stays 09:00 on the phone's clock, which is two hours earlier in Tokyo.
        val inVietnam = ReminderTime.triggerAtMillis(start.toEpochDay(), 0, 9 * 60, 0, vietnam)
        val inTokyo = ReminderTime.triggerAtMillis(start.toEpochDay(), 0, 9 * 60, 0, tokyo)

        assertEquals(millis(LocalDateTime.of(2026, 11, 3, 9, 0), tokyo), inTokyo)
        assertEquals(2 * 60 * 60 * 1000L, inVietnam - inTokyo)
    }

    @Test
    fun leadTimeAcrossADaylightSavingChange_countsRealMinutes() {
        // New York falls back on 1 Nov 2026 (01:00-02:00 happens twice). The lead time counts real
        // minutes, so the alarm is exactly two hours before 03:00.
        val day = LocalDate.of(2026, 11, 1)
        val trigger = ReminderTime.triggerAtMillis(day.toEpochDay(), 0, 3 * 60, 120, newYork)

        val threeAm = millis(LocalDateTime.of(2026, 11, 1, 3, 0), newYork)
        assertEquals(threeAm - 120 * 60 * 1000L, trigger)
    }

    @Test
    fun startTimeInADaylightSavingGap_movesForward() {
        // New York springs forward at 02:00 on 8 Mar 2026: 02:30 does not exist and becomes 03:30 EDT.
        val day = LocalDate.of(2026, 3, 8)
        val trigger = ReminderTime.triggerAtMillis(day.toEpochDay(), 0, 2 * 60 + 30, 0, newYork)

        assertEquals(millis(LocalDateTime.of(2026, 3, 8, 3, 30), newYork), trigger)
    }
}
