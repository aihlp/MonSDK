package com.medmonitoring.core.reports

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.medmonitoring.core.domain.model.Measurement
import com.medmonitoring.core.domain.model.MedicationStatus
import com.medmonitoring.core.domain.model.RecordDimension
import com.medmonitoring.core.domain.model.RecordEvent
import com.medmonitoring.core.domain.model.UserRecord
import kotlinx.serialization.json.*
import java.io.File
import java.time.Instant

object JsonRecordCodec {
    private const val FORMAT = "medmonitor-records"
    private const val LEGACY_FORMAT = "lisinopril-monitor-records"

    fun encode(records: List<UserRecord>): String {
        return buildJsonObject {
            put("format", FORMAT)
            put("version", 1)
            put("records", buildJsonArray {
                records.forEach { record ->
                    add(buildJsonObject {
                        put("id", record.id)
                        put("timestamp", record.timestamp.toEpochMilli())
                        put("medicationName", record.medicationName)
                        put("medicationStatus", record.medicationStatus.name)
                        record.doseValue?.let { put("doseValue", it) }
                        put("doseUnit", record.doseUnit)
                        put("measurements", record.measurements.measurementsToJsonArray())
                        put("events", record.events.eventsToJsonArray())
                        put("dimensions", record.dimensions.dimensionsToJsonArray())
                        put("healthyTags", record.healthyTags.stringsToJsonArray())
                        put("unhealthyTags", record.unhealthyTags.stringsToJsonArray())
                        put("symptomTags", record.symptomTags.stringsToJsonArray())
                        put("otherMedicationTags", record.otherMedicationTags.stringsToJsonArray())
                        put("sideEffectTags", record.sideEffectTags.stringsToJsonArray())
                        put("customTags", record.customTags.stringsToJsonArray())
                        record.note?.let { put("note", it) }
                        put("createdAt", record.createdAt.toEpochMilli())
                        put("updatedAt", record.updatedAt.toEpochMilli())
                    })
                }
            })
        }.toString()
    }

    fun decode(json: String): List<UserRecord> {
        val root = Json.parseToJsonElement(json).jsonObject
        val format = root["format"]?.jsonPrimitive?.contentOrNull
        require(format == null || format == FORMAT || format == LEGACY_FORMAT) {
            "Unsupported MedMonitor record format: $format"
        }
        return root["records"]!!.jsonArray.map { element ->
            val item = element.jsonObject
            val events = item.events()
            val dimensions = item.dimensions()
            val primaryEvent = events.firstOrNull()
            UserRecord(
                id = item["id"]!!.jsonPrimitive.content,
                timestamp = Instant.ofEpochMilli(item["timestamp"]!!.jsonPrimitive.long),
                medicationName = item["medicationName"]?.jsonPrimitive?.contentOrNull ?: primaryEvent?.name.orEmpty(),
                medicationStatus = item["medicationStatus"]?.jsonPrimitive?.contentOrNull
                    ?.let { runCatching { MedicationStatus.valueOf(it) }.getOrNull() }
                    ?: primaryEvent?.status.toMedicationStatus(),
                doseValue = item["doseValue"]?.jsonPrimitive?.doubleOrNull ?: primaryEvent?.amount,
                doseUnit = item["doseUnit"]?.jsonPrimitive?.contentOrNull ?: primaryEvent?.unit.orEmpty(),
                measurements = item.measurements(),
                events = events,
                dimensions = dimensions,
                healthyTags = item.listOrDimensions("healthyTags", dimensions, "healthy"),
                unhealthyTags = item.listOrDimensions("unhealthyTags", dimensions, "unhealthy"),
                symptomTags = item.listOrDimensions("symptomTags", dimensions, "symptoms"),
                otherMedicationTags = item.listOrDimensions("otherMedicationTags", dimensions, "other_medications"),
                sideEffectTags = item.listOrDimensions("sideEffectTags", dimensions, "side_effects"),
                customTags = item.listOrDimensions("customTags", dimensions, "custom"),
                note = item["note"]?.jsonPrimitive?.contentOrNull,
                createdAt = Instant.ofEpochMilli(item["createdAt"]!!.jsonPrimitive.long),
                updatedAt = Instant.ofEpochMilli(item["updatedAt"]!!.jsonPrimitive.long)
            )
        }
    }

    private fun List<String>.stringsToJsonArray(): JsonArray = buildJsonArray { forEach { add(it) } }
    private fun JsonObject.list(name: String): List<String> = this[name]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
    private fun JsonObject.listOrDimensions(
        name: String,
        dimensions: List<RecordDimension>,
        group: String
    ): List<String> = if (containsKey(name)) list(name) else dimensions.filter { it.group == group }.map { it.label }
    private fun List<Measurement>.measurementsToJsonArray(): JsonArray = buildJsonArray {
        forEach { measurement ->
            add(buildJsonObject {
                put("key", measurement.key)
                put("value", measurement.value)
                put("unit", measurement.unit)
                measurement.group?.let { put("group", it) }
            })
        }
    }
    private fun List<RecordEvent>.eventsToJsonArray(): JsonArray = buildJsonArray {
        forEach { event ->
            add(buildJsonObject {
                put("key", event.key)
                put("name", event.name)
                put("status", event.status)
                event.amount?.let { put("amount", it) }
                event.unit?.let { put("unit", it) }
            })
        }
    }
    private fun List<RecordDimension>.dimensionsToJsonArray(): JsonArray = buildJsonArray {
        forEach { dimension ->
            add(buildJsonObject {
                put("group", dimension.group)
                put("key", dimension.key)
                put("label", dimension.label)
            })
        }
    }
    private fun JsonObject.measurements(): List<Measurement> {
        val generic = this["measurements"]?.jsonArray?.mapNotNull { element ->
            val item = element.jsonObject
            Measurement(
                key = item["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                value = item["value"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
                unit = item["unit"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                group = item["group"]?.jsonPrimitive?.contentOrNull
            )
        }.orEmpty()
        if (generic.isNotEmpty()) return generic
        return listOfNotNull(
            this["systolic"]?.jsonPrimitive?.doubleOrNull?.let { Measurement("systolic", it, "mmHg") },
            this["diastolic"]?.jsonPrimitive?.doubleOrNull?.let { Measurement("diastolic", it, "mmHg") },
            this["pulse"]?.jsonPrimitive?.doubleOrNull?.let { Measurement("pulse", it, "bpm") }
        )
    }
    private fun JsonObject.events(): List<RecordEvent> =
        this["events"]?.jsonArray?.mapNotNull { element ->
            val item = element.jsonObject
            RecordEvent(
                key = item["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                status = item["status"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                amount = item["amount"]?.jsonPrimitive?.doubleOrNull,
                unit = item["unit"]?.jsonPrimitive?.contentOrNull
            )
        }.orEmpty()
    private fun JsonObject.dimensions(): List<RecordDimension> =
        this["dimensions"]?.jsonArray?.mapNotNull { element ->
            val item = element.jsonObject
            RecordDimension(
                group = item["group"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                key = item["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                label = item["label"]?.jsonPrimitive?.contentOrNull.orEmpty()
            )
        }.orEmpty()

    private fun String?.toMedicationStatus(): MedicationStatus = when {
        this == null -> MedicationStatus.NOT_RECORDED
        equals("taken", ignoreCase = true) || equals(MedicationStatus.TAKEN.name, ignoreCase = true) -> MedicationStatus.TAKEN
        equals("missed", ignoreCase = true) || equals(MedicationStatus.MISSED.name, ignoreCase = true) -> MedicationStatus.MISSED
        else -> MedicationStatus.NOT_RECORDED
    }
}

object CsvRecordCodec {
    fun encode(records: List<UserRecord>): String {
        val metricKeys = records.flatMap { record -> record.measurements.map { it.key } }.distinct()
        return buildString {
            appendLine((listOf("datetime", "event_key", "event_status", "event_name", "event_amount", "event_unit") + metricKeys + listOf("dimensions", "note")).joinToString(","))
            records.sortedBy { it.timestamp }.forEach { record ->
                val values = record.measurements.associate { it.key to it.value }
                val event = record.events.firstOrNull() ?: record.legacyEvent()
                appendLine(
                    listOf(
                        record.timestamp,
                        event?.key.orEmpty(),
                        event?.status.orEmpty(),
                        event?.name.orEmpty(),
                        event?.amount ?: "",
                        event?.unit.orEmpty()
                    ) + metricKeys.map { values[it] ?: "" } + listOf(
                        record.csvDimensions().joinToString("|") { "${it.group}:${it.key}:${it.label}" },
                        record.note.orEmpty()
                    ).joinToString(",") { "\"${it.toString().replace("\"", "\"\"")}\"" }
                )
            }
        }
    }

    fun decode(csv: String): List<UserRecord> {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        val header = lines.firstOrNull()?.let(::parseLine).orEmpty()
        if ("event_key" !in header) return decodeLegacy(csv)
        val metricStart = 6
        val dimensionsIndex = header.indexOf("dimensions")
        val noteIndex = header.indexOf("note")
        val metricKeys = header.subList(metricStart, dimensionsIndex.coerceAtLeast(metricStart))
        return lines.asSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val cells = parseLine(line)
                val now = Instant.now()
                val event = cells.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { key ->
                    RecordEvent(
                        key = key,
                        status = cells.getOrNull(2).orEmpty(),
                        name = cells.getOrNull(3).orEmpty(),
                        amount = cells.getOrNull(4)?.toDoubleOrNull(),
                        unit = cells.getOrNull(5)?.takeIf { it.isNotBlank() }
                    )
                }
                val dimensions = cells.getOrNull(dimensionsIndex).orEmpty().splitDimensions()
                UserRecord(
                    id = java.util.UUID.randomUUID().toString(),
                    timestamp = runCatching { Instant.parse(cells[0]) }.getOrDefault(now),
                    medicationName = event?.name.orEmpty(),
                    medicationStatus = event?.status.toMedicationStatus(),
                    doseValue = event?.amount,
                    doseUnit = event?.unit.orEmpty(),
                    measurements = metricKeys.mapIndexedNotNull { index, key ->
                        cells.getOrNull(metricStart + index)?.toDoubleOrNull()?.let { Measurement(key, it, "") }
                    },
                    events = listOfNotNull(event),
                    dimensions = dimensions,
                    healthyTags = dimensions.labelsFor("healthy"),
                    unhealthyTags = dimensions.labelsFor("unhealthy"),
                    symptomTags = dimensions.labelsFor("symptoms"),
                    otherMedicationTags = dimensions.labelsFor("other_medications"),
                    sideEffectTags = dimensions.labelsFor("side_effects"),
                    customTags = dimensions.labelsFor("custom"),
                    note = cells.getOrNull(noteIndex)?.takeIf { it.isNotBlank() },
                    createdAt = now,
                    updatedAt = now
                )
            }
            .toList()
    }

    private fun decodeLegacy(csv: String): List<UserRecord> {
        return csv.lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val cells = parseLine(line)
                if (cells.size < 13) return@mapNotNull null
                val status = runCatching { MedicationStatus.valueOf(cells[1]) }.getOrDefault(MedicationStatus.TAKEN)
                val doseParts = cells[2].trim().split(" ")
                val now = Instant.now()
                val event = RecordEvent(
                    key = "medication",
                    name = "Medication",
                    status = status.name.lowercase(),
                    amount = doseParts.firstOrNull()?.toDoubleOrNull(),
                    unit = doseParts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "mg"
                )
                val dimensions = buildList {
                    addAll(cells.getOrNull(6).orEmpty().splitTags().toDimensions("healthy"))
                    addAll(cells.getOrNull(7).orEmpty().splitTags().toDimensions("unhealthy"))
                    addAll(cells.getOrNull(8).orEmpty().splitTags().toDimensions("symptoms"))
                    addAll(cells.getOrNull(9).orEmpty().splitTags().toDimensions("other_medications"))
                    addAll(cells.getOrNull(10).orEmpty().splitTags().toDimensions("side_effects"))
                    addAll(cells.getOrNull(11).orEmpty().splitTags().toDimensions("custom"))
                }
                UserRecord(
                    id = java.util.UUID.randomUUID().toString(),
                    timestamp = runCatching { Instant.parse(cells[0]) }.getOrDefault(now),
                    medicationName = event.name,
                    medicationStatus = status,
                    doseValue = event.amount,
                    doseUnit = event.unit.orEmpty(),
                    measurements = listOfNotNull(
                        cells.getOrNull(3)?.toDoubleOrNull()?.let { Measurement("systolic", it, "mmHg") },
                        cells.getOrNull(4)?.toDoubleOrNull()?.let { Measurement("diastolic", it, "mmHg") },
                        cells.getOrNull(5)?.toDoubleOrNull()?.let { Measurement("pulse", it, "bpm") }
                    ),
                    events = listOf(event),
                    dimensions = dimensions,
                    healthyTags = dimensions.labelsFor("healthy"),
                    unhealthyTags = dimensions.labelsFor("unhealthy"),
                    symptomTags = dimensions.labelsFor("symptoms"),
                    otherMedicationTags = dimensions.labelsFor("other_medications"),
                    sideEffectTags = dimensions.labelsFor("side_effects"),
                    customTags = dimensions.labelsFor("custom"),
                    note = cells.getOrNull(12)?.takeIf { it.isNotBlank() },
                    createdAt = now,
                    updatedAt = now
                )
            }
            .toList()
    }

    private fun parseLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        result += current.toString()
        return result
    }

    private fun String.splitTags(): List<String> = split("|").map { it.trim() }.filter { it.isNotBlank() }

    private fun List<String>.toDimensions(group: String): List<RecordDimension> =
        map { RecordDimension(group, it.toDimensionKey(), it) }

    private fun String.splitDimensions(): List<RecordDimension> =
        split("|").mapNotNull { encoded ->
            val parts = encoded.split(":", limit = 3)
            if (parts.size != 3) return@mapNotNull null
            RecordDimension(parts[0], parts[1], parts[2])
        }

    private fun List<RecordDimension>.labelsFor(group: String): List<String> =
        filter { it.group == group }.map { it.label }

    private fun UserRecord.csvDimensions(): List<RecordDimension> =
        dimensions.ifEmpty {
            buildList {
                addAll(healthyTags.toDimensions("healthy"))
                addAll(unhealthyTags.toDimensions("unhealthy"))
                addAll(symptomTags.toDimensions("symptoms"))
                addAll(otherMedicationTags.toDimensions("other_medications"))
                addAll(sideEffectTags.toDimensions("side_effects"))
                addAll(customTags.toDimensions("custom"))
            }
        }

    private fun UserRecord.legacyEvent(): RecordEvent? {
        if (medicationStatus == MedicationStatus.NOT_RECORDED && medicationName.isBlank() && doseValue == null) return null
        return RecordEvent("medication", medicationName, medicationStatus.name.lowercase(), doseValue, doseUnit.ifBlank { null })
    }

    private fun String?.toMedicationStatus(): MedicationStatus = when {
        this == null -> MedicationStatus.NOT_RECORDED
        equals("taken", ignoreCase = true) || equals(MedicationStatus.TAKEN.name, ignoreCase = true) -> MedicationStatus.TAKEN
        equals("missed", ignoreCase = true) || equals(MedicationStatus.MISSED.name, ignoreCase = true) -> MedicationStatus.MISSED
        else -> MedicationStatus.NOT_RECORDED
    }

    private fun String.toDimensionKey(): String = lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}

object ReportShareHelper {
    fun shareTextFile(context: Context, text: String, fileName: String, mimeType: String) {
        val file = File(context.cacheDir, fileName)
        file.writeText(text)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    }
}
