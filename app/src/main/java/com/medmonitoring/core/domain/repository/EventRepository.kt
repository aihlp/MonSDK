package com.medmonitoring.core.domain.repository

import com.medmonitoring.core.domain.model.RawSourceEvent
import com.medmonitoring.core.domain.model.SourceType
import com.medmonitoring.core.domain.model.UserRecord
import com.medmonitoring.core.storage.entity.CustomTagEntity
import com.medmonitoring.core.storage.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

interface EventRepository {
    suspend fun insertRawEvent(event: RawSourceEvent)
    suspend fun getUnprocessedRawEvents(): List<RawSourceEvent>
    suspend fun getAllRawEvents(): List<RawSourceEvent>
    suspend fun markRawEventProcessed(eventId: String, error: String? = null)

    suspend fun upsertRecord(record: UserRecord)
    suspend fun replaceRecords(records: List<UserRecord>)
    fun observeRecords(): Flow<List<UserRecord>>
    suspend fun getRecords(): List<UserRecord>
    suspend fun deleteRecord(id: String)
    suspend fun deleteBySourceRecordId(sourceRecordId: String)
    suspend fun linkSourceRecord(sourceRecordId: String, localRecordId: String)
    suspend fun replaceSlotRecord(record: UserRecord, sourceRecordIds: Set<String>)
    suspend fun deleteRecordsBySourceType(sourceType: SourceType)

    suspend fun upsertCustomTag(groupId: String, label: String)
    fun observeCustomTags(): Flow<List<CustomTagEntity>>
    suspend fun getCustomTags(): List<CustomTagEntity>

    suspend fun upsertReminder(reminder: ReminderEntity)
    fun observeReminders(): Flow<List<ReminderEntity>>
    suspend fun getReminders(): List<ReminderEntity>
    suspend fun deleteReminder(id: String)

    suspend fun saveLastInput(eventText: String, metricValues: Map<String, Int>)
    suspend fun getLastInput(): Pair<String, Map<String, Int>>?

    suspend fun clearAll()
}
