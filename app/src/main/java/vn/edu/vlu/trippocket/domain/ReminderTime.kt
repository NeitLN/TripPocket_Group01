package vn.edu.vlu.trippocket.domain

import java.time.LocalDate
import java.time.ZoneId

/**
 * When an activity reminder fires (I4, docs/ARCHITECTURE.md "Thời gian"). Owner: Bảo.
 *
 * Activity times are floating local time: "09:00 on day 2" is 09:00 on the wall clock of the
 * zone the phone is in now, so the zone is passed in and the result is worked out again after a
 * time zone change (ReminderReconciler).
 */
object ReminderTime {

    /**
     * Epoch millis of (trip start date + [dayIndex]) at [startMinuteOfDay] in [zoneId], minus
     * [minutesBefore]. The lead time may cross midnight into the previous day. A wall time that
     * does not exist in [zoneId] (a daylight saving gap) moves forward by the length of the gap.
     */
    fun triggerAtMillis(
        startDateEpochDay: Long,
        dayIndex: Int,
        startMinuteOfDay: Int,
        minutesBefore: Int,
        zoneId: ZoneId,
    ): Long = LocalDate.ofEpochDay(startDateEpochDay + dayIndex)
        .atTime(startMinuteOfDay / 60, startMinuteOfDay % 60)
        .atZone(zoneId)
        .minusMinutes(minutesBefore.toLong())
        .toInstant()
        .toEpochMilli()
}
