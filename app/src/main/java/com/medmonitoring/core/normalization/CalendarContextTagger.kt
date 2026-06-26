package com.medmonitoring.core.normalization

import java.time.Instant
import java.time.ZoneId

object CalendarContextTagger {
    fun tags(timestamp: Instant, zoneId: ZoneId = ZoneId.systemDefault()): Set<String> {
        val local = timestamp.atZone(zoneId)
        val season = when (local.monthValue) {
            12, 1, 2 -> "winter"
            3, 4, 5 -> "spring"
            6, 7, 8 -> "summer"
            else -> "autumn"
        }
        val dayType = if (local.dayOfWeek.value >= 6) "weekend" else "weekday"
        val timeSlot = when (local.hour) {
            in 5..10 -> "morning"
            in 11..16 -> "day"
            in 17..21 -> "evening"
            else -> "night"
        }
        return setOf(
            "context.calendar.day.${local.dayOfWeek.name.lowercase()}",
            "context.calendar.$dayType",
            "context.calendar.season.$season",
            "context.calendar.time.$timeSlot",
            "context.calendar.month.${local.monthValue}"
        )
    }
}
