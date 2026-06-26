package com.medmonitoring

import com.medmonitoring.core.analytics.AdherenceRule
import com.medmonitoring.core.analytics.AnalyticsEngine
import com.medmonitoring.core.analytics.ExtremeValueRule
import com.medmonitoring.core.analytics.FrequentTagRule
import com.medmonitoring.core.domain.model.AnalysisLevel
import com.medmonitoring.core.domain.model.AnalyticsThresholds
import com.medmonitoring.core.domain.model.DataQuality
import com.medmonitoring.core.domain.model.DimensionSpec
import com.medmonitoring.core.domain.model.Measurement
import com.medmonitoring.core.domain.model.MedicationStatus
import com.medmonitoring.core.domain.model.MetricSpec
import com.medmonitoring.core.domain.model.ProgramAnalyticsSchema
import com.medmonitoring.core.domain.model.RecordDimension
import com.medmonitoring.core.domain.model.RecordSource
import com.medmonitoring.core.domain.model.SourceType
import com.medmonitoring.core.domain.model.StatisticRole
import com.medmonitoring.core.domain.model.UserRecord
import com.medmonitoring.program.bloodpressure.BloodPressureDefinitions
import com.medmonitoring.program.bloodpressure.BloodPressureProgramModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AnalyticsRefactorTest {
    @Test
    fun testBloodPressureMapping() {
        val mapped = BloodPressureProgramModule.recordMapper.map(
            healthRecord(
                medicationStatus = MedicationStatus.TAKEN,
                healthyTags = listOf("Walking"),
                symptomTags = listOf("Headache"),
                otherMedicationTags = listOf("Aspirin")
            )
        )

        assertEquals(BloodPressureDefinitions.program.programId, mapped.programId)
        assertTrue(mapped.measurements.any { it.key == "systolic" && it.value == 126.0 })
        assertTrue(mapped.events.any { it.key == "medication" && it.status == "taken" })
        assertTrue(mapped.dimensions.any { it.group == "healthy" && it.label == "Walking" })
        assertTrue(mapped.dimensions.any { it.group == "symptoms" && it.label == "Headache" })
        assertTrue(mapped.dimensions.any { it.group == "other_medications" && it.label == "Aspirin" })
        assertTrue(mapped.dimensions.any { it.group == "time_of_day" })
    }

    @Test
    fun testProgramWithoutMedication() {
        val schema = ProgramAnalyticsSchema(
            metrics = listOf(MetricSpec("steps", "Steps", "count")),
            actions = emptyList(),
            tagGroups = emptyList(),
            rules = listOf(AdherenceRule(actionKey = "medication", positiveStatus = "taken", negativeStatus = "missed")),
            thresholds = AnalyticsThresholds(minRecordsForFindings = 3)
        )

        val analytics = AnalyticsEngine().calculate((1..5).map { activityRecord("r$it") }, schema)

        assertFalse(analytics.allFindings.any { it.sourceRuleId.startsWith("adherence") })
        assertFalse(analytics.dashboardMetrics.any { it.id == "dashboard_adherence" })
    }

    @Test
    fun testAnalysisLevels() {
        val engine = AnalyticsEngine()
        val schema = ProgramAnalyticsSchema(metrics = emptyList(), actions = emptyList(), tagGroups = emptyList(), rules = emptyList())

        assertEquals(AnalysisLevel.NONE, engine.calculate(emptyList(), schema).analysisLevel)
        assertEquals(AnalysisLevel.BASIC_3_9, engine.calculate((1..3).map { activityRecord("r$it") }, schema).analysisLevel)
        assertEquals(AnalysisLevel.COMPARATIVE_10_30, engine.calculate((1..10).map { activityRecord("r$it") }, schema).analysisLevel)
        assertEquals(AnalysisLevel.ADVANCED_31_PLUS, engine.calculate((1..31).map { activityRecord("r$it") }, schema).analysisLevel)
    }

    @Test
    fun testShowcaseFindingsLimit() {
        val groups = (1..12).map { "group_$it" }
        val schema = ProgramAnalyticsSchema(
            metrics = emptyList(),
            actions = emptyList(),
            tagGroups = groups.map { DimensionSpec(it, it) },
            rules = groups.map { FrequentTagRule(it) },
            thresholds = AnalyticsThresholds(minRecordsForFindings = 3, minOccurrencesForTag = 3)
        )
        val records = (1..5).map { index ->
            UserRecord(
                id = "r$index",
                programId = "test",
                timestamp = Instant.now(),
                measurements = emptyList(),
                events = emptyList(),
                dimensions = groups.map { RecordDimension(it, "common", "Common") },
                quality = DataQuality(),
                source = RecordSource(SourceType.MANUAL),
                note = null
            )
        }

        val analytics = AnalyticsEngine().calculate(records, schema)

        assertEquals(10, analytics.showcaseFindings.size)
        assertEquals(12, analytics.allFindings.size)
    }

    @Test
    fun testNoFalseRisks() {
        val schema = ProgramAnalyticsSchema(
            metrics = listOf(MetricSpec("systolic", "SYS", "mmHg", role = StatisticRole.Primary)),
            actions = emptyList(),
            tagGroups = emptyList(),
            rules = listOf(ExtremeValueRule(metricKey = "systolic", mode = "highest")),
            thresholds = AnalyticsThresholds(minRecordsForFindings = 3)
        )

        val analytics = AnalyticsEngine().calculate((1..5).map { activityRecord("r$it", 190.0) }, schema)

        assertTrue(analytics.allFindings.isEmpty())
    }

    private fun healthRecord(
        medicationStatus: MedicationStatus = MedicationStatus.NOT_RECORDED,
        healthyTags: List<String> = emptyList(),
        symptomTags: List<String> = emptyList(),
        otherMedicationTags: List<String> = emptyList()
    ) = UserRecord(
        id = "bp1",
        timestamp = Instant.parse("2026-06-16T08:00:00Z"),
        medicationName = "Lisinopril",
        medicationStatus = medicationStatus,
        doseValue = 10.0,
        doseUnit = "mg",
        measurements = listOf(
            Measurement("systolic", 126.0, "mmHg"),
            Measurement("diastolic", 80.0, "mmHg"),
            Measurement("pulse", 72.0, "bpm")
        ),
        healthyTags = healthyTags,
        unhealthyTags = emptyList(),
        symptomTags = symptomTags,
        otherMedicationTags = otherMedicationTags,
        sideEffectTags = emptyList(),
        customTags = emptyList(),
        note = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun activityRecord(id: String, value: Double = 1000.0) = UserRecord(
        id = id,
        programId = "activity",
        timestamp = Instant.now(),
        measurements = listOf(Measurement("sys", value, "mmHg")),
        events = emptyList(),
        dimensions = emptyList(),
        quality = DataQuality(),
        source = RecordSource(SourceType.MANUAL),
        note = null
    )
}
