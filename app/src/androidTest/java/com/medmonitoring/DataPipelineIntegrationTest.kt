package com.medmonitoring

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medmonitoring.core.domain.model.MedicationStatus
import com.medmonitoring.core.domain.model.RawSourceEvent
import com.medmonitoring.core.domain.model.SourceType
import com.medmonitoring.core.normalization.NormalizerService
import com.medmonitoring.core.storage.db.MedDatabase
import com.medmonitoring.core.storage.repository.EventRepositoryImpl
import com.medmonitoring.program.bloodpressure.BloodPressureDefinitions
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataPipelineIntegrationTest {
    private lateinit var database: MedDatabase
    private lateinit var repository: EventRepositoryImpl
    private lateinit var normalizer: NormalizerService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = EventRepositoryImpl(database)
        normalizer = NormalizerService(repository, BloodPressureDefinitions.program)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun healthConnectMetricsAndSecondaryContextAreStoredTogether() = runBlocking {
        insertHealthEvent(
            id = "bp",
            payload = """{"id":"hc-bp","sourceRecordId":"bp","timestamp":1781510400000,"systolic":120.0,"diastolic":75.0,"contextOnly":false,"contextTags":[]}"""
        )
        insertHealthEvent(
            id = "weight",
            payload = """{"id":"hc-weight","sourceRecordId":"weight","timestamp":1781511000000,"contextOnly":true,"contextTags":["context.nutrition.weight_above_target"]}"""
        )
        insertHealthEvent(
            id = "glucose",
            payload = """{"id":"hc-glucose","sourceRecordId":"glucose","timestamp":1781511600000,"contextOnly":true,"contextTags":["context.nutrition.glucose_high"]}"""
        )

        normalizer.processPendingEvents()

        val records = repository.getRecords()
        assertEquals(1, records.size)
        assertEquals(120.0, records.single().measurement("systolic")!!, 0.0)
        assertEquals(75.0, records.single().measurement("diastolic")!!, 0.0)
        assertEquals(MedicationStatus.NOT_RECORDED, records.single().medicationStatus)
        assertTrue("context.nutrition.weight_above_target" in records.single().dimensions.map { it.label })
        assertTrue("context.nutrition.glucose_high" in records.single().dimensions.map { it.label })
        assertTrue(repository.getUnprocessedRawEvents().isEmpty())
    }

    @Test
    fun manualRecordKeepsEnteredMetricsAndMedication() = runBlocking {
        repository.insertRawEvent(
            RawSourceEvent(
                id = "manual",
                sourceType = SourceType.MANUAL,
                payloadJson = """{"id":"manual-record","timestamp":1781510400000,"medicationName":"Lisinopril","medicationStatus":"TAKEN","doseValue":10.0,"doseUnit":"mg","systolic":150,"diastolic":90,"pulse":72,"healthyTags":[],"unhealthyTags":[],"symptomTags":[],"otherMedicationTags":[],"sideEffectTags":[],"customTags":[]}""",
                capturedAt = Instant.ofEpochMilli(1781510400000),
                sourceTimestamp = null,
                schemaVersion = 1,
                error = null
            )
        )

        normalizer.processPendingEvents()

        val record = repository.getRecords().single()
        assertEquals(150.0, record.measurement("systolic")!!, 0.0)
        assertEquals(90.0, record.measurement("diastolic")!!, 0.0)
        assertEquals(72.0, record.measurement("pulse")!!, 0.0)
        assertEquals(MedicationStatus.TAKEN, record.medicationStatus)
        assertEquals(SourceType.MANUAL, record.sourceType)
    }

    @Test
    fun slotRecordRebuildsForRepeatedSyncSourceUpdateAndDelete() = runBlocking {
        val at = Instant.parse("2026-06-15T06:00:00Z")
        insertHealthEvent("a", "a", at, """{"id":"hc-a","timestamp":${at.toEpochMilli()},"systolic":120.0,"contextOnly":false,"contextTags":[]}""")
        insertHealthEvent("b", "b", at.plusSeconds(60), """{"id":"hc-b","timestamp":${at.plusSeconds(60).toEpochMilli()},"diastolic":80.0,"contextOnly":false,"contextTags":[]}""")
        normalizer.processPendingEvents()
        val automatic = repository.getRecords().single()
        assertEquals(120.0, automatic.measurement("systolic")!!, 0.0)
        assertEquals(80.0, automatic.measurement("diastolic")!!, 0.0)

        // A manually edited slot owns its entered metric; the next sync may only fill the gap.
        repository.insertRawEvent(RawSourceEvent("manual", SourceType.MANUAL,
            """{"id":"manual","timestamp":${at.toEpochMilli()},"systolic":155.0,"note":"edited"}""",
            at, at, 1, null))
        normalizer.processPendingEvents()
        val manual = repository.getRecords().single()
        assertEquals(155.0, manual.measurement("systolic")!!, 0.0)
        assertEquals(80.0, manual.measurement("diastolic")!!, 0.0)
        assertEquals("edited", manual.note)

        // Re-upserting the same provider source is an update, not a duplicate contributor.
        insertHealthEvent("b", "b", at.plusSeconds(60), """{"id":"hc-b","timestamp":${at.plusSeconds(60).toEpochMilli()},"diastolic":85.0,"contextOnly":false,"contextTags":[]}""")
        normalizer.processPendingEvents()
        assertEquals(155.0, repository.getRecords().single().measurement("systolic")!!, 0.0)
        assertEquals(85.0, repository.getRecords().single().measurement("diastolic")!!, 0.0)

        normalizer.deleteSourceRecord("b")
        val afterDelete = repository.getRecords().single()
        assertEquals(155.0, afterDelete.measurement("systolic")!!, 0.0)
        assertEquals(null, afterDelete.measurement("diastolic"))
        assertEquals("edited", afterDelete.note)
    }

    @Test
    fun decimalGlucoseIsNotTruncatedDuringSlotAggregation() = runBlocking {
        normalizer = NormalizerService(repository, BloodPressureDefinitions.program.copy(
            metricComponents = listOf(BloodPressureDefinitions.program.metricComponents.first().copy(id = "level", unit = "mmol/L"))
        ))
        val at = Instant.parse("2026-06-15T06:00:00Z")
        insertHealthEvent("glucose", "glucose", at, """{"id":"hc-g","timestamp":${at.toEpochMilli()},"level":5.7,"contextOnly":false,"contextTags":[]}""")
        normalizer.processPendingEvents()
        assertEquals(5.7, repository.getRecords().single().measurement("level")!!, 0.0)
    }

    private suspend fun insertHealthEvent(id: String, payload: String) {
        val timestamp = Instant.ofEpochMilli(1781510400000)
        repository.insertRawEvent(
            RawSourceEvent(
                id = "hc-$id",
                sourceType = SourceType.HEALTH_CONNECT,
                payloadJson = payload,
                capturedAt = timestamp,
                sourceTimestamp = timestamp,
                schemaVersion = 1,
                error = null,
                sourceRecordId = id
            )
        )
    }

    private suspend fun insertHealthEvent(id: String, sourceRecordId: String, timestamp: Instant, payload: String) {
        repository.insertRawEvent(RawSourceEvent("hc-$id", SourceType.HEALTH_CONNECT, payload, timestamp, timestamp, 1, null, sourceRecordId))
    }

    private fun com.medmonitoring.core.domain.model.UserRecord.measurement(metricId: String): Double? =
        measurements.firstOrNull { it.key == metricId }?.value
}
