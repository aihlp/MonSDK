package com.medmonitoring.core.ingestion

import com.medmonitoring.core.domain.model.RawSourceEvent
import com.medmonitoring.core.domain.model.SourceType
import com.medmonitoring.core.domain.repository.EventRepository
import com.medmonitoring.core.normalization.NormalizerService
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface IngestionAdapter {
    suspend fun ingestData(payloadJson: String, source: SourceType = SourceType.MANUAL): IngestionResult
}

data class IngestionResult(val rawEventId: String)

@Singleton
class IngestionManager @Inject constructor(
    private val repository: EventRepository,
    private val normalizerService: NormalizerService
) : IngestionAdapter {
    override suspend fun ingestData(payloadJson: String, source: SourceType): IngestionResult {
        val rawEventId = UUID.randomUUID().toString()
        repository.insertRawEvent(
            RawSourceEvent(
                id = rawEventId,
                sourceType = source,
                payloadJson = payloadJson,
                capturedAt = Instant.now(),
                sourceTimestamp = null,
                schemaVersion = 1,
                error = null
            )
        )
        normalizerService.processPendingEvents()
        return IngestionResult(rawEventId)
    }
}
