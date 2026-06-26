package com.medmonitoring.core.normalization

import com.medmonitoring.core.domain.model.Measurement
import com.medmonitoring.core.domain.model.NormalizedObservation
import com.medmonitoring.core.domain.model.RawSourceEvent
import com.medmonitoring.core.domain.model.RecordDimension
import com.medmonitoring.core.domain.model.RecordEvent
import com.medmonitoring.core.domain.model.SourceType
import com.medmonitoring.core.domain.model.UniversalProgramDefinition
import com.medmonitoring.core.domain.model.UserRecord
import com.medmonitoring.core.domain.repository.EventRepository
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Turns the append-only sample stream into the program's canonical slot records. */
@Singleton
class NormalizerService @Inject constructor(
    private val repository: EventRepository,
    private val program: UniversalProgramDefinition
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val zoneId: ZoneId get() = ZoneId.systemDefault()

    suspend fun processPendingEvents() {
        val pending = repository.getUnprocessedRawEvents()
        pending.filter { it.sourceType == SourceType.MANUAL }.forEach { event ->
            try {
                normalizeHealthRecord(event)?.let { mergeManual(it.record) }
                repository.markRawEventProcessed(event.id)
            } catch (error: Exception) {
                repository.markRawEventProcessed(event.id, error.message ?: "Manual normalization error")
            }
        }
        val automatic = pending.filter { it.sourceType != SourceType.MANUAL }
        if (automatic.isEmpty()) return
        try {
            if (program.autoCollection.recordSlots.isEmpty()) {
                // Programs without slots retain the existing window aggregation behaviour.
                AutomatedEventAggregator.aggregate(automatic, program.metricComponents, program.programId,
                    program.autoCollection.aggregationWindowMinutes).forEach { result ->
                    result.records.forEach { repository.replaceSlotRecord(it, result.eventIds.toSet()) }
                }
            } else {
                automatic.mapNotNull(::slotKey).distinct().forEach { rebuildSlot(it) }
            }
            automatic.forEach { repository.markRawEventProcessed(it.id) }
        } catch (error: Exception) {
            automatic.forEach { repository.markRawEventProcessed(it.id, error.message ?: "Automatic normalization error") }
        }
    }

    /** Called before an upstream deletion is discarded, so its former slot can be recomputed. */
    suspend fun deleteSourceRecord(sourceRecordId: String) {
        val keys = repository.getAllRawEvents().filter { it.sourceRecordId == sourceRecordId }
            .mapNotNull(::slotKey).distinct()
        repository.deleteBySourceRecordId(sourceRecordId)
        keys.forEach { rebuildSlot(it) }
    }

    private suspend fun mergeManual(record: UserRecord) {
        val key = slotKey(record.timestamp) ?: run {
            repository.upsertRecord(record)
            return
        }
        val existing = repository.getRecords().firstOrNull { it.matchesSlot(key) }
        val automatic = automaticRecordFor(key)
        val merged = record.copy(
            id = key.recordId,
            programId = program.programId,
            dimensions = (record.dimensions + slotDimension(key) + automatic?.dimensions.orEmpty() +
                autoKeysDimension(automatic?.measurements.orEmpty().filterNot { it.key in record.measurements.map(Measurement::key).toSet() }))
                .filterNot { it.group == "system" && it.key == "auto_measurements" && automatic == null }
                .distinctBy { "${it.group}:${it.key}" },
            measurements = mergeMeasurements(record.measurements, automatic?.measurements.orEmpty()),
            createdAt = existing?.createdAt ?: record.createdAt,
            updatedAt = Instant.now(),
            sourceType = SourceType.MANUAL
        )
        repository.replaceSlotRecord(merged, automaticContributors(key))
    }

    private suspend fun rebuildSlot(key: SlotKey) {
        val existing = repository.getRecords().firstOrNull { it.matchesSlot(key) }
        val automatic = automaticRecordFor(key)
        val contributors = automaticContributors(key)
        if (existing?.sourceType == SourceType.MANUAL) {
            val manualMeasurements = existing.measurements.filterNot { it.key in existing.autoMeasurementKeys() }
            val refreshed = existing.copy(
                measurements = mergeMeasurements(manualMeasurements, automatic?.measurements.orEmpty()),
                dimensions = (existing.dimensions.filterNot { it.group == "system" && it.key == "auto_measurements" } +
                    automatic?.dimensions.orEmpty() + autoKeysDimension(automatic?.measurements.orEmpty()
                        .filterNot { it.key in manualMeasurements.map(Measurement::key).toSet() }))
                    .filterNot { it.group == "system" && it.key == "auto_measurements" && automatic == null }
                    .distinctBy { "${it.group}:${it.key}" },
                updatedAt = Instant.now()
            )
            repository.replaceSlotRecord(refreshed, contributors)
        } else if (automatic != null) {
            repository.replaceSlotRecord(automatic, contributors)
        } else if (existing != null) {
            // An automatic-only slot disappears when its final source is deleted.
            repository.deleteRecord(existing.id)
        }
    }

    private suspend fun automaticRecordFor(key: SlotKey): UserRecord? {
        val events = repository.getAllRawEvents().filter { it.sourceType != SourceType.MANUAL && slotKey(it) == key }
        if (events.isEmpty()) return null
        val partial = AutomatedEventAggregator.aggregate(events, program.metricComponents, program.programId, 24 * 60)
            .flatMap { it.records }
        if (partial.isEmpty()) return null
        return partial.first().copy(
            id = key.recordId,
            timestamp = key.start,
            measurements = partial.flatMap { it.measurements }.groupBy { it.key }.map { (_, values) ->
                values.last().copy(value = values.map { it.value }.average())
            },
            dimensions = (partial.flatMap { it.dimensions } + slotDimension(key)).distinctBy { "${it.group}:${it.key}" },
            updatedAt = Instant.now()
        )
    }

    private suspend fun automaticContributors(key: SlotKey): Set<String> = repository.getAllRawEvents()
        .filter { it.sourceType != SourceType.MANUAL && slotKey(it) == key }
        .mapNotNull { it.sourceRecordId }.toSet()

    private fun mergeMeasurements(manual: List<Measurement>, automatic: List<Measurement>): List<Measurement> {
        val owned = manual.associateBy { it.key }
        return owned.values + automatic.filterNot { it.key in owned }
    }

    private fun slotKey(event: RawSourceEvent): SlotKey? = sampleTimestamp(event)?.let(::slotKey)
    private fun sampleTimestamp(event: RawSourceEvent): Instant? = runCatching {
        json.parseToJsonElement(event.payloadJson).jsonObject["timestamp"]?.jsonPrimitive?.longOrNull?.let(Instant::ofEpochMilli)
    }.getOrNull() ?: event.sourceTimestamp ?: event.capturedAt

    private fun slotKey(timestamp: Instant): SlotKey? {
        val slot = RecordSlotResolver.resolve(timestamp, program.autoCollection.recordSlots, zoneId) ?: return null
        val local = timestamp.atZone(zoneId)
        val date = if (slot.startHourInclusive > slot.endHourExclusive && local.hour < slot.endHourExclusive) local.toLocalDate().minusDays(1) else local.toLocalDate()
        val start = date.atTime(slot.startHourInclusive, 0).atZone(zoneId).toInstant()
        return SlotKey("slot:${program.programId}:$date:${slot.id}", slot.id, start)
    }

    private fun slotDimension(key: SlotKey) = RecordDimension("system", "record_slot", key.slotId)
    private fun autoKeysDimension(measurements: List<Measurement>) =
        RecordDimension("system", "auto_measurements", measurements.joinToString(",") { it.key })
    private fun UserRecord.autoMeasurementKeys(): Set<String> = dimensions.firstOrNull {
        it.group == "system" && it.key == "auto_measurements"
    }?.label?.split(',')?.filter(String::isNotBlank)?.toSet().orEmpty()
    private fun UserRecord.matchesSlot(key: SlotKey) = id == key.recordId ||
        (timestamp == key.start && dimensions.any { it.group == "system" && it.key == "record_slot" && it.label == key.slotId })
    private data class SlotKey(val recordId: String, val slotId: String, val start: Instant)

    private fun normalizeHealthRecord(event: RawSourceEvent): NormalizedObservation? {
        val root = json.parseToJsonElement(event.payloadJson).jsonObject
        if (root["contextOnly"]?.jsonPrimitive?.booleanOrNull == true) return null
        val timestamp = sampleTimestamp(event) ?: event.capturedAt
        return NormalizedObservation(UserRecord(
            id = root["id"]?.jsonPrimitive?.content ?: event.id,
            programId = root["programId"]?.jsonPrimitive?.contentOrNull ?: program.programId,
            timestamp = timestamp,
            measurements = root.measurementsFrom(),
            events = root.eventsFrom(), dimensions = root.dimensionsFrom(),
            note = root["note"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
            createdAt = root["createdAt"]?.jsonPrimitive?.longOrNull?.let(Instant::ofEpochMilli) ?: Instant.now(),
            updatedAt = Instant.now(), sourceType = SourceType.MANUAL
        ))
    }

    private fun JsonObject.measurementsFrom() = program.metricComponents.mapNotNull { metric ->
        (this["measurements"]?.jsonObject?.get(metric.id)?.jsonPrimitive?.doubleOrNull ?: this[metric.id]?.jsonPrimitive?.doubleOrNull)
            ?.let { Measurement(metric.id, it, metric.unit) }
    }
    private fun JsonObject.eventsFrom(): List<RecordEvent> {
        val explicit = this["events"]?.jsonArray?.mapNotNull { element ->
        element.jsonObject.let { RecordEvent(it["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
            it["name"]?.jsonPrimitive?.contentOrNull.orEmpty(), it["status"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            it["amount"]?.jsonPrimitive?.doubleOrNull, it["unit"]?.jsonPrimitive?.contentOrNull) }
        }.orEmpty()
        if (explicit.isNotEmpty()) return explicit
        val status = this["medicationStatus"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
        if (status == "NOT_RECORDED") return emptyList()
        return listOf(RecordEvent("medication", this["medicationName"]?.jsonPrimitive?.contentOrNull.orEmpty(), status,
            this["doseValue"]?.jsonPrimitive?.doubleOrNull, this["doseUnit"]?.jsonPrimitive?.contentOrNull))
    }
    private fun JsonObject.dimensionsFrom() = this["dimensions"]?.jsonArray?.mapNotNull { element ->
        element.jsonObject.let { RecordDimension(it["group"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
            it["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
            it["label"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null) }
    }.orEmpty()
}
