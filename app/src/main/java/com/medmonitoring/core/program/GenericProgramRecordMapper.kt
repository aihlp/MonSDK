package com.medmonitoring.core.program

import com.medmonitoring.core.analytics.ProgramRecordMapper
import com.medmonitoring.core.domain.model.DataQuality
import com.medmonitoring.core.domain.model.MedicationStatus
import com.medmonitoring.core.domain.model.RecordDimension
import com.medmonitoring.core.domain.model.RecordEvent
import com.medmonitoring.core.domain.model.RecordSource
import com.medmonitoring.core.domain.model.UniversalProgramDefinition
import com.medmonitoring.core.domain.model.UserRecord
import java.time.ZoneId

class GenericProgramRecordMapper(
    private val program: UniversalProgramDefinition
) : ProgramRecordMapper<UserRecord> {
    override fun map(record: UserRecord): UserRecord {
        val measurementById = record.measurements.associateBy { it.key }
        val qualityWarnings = buildList {
            program.metricComponents
                .filter { it.isRequired }
                .forEach { metric ->
                    if (measurementById[metric.id] == null) add("missing_${metric.id}")
                }
        }
        val configuredDimensionGroups = program.tagGroups.map { it.id }.toSet()
        val explicitDimensions = record.dimensions.filter { it.group in configuredDimensionGroups }
        return UserRecord(
            id = record.id,
            programId = program.programId,
            timestamp = record.timestamp,
            measurements = program.metricComponents.mapNotNull { metric ->
                measurementById[metric.id]?.copy(unit = metric.unit)
            },
            events = record.programEvents(),
            dimensions = buildList {
                if (explicitDimensions.isNotEmpty()) {
                    addAll(explicitDimensions)
                } else {
                    addAll(record.legacyDimensions(configuredDimensionGroups))
                }
                add(timeOfDayDimension(record))
            },
            quality = DataQuality(complete = qualityWarnings.isEmpty(), warnings = qualityWarnings),
            source = RecordSource(record.sourceType),
            note = record.note
        )
    }

    private fun timeOfDayDimension(record: UserRecord): RecordDimension {
        val hour = record.timestamp.atZone(ZoneId.systemDefault()).hour
        val label = when (hour) {
            in 5..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..21 -> "Evening"
            else -> "Night"
        }
        return RecordDimension("time_of_day", label.toKey(), label)
    }

    private fun List<String>.toDimensions(group: String): List<RecordDimension> {
        return map { label -> RecordDimension(group, label.toKey(), label) }
    }

    private fun UserRecord.legacyDimensions(configuredGroups: Set<String>): List<RecordDimension> =
        buildList {
            addAll(healthyTags.toDimensions("healthy"))
            addAll(unhealthyTags.toDimensions("unhealthy"))
            addAll((symptomTags + sideEffectTags).distinct().toDimensions("symptoms"))
            addAll(otherMedicationTags.toDimensions("other_medications"))
            addAll(customTags.toDimensions("custom"))
        }.filter { it.group in configuredGroups || it.group == "custom" }

    private fun String.toKey(): String = lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    private fun UserRecord.programEvents(): List<RecordEvent> {
        val configuredKeys = program.eventInputs.map { it.key }.toSet() +
            program.graphDefinition.eventMarkers.map { it.eventKey }.toSet()
        val explicitEvents = events.filter { it.key in configuredKeys }
        if (explicitEvents.isNotEmpty()) return explicitEvents
        if (medicationStatus == MedicationStatus.NOT_RECORDED) return emptyList()
        return listOf(
            RecordEvent(
                key = "medication",
                name = medicationName,
                status = medicationStatus.name.lowercase(),
                amount = doseValue,
                unit = doseUnit
            )
        )
    }
}
