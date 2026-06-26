package com.medmonitoring

import com.medmonitoring.core.domain.model.RecordSlotDefinition
import com.medmonitoring.core.normalization.RecordSlotResolver
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class RecordSlotResolverTest {
    private val slots = listOf(
        RecordSlotDefinition("morning", 5, 11),
        RecordSlotDefinition("day", 11, 17),
        RecordSlotDefinition("evening", 17, 22),
        RecordSlotDefinition("night", 22, 5)
    )

    @Test
    fun resolvesConfiguredDayAndOvernightSlots() {
        assertEquals("morning", slot("2026-06-26T05:00:00Z"))
        assertEquals("day", slot("2026-06-26T11:00:00Z"))
        assertEquals("evening", slot("2026-06-26T17:00:00Z"))
        assertEquals("night", slot("2026-06-26T22:00:00Z"))
        assertEquals("night", slot("2026-06-26T04:59:00Z"))
    }

    private fun slot(timestamp: String): String? =
        RecordSlotResolver.resolve(Instant.parse(timestamp), slots, ZoneId.of("UTC"))?.id
}
