package com.medmonitoring.core.normalization

import com.medmonitoring.core.domain.model.RecordSlotDefinition
import java.time.Instant
import java.time.ZoneId

object RecordSlotResolver {
    fun resolve(
        timestamp: Instant,
        slots: List<RecordSlotDefinition>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): RecordSlotDefinition? {
        val hour = timestamp.atZone(zoneId).hour
        return slots.firstOrNull { slot ->
            if (slot.startHourInclusive < slot.endHourExclusive) {
                hour in slot.startHourInclusive until slot.endHourExclusive
            } else {
                hour >= slot.startHourInclusive || hour < slot.endHourExclusive
            }
        }
    }
}
