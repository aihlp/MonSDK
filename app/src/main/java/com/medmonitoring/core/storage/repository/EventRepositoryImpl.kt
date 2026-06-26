package com.medmonitoring.core.storage.repository

import com.medmonitoring.core.domain.model.*
import com.medmonitoring.core.domain.repository.EventRepository
import com.medmonitoring.core.storage.db.MedDatabase
import com.medmonitoring.core.storage.entity.*
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepositoryImpl @Inject constructor(
    private val db: MedDatabase
) : EventRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun insertRawEvent(event: RawSourceEvent) {
        db.rawEventDao().insert(
            RawEventEntity(
                id = event.id,
                sourceType = event.sourceType.name,
                payloadJson = event.payloadJson,
                capturedAt = event.capturedAt.toEpochMilli(),
                sourceTimestamp = event.sourceTimestamp?.toEpochMilli(),
                schemaVersion = event.schemaVersion,
                error = event.error,
                sourceRecordId = event.sourceRecordId,
                processed = false
            )
        )
    }

    override suspend fun getUnprocessedRawEvents(): List<RawSourceEvent> {
        return db.rawEventDao().getUnprocessed().map(::toRawEvent)
    }

    override suspend fun getAllRawEvents(): List<RawSourceEvent> =
        db.rawEventDao().getAll().map(::toRawEvent)

    private fun toRawEvent(it: RawEventEntity) =
            RawSourceEvent(
                id = it.id,
                sourceType = SourceType.valueOf(it.sourceType),
                payloadJson = it.payloadJson,
                capturedAt = Instant.ofEpochMilli(it.capturedAt),
                sourceTimestamp = it.sourceTimestamp?.let(Instant::ofEpochMilli),
                schemaVersion = it.schemaVersion,
                error = it.error,
                sourceRecordId = it.sourceRecordId
            )

    override suspend fun markRawEventProcessed(eventId: String, error: String?) {
        db.rawEventDao().updateProcessedState(eventId, true, error)
    }

    override suspend fun upsertRecord(record: UserRecord) {
        db.userRecordDao().upsert(record.toEntity())
    }

    override suspend fun replaceRecords(records: List<UserRecord>) {
        db.userRecordDao().clear()
        records.forEach { db.userRecordDao().upsert(it.toEntity()) }
    }

    override fun observeRecords(): Flow<List<UserRecord>> {
        return db.userRecordDao().observeRecords().map { it.map(::toRecord) }
    }

    override suspend fun getRecords(): List<UserRecord> {
        return db.userRecordDao().getRecords().map(::toRecord)
    }

    override suspend fun deleteRecord(id: String) {
        db.userRecordDao().delete(id)
    }

    override suspend fun deleteBySourceRecordId(sourceRecordId: String) {
        db.rawEventDao().deleteBySourceRecordId(sourceRecordId)
        // Slot records are recomputed by NormalizerService.  Do not delete a record here:
        // it may be a manual record with other raw contributors.
        db.recordSourceLinkDao().deleteBySourceRecordId(sourceRecordId)
    }

    override suspend fun linkSourceRecord(sourceRecordId: String, localRecordId: String) {
        db.recordSourceLinkDao().insert(RecordSourceLinkEntity(sourceRecordId, localRecordId))
    }

    override suspend fun replaceSlotRecord(record: UserRecord, sourceRecordIds: Set<String>) {
        db.withTransaction {
            db.userRecordDao().upsert(record.toEntity())
            db.recordSourceLinkDao().deleteByLocalRecordId(record.id)
            sourceRecordIds.forEach { db.recordSourceLinkDao().insert(RecordSourceLinkEntity(it, record.id)) }
        }
    }

    override suspend fun deleteRecordsBySourceType(sourceType: SourceType) {
        db.userRecordDao().deleteBySourceType(sourceType.name)
        if (sourceType == SourceType.HEALTH_CONNECT) db.recordSourceLinkDao().clear()
    }

    override suspend fun upsertCustomTag(groupId: String, label: String) {
        val normalized = label.trim()
        if (normalized.isEmpty()) return
        db.customTagDao().upsert(
            CustomTagEntity(
                id = "${groupId}_${normalized.lowercase().replace(" ", "_")}",
                groupId = groupId,
                label = normalized,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override fun observeCustomTags(): Flow<List<CustomTagEntity>> = db.customTagDao().observeTags()
    override suspend fun getCustomTags(): List<CustomTagEntity> = db.customTagDao().getTags()

    override suspend fun upsertReminder(reminder: ReminderEntity) {
        db.reminderDao().upsert(reminder)
    }

    override fun observeReminders(): Flow<List<ReminderEntity>> = db.reminderDao().observeReminders()
    override suspend fun getReminders(): List<ReminderEntity> = db.reminderDao().getReminders()
    override suspend fun deleteReminder(id: String) = db.reminderDao().delete(id)

    override suspend fun saveLastInput(medication: String, metricValues: Map<String, Int>) {
        db.lastInputDao().upsert(
            LastInputEntity(
                medicationFullText = medication,
                valuesJson = metricValues.toJsonObject()
            )
        )
    }

    override suspend fun getLastInput(): Pair<String, Map<String, Int>>? {
        val entity = db.lastInputDao().getLastInput() ?: return null
        return entity.medicationFullText to entity.valuesJson.toIntMap()
    }

    override suspend fun clearAll() {
        db.rawEventDao().clear()
        db.userRecordDao().clear()
        db.customTagDao().clear()
        db.recordSourceLinkDao().clear()
    }

    private fun UserRecord.toEntity(): UserRecordEntity {
        return UserRecordEntity(
            id = id,
            programId = programId,
            timestamp = timestamp.toEpochMilli(),
            measurementsJson = measurements.measurementsToJson(),
            eventsJson = events.ifEmpty { medicationEvent() }.eventsToJson(),
            dimensionsJson = dimensions.ifEmpty { tagDimensions() }.dimensionsToJson(),
            qualityJson = quality.toJson(),
            note = note,
            createdAt = createdAt.toEpochMilli(),
            updatedAt = updatedAt.toEpochMilli(),
            sourceType = sourceType.name,
            flag = flag.name
        )
    }

    private fun toRecord(entity: UserRecordEntity): UserRecord {
        val measurements = entity.measurementsJson.toMeasurements()
        val events = entity.eventsJson.toEvents()
        val dimensions = entity.dimensionsJson.toDimensions()
        val medication = events.firstOrNull { it.key == "medication" }
        val dimensionsByGroup = dimensions.groupBy { it.group }.mapValues { (_, values) -> values.map { it.label } }
        return UserRecord(
            id = entity.id,
            programId = entity.programId,
            timestamp = Instant.ofEpochMilli(entity.timestamp),
            measurements = measurements,
            events = events,
            dimensions = dimensions,
            quality = entity.qualityJson.toQuality(),
            medicationName = medication?.name.orEmpty(),
            medicationStatus = medication?.status?.let { runCatching { MedicationStatus.valueOf(it) }.getOrNull() }
                ?: MedicationStatus.NOT_RECORDED,
            doseValue = medication?.amount,
            doseUnit = medication?.unit.orEmpty(),
            healthyTags = dimensionsByGroup["healthy"].orEmpty(),
            unhealthyTags = dimensionsByGroup["unhealthy"].orEmpty(),
            symptomTags = dimensionsByGroup["symptoms"].orEmpty(),
            otherMedicationTags = dimensionsByGroup["other_medications"].orEmpty(),
            sideEffectTags = dimensionsByGroup["side_effects"].orEmpty(),
            customTags = dimensionsByGroup["custom"].orEmpty(),
            note = entity.note,
            createdAt = Instant.ofEpochMilli(entity.createdAt),
            updatedAt = Instant.ofEpochMilli(entity.updatedAt),
            sourceType = SourceType.valueOf(entity.sourceType),
            flag = RecordFlag.valueOf(entity.flag)
        )
    }

    private fun List<Measurement>.measurementsToJson(): String {
        return buildJsonArray {
            forEach { measurement ->
                add(buildJsonObject {
                    put("key", measurement.key)
                    put("value", measurement.value)
                    put("unit", measurement.unit)
                    measurement.group?.let { put("group", it) }
                })
            }
        }.toString()
    }

    private fun List<RecordEvent>.eventsToJson(): String {
        return buildJsonArray {
            forEach { event ->
                add(buildJsonObject {
                    put("key", event.key)
                    put("name", event.name)
                    put("status", event.status)
                    event.amount?.let { put("amount", it) }
                    event.unit?.let { put("unit", it) }
                })
            }
        }.toString()
    }

    private fun List<RecordDimension>.dimensionsToJson(): String {
        return buildJsonArray {
            forEach { dimension ->
                add(buildJsonObject {
                    put("group", dimension.group)
                    put("key", dimension.key)
                    put("label", dimension.label)
                })
            }
        }.toString()
    }

    private fun DataQuality.toJson(): String {
        return buildJsonObject {
            put("complete", complete)
            put("warnings", buildJsonArray { warnings.forEach { add(it) } })
        }.toString()
    }

    private fun String.toMeasurements(): List<Measurement> {
        return jsonArrayOrEmpty().mapNotNull { element ->
            val item = element.jsonObject
            val key = item["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val value = item["value"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val unit = item["unit"]?.jsonPrimitive?.contentOrNull ?: ""
            Measurement(
                key = key,
                value = value,
                unit = unit,
                group = item["group"]?.jsonPrimitive?.contentOrNull
            )
        }
    }

    private fun String.toEvents(): List<RecordEvent> {
        return jsonArrayOrEmpty().mapNotNull { element ->
            val item = element.jsonObject
            RecordEvent(
                key = item["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                name = item["name"]?.jsonPrimitive?.contentOrNull ?: "",
                status = item["status"]?.jsonPrimitive?.contentOrNull ?: "",
                amount = item["amount"]?.jsonPrimitive?.doubleOrNull,
                unit = item["unit"]?.jsonPrimitive?.contentOrNull
            )
        }
    }

    private fun String.toDimensions(): List<RecordDimension> {
        return jsonArrayOrEmpty().mapNotNull { element ->
            val item = element.jsonObject
            RecordDimension(
                group = item["group"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                key = item["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                label = item["label"]?.jsonPrimitive?.contentOrNull ?: item["key"]?.jsonPrimitive?.contentOrNull.orEmpty()
            )
        }
    }

    private fun String.toQuality(): DataQuality {
        return try {
            val item = json.parseToJsonElement(this).jsonObject
            DataQuality(
                complete = item["complete"]?.jsonPrimitive?.booleanOrNull ?: true,
                warnings = item["warnings"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            )
        } catch (_: Exception) {
            DataQuality()
        }
    }

    private fun String.jsonArrayOrEmpty(): JsonArray {
        return try {
            json.parseToJsonElement(this).jsonArray
        } catch (_: Exception) {
            JsonArray(emptyList())
        }
    }

    private fun Map<String, Int>.toJsonObject(): String {
        return buildJsonObject { forEach { (key, value) -> put(key, value) } }.toString()
    }

    private fun String.toIntMap(): Map<String, Int> {
        return try {
            json.parseToJsonElement(this).jsonObject.mapNotNull { (key, value) ->
                value.jsonPrimitive.intOrNull?.let { key to it }
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun UserRecord.medicationEvent(): List<RecordEvent> {
        if (medicationName.isBlank() && medicationStatus == MedicationStatus.NOT_RECORDED && doseValue == null) {
            return emptyList()
        }
        return listOf(
            RecordEvent(
                key = "medication",
                name = medicationName,
                status = medicationStatus.name,
                amount = doseValue,
                unit = doseUnit.ifBlank { null }
            )
        )
    }

    private fun UserRecord.tagDimensions(): List<RecordDimension> {
        return buildList {
            healthyTags.forEach { add(RecordDimension("healthy", it, it)) }
            unhealthyTags.forEach { add(RecordDimension("unhealthy", it, it)) }
            symptomTags.forEach { add(RecordDimension("symptoms", it, it)) }
            otherMedicationTags.forEach { add(RecordDimension("other_medications", it, it)) }
            sideEffectTags.forEach { add(RecordDimension("side_effects", it, it)) }
            customTags.forEach { add(RecordDimension("custom", it, it)) }
        }
    }
}
