package com.medmonitoring

import com.medmonitoring.core.analytics.AnalyticsEngine
import com.medmonitoring.core.domain.model.DataQuality
import com.medmonitoring.core.domain.model.ActionEvent
import com.medmonitoring.core.domain.model.FindingSeverity
import com.medmonitoring.core.domain.model.Observation
import com.medmonitoring.core.domain.model.RecordSource
import com.medmonitoring.core.domain.model.SourceType
import com.medmonitoring.core.domain.model.TagEntry
import com.medmonitoring.core.domain.model.UserRecord
import com.medmonitoring.program.bloodpressure.BloodPressureDefinitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BloodPressureAnalyticsTest {
    @Test
    fun adherenceIsCalculatedFromTakenAndMissedRecords() {
        val records = (1..15).map { record("t$it", "taken") } +
            (1..5).map { record("m$it", "missed") }

        val analytics = AnalyticsEngine().calculate(records, BloodPressureDefinitions.analyticsConfig)

        assertEquals("75", analytics.dashboardMetrics.first { it.id == "dashboard_adherence" }.value)
        assertEquals("dashboard_adherence", analytics.dashboardMetrics.first { it.id == "dashboard_adherence" }.labelKey)
        assertEquals("5", analytics.dashboardMetrics.first { it.id == "dashboard_missed_medication" }.value)
        assertEquals("dashboard_missed_medication", analytics.dashboardMetrics.first { it.id == "dashboard_missed_medication" }.labelKey)
        assertEquals("20", analytics.dashboardMetrics.first { it.id == "dashboard_records" }.value)
        assertEquals("dashboard_records", analytics.dashboardMetrics.first { it.id == "dashboard_records" }.labelKey)
    }

    @Test
    fun frequencyAnalyticsReturnsMostCommonTagsAsFindings() {
        val records = (1..10).map {
            record(
                id = "r$it",
                status = "taken",
                tags = listOfNotNull(
                    if (it <= 6) TagEntry("symptoms", "headache", "Headache") else null,
                    if (it <= 4) TagEntry("symptoms", "dry_cough", "Dry Cough") else null,
                    if (it <= 5) TagEntry("unhealthy", "poor_sleep", "Poor Sleep") else null
                )
            )
        }

        val analytics = AnalyticsEngine().calculate(records, BloodPressureDefinitions.analyticsConfig)

        assertTrue(analytics.allFindings.any {
            it.leftGroupLabel == "Headache" &&
                it.messageKey == "msg_frequent" &&
                it.titleKey == "finding_frequent_prefix" &&
                it.basisKey == "basis_records" &&
                it.leftGroupSize == 6
        })
        assertTrue(analytics.allFindings.any {
            it.leftGroupLabel == "Poor Sleep" &&
                it.messageKey == "msg_frequent" &&
                it.titleKey == "finding_frequent_prefix" &&
                it.basisKey == "basis_records" &&
                it.leftGroupSize == 5
        })
    }

    @Test
    fun findingsAppearAfterFiveBloodPressureRecords() {
        val records = (1..5).map {
            record(
                id = "r$it",
                status = if (it == 5) "missed" else "taken",
                tags = if (it <= 3) listOf(TagEntry("symptoms", "headache", "Headache")) else emptyList()
            )
        }

        val analytics = AnalyticsEngine().calculate(records, BloodPressureDefinitions.analyticsConfig)

        assertEquals("5", analytics.dashboardMetrics.first { it.id == "dashboard_records" }.value)
        assertTrue(analytics.findings.isNotEmpty())
        assertTrue(analytics.allFindings.any {
            it.leftGroupLabel == "Headache" &&
                it.messageKey == "msg_frequent" &&
                it.leftGroupSize == 3 &&
                it.recordCount == 5
        })
    }

    @Test
    fun symptomAndUnhealthyFindingsAreRiskColoredByAnalyticsResult() {
        val records = (1..5).map {
            record(
                id = "r$it",
                status = "taken",
                tags = listOfNotNull(
                    if (it <= 3) TagEntry("symptoms", "headache", "Headache") else null,
                    if (it <= 3) TagEntry("unhealthy", "poor_sleep", "Poor Sleep") else null,
                    if (it <= 3) TagEntry("healthy", "walking", "Walking") else null
                )
            )
        }

        val analytics = AnalyticsEngine().calculate(records, BloodPressureDefinitions.analyticsConfig)

        assertEquals(FindingSeverity.Risk, analytics.findings.first { it.sourceRuleId == "frequent_tag_symptoms" }.severity)
        assertEquals(FindingSeverity.Risk, analytics.findings.first { it.sourceRuleId == "frequent_tag_unhealthy" }.severity)
        assertEquals(FindingSeverity.Positive, analytics.findings.first { it.sourceRuleId == "frequent_tag_healthy" }.severity)
    }

    @Test
    fun riskFindingsArePrioritizedBeforeNeutralCards() {
        val records = (1..8).map {
            record(
                id = "r$it",
                status = if (it <= 4) "missed" else "taken",
                systolic = if (it <= 4) 140.0 else 124.0,
                tags = listOfNotNull(
                    if (it <= 5) TagEntry("other_medications", "aspirin", "Aspirin") else null,
                    if (it <= 5) TagEntry("symptoms", "headache", "Headache") else null
                )
            )
        }

        val analytics = AnalyticsEngine().calculate(records, BloodPressureDefinitions.analyticsConfig)

        assertEquals(FindingSeverity.Risk, analytics.findings.first().severity)
    }

    @Test
    fun actionComparisonUsesSeverityFromRuleResult() {
        val records = (1..8).map { record("t$it", "taken", systolic = 126.0) } +
            (1..8).map { record("m$it", "missed", systolic = 138.0) }

        val analytics = AnalyticsEngine().calculate(records, BloodPressureDefinitions.analyticsConfig)

        val comparison = analytics.allFindings.first { it.sourceRuleId == "systolic_by_medication_status" }
        assertEquals(FindingSeverity.Risk, comparison.severity)
        assertEquals("finding_comparison", comparison.titleKey)
        assertEquals("msg_comparison", comparison.messageKey)
        assertEquals("basis_comparison", comparison.basisKey)
        assertTrue(comparison.evidence.all { it.labelKey != null })
    }

    private fun record(
        id: String,
        status: String,
        systolic: Double = if (status == "missed") 138.0 else 126.0,
        tags: List<TagEntry> = emptyList()
    ) = UserRecord(
        id = id,
        programId = BloodPressureDefinitions.program.programId,
        timestamp = Instant.now(),
        measurements = listOf(
            Observation("systolic", systolic, "mmHg", group = "blood_pressure"),
            Observation("diastolic", 80.0, "mmHg", group = "blood_pressure"),
            Observation("pulse", 90.0, "bpm")
        ),
        events = listOf(ActionEvent("medication", "Lisinopril", status, if (status == "taken") 10.0 else null, "mg")),
        dimensions = tags,
        quality = DataQuality(),
        source = RecordSource(SourceType.MANUAL),
        note = null
    )
}
