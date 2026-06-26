package com.medmonitoring.core.feature

import com.medmonitoring.core.domain.model.MedicationStatus
import com.medmonitoring.core.domain.model.UserRecord

data class MedicationFeatureSet(
    val recordsCount: Int,
    val takenCount: Int,
    val missedCount: Int,
    val averageMetricAfterTaken: Int?,
    val averageMetricAfterMissed: Int?
)

class FeatureBuilder {
    fun build(records: List<UserRecord>, metricKey: String): MedicationFeatureSet {
        val taken = records.filter { it.medicationStatus == MedicationStatus.TAKEN }
        val missed = records.filter { it.medicationStatus == MedicationStatus.MISSED }
        return MedicationFeatureSet(
            recordsCount = records.size,
            takenCount = taken.size,
            missedCount = missed.size,
            averageMetricAfterTaken = taken.mapNotNull { it.metricValue(metricKey) }.avg(),
            averageMetricAfterMissed = missed.mapNotNull { it.metricValue(metricKey) }.avg()
        )
    }

    private fun List<Int>.avg(): Int? = if (isEmpty()) null else average().toInt()
    private fun UserRecord.metricValue(metricKey: String): Int? =
        measurements.firstOrNull { it.key == metricKey }?.value?.toInt()
}
