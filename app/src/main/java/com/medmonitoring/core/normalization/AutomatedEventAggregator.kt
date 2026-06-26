package com.medmonitoring.core.normalization

import com.medmonitoring.core.domain.model.AggregationStrategy
import com.medmonitoring.core.domain.model.Measurement
import com.medmonitoring.core.domain.model.MetricComponent
import com.medmonitoring.core.domain.model.RawSourceEvent
import com.medmonitoring.core.domain.model.RecordDimension
import com.medmonitoring.core.domain.model.RecordFlag
import com.medmonitoring.core.domain.model.UserRecord
import com.medmonitoring.core.ingestion.AggregationProcessor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import java.time.Duration
import java.time.Instant

object AutomatedEventAggregator {
    private val json = Json { ignoreUnknownKeys = true }

    data class Result(
        val records: List<UserRecord>,
        val eventIds: List<String>
    )

    fun aggregate(
        events: List<RawSourceEvent>,
        metrics: List<MetricComponent>,
        programId: String,
        windowMinutes: Long,
        now: Instant = Instant.now()
    ): List<Result> {
        val metricById = metrics.associateBy { it.id }
        val parsed = events.map { event ->
            val root = json.parseToJsonElement(event.payloadJson).jsonObject
            ParsedEvent(
                event = event,
                timestamp = root["timestamp"]?.jsonPrimitive?.longOrNull
                    ?.let(Instant::ofEpochMilli) ?: event.sourceTimestamp ?: event.capturedAt,
                values = root.measurementValues(metrics),
                contextOnly = root["contextOnly"]?.jsonPrimitive?.booleanOrNull == true,
                contextTags = root["contextTags"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            )
        }
        val anomalous = parsed.filter { sample ->
            !sample.contextOnly &&
            sample.values.any { (id, value) ->
                metricById[id]?.normalRange?.contains(value.toInt()) == false
            }
        }
        val normal = parsed - anomalous.toSet()
        val windowMillis = Duration.ofMinutes(windowMinutes.coerceAtLeast(1)).toMillis()

        val anomalyResults = anomalous.map { sample ->
            Result(
                records = listOf(sample.toRecord(sample.event.id, programId, sample.timestamp, RecordFlag.Anomaly, now, metricById)),
                eventIds = listOf(sample.event.id)
            )
        }
        val aggregateResults = normal.groupBy { sample ->
            sample.event.sourceType to (sample.timestamp.toEpochMilli() / windowMillis)
        }.map { (key, samples) ->
            val timestamp = Instant.ofEpochMilli(key.second * windowMillis)
            val values = metrics.associate { metric ->
                val samplesForMetric = samples.mapNotNull { it.values[metric.id] }
                val strategy = metric.aggregationStrategy.takeUnless {
                    it == AggregationStrategy.None && samplesForMetric.size > 1
                } ?: AggregationStrategy.Average
                metric.id to AggregationProcessor.aggregate(samplesForMetric, strategy)
            }
            val source = samples.first().event.sourceType
            val primarySamples = samples.filterNot(ParsedEvent::contextOnly)
            if (primarySamples.none { it.values.isNotEmpty() }) {
                return@map Result(emptyList(), samples.map { it.event.id })
            }
            val record = ParsedEvent(
                primarySamples.first().event,
                timestamp,
                values.filterValues { it != null }.mapValues { it.value!! },
                false,
                samples.flatMap { it.contextTags }.distinct()
            )
                .toRecord("auto-${source.name}-${timestamp.toEpochMilli()}", programId, timestamp, RecordFlag.Normal, now, metricById)
            Result(listOf(record), samples.map { it.event.id })
        }
        return anomalyResults + aggregateResults
    }

    private data class ParsedEvent(
        val event: RawSourceEvent,
        val timestamp: Instant,
        val values: Map<String, Double>,
        val contextOnly: Boolean,
        val contextTags: List<String>
    ) {
        fun toRecord(
            id: String,
            programId: String,
            recordTimestamp: Instant,
            flag: RecordFlag,
            now: Instant,
            metricById: Map<String, MetricComponent>
        ) =
            UserRecord(
                id = id,
                programId = programId,
                timestamp = recordTimestamp,
                measurements = values.map { (metricId, value) ->
                    val metric = metricById[metricId]
                    Measurement(metricId, value.toDouble(), metric?.unit.orEmpty())
                },
                dimensions = (contextTags + CalendarContextTagger.tags(recordTimestamp))
                    .distinct()
                    .map { label -> RecordDimension("context", label.toKey(), label) },
                note = null,
                createdAt = now,
                updatedAt = now,
                sourceType = event.sourceType,
                flag = flag
            )
    }

    private fun JsonObject.measurementValues(metrics: List<MetricComponent>): Map<String, Double> {
        val values = this["measurements"]?.jsonObject.orEmpty()
        return metrics.mapNotNull { metric ->
            val value = values[metric.id]?.jsonPrimitive?.doubleOrNull
                ?: this[metric.id]?.jsonPrimitive?.doubleOrNull
                ?: return@mapNotNull null
            metric.id to value
        }.toMap()
    }

    private fun String.toKey(): String = lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}
