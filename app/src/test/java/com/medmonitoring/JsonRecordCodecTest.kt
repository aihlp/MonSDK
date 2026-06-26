package com.medmonitoring

import com.medmonitoring.core.domain.model.MedicationStatus
import com.medmonitoring.core.domain.model.Measurement
import com.medmonitoring.core.domain.model.UserRecord
import com.medmonitoring.core.reports.JsonRecordCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class JsonRecordCodecTest {
    @Test
    fun jsonRoundTripRestoresRecords() {
        val records = listOf(
            UserRecord(
                id = "r1",
                timestamp = Instant.parse("2026-05-30T16:02:00Z"),
                medicationName = "Lisinopril",
                medicationStatus = MedicationStatus.TAKEN,
                doseValue = 10.0,
                doseUnit = "mg",
                measurements = listOf(
                    Measurement("systolic", 120.0, "mmHg"),
                    Measurement("diastolic", 80.0, "mmHg"),
                    Measurement("pulse", 90.0, "bpm")
                ),
                healthyTags = listOf("Good Sleep"),
                unhealthyTags = emptyList(),
                symptomTags = listOf("blurred vision"),
                otherMedicationTags = emptyList(),
                sideEffectTags = listOf("Dry Cough"),
                customTags = emptyList(),
                note = "Morning record",
                createdAt = Instant.parse("2026-05-30T16:02:00Z"),
                updatedAt = Instant.parse("2026-05-30T16:02:00Z")
            )
        )

        val json = JsonRecordCodec.encode(records)
        val restored = JsonRecordCodec.decode(json)

        assertTrue(json.contains("medmonitor-records"))
        assertEquals(records, restored)
    }

    @Test
    fun legacyLisinoprilFormatRemainsImportable() {
        val current = JsonRecordCodec.encode(
            listOf(
                UserRecord(
                    id = "legacy",
                    timestamp = Instant.EPOCH,
                    medicationName = "Lisinopril",
                    medicationStatus = MedicationStatus.TAKEN,
                    doseValue = 10.0,
                    doseUnit = "mg",
                    measurements = listOf(
                        Measurement("systolic", 120.0, "mmHg"),
                        Measurement("diastolic", 80.0, "mmHg"),
                        Measurement("pulse", 70.0, "bpm")
                    ),
                    healthyTags = emptyList(),
                    unhealthyTags = emptyList(),
                    symptomTags = emptyList(),
                    otherMedicationTags = emptyList(),
                    sideEffectTags = emptyList(),
                    customTags = emptyList(),
                    note = null,
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH
                )
            )
        )
        val legacy = current.replace("medmonitor-records", "lisinopril-monitor-records")

        assertEquals("legacy", JsonRecordCodec.decode(legacy).single().id)
    }
}
