package com.medmonitoring.core.ingestion

import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import com.medmonitoring.core.domain.model.HealthConnectMapping
import com.medmonitoring.core.domain.model.HealthConnectRecordType
import com.medmonitoring.core.domain.model.RawSourceEvent
import com.medmonitoring.core.domain.model.SourceType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import com.medmonitoring.core.normalization.SensorRuleEvaluator
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectMapper @Inject constructor() {
    fun map(record: Record, mappings: List<HealthConnectMapping>): RawSourceEvent? {
        val type = record.recordType() ?: return null
        val mapping = mappings.firstOrNull { it.recordType == type } ?: return null
        val values = record.values()
        val timestamp = record.timestamp()
        val sourceId = record.metadata.id
        val ruleValues = values.mapKeys { (field, _) -> "hc.${type.name.lowercase()}.$field" }
        val contextTags = SensorRuleEvaluator.evaluate(ruleValues, mapping.rules).tags
        val payload = buildJsonObject {
            put("id", stableEventId(sourceId))
            put("sourceRecordId", sourceId)
            put("timestamp", timestamp.toEpochMilli())
            mapping.metricMappings.forEach { (sourceField, metricId) ->
                values[sourceField]?.let { put(metricId, it) }
            }
            put("contextOnly", mapping.role.name == "CONTEXT_TAG")
            put("contextTags", buildJsonArray {
                contextTags.forEach { add(it.label) }
            })
        }
        return RawSourceEvent(
            id = stableEventId(sourceId),
            sourceType = SourceType.HEALTH_CONNECT,
            payloadJson = payload.toString(),
            capturedAt = Instant.now(),
            sourceTimestamp = timestamp,
            schemaVersion = 1,
            error = null,
            sourceRecordId = sourceId
        )
    }

    fun stableEventId(sourceRecordId: String) = HealthConnectIds.EVENT_PREFIX + sourceRecordId

    private fun Record.recordType() = when (this) {
        is BloodPressureRecord -> HealthConnectRecordType.BLOOD_PRESSURE
        is HeartRateRecord -> HealthConnectRecordType.HEART_RATE
        is BloodGlucoseRecord -> HealthConnectRecordType.BLOOD_GLUCOSE
        is StepsRecord -> HealthConnectRecordType.STEPS
        is WeightRecord -> HealthConnectRecordType.WEIGHT
        is ExerciseSessionRecord -> HealthConnectRecordType.EXERCISE
        is SleepSessionRecord -> HealthConnectRecordType.SLEEP
        else -> null
    }

    private fun Record.values(): Map<String, Double> = when (this) {
        is BloodPressureRecord -> mapOf(
            "systolic" to systolic.inMillimetersOfMercury,
            "diastolic" to diastolic.inMillimetersOfMercury
        )
        is HeartRateRecord -> mapOf("beatsPerMinute" to samples.map { it.beatsPerMinute }.average())
        is BloodGlucoseRecord -> mapOf("level" to level.inMillimolesPerLiter)
        is StepsRecord -> mapOf("count" to count.toDouble())
        is WeightRecord -> mapOf("weight" to weight.inKilograms)
        is ExerciseSessionRecord -> mapOf("durationMinutes" to Duration.between(startTime, endTime).toMinutes().toDouble())
        is SleepSessionRecord -> mapOf("durationHours" to Duration.between(startTime, endTime).toMinutes() / 60.0)
        else -> emptyMap()
    }

    private fun Record.timestamp(): Instant = when (this) {
        is BloodPressureRecord -> time
        is HeartRateRecord -> startTime
        is BloodGlucoseRecord -> time
        is StepsRecord -> startTime
        is WeightRecord -> time
        is ExerciseSessionRecord -> startTime
        is SleepSessionRecord -> startTime
        else -> Instant.now()
    }
}
