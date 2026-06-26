package com.medmonitoring

import com.medmonitoring.core.normalization.CalendarContextTagger
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarContextTaggerTest {
    @Test
    fun createsStableCalendarContextIds() {
        val tags = CalendarContextTagger.tags(
            Instant.parse("2026-01-03T23:00:00Z"),
            ZoneId.of("UTC")
        )

        assertTrue("context.calendar.day.saturday" in tags)
        assertTrue("context.calendar.weekend" in tags)
        assertTrue("context.calendar.season.winter" in tags)
        assertTrue("context.calendar.time.night" in tags)
    }
}
