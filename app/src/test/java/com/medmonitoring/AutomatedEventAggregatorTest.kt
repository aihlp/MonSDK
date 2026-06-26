package com.medmonitoring

import com.medmonitoring.core.domain.model.AggregationStrategy
import com.medmonitoring.core.domain.model.MetricComponent
import com.medmonitoring.core.domain.model.MetricInputStyle
import com.medmonitoring.core.domain.model.MetricZonePalette
import com.medmonitoring.core.domain.model.RawSourceEvent
import com.medmonitoring.core.domain.model.RecordFlag
import com.medmonitoring.core.domain.model.MedicationStatus
import com.medmonitoring.core.domain.model.SourceType
import com.medmonitoring.core.normalization.AutomatedEventAggregator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class AutomatedEventAggregatorTest {
    private val palette = MetricZonePalette("", "", "", "", "", "")
    private val metrics = listOf(
        MetricComponent("systolic", "SYS", "mmHg", 70..220, 90..129, palette = palette),
        MetricComponent("diastolic", "DIA", "mmHg", 40..140, 60..79, palette = palette),
        MetricComponent(
            "pulse", "Pulse", "bpm", 40..180, 60..100,
            inputStyle = MetricInputStyle.HorizontalWheel,
            palette = palette,
            aggregationStrategy = AggregationStrategy.Average
        )
    )

    @Test
    fun combinesNormalSamplesInsideAnHour() {
        val events = (0 until 10).map { index ->
            event("hr-$index", "2026-06-12T10:${index.toString().padStart(2, '0')}:00Z", "pulse", 60 + index * 2)
        }

        val results = AutomatedEventAggregator.aggregate(events, metrics, "test", 60, Instant.EPOCH)

        assertEquals(1, results.size)
        assertEquals(69.0, results.single().records.single().measurement("pulse")!!, 0.0)
        assertEquals(10, results.single().eventIds.size)
    }

    @Test
    fun emitsAnomalySeparatelyFromNormalWindow() {
        val events = listOf(
            event("normal", "2026-06-12T10:00:00Z", "pulse", 70),
            event("anomaly", "2026-06-12T10:10:00Z", "pulse", 140),
            event("normal-2", "2026-06-12T10:20:00Z", "pulse", 80)
        )

        val records = AutomatedEventAggregator.aggregate(events, metrics, "test", 60, Instant.EPOCH)
            .flatMap { it.records }

        assertEquals(2, records.size)
        assertEquals(140.0, records.single { it.flag == RecordFlag.Anomaly }.measurement("pulse")!!, 0.0)
        assertEquals(75.0, records.single { it.flag == RecordFlag.Normal }.measurement("pulse")!!, 0.0)
        assertEquals(
            setOf(MedicationStatus.NOT_RECORDED),
            records.map { it.medicationStatus }.toSet()
        )
    }

    @Test
    fun preservesDecimalMeasurementsAndProgramId() {
        val glucose = MetricComponent("glucose", "Glucose", "mmol/L", 2..30, 4..8, palette = palette)
        val event = RawSourceEvent(
            id = "glucose-1",
            sourceType = SourceType.HEALTH_CONNECT,
            payloadJson = """{"id":"glucose-1","timestamp":1781510400000,"glucose":4.6}""",
            capturedAt = Instant.ofEpochMilli(1781510400000),
            sourceTimestamp = Instant.ofEpochMilli(1781510400000),
            schemaVersion = 1,
            error = null
        )

        val record = AutomatedEventAggregator.aggregate(listOf(event), listOf(glucose), "glucose", 60, Instant.EPOCH)
            .single().records.single()

        assertEquals("glucose", record.programId)
        assertEquals(4.6, record.measurement("glucose")!!, 0.0)
    }

    private fun event(id: String, timestamp: String, metric: String, value: Int): RawSourceEvent {
        val instant = Instant.parse(timestamp)
        return RawSourceEvent(
            id = id,
            sourceType = SourceType.HEALTH_CONNECT,
            payloadJson = """{"id":"$id","timestamp":${instant.toEpochMilli()},"$metric":$value}""",
            capturedAt = instant,
            sourceTimestamp = instant,
            schemaVersion = 1,
            error = null
        )
    }

    private fun com.medmonitoring.core.domain.model.UserRecord.measurement(metricId: String): Double? =
        measurements.firstOrNull { it.key == metricId }?.value
}
